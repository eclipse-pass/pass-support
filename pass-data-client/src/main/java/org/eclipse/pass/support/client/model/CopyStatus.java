/*
 * Copyright 2023 Johns Hopkins University
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
 * Possible repository copy statuses. Note that some repositories may not go through every status.
 */
public enum CopyStatus {
    /**
     * The target Repository has accepted the Deposit
     */
    ACCEPTED("accepted"),
    /**
     * PASS has sent a package to the target Repository and is waiting for an update on the status
     */
    IN_PROGRESS("in-progress"),
    /**
     * The target [Repository](Repository.md) has detected a problem that has caused the progress to stall.
     */
    STALLED("stalled"),
    /**
     * The target Repository has rejected the Deposit
     */
    COMPLETE("complete"),

    /**
     * The RepositoryCopy has been rejected by the remote Repository.
     */
    REJECTED("rejected");

    private static final Map<String, CopyStatus> map = new HashMap<>(values().length, 1);

    static {
        for (CopyStatus c : values()) {
            map.put(c.value, c);
        }
    }

    private final String value;

    CopyStatus(String value) {
        this.value = value;
    }

    /**
     * Parse the copy status.
     *
     * @param status Serialized status.
     * @return Parsed status.
     */
    public static CopyStatus of(String status) {
        CopyStatus result = map.get(status);
        if (result == null) {
            throw new IllegalArgumentException("Invalid Copy Status: " + status);
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