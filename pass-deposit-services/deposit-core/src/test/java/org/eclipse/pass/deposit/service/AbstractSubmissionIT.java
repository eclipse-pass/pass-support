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
package org.eclipse.pass.deposit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.eclipse.pass.deposit.AbstractDepositSubmissionIT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractSubmissionIT extends AbstractDepositSubmissionIT {

    @Autowired
    @Qualifier("submissionProcessor")
    protected SubmissionProcessor submissionProcessor;

    protected void initDSpaceApiStubs() {
        stubFor(get("/dspace/api/security/csrf").willReturn(WireMock.notFound().
            withHeader("DSPACE-XSRF-TOKEN", "csrftoken")));
        stubFor(post("/dspace/api/authn/login").willReturn(WireMock.ok().withHeader("Authorization", "authtoken")));

        String searchJson = "{\n"
            + "  \"_embedded\": {\n"
            + "    \"searchResult\": {\n"
            + "      \"_embedded\": {\n"
            + "        \"objects\": [\n"
            + "          {\n"
            + "            \"_embedded\": {\n"
            + "              \"indexableObject\": {\n"
            + "                \"handle\": \"collectionhandle\",\n"
            + "                \"uuid\": \"collectionuuid\"\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        ]\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}\n";
        stubFor(get("/dspace/api/discover/search/objects?query=handle:collectionhandle")
            .willReturn(ok(searchJson)));

        stubFor(post("/dspace/api/submission/workspaceitems?owningCollection=collectionuuid")
            .willReturn(WireMock.ok("{\"_embedded\": {\"workspaceitems\": [{\"id\": 1,"
                + "\"_embedded\": {\"item\": {\"uuid\": \"uuid\", \"metadata\": {}}}}]}}")));

        stubFor(patch("/dspace/api/submission/workspaceitems/1").willReturn(WireMock.ok()));

        stubFor(post("/dspace/api/workflow/workflowitems").willReturn(WireMock.ok()));
    }

    protected void initDSpaceApiStubsForError() {
        stubFor(get("/dspace/api/security/csrf").willReturn(WireMock.notFound().
            withHeader("DSPACE-XSRF-TOKEN", "csrftoken")));
        stubFor(post("/dspace/api/authn/login")
            .willReturn(WireMock.badRequest().withStatusMessage("Testing deposit error")));
    }

    protected void verifyDSpaceApiStubs(int expectedCount) {
        WireMock.verify(expectedCount, getRequestedFor(urlEqualTo("/dspace/api/security/csrf")));
        WireMock.verify(expectedCount, postRequestedFor(urlEqualTo("/dspace/api/authn/login")));
        WireMock.verify(expectedCount, getRequestedFor(
            urlEqualTo("/dspace/api/discover/search/objects?query=handle:collectionhandle")));
        WireMock.verify(expectedCount, postRequestedFor(
            urlEqualTo("/dspace/api/submission/workspaceitems?owningCollection=collectionuuid")));
        WireMock.verify(expectedCount, patchRequestedFor(urlEqualTo("/dspace/api/submission/workspaceitems/1")));
        WireMock.verify(expectedCount, postRequestedFor(urlEqualTo("/dspace/api/workflow/workflowitems")));
    }

}
