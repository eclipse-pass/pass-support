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
package org.dataconservancy.pass.deposit.messaging.service;

import static java.time.Instant.ofEpochMilli;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import javax.jms.Session;

import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.policy.TerminalDepositStatusPolicy;
import org.dataconservancy.pass.deposit.messaging.policy.TerminalSubmissionStatusPolicy;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusEvaluator;
import org.dataconservancy.pass.deposit.messaging.status.SubmissionStatusEvaluator;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction.CriticalResult;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jms.JmsProperties;

/**
 * Utility methods for deposit messaging.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositUtil {

    private DepositUtil() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(DepositUtil.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private static final String UTC = "UTC";

    private static final TerminalDepositStatusPolicy TERMINAL_DEPOSIT_STATUS_POLICY =
            new TerminalDepositStatusPolicy(new DepositStatusEvaluator());

    private static final TerminalSubmissionStatusPolicy TERMINAL_SUBMISSION_STATUS_POLICY = new
        TerminalSubmissionStatusPolicy(new SubmissionStatusEvaluator());

    static final String UNKNOWN_DATETIME = "UNKNOWN";

    /**
     * Parses a timestamp into a formatted date and time string.
     *
     * @param timeStamp the timestamp
     * @return a formatted date and time string
     */
    public static String parseDateTime(long timeStamp) {
        return (timeStamp > 0) ? TIME_FORMATTER.format(
            ofEpochMilli(timeStamp).atZone(ZoneId.of(UTC))) : UNKNOWN_DATETIME;
    }

    /**
     * Obtain the acknowledgement mode of the {@link Session} as a String.
     *
     * @param session  the JMS session
     * @param dateTime the formatted date and time the message was received
     * @param id       the identifier of the received message
     * @return the acknowlegement mode as a {@code String}
     */
    public static String parseAckMode(Session session, String dateTime, String id) {
        String ackMode;
        try {
            JmsProperties.AcknowledgeMode mode = asAcknowledgeMode(session.getAcknowledgeMode());
            ackMode = mode.name();
        } catch (Exception e) {
            ackMode = "UNKNOWN";
        }
        return ackMode;
    }

    /**
     * Converts the acknowledgement mode from the JMS {@link Session} to the Spring {@link
     * JmsProperties.AcknowledgeMode}.
     *
     * @param mode the mode from the JMS {@code Session}
     * @return the Spring {@code AcknowledgeMode}
     * @throws RuntimeException if the supplied {@code mode} cannot be mapped to a Spring {@code AcknowledgeMode}.
     */
    public static JmsProperties.AcknowledgeMode asAcknowledgeMode(int mode) {
        switch (mode) {
            case Session.AUTO_ACKNOWLEDGE:
                return JmsProperties.AcknowledgeMode.AUTO;
            case Session.CLIENT_ACKNOWLEDGE:
                return JmsProperties.AcknowledgeMode.CLIENT;
            case Session.DUPS_OK_ACKNOWLEDGE:
                return JmsProperties.AcknowledgeMode.DUPS_OK;
            default:
        }

        throw new RuntimeException("Unknown acknowledgement mode for session: " + mode);
    }

    /**
     * Splits a comma-delimited multi-valued string into individual strings, and tests whether {@code toMatch} matches
     * any of the values.
     *
     * @param toMatch       the String to match
     * @param csvCandidates a String that may contain multiple values separated by commas
     * @return true if {@code toMatch} is contained within {@code csvCandidates}
     */
    public static boolean csvStringContains(String toMatch, String csvCandidates) {
        if (csvCandidates == null || csvCandidates.trim().length() == 0) {
            return false;
        }

        return Stream.of(csvCandidates.split(","))
                     .anyMatch(candidateType -> candidateType.trim().equals(toMatch));
    }

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
                                                              Repository repository, Packager packager) {
        DepositWorkerContext dc = new DepositWorkerContext();
        dc.depositResource = depositResource;
        dc.depositSubmission = depositSubmission;
        dc.repository = repository;
        dc.packager = packager;
        dc.submission = submission;
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
                (submission) -> !TERMINAL_SUBMISSION_STATUS_POLICY.test(submission.getAggregatedDepositStatus()),
                (submission) -> submission.getAggregatedDepositStatus() == AggregatedDepositStatus.FAILED,
                (submission) -> {
                    submission.setAggregatedDepositStatus(AggregatedDepositStatus.FAILED);
                    return submission;
                }, true);

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
                (deposit) -> !TERMINAL_DEPOSIT_STATUS_POLICY.test(deposit.getDepositStatus()),
                (deposit) -> deposit.getDepositStatus() == DepositStatus.FAILED,
                (deposit) -> {
                    deposit.setDepositStatus(DepositStatus.FAILED);
                    return deposit;
                }, true);

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
