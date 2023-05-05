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
 * Status of award/grant
 */
public enum AwardStatus {

    /**
     * Active award
     */
    ACTIVE("active"),

    /**
     * Pre-award
     */
    PRE_AWARD("pre-award"),

    /**
     * Terminated
     */
    TERMINATED("terminated");

    private static final Map<String, AwardStatus> map = new HashMap<>(values().length, 1);

    static {
        for (AwardStatus a : values()) {
            map.put(a.value, a);
        }
    }

    private final String value;

    AwardStatus(String value) {
        this.value = value;
    }

    /**
     * Parse award status
     *
     * @param status Serialized status
     * @return Parsed status
     */
    public static AwardStatus of(String status) {
        AwardStatus result = map.get(status);
        if (result == null) {
            throw new IllegalArgumentException("Invalid Award Status: " + status);
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