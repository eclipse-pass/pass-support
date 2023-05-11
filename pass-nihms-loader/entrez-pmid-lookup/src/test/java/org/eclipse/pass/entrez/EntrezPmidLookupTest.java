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
package org.eclipse.pass.entrez;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * @author Karen Hanson
 * @version $Id$
 */
public class EntrezPmidLookupTest {

    @Test
    public void testGetEntrezRecordJson() {
        PmidLookup apiService = new PmidLookup();

        String pmid = "29249144";

        JSONObject pmr = apiService.retrievePubMedRecordAsJson(pmid);
        assertTrue(pmr.getString("source").contains("Proteome"));

    }

    @Test
    public void testGetPubMedRecord() {
        PmidLookup pmidLookup = new PmidLookup();
        String pmid = "29249144";
        PubMedEntrezRecord record = pmidLookup.retrievePubMedRecord(pmid);
        assertEquals("10.1021/acs.jproteome.7b00775", record.getDoi());
    }

    @Test
    public void testGetPubMedRecordWithHighAsciiChars() {
        PmidLookup pmidLookup = new PmidLookup();
        String pmid = "27648456";
        PubMedEntrezRecord record = pmidLookup.retrievePubMedRecord(pmid);
        assertEquals("10.1002/acn3.333", record.getDoi());
        assertEquals("Age-dependent effects of APOE ε4 in preclinical Alzheimer's disease.", record.getTitle());
    }

}
