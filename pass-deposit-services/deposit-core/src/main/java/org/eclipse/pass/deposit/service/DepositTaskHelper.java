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

import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.lang.System.identityHashCode;
import static org.eclipse.deposit.util.loggers.Loggers.WORKERS_LOGGER;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.deposit.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.RemedialDepositException;
import org.eclipse.pass.deposit.config.repository.Repositories;
import org.eclipse.pass.deposit.config.repository.RepositoryConfig;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction.CriticalResult;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.model.Packager;
import org.eclipse.pass.deposit.status.DepositStatusProcessor;
import org.eclipse.pass.deposit.transport.devnull.DevNullTransport;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encapsulates functionality common to performing the submission of a Deposit to the TaskExecutor.
 * <p>
 * This functionality is useful when creating <em>new</em> Deposits, as well as re-trying existing Deposits which have
 * failed.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class DepositTaskHelper {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionProcessor.class);

    public static final String FAILED_TO_PROCESS_DEPOSIT = "Failed to process Deposit for tuple [%s, %s, %s]: %s";
    public static final String MISSING_PACKAGER = "No Packager found for tuple [{}, {}, {}]: " +
                                                  "Missing Packager for Repository named '{}', marking Deposit as " +
                                                  "FAILED.";
    private static final String PRECONDITION_FAILED = "Refusing to update {}, the following pre-condition failed: ";
    private static final String ERR_PARSING_STATUS_DOC = "Failed to update deposit status for [%s], parsing the " +
                                                         "status document referenced by %s failed: %s";
    private static final String ERR_MAPPING_STATUS = "Failed to update deposit status for [%s], mapping the status " +
                                                     "obtained from  %s failed";
    private static final String ERR_UPDATE_REPOCOPY = "Failed to create or update RepositoryCopy '%s' for %s";

    private final PassClient passClient;
    private final CriticalRepositoryInteraction cri;
    private final DevNullTransport devNullTransport;

    @Value("${pass.deposit.transport.swordv2.sleep-time-ms}")
    private long swordDepositSleepTimeMs;

    @Value("${jscholarship.hack.sword.statement.uri-prefix}")
    private String statementUriPrefix;

    @Value("${jscholarship.hack.sword.statement.uri-replacement}")
    private String statementUriReplacement;

    @Value("${pass.test.skip.deposits}")
    private Boolean skipDeploymentTestDeposits;

    private final Repositories repositories;

    @Autowired
    public DepositTaskHelper(PassClient passClient,
                             CriticalRepositoryInteraction cri,
                             Repositories repositories, DevNullTransport devNullTransport) {
        this.passClient = passClient;
        this.cri = cri;
        this.repositories = repositories;
        this.devNullTransport = devNullTransport;
    }

    /**
     * Composes a {@link DepositUtil.DepositWorkerContext} from the supplied arguments, and submits the context to the {@code
     * TaskExecutor}.  If the executor throws any exceptions, a {@link DepositServiceRuntimeException} will be thrown
     * referencing the {@code Deposit} that failed.
     * <p>
     * Note that the {@link DepositServiceErrorHandler} will be invoked to handle the {@code
     * DepositServiceRuntimeException}, which will attempt to mark the {@code Deposit} as FAILED.
     * </p>
     * <p>
     * The {@code DepositTask} composed by this helper method will only accept {@code Deposit} resources with
     * <em>intermediate</em> state.
     * </p>
     *
     * @param submission        the submission that the {@code deposit} belongs to
     * @param depositSubmission the submission in the Deposit Services' model
     * @param repo              the {@code Repository} that is the target of the {@code Deposit}, for which the
     * {@code Packager}
     *                          knows how to communicate
     * @param deposit           the {@code Deposit} that is being submitted
     * @param packager          the Packager for the {@code repo}
     */
    public void submitDeposit(Submission submission, DepositSubmission depositSubmission, Repository repo,
                              Deposit deposit, Packager packager) {
        try {
            Submission includedSubmission = passClient.getObject(submission, "grants");
            DepositUtil.DepositWorkerContext dc = DepositUtil.toDepositWorkerContext(
                deposit, includedSubmission, depositSubmission, repo, packager, devNullTransport,
                skipDeploymentTestDeposits);
            DepositTask depositTask = new DepositTask(dc, passClient, cri);
            depositTask.setSwordSleepTimeMs(swordDepositSleepTimeMs);
            depositTask.setPrefixToMatch(statementUriPrefix);
            depositTask.setReplacementPrefix(statementUriReplacement);

            WORKERS_LOGGER.debug("Submitting task ({}@{}) for tuple [{}, {}, {}]",
                                 depositTask.getClass().getSimpleName(), toHexString(identityHashCode(depositTask)),
                                 submission.getId(), repo.getId(), deposit.getId());

            depositTask.executeDeposit();
        } catch (Exception e) {
            // For example, if the task isn't accepted by the taskExecutor
            String msg = format(FAILED_TO_PROCESS_DEPOSIT, submission.getId(), repo.getId(),
                                (deposit == null) ? "null" : deposit.getId(), e.getMessage());
            throw new DepositServiceRuntimeException(msg, e, deposit);
        }
    }

    void processDepositStatus(Deposit deposit) {
        try {
            Repository repository = passClient.getObject(deposit.getRepository());
            RepositoryConfig repositoryConfig = repositories.getConfig(repository.getRepositoryKey());
            getDepositStatusProcessor(repositoryConfig)
                .ifPresentOrElse(
                    depositStatusProcessor ->
                        updateDepositStatusIfNeeded(deposit, repositoryConfig, depositStatusProcessor),
                    () -> LOG.info("No deposit status processor found for Deposit {}, status not updated",
                        deposit.getId())
                );
        } catch (IOException e) {
            LOG.error("Failed to update Deposit Status {}", deposit.getId(), e);
        }
    }

    void updateDepositRepositoryCopyStatus(Deposit deposit) {
        try {
            RepositoryCopy repoCopy = passClient.getObject(deposit.getRepositoryCopy());
            switch (deposit.getDepositStatus()) {
                case ACCEPTED -> {
                    LOG.debug("Deposit {} was accepted.", deposit.getId());
                    repoCopy.setCopyStatus(CopyStatus.COMPLETE);
                    passClient.updateObject(repoCopy);
                }
                case REJECTED -> {
                    LOG.debug("Deposit {} was rejected.", deposit.getId());
                    repoCopy.setCopyStatus(CopyStatus.REJECTED);
                    passClient.updateObject(repoCopy);
                }
                default -> {
                }
            }
        } catch (Exception e) {
            String msg = String.format(ERR_UPDATE_REPOCOPY, deposit.getRepositoryCopy(), deposit.getId());
            throw new DepositServiceRuntimeException(msg, e, deposit);
        }
    }

    private Optional<DepositStatusProcessor> getDepositStatusProcessor(RepositoryConfig repositoryConfig) {
        if (Objects.isNull(repositoryConfig)
            || Objects.isNull(repositoryConfig.getRepositoryDepositConfig())
            || Objects.isNull(repositoryConfig.getRepositoryDepositConfig().getDepositProcessing())
            || Objects.isNull(repositoryConfig.getRepositoryDepositConfig().getDepositProcessing().getProcessor())) {
            return Optional.empty();
        }
        return Optional.of(repositoryConfig.getRepositoryDepositConfig().getDepositProcessing().getProcessor());
    }

    private void updateDepositStatusIfNeeded(Deposit deposit, RepositoryConfig repositoryConfig,
                                             DepositStatusProcessor depositStatusProcessor) {
        CriticalResult<Deposit, Deposit> cr = cri.performCritical(
            deposit.getId(), Deposit.class,
            DepositStatusCriFunc.precondition(),
            DepositStatusCriFunc.postcondition(),
            DepositStatusCriFunc.critical(repositoryConfig, depositStatusProcessor)
        );

        if (!cr.success()) {
            if (cr.throwable().isPresent()) {
                Throwable t = cr.throwable().get();
                if (t instanceof RemedialDepositException) {
                    LOG.error(format("Failed to update Deposit %s", deposit.getId()), t);
                    return;
                }

                if (t instanceof DepositServiceRuntimeException) {
                    throw (DepositServiceRuntimeException) t;
                }

                if (cr.resource().isPresent()) {
                    throw new DepositServiceRuntimeException(
                        format("Failed to update Deposit %s: %s", deposit.getId(), t.getMessage()),
                        t, cr.resource().get());
                }
            }

            LOG.debug(format("Failed to update Deposit %s: no cause was present, probably a pre- or post-condition " +
                "was not satisfied.", deposit.getId()));
            return;
        }

        cr.result().ifPresent(this::updateDepositRepositoryCopyStatus);
        LOG.info("Successfully processed Deposit {}", deposit.getId());
    }

    static class DepositStatusCriFunc {

        /**
         * Preconditions:
         * <ul>
         *     <li>Deposit must be in an intermediate state</li>
         *     <li>Deposit must have a depositStatusRef</li>
         *     <li>Deposit must have a Repository</li>
         *     <li>Deposit must have a RepositoryCopy, even if it is just a placeholder</li>
         * </ul>
         */
        static Predicate<Deposit> precondition() {
            return (deposit) -> {
                if (DepositStatus.isTerminalStatus(deposit.getDepositStatus())) {
                    LOG.debug(PRECONDITION_FAILED + " Deposit.DepositStatus = {}, a terminal state.",
                              deposit.getId(), deposit.getDepositStatus());
                    return false;
                }

                if (deposit.getDepositStatusRef() == null || StringUtils.isBlank(deposit.getDepositStatusRef())) {
                    LOG.debug(PRECONDITION_FAILED + " missing Deposit status reference.", deposit.getId());
                    return false;
                }

                if (deposit.getRepository() == null) {
                    LOG.debug(PRECONDITION_FAILED + " missing Repository URI on the Deposit", deposit.getId());
                    return false;
                }

                RepositoryCopy repoCopy = deposit.getRepositoryCopy();

                if (repoCopy == null) {
                    LOG.debug(PRECONDITION_FAILED + " missing RepositoryCopy on the Deposit", deposit.getId());
                    return false;
                }

                return true;
            };
        }

        static BiPredicate<Deposit, Deposit> postcondition() {
            return (deposit, updatedDeposit) -> Objects.nonNull(updatedDeposit.getRepositoryCopy());
        }

        static Function<Deposit, Deposit> critical(RepositoryConfig repositoryConfig,
                                                   DepositStatusProcessor depositStatusProcessor) {
            return (deposit) -> {
                AtomicReference<DepositStatus> status = new AtomicReference<>();
                try {
                    status.set(depositStatusProcessor.process(deposit, repositoryConfig));
                } catch (Exception e) {
                    String msg = format(ERR_PARSING_STATUS_DOC,
                                        deposit.getId(), deposit.getDepositStatusRef(), e.getMessage());
                    throw new DepositServiceRuntimeException(msg, e, deposit);
                }

                if (status.get() == null) {
                    String msg = format(ERR_MAPPING_STATUS, deposit.getId(), deposit.getDepositStatusRef());
                    throw new DepositServiceRuntimeException(msg, deposit);
                }

                deposit.setDepositStatus(status.get());
                return deposit;
            };
        }
    }

}
