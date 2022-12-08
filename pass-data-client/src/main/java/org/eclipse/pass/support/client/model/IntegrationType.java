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
public enum IntegrationType {
    /**
     * PASS can make Deposits to this Repository, and will received updates about its status
     */
    FULL("full"),
    /**
     * PASS can make Deposits to this Repository but will not automatically receive updates about its status
     */
    ONE_WAY("one-way"),
    /**
     * A deposit cannot automatically be made to this Repository from PASS, only a web link can be created.
     */
    WEB_LINK("web-link");

    private static final Map<String, IntegrationType> map = new HashMap<>(values().length, 1);

    static {
        for (IntegrationType d : values()) {
            map.put(d.value, d);
        }
    }

    private String value;

    private IntegrationType(String value) {
        this.value = value;
    }

    /**
     * Parse the integration type.
     *
     * @param integrationType String serialized integration type
     * @return parsed integration type.
     */
    public static IntegrationType of(String integrationType) {
        IntegrationType result = map.get(integrationType);
        if (result == null) {
            throw new IllegalArgumentException("Invalid Integration Type: " + integrationType);
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