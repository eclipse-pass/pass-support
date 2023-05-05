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
package org.eclipse.pass.loader.nihms.integration;

import static org.junit.Assert.assertEquals;

import java.net.URI;

import org.eclipse.pass.model.Grant;
import org.junit.Test;

/**
 * Grant matching depends on specific normalization by the indexer. This verifies normalization is happening as expected
 * if these tests fail, indexer normalization may have changed and may need to change grant matching code.
 *
 * @author Karen Hanson
 */
public class GrantNormalizationIT extends NihmsSubmissionEtlITBase {

    /**
     * Tests a number of variations seen for the NIH grant IDs to verify that normalization
     * by the indexer works as expected.
     *
     * @throws Exception
     */
    @Test
    public void checkAwardNumberSensitivityForNIHGrant() throws Exception {
        final String awardNumber = "R01HL003043";

        URI grantId = createGrant(awardNumber, "http://test:8080/fcrepo/rest/users/1");
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Grant.class, "awardNumber", awardNumber);
            assertEquals(grantId, uri);
        });

        URI matchedUri = client.findByAttribute(Grant.class, "awardNumber", awardNumber);
        assertEquals(grantId, matchedUri);

        matchedUri = client.findByAttribute(Grant.class, "awardNumber", "R01 HL003043");
        assertEquals(grantId, matchedUri);

        matchedUri = client.findByAttribute(Grant.class, "awardNumber", "R01HL03043");
        assertEquals(grantId, matchedUri);

        matchedUri = client.findByAttribute(Grant.class, "awardNumber", "R01HL3043");
        assertEquals(grantId, matchedUri);

        matchedUri = client.findByAttribute(Grant.class, "awardNumber", "R02HL3043");
        assertEquals(grantId, matchedUri);

        matchedUri = client.findByAttribute(Grant.class, "awardNumber", "TM2HL3043");
        assertEquals(grantId, matchedUri);

        matchedUri = client.findByAttribute(Grant.class, "awardNumber", "TM2HL003043");
        assertEquals(grantId, matchedUri);

        matchedUri = client.findByAttribute(Grant.class, "awardNumber", "TM2HE003043");
        assertEquals(null, matchedUri);

        matchedUri = client.findByAttribute(Grant.class, "awardNumber", "TM2HL000000000000000003043");
        assertEquals(null, matchedUri);

        matchedUri = client.findByAttribute(Grant.class, "awardNumber", "TM2HL0003041");
        assertEquals(null, matchedUri);

    }

    /**
     * Tests an award number that looks similar to NIH number but is too long to make sure it doesn't normalize it
     *
     * @throws Exception
     */
    @Test
    public void checkSearchForNonNihGrantWithSimilarId() throws Exception {
        String awardNumber = "R01HL1113043222";
        URI grantId = createGrant(awardNumber, "http://test:8080/fcrepo/rest/users/2");
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Grant.class, "awardNumber", awardNumber);
            assertEquals(grantId, uri);
        });

        URI matchedUri = client.findByAttribute(Grant.class, "awardNumber", "R21HL1113043222");
        assertEquals(null, matchedUri);
    }

    /**
     * Tests an award number that looks nothing like an NIH award number to make sure it is not normalized
     *
     * @throws Exception
     */
    @Test
    public void checkSearchForNonNihGrantWithDifferentId() throws Exception {
        String awardNumber = "ABC1234567";
        URI grantId = createGrant(awardNumber, "http://test:8080/fcrepo/rest/users/2");
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Grant.class, "awardNumber", awardNumber);
            assertEquals(grantId, uri);
        });

        URI matchedUri = client.findByAttribute(Grant.class, "awardNumber", "CDE1234567");
        assertEquals(null, matchedUri);

    }

}
