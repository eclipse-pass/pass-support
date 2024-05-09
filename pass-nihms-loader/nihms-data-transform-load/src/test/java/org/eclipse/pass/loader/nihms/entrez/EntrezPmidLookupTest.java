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
package org.eclipse.pass.loader.nihms.entrez;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Karen Hanson
 * @version $Id$
 */
public class EntrezPmidLookupTest {

    @MockBean
    protected PmidLookup mockPmidLookup;

    @BeforeEach
    public void setUp() {
        mockPmidLookup = mock(PmidLookup.class);
    }

    @Test
    public void testGetEntrezRecordJson() throws IOException {
        JSONObject entrezJson = new JSONObject(IOUtils.toString(getClass().getClassLoader().
                getResourceAsStream("pmidrecord.json"), StandardCharsets.UTF_8));
        String pmid = "11111111";

        when(mockPmidLookup.retrievePubMedRecordAsJson(pmid)).thenReturn(entrezJson);

        JSONObject pmr = mockPmidLookup.retrievePubMedRecordAsJson(pmid);
        assertTrue(pmr.getString("source").contains("Journal A"));
    }

    @Test
    public void testGetPubMedRecord() throws IOException {
        JSONObject pubMedJsonRecord = new JSONObject(IOUtils.toString(getClass().getClassLoader().
                getResourceAsStream("pmidrecord.json"), StandardCharsets.UTF_8));
        PubMedEntrezRecord pubMedEntrezRecord = new PubMedEntrezRecord(pubMedJsonRecord);
        String pmid = "11111111";

        when(mockPmidLookup.retrievePubMedRecord(pmid)).thenReturn(pubMedEntrezRecord);

        PubMedEntrezRecord record = mockPmidLookup.retrievePubMedRecord(pmid);
        assertEquals("10.1000/a.abcd.1234", record.getDoi());
    }

    @Test
    public void testGetPubMedRecordWithHighAsciiChars() {
        PmidLookup pmidLookup = new PmidLookup();
        String pmid = "27648456";

        when(mockPmidLookup.retrievePubMedRecord(pmid)).thenReturn(pubMedEntrezRecord);

        PubMedEntrezRecord record = pmidLookup.retrievePubMedRecord(pmid);
        assertEquals("10.1002/acn3.333", record.getDoi());
        assertEquals("Age-dependent effects of APOE ε4 in preclinical Alzheimer's disease.", record.getTitle());
    }

}
