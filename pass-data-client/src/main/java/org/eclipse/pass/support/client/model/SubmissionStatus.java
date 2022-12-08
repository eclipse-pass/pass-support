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
package org.eclipse.pass.support.client.model;

import java.util.HashMap;
import java.util.Map;

/**
 * The possible values for the Submission.submissionStatus field.
 * Note that not all Submissions will go through every status.
 */
public enum SubmissionStatus {
    /**
     * When the PASS system identifies a need for a User to submit a Publication to a particular Repository,
     * it will create a new Submission record with this status in order to prompt the User to provide the
     * document and complete the Submission.
     */
    MANUSCRIPT_REQUIRED("manuscript-required", false),

    /**
     * A Submission was prepared by a preparer but now needs the submitter to approve and submit it or provide
     * feedback.
     */
    APPROVAL_REQUESTED("approval-requested", false),

    /**
     * A Submission was prepared by a preparer, but on review by the submitter, a change was requested.
     * The Submission has been handed back to the preparer for editing.
     */
    CHANGES_REQUESTED("changes-requested", false),

    /**
     * A Submission was prepared and then cancelled by the submitter or preparer without being submitted.
     * No further edits can be made to the Submission.
     */
    CANCELLED("cancelled", false),

    /**
     * The submit button has been pressed through the UI. From this status forward, the Submission
     * becomes read-only to both the submitter and preparers. This status indicates that either
     * (a) the Submission is still being processed, or (b) PASS has finished the Deposit process,
     * but there is not yet confirmation from the Repository that indicates the Submission was valid.
     * Some Submissions may remain in a submitted state indefinitely depending on PASS's capacity to
     * verify completion of the process in the target Repository.
     */
    SUBMITTED("submitted", true),

    /**
     * Indicates that a User action may be required outside of PASS. The Submission is stalled or
     * has been rejected by one or more Repository
     */
    NEEDS_ATTENTION("needs-attention", true),

    /**
     * The target repositories have all received a copy of the Submission, and have indicated that
     * the Submission was successful.
     */
    COMPLETE("complete", true),

    /**
     * Submissions newly created by the UI will have this status.  Submissions with this status have not yet
     * been submitted.
     */
    DRAFT("draft", false);

    private static final Map<String, SubmissionStatus> map = new HashMap<>(values().length, 1);

    static {
        for (SubmissionStatus s : values()) {
            map.put(s.value, s);
        }
    }

    private String value;

    private boolean submitted;

    private SubmissionStatus(String value, boolean submitted) {
        this.value = value;
        this.submitted = submitted;
    }

    /**
     * Parse the submission status.
     *
     * @param status Serialized submission status string
     * @return The submission status
     */
    public static SubmissionStatus of(String status) {
        SubmissionStatus result = map.get(status);
        if (result == null) {
            throw new IllegalArgumentException("Invalid Submission Status: " + status);
        }
        return result;
    }

    /**
     * @return public value
     */
    public String getValue() {
        return value;
    }

    /**
     * Determine if submitted.
     *
     * @return True if submitted.
     */
    public boolean isSubmitted() {
        return submitted;
    }
}