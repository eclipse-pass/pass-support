/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.pass.deposit.service;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.pass.deposit.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.builder.DepositSubmissionModelBuilder;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction.CriticalResult;
import org.eclipse.pass.deposit.model.DepositFile;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.model.Packager;
import org.eclipse.pass.deposit.model.Registry;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.IntegrationType;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.Source;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Processes an incoming {@code Submission} by composing and submitting a {@link DepositTask} for execution.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Service
public class SubmissionProcessor implements Consumer<Submission> {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionProcessor.class);

    private final String FAILED_TO_PROCESS_DEPOSIT = "Failed to process Deposit for tuple [%s, %s, %s]: %s";

    private final PassClient passClient;
    private final DepositSubmissionModelBuilder depositSubmissionModelBuilder;
    private final Registry<Packager> packagerRegistry;
    private final CriticalRepositoryInteraction critical;
    private final DepositTaskHelper depositTaskHelper;
    private final DepositServiceErrorHandler depositServiceErrorHandler;

    @Autowired
    public SubmissionProcessor(PassClient passClient, DepositSubmissionModelBuilder depositSubmissionModelBuilder,
                               Registry<Packager> packagerRegistry, DepositTaskHelper depositTaskHelper,
                               CriticalRepositoryInteraction critical,
                               DepositServiceErrorHandler errorHandler) {

        this.passClient = passClient;
        this.depositSubmissionModelBuilder = depositSubmissionModelBuilder;
        this.packagerRegistry = packagerRegistry;
        this.critical = critical;
        this.depositTaskHelper = depositTaskHelper;
        this.depositServiceErrorHandler = errorHandler;
    }

    @Override
    public void accept(Submission submission) {

        // Validates the incoming Submission, marks it as being IN_PROGRESS immediately.
        // If this fails, we've essentially lost a JMS message

        CriticalResult<DepositSubmission, Submission> result =
            critical.performCritical(submission.getId(), Submission.class,
                                     CriFunc.preCondition(),
                                     CriFunc.postCondition(),
                                     CriFunc.critical(depositSubmissionModelBuilder));

        if (!result.success()) {
            // Throw DepositServiceRuntimeException, which will be processed by the DepositServiceErrorHandler
            final String msg_tmpl = "Unable to update status of %s to '%s': %s";

            if (result.throwable().isPresent()) {
                Throwable cause = result.throwable().get();
                String msg = format(msg_tmpl, submission.getId(), AggregatedDepositStatus.IN_PROGRESS,
                    cause.getMessage());
                throw new DepositServiceRuntimeException(msg, cause, submission);
            } else {
                String msg = format(msg_tmpl, submission.getId(), AggregatedDepositStatus.IN_PROGRESS,
                                    "no cause was present, probably a pre- or post-condition was not satisfied.");
                LOG.debug(msg);
                return;
            }
        }

        Submission updatedS = result.resource().orElseThrow(() ->
                                                                new DepositServiceRuntimeException(
                                                                    "Missing expected Submission " + submission.getId(),
                                                                    submission));

        DepositSubmission depositSubmission = result.result().orElseThrow(() ->
                                                                              new DepositServiceRuntimeException(
                                                                                  "Missing expected DepositSubmission",
                                                                                  submission));

        LOG.info("Processing Submission {}", submission.getId());

        updatedS.getRepositories()
                .stream()
                .map(repo -> {
                    try {
                        return passClient.getObject(repo);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to retrieve repository: " + repo.getId(), e);
                    }
                })
                .filter(repo -> IntegrationType.WEB_LINK != repo.getIntegrationType())
                .forEach(repo -> {
                    try {
                        submitDeposit(updatedS, depositSubmission, repo);
                    } catch (Exception e) {
                        depositServiceErrorHandler.handleError(e);
                    }
                });
    }

    private void submitDeposit(Submission submission, DepositSubmission depositSubmission, Repository repo) {
        Deposit deposit = null;
        Packager packager = null;
        try {
            deposit = createDeposit(submission, repo);

            for (final String key : getLookupKeys(repo)) {
                if ((packager = packagerRegistry.get(key)) != null) {
                    break;
                }
            }

            if (packager == null) {
                throw new NullPointerException(format("No Packager found for tuple [%s, %s, %s]: " +
                                                      "Missing Packager for Repository named '%s' (key: %s)",
                                                      submission.getId(), deposit.getId(), repo.getId(), repo.getName(),
                                                      repo.getRepositoryKey()));
            }
            passClient.createObject(deposit);
        } catch (Exception e) {
            String msg = format(FAILED_TO_PROCESS_DEPOSIT, submission.getId(), repo.getId(),
                                (deposit == null) ? "null" : deposit.getId(), e.getMessage());
            throw new DepositServiceRuntimeException(msg, e, deposit);
        }

        depositTaskHelper.submitDeposit(submission, depositSubmission, repo, deposit, packager);
    }

    static class CriFunc {

        /**
         * Answers the critical function which builds a {@link DepositSubmission} from the Submission, then sets the
         * {@link AggregatedDepositStatus} to {@code IN_PROGRESS}.
         *
         * @param modelBuilder the model builder used to build the {@code DepositSubmission}
         * @return the Function that builds the DepositSubmission and sets the aggregated deposit status on the
         * Submission
         */
        static Function<Submission, DepositSubmission> critical(DepositSubmissionModelBuilder modelBuilder) {
            return (s) -> {
                DepositSubmission ds = null;
                try {
                    ds = modelBuilder.build(s.getId().toString());
                } catch (IOException ex) {
                    throw new DepositServiceRuntimeException("Error building deposit submission", ex);
                }
                s.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
                return ds;
            };
        }

        /**
         * Answers a BiPredicate that verifies the state of the Submission and the DepositSubmission.
         * <ul>
         *     <li>The Submission.AggregatedDepositStatus must be IN_PROGRESS</li>
         *     <li>The DepositSubmission must have at least one {@link DepositSubmission#getFiles() file} attached</li>
         *     <li>Each DepositFile must have a non-empty {@link DepositFile#getLocation() location}</li>
         * </ul>
         *
         * @return a BiPredicate that verifies the state created or set by the critical function on repository resources
         * @throws IllegalStateException if the Submission, DepositSubmission, or DepositFiles have invalid state
         */
        static BiPredicate<Submission, DepositSubmission> postCondition() {
            return (s, ds) -> {
                if (AggregatedDepositStatus.IN_PROGRESS != s.getAggregatedDepositStatus()) {
                    String msg = "Update postcondition failed for %s: expected status '%s' but actual status is " +
                                 "'%s'";
                    throw new IllegalStateException(String.format(msg, s.getId(), AggregatedDepositStatus.IN_PROGRESS, s
                        .getAggregatedDepositStatus()));
                }

                // Treat the lack of files on the Submission as a FAILURE, as that is not a transient issue
                // (that is, files will not magically appear on the Submission in the future).

                if (ds.getFiles().size() < 1) {
                    String msg = "Update postcondition failed for %s: the DepositSubmission has no files " +
                                 "attached! (Hint: check the incoming links to the Submission)";
                    throw new IllegalStateException(String.format(msg, s.getId()));
                }

                // Each DepositFile must have a URI that links to its content
                String filesMissingLocations = ds.getFiles().stream()
                                                 .filter(df -> df.getLocation() == null || df.getLocation().trim()
                                                                                             .length() == 0)
                                                 .map(DepositFile::getName)
                                                 .collect(Collectors.joining(", "));

                if (filesMissingLocations != null && filesMissingLocations.length() > 0) {
                    String msg = "Update postcondition failed for %s: the following DepositFiles are missing " +
                                 "URIs referencing their binary content: %s";
                    throw new IllegalStateException(String.format(msg, s.getId(), filesMissingLocations));
                }

                return true;
            };
        }

        /**
         * Answers a Predicate that will accept the Submission for processing if it is accepted.
         *
         * @return a Predicate that invokes the precondition
         */
        static Predicate<Submission> preCondition() {
            return CriFunc::isSubmittedByUser;
        }

        /**
         * Returns {@code true} if the {@code Submission} was submitted using the PASS UI, and if the user of the UI has
         * interactively pressed the "Submit" button.
         *
         * @param submission the Submission
         * @return {@code true} if the {@code Submission} was submitted by a user of the PASS UI
         * @see
         * <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Submission.md">Submission model documentation</a>
         */
        private static boolean isSubmittedByUser(Submission submission) {

            if (submission == null) {
                LOG.debug("Null submissions not accepted for processing.");
                return false;
            }

            if (submission.getSubmitted() != null && submission.getSubmitted() == Boolean.FALSE) {
                LOG.debug("Submission {} will not be accepted for processing: submitted = {}, " +
                        "expected submitted = true", submission.getId(), submission.getSubmitted());
                return false;
            }

            if (submission.getSource() != Source.PASS) {
                LOG.debug("Submission {} will not be accepted for processing: source = {}, expected source = {}",
                    submission.getId(), submission.getSource(), Source.PASS);
                return false;
            }

            // Currently we dis-allow FAILED Submissions; the SubmissionProcessor is not capable of "re-processing"
            // failures.
            if (submission.getAggregatedDepositStatus() != AggregatedDepositStatus.NOT_STARTED) {
                LOG.debug("Submission {} will not be accepted for processing: status = {}, expected status = {}",
                    submission.getId(), submission.getAggregatedDepositStatus(), AggregatedDepositStatus.NOT_STARTED);
                return false;
            }

            return true;
        }
    }

    static Collection<String> getLookupKeys(Repository repo) {
        final List<String> keys = new ArrayList<>();

        ofNullable(repo.getRepositoryKey()).ifPresent(keys::add);
        ofNullable(repo.getName()).ifPresent(keys::add);
        ofNullable(repo.getId()).map(Object::toString).ifPresent(keys::add);

        return keys;
    }

    private static Deposit createDeposit(Submission submission, Repository repo) {
        Deposit deposit;
        deposit = new Deposit();
        deposit.setRepository(repo);
        deposit.setSubmission(submission);
        return deposit;
    }

}
