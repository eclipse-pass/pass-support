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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Grant;
import org.junit.jupiter.api.Test;

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
     * @throws Exception if an error occurs
     */
    @Test
    public void checkAwardNumberSensitivityForNIHGrant() throws Exception {
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        final String awardNumber = "R01HL003043";

        String grantId = createGrant(awardNumber, "1");
        attempt(RETRIES, () -> {
            grantSelector.setFilter(RSQL.equals("awardNumber", awardNumber));
            final String testGrantId;
            try {
                testGrantId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertEquals(grantId, testGrantId);
        });

        grantSelector.setFilter(RSQL.equals("awardNumber", awardNumber));
        String matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertEquals(grantId, matchedId);

        grantSelector.setFilter(RSQL.equals("awardNumber", "R01 HL003043"));
        matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertEquals(grantId, matchedId);

        grantSelector.setFilter(RSQL.equals("awardNumber", "R01HL03043"));
        matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertEquals(grantId, matchedId);

        grantSelector.setFilter(RSQL.equals("awardNumber", "R01HL3043"));
        matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertEquals(grantId, matchedId);

        grantSelector.setFilter(RSQL.equals("awardNumber", "R02HL3043"));
        matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertEquals(grantId, matchedId);

        grantSelector.setFilter(RSQL.equals("awardNumber", "TM2HL3043"));
        matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertEquals(grantId, matchedId);

        grantSelector.setFilter(RSQL.equals("awardNumber", "TM2HL003043"));
        matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertEquals(grantId, matchedId);

        grantSelector.setFilter(RSQL.equals("awardNumber", "TM2HE003043"));
        matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertNull(matchedId);

        grantSelector.setFilter(RSQL.equals("awardNumber", "TM2HL000000000000000003043"));
        matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertNull(matchedId);

        grantSelector.setFilter(RSQL.equals("awardNumber", "TM2HL0003041"));
        matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertNull(matchedId);

    }

    /**
     * Tests an award number that looks similar to NIH number but is too long to make sure it doesn't normalize it
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void checkSearchForNonNihGrantWithSimilarId() throws Exception {
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        String awardNumber = "R01HL1113043222";
        String grantId = createGrant(awardNumber, "2");
        attempt(RETRIES, () -> {
            final String testId;
            grantSelector.setFilter(RSQL.equals("awardNumber", awardNumber));
            try {
                testId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertEquals(grantId, testId);
        });

        grantSelector.setFilter(RSQL.equals("awardNumber", "R21HL1113043222"));
        String matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertNull(matchedId);
    }

    /**
     * Tests an award number that looks nothing like an NIH award number to make sure it is not normalized
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void checkSearchForNonNihGrantWithDifferentId() throws Exception {
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        String awardNumber = "ABC1234567";
        String grantId = createGrant(awardNumber, "2");
        attempt(RETRIES, () -> {
            grantSelector.setFilter(RSQL.equals("awardNumber", awardNumber));
            final String testId;
            try {
                testId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertEquals(grantId, testId);
        });

        grantSelector.setFilter(RSQL.equals("awardNumber", "CDE1234567"));
        String matchedId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
        assertNull(matchedId);

    }

}
