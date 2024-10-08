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
package org.eclipse.pass.loader.nihms.client.cache;

import java.util.HashMap;

/**
 * Caches submission and depositId combination for easy lookup
 * Note: cache only contains deposits for nihms
 *
 * @author Karen Hanson
 */
public class NihmsDepositIdCache {

    private HashMap<String, String> depositCache;
    private static NihmsDepositIdCache depositSpace = null;

    private NihmsDepositIdCache() {
        depositCache = new HashMap<>();
    }

    /**
     * Get singleton instance of NihmsDepositIdCache
     * @return the deposit id cache
     */
    public static synchronized NihmsDepositIdCache getInstance() {
        if (depositSpace == null) {
            depositSpace = new NihmsDepositIdCache();
        }
        return depositSpace;
    }

    /**
     * Add deposit to map
     *
     * @param submissionId the submission id
     * @param depositId    the deposit id
     */
    public synchronized void put(String submissionId, String depositId) {
        depositCache.put(submissionId, depositId);
    }

    /**
     * Retrieve depositId by submissionId
     *
     * @param submissionId the submission id
     * @return the URI from the deposit cache
     */
    public synchronized String get(String submissionId) {
        return depositCache.get(submissionId);
    }

    /**
     * Remove a Deposit from cache
     *
     * @param submissionId the submission id
     */
    public synchronized void remove(String submissionId) {
        depositCache.remove(submissionId);
    }

    /**
     * Get number of cached deposits
     *
     * @return the number of cached deposits
     */
    public synchronized int size() {
        return depositCache.size();
    }

    /**
     * Empty map
     */
    public synchronized void clear() {
        depositCache.clear();
    }

}
