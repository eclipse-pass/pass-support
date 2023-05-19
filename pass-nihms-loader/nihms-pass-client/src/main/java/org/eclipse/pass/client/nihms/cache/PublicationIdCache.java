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
 * Caches pmid and publicationId combination for easy lookup
 *
 * @author Karen Hanson
 */
public class PublicationIdCache {

    private final HashMap<String, String> publicationCache;
    private static PublicationIdCache publicationSpace = null;

    private PublicationIdCache() {
        publicationCache = new HashMap<String, String>();
    }

    public static synchronized PublicationIdCache getInstance() {
        if (publicationSpace == null) {
            publicationSpace = new PublicationIdCache();
        }
        return publicationSpace;
    }

    /**
     * Add publication to map
     *
     * @param pmid          the pmid
     * @param publicationId the publication id
     */
    public synchronized void put(String pmid, String publicationId) {
        publicationCache.put(pmid, publicationId);
    }

    /**
     * Retrieve publicationId by pmid
     *
     * @param pmid the pmid
     * @return the publication uri
     */
    public synchronized String get(String pmid) {
        return publicationCache.get(pmid);
    }

    /**
     * Remove a Publication from cache
     *
     * @param pmid the pmid
     */
    public synchronized void remove(String pmid) {
        publicationCache.remove(pmid);
    }

    /**
     * Get number of cached publications
     *
     * @return the size of the cache
     */
    public synchronized int size() {
        return publicationCache.size();
    }

    /**
     * Empty map
     */
    public synchronized void clear() {
        publicationCache.clear();
    }

}
