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
package org.eclipse.pass.support.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.EventType;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility to calculate and validate the Submission Status. Separate calculations are provided depending
 * on whether the Submission has been submitted or not since different data and rules apply
 *
 * @author Karen Hanson
 */
public class SubmissionStatusCalculator {

    private SubmissionStatusCalculator() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionStatusCalculator.class);

    /**
     * Calculates the appropriate post-Submission status based on data provided.
     * <p>
     * Post-Submission calculations uses the {@code Deposits} and {@code RepositoryCopies} associated
     * with a {@code Submission's} {@code Repositories} to determine the status of a Submission
     * after it has been submitted ({@code Submission.status=true}.
     * </p>
     *
     * @param repositoryIds    Submission repository ids
     * @param deposits         Submission deposits
     * @param repositoryCopies Submission repository copies
     * @return Calculated submission status
     */
    public static SubmissionStatus calculatePostSubmissionStatus(List<String> repositoryIds,
                                                                 List<Deposit> deposits,
                                                                 List<RepositoryCopy> repositoryCopies) {
        if (repositoryIds == null) {
            repositoryIds = new ArrayList<String>();
        }
        if (deposits == null) {
            deposits = new ArrayList<Deposit>();
        }
        if (repositoryCopies == null) {
            repositoryCopies = new ArrayList<RepositoryCopy>();
        }

        Map<String, SubmissionStatus> statusMap = mapPostSubmissionRepositoryStatuses(repositoryIds, deposits,
                                                                                   repositoryCopies);
        return calculateFromStatusMap(statusMap);
    }

    /**
     * Calculates the appropriate pre-Submission status based on data provided.
     * <p>
     * Pre-Submission calculations use the {@code SubmissionEvents} associated with Submission
     * to determine the status of a Submission before it has been submitted Submission.submitted=false.
     * </p>
     * <p>
     * If a default status is provided, it will be returned <em>only if</em> a status cannot be determined from the
     * submission events. If no default status is provided, <em>and</em> a status cannot be determined from the
     * submission events, then this method returns {@code MANUSCRIPT_REQUIRED} in order to maintain backwards
     * compatibility.
     * </p>
     *
     * @param submissionEvents List of submission events
     * @param defaultStatus    the the status to be returned if no status can be determined from the submission
     *                         events, may
     *                         be {@code null}
     * @return calculated submission status, or the default status if one cannot be calculated from the events
     */
    public static SubmissionStatus calculatePreSubmissionStatus(List<SubmissionEvent> submissionEvents,
                                                                SubmissionStatus defaultStatus) {
        if (submissionEvents == null) {
            submissionEvents = new ArrayList<SubmissionEvent>();
        }
        if (submissionEvents.size() > 0) {
            // should only be used to set a status if the status is starting as null since UI is best for setting
            // status,
            // but will warn if the most recent event does not reflect current status
            EventType mostRecentEventType = Collections
                .max(submissionEvents, Comparator.comparing(SubmissionEvent::getPerformedDate))
                .getEventType();

            return mapEventTypeToSubmissionStatus(mostRecentEventType);

        } else {
            // has not yet been acted on; may be awaiting a manuscript, or the UI may have set the status.
            if (defaultStatus == null) {
                return SubmissionStatus.MANUSCRIPT_REQUIRED;
            }

            return defaultStatus;
        }
    }

    /**
     * Checks validity of {@link SubmissionStatus} change, will throw exception or output a warning if there are any
     * validation issue with the change
     *
     * @param submitted  Whether the submission is submitted
     * @param fromStatus Original status
     * @param toStatus   Desired new status
     */
    public static void validateStatusChange(boolean submitted, SubmissionStatus fromStatus, SubmissionStatus toStatus) {
        if (toStatus == null) {
            throw new IllegalArgumentException("The new status cannot be null");
        }
        if (submitted) {
            if (!toStatus.isSubmitted()) {
                String msg = String.format(
                    "Failed to validate the change of status due to conflicting data. The status "
                    + "`%s` cannot be assigned to a Submission that has not yet been submitted. There may be a data " +
                    "issue.",
                    fromStatus);
                throw new RuntimeException(msg);
            }
        } else {
            if (toStatus.isSubmitted()) {
                String msg = String.format(
                    "Failed to validate the change of status due to conflicting data. The status "
                    + "`%s` cannot be assigned to a Submission that has already been submitted. There may be a data " +
                    "issue.",
                    fromStatus);
                throw new RuntimeException(msg);
            }
            if (fromStatus != null && fromStatus.isSubmitted()) {
                String msg = String.format(
                    "Failed to validate the change of status due to conflicting data. The current "
                    + "status of the Submission is `%s`. This indicates that the Submission was already submitted and "
                    + "therefore should not be assigned a pre-submission status. There may be a data issue.",
                    fromStatus);
                throw new RuntimeException(msg);
            }

            if (fromStatus != null && !toStatus.equals(fromStatus)) {
                LOG.warn(
                    "The current status of the Submission conflicts with the status calculated based on the most " +
                    "recent SubmissionEvent. "
                    + "The status on the Submission record is `{}`, while the calculated status is `{}`. The UI is " +
                    "responsible for setting "
                    + "pre-Submission statuses, but this mismatch may indicate a data issue.", fromStatus, toStatus);
            }
        }

    }

    private static SubmissionStatus calculateFromStatusMap(Map<String, SubmissionStatus> statusMap) {
        //we only need to know if a status is present or not to determine combined status
        Set<SubmissionStatus> statuses = new HashSet<SubmissionStatus>(statusMap.values());

        if (statuses.contains(SubmissionStatus.NEEDS_ATTENTION)) {
            return SubmissionStatus.NEEDS_ATTENTION;
        } else if (statuses.size() == 1 && statuses.contains(SubmissionStatus.COMPLETE)) {
            return SubmissionStatus.COMPLETE;
        } else {
            return SubmissionStatus.SUBMITTED;
        }
    }

    private static Map<String, SubmissionStatus> mapPostSubmissionRepositoryStatuses(List<String> repositoryIds,
                                                                                  List<Deposit> deposits,
                                                                                  List<RepositoryCopy> repoCopies) {

        Map<String, SubmissionStatus> statusMap = new HashMap<String, SubmissionStatus>();

        for (String id: repositoryIds) {
            statusMap.put(id, null);
        }
        for (Deposit d : deposits) {
            if (DepositStatus.REJECTED.equals(d.getDepositStatus())) {
                statusMap.put(d.getRepository().getId(), SubmissionStatus.NEEDS_ATTENTION);
            } else {
                statusMap.put(d.getRepository().getId(), SubmissionStatus.SUBMITTED);
            }
        }
        for (RepositoryCopy rc : repoCopies) {
            Repository repo = rc.getRepository();
            CopyStatus copyStatus = rc.getCopyStatus();

            if (CopyStatus.COMPLETE.equals(copyStatus)) {
                statusMap.put(repo.getId(), SubmissionStatus.COMPLETE);
            } else if (CopyStatus.REJECTED.equals(copyStatus) || CopyStatus.STALLED.equals(copyStatus)) {
                statusMap.put(repo.getId(), SubmissionStatus.NEEDS_ATTENTION);
            } else {
                // There is a RepositoryCopy and nothing is wrong. Note in this state, it will overwrite a status of
                // REJECTED on the Deposit. This assumes that if all is OK with the RepositoryCopy things have been
                // resolved.
                statusMap.put(repo.getId(), SubmissionStatus.SUBMITTED);
            }
        }

        return statusMap;
    }

    private static SubmissionStatus mapEventTypeToSubmissionStatus(EventType eventType) {
        switch (eventType) {
            case APPROVAL_REQUESTED:
                return SubmissionStatus.APPROVAL_REQUESTED;
            case APPROVAL_REQUESTED_NEWUSER:
                return SubmissionStatus.APPROVAL_REQUESTED;
            case SUBMITTED:
                return SubmissionStatus.SUBMITTED;
            case CANCELLED:
                return SubmissionStatus.CANCELLED;
            case CHANGES_REQUESTED:
                return SubmissionStatus.CHANGES_REQUESTED;
            default:
                return null;
        }
    }
}