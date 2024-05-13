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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.loader.nihms.NihmsTransformLoadCLIRunner;
import org.json.JSONObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Karen Hanson
 */
@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
@WireMockTest
public class EntrezPmidLookupTest {

    @MockBean
    protected PmidLookup mockPmidLookup;

    // Needed so tests can run after application starts
    @MockBean private NihmsTransformLoadCLIRunner nihmsTransformLoadCLIRunner;

    /*private static final String ENTREZ_PATH = "/entrez/eutils/esummary." +
            "fcgi?db=pubmed&retmode=json&rettype=abstract&id=%s";*/

    @Value("${pmc.entrez.service.url}")
    private String ENTREZ_PATH;

    @Test
    public void testGetEntrezRecordJson(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        String entrezJson = IOUtils.toString(getClass().getClassLoader().
                getResourceAsStream("pmidrecord.json"), StandardCharsets.UTF_8);

        String pmid = "11111111";
        String entrezPath = String.format(ENTREZ_PATH, pmid);

        stubFor(get(entrezPath).willReturn(ok(entrezJson)));

        final int wmPort = wmRuntimeInfo.getHttpPort();

        //JSONObject pmr = mockPmidLookup.retrievePubMedRecordAsJson(pmid);
        //assertTrue(pmr.getString("source").contains("Journal A"));
    }

    @Test
    @Disabled
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
    @Disabled
    public void testGetPubMedRecordWithHighAsciiChars() throws IOException {
        JSONObject pubMedJsonRecord = new JSONObject(IOUtils.toString(getClass().getClassLoader().
                getResourceAsStream("pmidrecord.json"), StandardCharsets.UTF_8));
        PubMedEntrezRecord pubMedEntrezRecord = new PubMedEntrezRecord(pubMedJsonRecord);
        String pmid = "11111111";

        when(mockPmidLookup.retrievePubMedRecord(pmid)).thenReturn(pubMedEntrezRecord);

        PubMedEntrezRecord record = mockPmidLookup.retrievePubMedRecord(pmid);
        assertEquals("10.1000/a.abcd.1234", record.getDoi());
        assertEquals("Article A", record.getTitle());
    }

}
