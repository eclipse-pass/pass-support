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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.loader.nihms.NihmsTransformLoadCLIRunner;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Karen Hanson
 */
@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
@WireMockTest(httpPort = 9911)
public class EntrezPmidLookupTest {

    @Autowired
    protected PmidLookup pmidLookup;

    // Needed so tests can run after application starts
    @MockBean private NihmsTransformLoadCLIRunner nihmsTransformLoadCLIRunner;

    @Value("${pmc.entrez.service.url}")
    private String ENTREZ_PATH;

    @Test
    public void testGetEntrezRecordJson() throws IOException, URISyntaxException {
        String entrez = IOUtils.toString(getClass().getClassLoader().
                getResourceAsStream("pmidrecord.json"), StandardCharsets.UTF_8);

        String pmid = "11111111";

        stubFor(get(urlPathEqualTo("/entrez/eutils/esummary.fcgi"))
                .withQueryParam("db", WireMock.equalTo("pubmed"))
                .withQueryParam("retmode", WireMock.equalTo("json"))
                .withQueryParam("rettype", WireMock.equalTo("abstract"))
                .withQueryParam("id", WireMock.equalTo(pmid))
                .willReturn(aResponse().withStatus(200).withBody(entrez)));

        JSONObject pmr = pmidLookup.retrievePubMedRecordAsJson(pmid);
        assertTrue(pmr.getString("source").contains("Journal A"));
    }

    @Test
    public void testGetPubMedRecord() throws IOException {
        String entrez = IOUtils.toString(getClass().getClassLoader().
                getResourceAsStream("pmidrecord.json"), StandardCharsets.UTF_8);

        String pmid = "11111111";

        stubFor(get(urlPathEqualTo("/entrez/eutils/esummary.fcgi"))
                .withQueryParam("db", WireMock.equalTo("pubmed"))
                .withQueryParam("retmode", WireMock.equalTo("json"))
                .withQueryParam("rettype", WireMock.equalTo("abstract"))
                .withQueryParam("id", WireMock.equalTo(pmid))
                .willReturn(aResponse().withStatus(200).withBody(entrez)));

        PubMedEntrezRecord record = pmidLookup.retrievePubMedRecord(pmid);
        assertEquals("10.1000/a.abcd.1234", record.getDoi());
    }

    @Test
    public void testGetPubMedRecordWithHighAsciiChars() throws IOException {
        String entrez = IOUtils.toString(getClass().getClassLoader().
                getResourceAsStream("pmid_record_ascii.json"), StandardCharsets.UTF_8);

        String pmid = "11111111";

        stubFor(get(urlPathEqualTo("/entrez/eutils/esummary.fcgi"))
                .withQueryParam("db", WireMock.equalTo("pubmed"))
                .withQueryParam("retmode", WireMock.equalTo("json"))
                .withQueryParam("rettype", WireMock.equalTo("abstract"))
                .withQueryParam("id", WireMock.equalTo(pmid))
                .willReturn(aResponse().withStatus(200).withBody(entrez)));

        PubMedEntrezRecord record = pmidLookup.retrievePubMedRecord(pmid);
        assertEquals("10.1002/acn3.333", record.getDoi());
        assertEquals("Age-dependent effects of APOE ε4 in preclinical Alzheimer's disease.", record.getTitle());
    }

}
