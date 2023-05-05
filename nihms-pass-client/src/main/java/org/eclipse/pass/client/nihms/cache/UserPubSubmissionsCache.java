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
package org.eclipse.pass.client.nihms.cache;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Caches userIdPubId as concatenated string with the list of submissions relevant to that combination to ensure
 * all relevant submissions are retrieved regardless of whether index is up to date with most recent etl records
 *
 * @author Karen Hanson
 */
public class UserPubSubmissionsCache {

    private HashMap<String, Set<String>> userPubSubmissionsCache;
    private static UserPubSubmissionsCache userPubSubmissionCache = null;

    private UserPubSubmissionsCache() {
        userPubSubmissionsCache = new HashMap<String, Set<String>>();
    }

    public static synchronized UserPubSubmissionsCache getInstance() {
        if (userPubSubmissionCache == null) {
            userPubSubmissionCache = new UserPubSubmissionsCache();
        }
        return userPubSubmissionCache;
    }

    /**
     * Add an item to an existing Map Entry or create a new one if one does not already exist
     *
     * @param userIdPubId  the concatenated user id and publication id
     * @param submissionId the submission id
     */
    public synchronized void addToOrCreateEntry(String userIdPubId, String submissionId) {
        Set<String> submissionIds = userPubSubmissionsCache.get(userIdPubId);
        if (submissionIds == null) {
            submissionIds = new HashSet<String>();
        }
        submissionIds.add(submissionId);
        userPubSubmissionsCache.put(userIdPubId, submissionIds);
    }

    /**
     * Add userIdPubId/submissionIds combination to map
     *
     * @param userIdPubId   the concatenated user id and publication id
     * @param submissionIds the submission ids
     */
    public synchronized void put(String userIdPubId, Set<String> submissionIds) {
        userPubSubmissionsCache.put(userIdPubId, submissionIds);
    }

    /**
     * Retrieve submissionIds by userIdPubId
     *
     * @param userIdPubId the concatenated user id and publication id
     * @return submission ids
     */
    public synchronized Set<String> get(String userIdPubId) {
        return userPubSubmissionsCache.get(userIdPubId);
    }

    /**
     * Remove a Submission from cache
     *
     * @param userIdPubId the concatenated user id and publication id
     */
    public synchronized void remove(String userIdPubId) {
        userPubSubmissionsCache.remove(userIdPubId);
    }

    /**
     * Get number of cached submissions
     *
     * @return the size of the cache
     */
    public synchronized int size() {
        return userPubSubmissionsCache.size();
    }

    /**
     * Empty map
     */
    public synchronized void clear() {
        userPubSubmissionsCache.clear();
    }

}
