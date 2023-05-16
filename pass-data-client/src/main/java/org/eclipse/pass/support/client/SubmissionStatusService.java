package org.eclipse.pass.support.client;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A service for calculating and updating the `Submission.submissionStatus` value based on data
 * related to the Submission. By default, there is a division of responsibility in calculating
 * this status, with pre-submission statuses being managed by the UI, and post-Submission statuses
 * being managed by back-end services. For this reason, pre-submission statuses will only be changed
 * if the starting value is null, or overrideUIStatus is true.
 *
 * @author Karen Hanson
 */
public class SubmissionStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionStatusService.class);

    private PassClient client;

    /**
     * Initiate service
     */
    public SubmissionStatusService() {
        this.client = PassClient.newInstance();
    }

    /**
     * Supports setting a specific client.
     *
     * @param client PASS client
     */
    public SubmissionStatusService(PassClient client) {
        if (client == null) {
            throw new IllegalArgumentException("PassClient cannot be null");
        }
        this.client = client;
    }

    /**
     * Calculates the appropriate {@link SubmissionStatus} for the {@code Submission.id} provided.
     * This is based on the status of associated {@link Deposit}s and {@link RepositoryCopy}s for
     * {@code submitted} records, and the existing status (if any) and {@link SubmissionEvent}s for unsubmitted records.
     *
     * @param submissionId Submission URI
     * @return calculated submission status.
     */
    public SubmissionStatus calculateSubmissionStatus(String submissionId) {
        Submission submission = loadSubmission(submissionId);
        return calculateSubmissionStatus(submission);
    }

    /**
     * Calculates the appropriate {@link SubmissionStatus} for the {@link Submission} provided.
     * This is based on the status of associated {@link Deposit}s and {@link RepositoryCopy}s for
     * {@code submitted} records, and the existing status (if any) and {@link SubmissionEvent}s for unsubmitted records.
     *
     * @param submission The submission
     * @return Calculated submission status
     */
    public SubmissionStatus calculateSubmissionStatus(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("submission cannot be null");
        }
        if (submission.getId() == null) {
            throw new IllegalArgumentException(
                "No status could be calculated for the Submission as it does not have a `Submission.id`.");
        }

        SubmissionStatus fromStatus = submission.getSubmissionStatus();
        SubmissionStatus toStatus;

        if (!submission.getSubmitted()) {
            List<SubmissionEvent> submissionEvents = getRelationshipSubject(SubmissionEvent.class, "submission.id",
                    submission.getId());

            // Calculate the pre-submission status, defaulting to the existing status if one cannot be determined
            // from the submission events.
            toStatus = SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents,
                                                                               submission.getSubmissionStatus());

        } else {
            List<Deposit> deposits = getRelationshipSubject(Deposit.class, "submission.id", submission.getId());
            List<RepositoryCopy> repositoryCopies = getRelationshipSubject(RepositoryCopy.class,
                    "publication.id", submission.getPublication().getId());

            toStatus = SubmissionStatusCalculator.calculatePostSubmissionStatus(submission.getRepositories()
                    .stream().map(Repository::getId).collect(Collectors.toList()), deposits,
                    repositoryCopies);
        }

        try {
            SubmissionStatusCalculator.validateStatusChange(submission.getSubmitted(), fromStatus, toStatus);
        } catch (RuntimeException ex) {
            String msg = String.format("Cannot change status from %s to %s on Submission %s. "
                                       + "The following explaination was provided: %s", fromStatus, toStatus,
                                       submission.getId(), ex.getMessage());
            throw new RuntimeException(msg);
        }

        return toStatus;

    }

    <T extends PassEntity> List<T> getRelationshipSubject(Class<T> type, String predicate,
            String targetId, String... include) {
        PassClientSelector<T> sel = new PassClientSelector<>(type);
        sel.setFilter(RSQL.equals(predicate, targetId));
        sel.setInclude(include);

        try {
            return client.streamObjects(sel).collect(Collectors.toList());
        } catch (IOException e) {
            String msg = String.format("Failed to retrieve objects with target %s and predicate %s. "
                    + "The following explaination was provided: %s", targetId, predicate, e.getMessage());
            throw new RuntimeException(msg);
        }
    }

    /**
     * Calculates the appropriate {@link SubmissionStatus} for the {@code Submission.id} provided.
     * <p>
     * This is based on the status of associated {@link Deposit}s and {@link RepositoryCopy}s for
     * {@code submitted} records, and {@link SubmissionEvent}s for unsubmitted records then updates
     * the status as appropriate.
     * </p>
     * <p>
     * The UI will typically have responsibility for updating the {@code submissionStatus} before
     * the {@link Submission} is submitted. Therefore, by default this service will not replace
     * the existing status of an unsubmitted record unless the starting value was null (i.e. it has not
     * been populated yet). To override this constraint, and replace the value anyway, use the method
     * {@code calculateAndUpdateSubmissionStatus(boolean overrideUIStatus)} and supply a parameter of {@code true}
     * </p>
     *
     * @param submissionId Submission URI
     * @return Calculated submission status
     */
    public SubmissionStatus calculateAndUpdateSubmissionStatus(String submissionId) {
        return calculateAndUpdateSubmissionStatus(submissionId, false);
    }

    /**
     * Calculates the appropriate {@link SubmissionStatus} for the {@link Submission} provided.
     *
     * <p>
     * This is based on the status of associated {@link Deposit}s and {@link RepositoryCopy}s for
     * {@code submitted} records, and {@link SubmissionEvent}s for unsubmitted records then updates
     * the status as appropriate.
     * </p>
     * <p>
     * The UI will typically have responsibility for updating the {@code submissionStatus} before
     * the {@link Submission} is submitted. Therefore, by default this service will not replace
     * the existing status of an unsubmitted record unless the starting value was null (i.e. it has not
     * been populated yet). To override this constraint, set the {@code overrideUIStatus} parameter to
     * {@code true}
     * </p>
     *
     * @param submissionId     Submission identifier
     * @param overrideUIStatus - {@code true} will override the current pre-submission status on the
     *                         {@code Submission} record, regardless of whether it was set by the UI.
     *                         {@code false} will not replace the current submission value, and favor the value set
     *                         by the UI
     * @return calculated submission status.
     */
    public SubmissionStatus calculateAndUpdateSubmissionStatus(String submissionId, boolean overrideUIStatus) {

        Submission submission = loadSubmission(submissionId);

        SubmissionStatus fromStatus = submission.getSubmissionStatus();
        SubmissionStatus toStatus = calculateSubmissionStatus(submission);

        if (fromStatus == null || !fromStatus.equals(toStatus)) {

            //Applies special rule - this service should not overwrite what the UI has set the status to
            //unless the original status was null or this service has been specifically configured to do so
            //by setting overrideUIStatus to true.
            if (!overrideUIStatus && !submission.getSubmitted() && fromStatus != null) {
                LOG.info("Status of Submission {} did not change because pre-submission UI statuses are protected. "
                         + "The current status will stay as `{}`", submission.getId(), fromStatus);
                return fromStatus;
            }

            submission.setSubmissionStatus(toStatus);
            LOG.info("Updating status of Submission {} from `{}` to `{}`", submission.getId(), fromStatus, toStatus);
            try {
                client.updateObject(submission);
            } catch (IOException e) {
                String msg = String.format("Failed to retrieve Submission with ID %s from the database", submissionId);
                throw new RuntimeException(msg);
            }

        } else {
            LOG.debug("Status of Submission {} did not change. The current status is `{}`", submission.getId(),
                      fromStatus);
        }

        return toStatus;
    }

    /**
     * Load submission based on identifier
     *
     * @param submissionId Submission identifier
     * @return The submission
     */
    private Submission loadSubmission(String submissionId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId cannot be null");
        }
        Submission submission = null;
        try {
            submission = client.getObject(Submission.class, submissionId, "repositories", "publication");
        } catch (IOException ex) {
            String msg = String.format("Failed to retrieve Submission with ID %s from the database", submissionId);
            throw new RuntimeException(msg);
        }
        return submission;
    }
}
