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

import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction.CriticalResult;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.model.Packager;
import org.eclipse.pass.deposit.transport.devnull.DevNullTransport;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for deposit messaging.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositUtil {

    private DepositUtil() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(DepositUtil.class);

    /**
     * Creates a convenience object that holds references to the objects related to performing a deposit.
     *
     * @param depositResource   the {@code Deposit} itself
     * @param submission        the {@code Submission} the {@code Deposit} is for
     * @param depositSubmission the {@code Submission} adapted to the deposit services model
     * @param repository        the {@code Repository} the custodial content should be transferred to
     * @param packager          the {@code Packager} used to assemble and stream the custodial content
     * @return an Object with references necessary for a {@code DepositTask} to be executed
     */
    public static DepositWorkerContext toDepositWorkerContext(Deposit depositResource, Submission submission,
                                                              DepositSubmission depositSubmission,
                                                              Repository repository, Packager packager,
                                                              DevNullTransport devNullTransport,
                                                              boolean skipDeploymentTestDeposits) {
        DepositWorkerContext dc = new DepositWorkerContext();
        dc.depositResource = depositResource;
        dc.depositSubmission = depositSubmission;
        dc.repository = repository;
        dc.packager = packager;
        dc.submission = submission;
        dc.devNullTransport = devNullTransport;
        dc.skipDeploymentTestDeposits = skipDeploymentTestDeposits;
        return dc;
    }

    /**
     * Uses the {@code cri} to update the referenced {@code Submission} {@code aggregatedDepositStatus} to {@code
     * FAILED}.  Submissions that are already in a <em>terminal</em> state will <em>not</em> be modified by this method.
     * That is to say, a {@code Submission} that has already been marked {@code ACCEPTED} or {@code REJECTED} cannot be
     * later marked as {@code FAILED} (even if the thread calling this method perceives a {@code Submission} as {@code
     * FAILED}, another thread may have succeeded in the interim).
     *
     * @param submissionId the id of the submission
     * @param cri           the critical repository interaction
     * @return true if the {@code Submission} was marked {@code FAILED}
     */
    public static boolean markSubmissionFailed(String submissionId, CriticalRepositoryInteraction cri) {
        CriticalResult<Submission, Submission> updateResult = cri.performCritical(
                submissionId, Submission.class,
                (submission) -> !AggregatedDepositStatus.isTerminalStatus(submission.getAggregatedDepositStatus()),
                (submission) -> submission.getAggregatedDepositStatus() == AggregatedDepositStatus.FAILED,
                (submission) -> {
                    submission.setAggregatedDepositStatus(AggregatedDepositStatus.FAILED);
                    return submission;
                });

        if (!updateResult.success()) {
            LOG.debug(
                    "Updating status of {} to {} failed: {}",
                    submissionId,
                    AggregatedDepositStatus.FAILED,
                    updateResult.throwable().isPresent() ?
                            updateResult.throwable().get().getMessage() : "(missing Throwable cause)",
                    updateResult.throwable().get());
        } else {
            LOG.debug("Marked {} as FAILED.", submissionId);
        }

        return updateResult.success();
    }

    /**
     * Uses the {@code cri} to update the referenced {@code Deposit} {@code DepositStatus} to {@code FAILED}.  Deposits
     * that are already in a <em>terminal</em> state will <em>not</em> be modified by this method. That is to say, a
     * {@code Deposit} that has already been marked {@code ACCEPTED} or {@code REJECTED} cannot be later marked as
     * {@code FAILED} (even if the thread calling this method perceives a {@code Deposit} as {@code FAILED}, another
     * thread may have succeeded in the interim).
     *
     * @param depositId the URI of the deposit
     * @param cri        the critical repository interaction
     * @return true if the {@code Deposit} was marked {@code FAILED}
     */
    public static boolean markDepositFailed(String depositId, CriticalRepositoryInteraction cri) {
        CriticalResult<Deposit, Deposit> updateResult = cri.performCritical(
                depositId, Deposit.class,
                (deposit) -> !DepositStatus.isTerminalStatus(deposit.getDepositStatus()),
                (deposit) -> deposit.getDepositStatus() == DepositStatus.FAILED,
                (deposit) -> {
                    deposit.setDepositStatus(DepositStatus.FAILED);
                    return deposit;
                });

        if (!updateResult.success()) {
            LOG.debug("Updating status of {} to {} failed: {}", depositId, DepositStatus.FAILED,
                      updateResult.throwable()
                                  .isPresent() ? updateResult.throwable().get()
                                                             .getMessage() : "(missing Throwable cause)",
                      updateResult.throwable().get());
        } else {
            LOG.debug("Marked {} as FAILED.", depositId);
        }

        return updateResult.success();
    }

    /**
     * Holds references to objects related to performing a deposit by a {@link DepositTask}
     */
    public static class DepositWorkerContext {
        private Deposit depositResource;
        private DepositSubmission depositSubmission;
        private Submission submission;
        private Repository repository;
        private Packager packager;
        private RepositoryCopy repoCopy;
        private String statusUri;
        private DevNullTransport devNullTransport;
        private boolean skipDeploymentTestDeposits;

        /**
         * the {@code Deposit} itself
         *
         * @return the Deposit
         */
        public Deposit deposit() {
            return depositResource;
        }

        public void deposit(Deposit deposit) {
            this.depositResource = deposit;
        }

        /**
         * the {@code Submission} adapted to the deposit services model
         *
         * @return the DepositSubmission
         */
        public DepositSubmission depositSubmission() {
            return depositSubmission;
        }

        /**
         * the {@code Repository} the custodial content should be transferred to
         *
         * @return the Repository
         */
        public Repository repository() {
            return repository;
        }

        public void repository(Repository repository) {
            this.repository = repository;
        }

        /**
         * the {@code Packager} used to assemble and stream the custodial content
         *
         * @return the Packager
         */
        public Packager packager() {
            return packager;
        }

        /**
         * the {@code Submission} the {@code Deposit} is for
         *
         * @return the Submission
         */
        public Submission submission() {
            return submission;
        }

        public void submission(Submission submission) {
            this.submission = submission;
        }

        /**
         * the {@code RepositoryCopy} created by a successful deposit
         *
         * @return the RepositoryCopy
         */
        public RepositoryCopy repoCopy() {
            return repoCopy;
        }

        public void repoCopy(RepositoryCopy repoCopy) {
            this.repoCopy = repoCopy;
        }

        /**
         * a URI that may be polled to determine the status of a Deposit
         *
         * @return the status URI
         */
        public String statusUri() {
            return statusUri;
        }

        public void statusUri(String statusUri) {
            this.statusUri = statusUri;
        }

        public DevNullTransport getDevNullTransport() {
            return devNullTransport;
        }

        public boolean isSkipDeploymentTestDeposits() {
            return skipDeploymentTestDeposits;
        }

        @Override
        public String toString() {
            return "DepositWorkerContext{" +
                   "depositResource=" + depositResource +
                   ", depositSubmission=" + depositSubmission +
                   ", submission=" + submission +
                   ", repository=" + repository +
                   ", packager=" + packager +
                   ", repoCopy=" + repoCopy +
                   ", statusUri='" + statusUri + '\'' +
                   '}';
        }
    }
}
