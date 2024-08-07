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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests CompletedPublicationsCache class
 *
 * @author Karen Hanson
 */
public class CompletedPublicationsCacheTest {

    private CompletedPublicationsCache completedPubsCache;

    @BeforeEach
    public void startup() {
        completedPubsCache = new CompletedPublicationsCache();
    }

    @AfterEach
    public void cleanup() {
        completedPubsCache.clear();
    }

    /**
     * Makes sure cache contains items after you add them
     */
    @Test
    public void testAddThenFindMatch() {
        String pmid1 = "123456";
        String pmid2 = "987654";
        String awardNum1 = "AB1 EI12345";
        String awardNum2 = "AB2 MF21355";

        assertFalse(completedPubsCache.contains(pmid1, awardNum1));
        assertFalse(completedPubsCache.contains(pmid2, awardNum2));

        completedPubsCache.add(pmid1, awardNum1);
        completedPubsCache.add(pmid2, awardNum2);

        assertTrue(completedPubsCache.contains(pmid1, awardNum1));
        assertTrue(completedPubsCache.contains(pmid2, awardNum2));

    }

    /**
     * Makes sure cache clear works
     */
    @Test
    public void testClearCompletedPublicationsCache() {
        String pmid1 = "123456";
        String pmid2 = "987654";
        String awardNum1 = "AB1 EI12345";
        String awardNum2 = "AB2 MF21355";

        assertFalse(completedPubsCache.contains(pmid1, awardNum1));
        assertFalse(completedPubsCache.contains(pmid2, awardNum2));

        completedPubsCache.add(pmid1, awardNum1);
        completedPubsCache.add(pmid2, awardNum2);

        assertTrue(completedPubsCache.contains(pmid1, awardNum1));
        assertTrue(completedPubsCache.contains(pmid2, awardNum2));

        completedPubsCache.clear();

        assertFalse(completedPubsCache.contains(pmid1, awardNum1));
        assertFalse(completedPubsCache.contains(pmid2, awardNum2));

    }

    /**
     * Makes sure adding duplicate data does not add rows to the cache file
     *
     * @throws Exception
     */
    @Test
    public void testDoesNotAddDuplicates() throws Exception {
        String pmid1 = "123456";
        String pmid2 = "987654";
        String awardNum1 = "AB1 EI12345";
        String awardNum2 = "AB2 MF21355";

        assertFalse(completedPubsCache.contains(pmid1, awardNum1));
        assertFalse(completedPubsCache.contains(pmid2, awardNum2));

        completedPubsCache.add(pmid1, awardNum1);
        completedPubsCache.add(pmid2, awardNum2);
        completedPubsCache.add(pmid1, awardNum1);
        completedPubsCache.add(pmid2, awardNum2);
        completedPubsCache.add(pmid1, awardNum1);
        completedPubsCache.add(pmid2, awardNum2);

        assertTrue(completedPubsCache.contains(pmid1, awardNum1));
        assertTrue(completedPubsCache.contains(pmid2, awardNum2));

        File cacheFile = (File) ReflectionTestUtils.getField(completedPubsCache, "cacheFile");
        List<String> processed = Files.readAllLines(cacheFile.toPath());
        assertEquals(2, processed.size());
    }

}
