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
 * Caches publicationId and repositoryCopyId mapping for easy lookup
 * Important: includes RepositoryCopies for NIHMS records only
 *
 * @author Karen Hanson
 */
public class NihmsRepositoryCopyIdCache {

    private HashMap<String, String> nihmsRepoCopyCache;
    private static NihmsRepositoryCopyIdCache repositoryCopySpace = null;

    private NihmsRepositoryCopyIdCache() {
        nihmsRepoCopyCache = new HashMap<String, String>();
    }

    /**
     * Get singleton instance of NihmsRepositoryCopyIdCache
     * @return the RepositoryCopy ID cache
     */
    public static synchronized NihmsRepositoryCopyIdCache getInstance() {
        if (repositoryCopySpace == null) {
            repositoryCopySpace = new NihmsRepositoryCopyIdCache();
        }
        return repositoryCopySpace;
    }

    /**
     * Add publicationId to repositoryCopyId mapping
     *
     * @param publicationId    the publication id
     * @param repositoryCopyId the repository copy it
     */
    public synchronized void put(String publicationId, String repositoryCopyId) {
        nihmsRepoCopyCache.put(publicationId, repositoryCopyId);
    }

    /**
     * Retrieve RepositoryCopyId by publicationId
     *
     * @param publicationId the publication id
     * @return the repository copy id
     */
    public synchronized String get(String publicationId) {
        return nihmsRepoCopyCache.get(publicationId);
    }

    /**
     * Remove a publicationId to repositoryCopyId mapping from cache
     *
     * @param publicationId the publication id
     */
    public synchronized void remove(String publicationId) {
        nihmsRepoCopyCache.remove(publicationId);
    }

    /**
     * Get number of cached mappings
     *
     * @return the cache size
     */
    public synchronized int size() {
        return nihmsRepoCopyCache.size();
    }

    /**
     * Empty map
     */
    public synchronized void clear() {
        nihmsRepoCopyCache.clear();
    }

}
