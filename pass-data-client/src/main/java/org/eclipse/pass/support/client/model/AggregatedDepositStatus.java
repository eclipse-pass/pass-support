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
import java.util.Objects;

/**
 * Possible aggregatedDepositStatus of a submission, this is dependent on information from the server and
 * is calculated using the status of associated Deposits
 */
public enum AggregatedDepositStatus {
    /**
     * No Deposits have been initiated for the Submission
     */
    NOT_STARTED("not-started", false),

    /**
     * One or more Deposits for the Submission have been initiated, and at least one
     * has not reached the status of "accepted"
     */
    IN_PROGRESS("in-progress", false),

    /**
     * One or more Deposits for the Submission has a status of "failed"
     */
    FAILED("failed", false),

    /**
     * All related Deposits have a status of "accepted"
     */
    ACCEPTED("accepted", true),

    /**
     * One or more Deposits for the Submission has a status of "rejected"
     */
    REJECTED("rejected", true);

    private static final Map<String, AggregatedDepositStatus> map = new HashMap<>(values().length, 1);

    static {
        for (AggregatedDepositStatus s : values()) {
            map.put(s.value, s);
        }
    }

    private final String value;
    private final boolean terminal;

    AggregatedDepositStatus(String value, boolean terminal) {
        this.value = value;
        this.terminal = terminal;
    }

    /**
     * Parse the aggregated deposit status.
     *
     * @param status Serialized status
     * @return parsed deposit status.
     */
    public static AggregatedDepositStatus of(String status) {
        AggregatedDepositStatus result = map.get(status);
        if (result == null) {
            throw new IllegalArgumentException("Invalid Aggregated Deposit Status: " + status);
        }
        return result;
    }

    /**
     * Returns if {@code aggregatedDepositStatus} is in a <em>terminal</em> state.
     * A null status is not Terminal.
     *
     * @param aggregatedDepositStatus the status the PASS {@code DepositStatus}
     * @return {@code true} if the status is terminal
     */
    public static boolean isTerminalStatus(AggregatedDepositStatus aggregatedDepositStatus) {
        return Objects.nonNull(aggregatedDepositStatus) && aggregatedDepositStatus.terminal;
    }

    /**
     * @return public value
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns if enum value is terminal.
     * @return true if terminal
     */
    public boolean isTerminal() {
        return terminal;
    }
}