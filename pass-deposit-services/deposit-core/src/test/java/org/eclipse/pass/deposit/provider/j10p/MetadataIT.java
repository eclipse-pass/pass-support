/*
 * Copyright 2018 Johns Hopkins University
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
package org.eclipse.pass.deposit.provider.j10p;

import static org.eclipse.pass.deposit.provider.j10p.XMLConstants.DCTERMS_NS;
import static org.eclipse.pass.deposit.provider.j10p.XMLConstants.DCT_BIBLIOCITATION;
import static org.eclipse.pass.deposit.provider.j10p.XMLConstants.DC_CONTRIBUTOR;
import static org.eclipse.pass.deposit.provider.j10p.XMLConstants.DC_NS;
import static org.eclipse.pass.deposit.provider.j10p.XMLConstants.DC_PUBLISHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.pass.deposit.AbstractDepositSubmissionIT;
import org.eclipse.pass.deposit.builder.DepositSubmissionMapper;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.deposit.util.SubmissionTestUtil;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Element;

/**
 * Tests the transport of metadata from a Submission Fedora resource through the
 * deposit services data model and on to the DSpace XML that is suitable for JScholarship.
 */
public class MetadataIT extends AbstractDepositSubmissionIT {

    private static final String METADATA_1 = "{\n  \"authors\": [\n    {\n      \"author\": \"Christine Cagney\"\n" +
        "    },\n    {\n      \"author\": \"Mary Beth Lacey\"\n    }\n  ],\n  \"agreements\": {\n    " +
        "\"JScholarship\": \"Text removed.\"\n  },\n  \"title\": \"My Test Article\",\n  \"journal-title\": " +
        "\"Nature Communications\",\n  \"issns\": [\n    {\n      \"issn\": \"2041-1723\",\n      \"pubType\": " +
        "\"Print\"\n    }\n  ],\n  \"publisher\": \"Elsevier\",\n  \"publicationDate\": \"Fall 2016\",\n  " +
        "\"abstract\": \"Abstract text\",\n  \"journal-NLMTA-ID\": \"Nat Commun\",\n  \"agent_information\": " +
        "{\n    \"name\": \"Chrome\",\n    \"version\": \"69\"\n  }\n}";

    private static final String METADATA_2 = "{\n  \"authors\": [\n    {\n      \"author\": \"Christine Cagney\"\n   " +
        "},\n    {\n      \"author\": \"Mary Beth Lacey\"\n    },\n    {\n      \"author\": \"David Michael Starsky\"" +
        "\n    },\n    {\n      \"author\": \"Kenneth Richard Hutchinson\"\n    }\n  ],\n  \"agreements\": {\n    " +
        "\"JScholarship\": \"Text removed.\"\n  },\n  \"title\": \"My Test Article\",\n  \"journal-title\": " +
        "\"Nature Communications\",\n  \"issns\": [\n    {\n      \"issn\": \"2041-1723\",\n      " +
        "\"pubType\": \"Print\"\n    }\n  ],\n  \"publisher\": \"Elsevier\",\n  \"publicationDate\": " +
        "\"Fall 2016\",\n  \"abstract\": \"Abstract text\",\n  \"journal-NLMTA-ID\": \"Nat Commun\",\n  " +
        "\"agent_information\": {\n    \"name\": \"Chrome\",\n    \"version\": \"69\"\n  }\n}";

    @Autowired private SubmissionTestUtil submissionTestUtil;
    @Autowired private DepositSubmissionMapper depositSubmissionMapper;

    @Test
    public void commonContributorsAndFewAuthors() throws Exception {
        // GIVEN
        final List<String> expectedContributors = List.of("Christine Cagney", "Mary Beth Lacey", "Bob Smith",
            "Suzanne Vega", "John Doe");
        DepositSubmission depositSubmission = buildDepositSubmission(METADATA_1);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DspaceMetadataDomWriter domWriter = new DspaceMetadataDomWriter(dbf);

        // WHEN
        Element qdc = domWriter.createDublinCoreMetadataDCMES(depositSubmission);

        // THEN
        // Take publisher from "common" if not in "crossref".
        assertNotNull(qdc.getElementsByTagNameNS(DC_NS, DC_PUBLISHER).item(0).getTextContent());
        assertEquals("Elsevier", qdc.getElementsByTagNameNS(DC_NS, DC_PUBLISHER).item(0).getTextContent());

        // Contributor list does not include submitting user, only PIs (from Grant) and authors (from metadata)
        assertEquals(5, qdc.getElementsByTagNameNS(DC_NS, DC_CONTRIBUTOR).getLength());
        for (int i = 0; i < 5; i++) {
            // Contributors must have names, whether they come from
            // first/middle/last or only display name (in Submission), or from metadata.
            Element contributor = (Element) qdc.getElementsByTagNameNS(DC_NS, DC_CONTRIBUTOR).item(i);
            assertTrue(expectedContributors.contains(contributor.getTextContent()));
        }

        // In citation, list up to three authors.  Take publication date from "common" if not in "crossref".
        assertNotNull(qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent());
        assertEquals("Christine Cagney, Mary Beth Lacey. (Fall 2016). \"My Test Article.\" Nature Communications.",
                     qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent());
    }

    @Test
    public void crossrefAndManyAuthors() throws Exception {
        // GIVEN
        DepositSubmission depositSubmission = buildDepositSubmission(METADATA_2);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DspaceMetadataDomWriter domWriter = new DspaceMetadataDomWriter(dbf);

        // WHEN
        Element qdc = domWriter.createDublinCoreMetadataDCMES(depositSubmission);

        // THEN
        // In citation, use "et al" for more than three authors.
        // Publication date in "crossref" has precedence over one in "common".
        assertNotNull(qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent());
        assertEquals(
            "Christine Cagney, Mary Beth Lacey, David Michael Starsky, et al. (Fall 2016). \"My Test Article.\" " +
            "Nature Communications.",
            qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent());
    }

    private DepositSubmission buildDepositSubmission(String metadata) throws IOException {
        InputStream jsonInputStream = ResourceTestUtil.readSubmissionJson("sample1");
        List<PassEntity> entities = new ArrayList<>();
        Submission passSubmission = submissionTestUtil.readSubmissionJsonAndAddToPass(jsonInputStream, entities);
        passSubmission.setMetadata(metadata);
        return depositSubmissionMapper.createDepositSubmission(passSubmission, new ArrayList<>());
    }
}
