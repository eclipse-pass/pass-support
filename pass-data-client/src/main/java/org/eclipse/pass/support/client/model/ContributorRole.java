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
 * list of possible contributor Roles
 */
public enum ContributorRole {

    /**
     * Author role
     */
    AUTHOR("author"),

    /**
     * First author role
     */
    FIRST_AUTHOR("first-author"),

    /**
     * Last author role
     */
    LAST_AUTHOR("last-author"),

    /**
     * Corresponding author role
     */
    CORRESPONDING_AUTHOR("corresponding-author");

    private static final Map<String, ContributorRole> map = new HashMap<>(values().length, 1);

    static {
        for (ContributorRole r : values()) {
            map.put(r.value, r);
        }
    }

    private String value;

    private ContributorRole(String value) {
        this.value = value;
    }

    /**
     * Parse the role.
     *
     * @param role Serialized role string
     * @return The parsed value.
     */
    public static ContributorRole of(String role) {
        ContributorRole result = map.get(role);
        if (result == null) {
            throw new IllegalArgumentException("Invalid Role: " + role);
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