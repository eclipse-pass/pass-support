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
 * The types of events that might be recorded as SubmissionEvents
 */
public enum EventType {
    /**
     * A Submission was prepared by a preparer on behalf of a person who does not yet have a User
     * record in PASS. The preparer is requesting that the submitter join PASS and then approve and
     * submit it or provide feedback.
     */
    APPROVAL_REQUESTED_NEWUSER("approval-requested-newuser"),

    /**
     * A Submission was prepared by a preparer who is now requesting that the submitter approve and
     * submit it or provide feedback
     */
    APPROVAL_REQUESTED("approval-requested"),

    /**
     * A Submission was prepared by a preparer, but on review by the submitter, a change was requested.
     * The Submission has been handed back to the preparer for editing.
     */
    CHANGES_REQUESTED("changes-requested"),

    /**
     * A Submission was prepared and then cancelled by the submitter or preparer without being submitted.
     * No further edits can be made to the Submission.
     */
    CANCELLED("cancelled"),

    /**
     * The submit button has been pressed through the UI.
     */
    SUBMITTED("submitted");

    private static final Map<String, EventType> map = new HashMap<>(values().length, 1);

    static {
        for (EventType s : values()) {
            map.put(s.value, s);
        }
    }

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    /**
     * @param eventType event type as string
     * @return parsed event type
     */
    public static EventType of(String eventType) {
        EventType result = map.get(eventType);
        if (result == null) {
            throw new IllegalArgumentException("Invalid Event Type: " + eventType);
        }
        return result;
    }

    /**
     * @return public value
     */
    public String getValue() {
        return value;
    }
}