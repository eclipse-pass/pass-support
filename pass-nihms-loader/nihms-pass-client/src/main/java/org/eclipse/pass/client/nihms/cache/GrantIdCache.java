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

import java.util.HashMap;

/**
 * Caches awardNumber and grantId combination for easy lookup
 *
 * @author Karen Hanson
 */
public class GrantIdCache {

    private final HashMap<String, String> grantCache;
    private static GrantIdCache grantSpace = null;

    private GrantIdCache() {
        grantCache = new HashMap<String, String>();
    }

    /**
     * Get singleton instance of GrantIdCache
     * @return the grant id cache
     */
    public static synchronized GrantIdCache getInstance() {
        if (grantSpace == null) {
            grantSpace = new GrantIdCache();
        }
        return grantSpace;
    }

    /**
     * Add awardNumber/grantId combination to map
     *
     * @param awardNumber the award number
     * @param grantId     the grant id
     */
    public synchronized void put(String awardNumber, String grantId) {
        awardNumber = awardNumber.toLowerCase();
        grantCache.put(awardNumber, grantId);
    }

    /**
     * Retrieve grantId by awardNumber
     *
     * @param awardNumber the award number
     * @return the grant id
     */
    public synchronized String get(String awardNumber) {
        awardNumber = awardNumber.toLowerCase();
        return grantCache.get(awardNumber);
    }

    /**
     * Remove a Grant from cache
     *
     * @param awardNumber the award number
     */
    public synchronized void remove(String awardNumber) {
        awardNumber = awardNumber.toLowerCase();
        grantCache.remove(awardNumber);
    }

    /**
     * Get number of cached grants
     *
     * @return number of cached grants
     */
    public synchronized int size() {
        return grantCache.size();
    }

    /**
     * Empty map
     */
    public synchronized void clear() {
        grantCache.clear();
    }

}
