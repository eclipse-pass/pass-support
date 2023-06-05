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

/**
 * Roles of agents who might perform a SubmissionEvent
 */
public enum PerformerRole {
    /**
     * Prepares a submission.
     */
    PREPARER("preparer"),

    /**
     * Performs a submission
     */
    SUBMITTER("submitter");

    private final String value;

    PerformerRole(String value) {
        this.value = value;
    }

    /**
     * Parse performer role
     *
     * @param s status string
     * @return parsed role
     */
    public static PerformerRole of(String s) {
        for (PerformerRole r: PerformerRole.values()) {
            if (r.value.equals(s)) {
                return r;
            }
        }

        throw new IllegalArgumentException("Invalid performer role: " + s);
    }

    /**
     * @return public value
     */
    public String getValue() {
        return value;
    }
}