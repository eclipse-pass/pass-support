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
 * Possible deposit statuses. Note that some repositories may not go through every status.
 */
public enum DepositStatus {
    /**
     * PASS has sent a package to the target Repository and is waiting for an update on the status
     */
    SUBMITTED("submitted"),
    /**
     * The target Repository has accepted the files into the repository. More steps may be performed by the
     * Repository, but the
     * requirements of the Deposit have been satisfied
     */
    ACCEPTED("accepted"),
    /**
     * The target Repository has rejected the Deposit
     */
    REJECTED("rejected"),
    /**
     * A failure occurred performing the deposit; it may be re-tried later.
     */
    FAILED("failed");

    private static final Map<String, DepositStatus> map = new HashMap<>(values().length, 1);

    static {
        for (DepositStatus d : values()) {
            map.put(d.value, d);
        }
    }

    private final String value;

    DepositStatus(String value) {
        this.value = value;
    }

    /**
     * Parse deposit status
     *
     * @param status status string
     * @return parsed status
     */
    public static DepositStatus of(String status) {
        DepositStatus result = map.get(status);
        if (result == null) {
            throw new IllegalArgumentException("Invalid Deposit Status: " + status);
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