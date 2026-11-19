/*
 * Copyright 2026 Johns Hopkins University
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

@WireMockTest
public class PmcIdConverterClientTest {

    private static final String PATH = "/tools/idconv/api/v1/articles/";
    private static final String EMPTY = "{\"status\":\"ok\",\"records\":[]}";

    private static PmcIdConverterClient client(WireMockRuntimeInfo wm) {
        return new PmcIdConverterClient(new OkHttpClient(), wm.getHttpBaseUrl() + PATH, "pass-test",
                                        "test@localhost", 0, 1);
    }

    private String resource(String name) throws IOException {
        return new String(getClass().getResourceAsStream(name).readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    public void testLookupDois(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(PATH))
            .withQueryParam("ids", equalTo("10.1084/JEM.20020509,10.1000/notinpmc,10.1000/current"))
            .withQueryParam("idtype", equalTo("doi"))
            .withQueryParam("format", equalTo("json"))
            .withQueryParam("versions", equalTo("yes"))
            .withQueryParam("tool", equalTo("pass-test"))
            .withQueryParam("email", equalTo("test@localhost"))
            .willReturn(aResponse().withStatus(200).withBody(resource("/idconv-found.json"))));

        Map<String, PmcIds> found = client(wm).lookupDois(
            List.of("10.1084/JEM.20020509", "10.1000/notinpmc", "10.1000/current"));

        // The NIHMSID is taken from an older version, the key is the lower case DOI
        assertEquals(Map.of(
            "10.1084/jem.20020509", new PmcIds("14699080", "PMC1193645", "NIHMS2203"),
            "10.1000/current", new PmcIds("333", "PMC222", null)), found);
    }

    @Test
    public void testBatchesOfMaxIds(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody(EMPTY)));
        List<String> dois = new ArrayList<>();
        for (int i = 0; i < PmcIdConverterClient.MAX_IDS + 1; i++) {
            dois.add("10.1000/" + i);
        }

        assertTrue(client(wm).lookupDois(dois).isEmpty());

        verify(2, getRequestedFor(urlPathEqualTo(PATH)));
        verify(1, getRequestedFor(urlPathEqualTo(PATH))
            .withQueryParam("ids", equalTo("10.1000/" + PmcIdConverterClient.MAX_IDS)));
    }

    @Test
    public void testRejectedDoiDoesNotLoseOthers(WireMockRuntimeInfo wm) throws IOException {
        // Any request including the bad DOI is rejected as a whole
        stubFor(get(urlPathEqualTo(PATH)).atPriority(1).withQueryParam("ids", containing("bad-doi"))
            .willReturn(aResponse().withStatus(400).withBody(resource("/idconv-invalid.json"))));
        stubFor(get(urlPathEqualTo(PATH)).atPriority(2)
            .willReturn(aResponse().withStatus(200).withBody(resource("/idconv-found.json"))));

        Map<String, PmcIds> found = client(wm).lookupDois(
            List.of("10.1084/jem.20020509", "bad-doi", "10.1000/notinpmc", "10.1000/current"));

        assertEquals(2, found.size());
        assertEquals("PMC1193645", found.get("10.1084/jem.20020509").pmcid());
    }

    @Test
    public void testDoiWithCommaIsSkipped(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody(EMPTY)));

        assertTrue(client(wm).lookupDois(List.of("10.1000/a,b", "10.1000/ok")).isEmpty());

        verify(1, getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("ids", equalTo("10.1000/ok")));
    }

    @Test
    public void testFailsOnServerError(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        assertThrows(IOException.class, () -> client(wm).lookupDois(List.of("10.1000/a")));
        verify(HttpJsonFetcher.MAX_ATTEMPTS, getRequestedFor(urlPathEqualTo(PATH)));
    }
}
