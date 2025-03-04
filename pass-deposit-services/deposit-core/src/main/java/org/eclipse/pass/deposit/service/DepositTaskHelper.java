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

import org.eclipse.pass.deposit.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.model.Packager;
import org.eclipse.pass.deposit.transport.devnull.DevNullTransport;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
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
    private static final String ERR_UPDATE_REPOCOPY = "Failed to create or update RepositoryCopy '%s' for %s";

    private final PassClient passClient;
    private final CriticalRepositoryInteraction cri;
    private final DevNullTransport devNullTransport;

    @Value("${pass.test.skip.deposits}")
    private Boolean skipDeploymentTestDeposits;

    @Value("${pass.deposit.retry.failed.enabled}")
    private Boolean retryFailedDepositsEnabled;

    @Autowired
    public DepositTaskHelper(PassClient passClient,
                             CriticalRepositoryInteraction cri,
                             DevNullTransport devNullTransport) {
        this.passClient = passClient;
        this.cri = cri;
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
            Submission includedSubmission = passClient.getObject(submission,
                "publication", "repositories", "submitter", "preparers", "grants", "effectivePolicies");
            DepositUtil.DepositWorkerContext dc = DepositUtil.toDepositWorkerContext(
                deposit, includedSubmission, depositSubmission, repo, packager, devNullTransport);
            dc.setSkipDeploymentTestDeposits(skipDeploymentTestDeposits);
            dc.setRetryFailedDepositsEnabled(retryFailedDepositsEnabled);
            DepositTask depositTask = new DepositTask(dc, passClient, cri);

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

}
