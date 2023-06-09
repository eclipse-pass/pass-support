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
package org.eclipse.pass.loader.nihms;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.loader.nihms.util.FileUtil;

/**
 * This controls a simple local text file containing a list of compliant "pmid|grantNumber" combinations
 * that are considered DONE and therefore require no re-processing. Only compliant records with a PMCID already
 * assigned should be added to this list. The list is used as a lookup during processing to avoid the excessive
 * database interactions that are required to re-process completed nihms data.
 *
 * @author Karen Hanson
 */
public class CompletedPublicationsCache {

    private Set<String> completedPubsCache;

    private File cacheFile;

    private static CompletedPublicationsCache completedPubsSpace = null;

    private static final String CACHEPATH_KEY = "nihmsetl.loader.cachepath";

    private static final String CACHEPATH_DEFAULT = "/cache/compliant-cache.data";

    private CompletedPublicationsCache() {
        completedPubsCache = new HashSet<String>();
        String sCacheFile = ConfigUtil.getSystemProperty(CACHEPATH_KEY,
                                                         FileUtil.getCurrentDirectory() + CACHEPATH_DEFAULT);
        cacheFile = new File(sCacheFile);
        loadFromFile();
    }

    /**
     * Get singleton instance of cache
     * @return the singleton instance
     */
    public static synchronized CompletedPublicationsCache getInstance() {
        if (completedPubsSpace == null) {
            completedPubsSpace = new CompletedPublicationsCache();
        }
        return completedPubsSpace;
    }

    /**
     * Add pmid/awardNumber combination to set
     *
     * @param pmid        pub med id
     * @param awardNumber award number
     */
    public synchronized void add(String pmid, String awardNumber) {
        if (StringUtils.isNotEmpty(pmid) && StringUtils.isNotEmpty(awardNumber)
            && !contains(pmid, awardNumber)) {
            String cachevalue = pmid + "|" + awardNumber;
            try (PrintWriter output = new PrintWriter(new FileWriter(cacheFile, true))) {
                output.println(cachevalue);
                completedPubsCache.add(cachevalue);
            } catch (Exception ex) {
                throw new RuntimeException("Problem writing cachevalue: " + cachevalue + " to cache");
            }
        }
    }

    /**
     * Check if it contains pmid/award number combination
     *
     * @param pmid        pub med id
     * @param awardNumber award number
     * @return true if the id/award number combo is cached
     */
    public synchronized boolean contains(String pmid, String awardNumber) {
        if (StringUtils.isNotEmpty(pmid) && StringUtils.isNotEmpty(awardNumber)) {
            String cachevalue = pmid + "|" + awardNumber;
            return completedPubsCache.contains(cachevalue);
        }
        return false;
    }

    /**
     * Get number of items in cache
     *
     * @return the size of the cache
     */
    public synchronized int size() {
        return completedPubsCache.size();
    }

    /**
     * Empty cache
     */
    public synchronized void clear() {
        try {
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            completedPubsCache.clear();
        } catch (Exception ex) {
            throw new RuntimeException("Could not clear cache file at path " + cacheFile.getAbsolutePath(), ex);
        }
    }

    /**
     * Load contents of cache file into memory from file
     */
    @SuppressWarnings("unchecked")
    public synchronized void loadFromFile() {
        try {
            if (!cacheFile.exists()) {
                cacheFile.getParentFile().mkdirs();
                cacheFile.createNewFile();
            }
            // read in cached values
            completedPubsCache = new HashSet<String>(FileUtils.readLines(cacheFile));
        } catch (Exception ex) {
            throw new RuntimeException(
                "Could not create cache file to hold compliant records at path " + cacheFile.getAbsolutePath(), ex);
        }
    }

}
