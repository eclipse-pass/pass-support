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
import java.time.LocalDate;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

@WireMockTest
public class CrossrefClientTest {

    private static final String FILTER = "award.number:R01 EB019135,award.number:R01EB019135," +
        "from-pub-date:2025-11-18,type:journal-article";
    private static final List<String> AWARDS = List.of("R01 EB019135", "R01EB019135");
    private static final LocalDate FROM = LocalDate.of(2025, 11, 18);

    private static CrossrefClient client(WireMockRuntimeInfo wm, String mailto) {
        return new CrossrefClient(new OkHttpClient(), wm.getHttpBaseUrl(), mailto, 0, 1);
    }

    private static String page(int items, String nextCursor) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < items; i++) {
            array.put(new JSONObject().put("DOI", "10.1000/" + nextCursor + "-" + i)
                                      .put("title", new JSONArray().put("Title " + i)));
        }
        return new JSONObject().put("message", new JSONObject().put("items", array)
                                                               .put("next-cursor", nextCursor)).toString();
    }

    @Test
    public void testFindWorksByAward(WireMockRuntimeInfo wm) throws IOException {
        String body = new String(getClass().getResourceAsStream("/crossref-works.json").readAllBytes(),
                                 StandardCharsets.UTF_8);
        stubFor(get(urlPathEqualTo("/works"))
            .withQueryParam("filter", equalTo(FILTER))
            .withQueryParam("select", equalTo("DOI,title"))
            .withQueryParam("rows", equalTo("1000"))
            .withQueryParam("cursor", equalTo("*"))
            .withQueryParam("mailto", equalTo("test@localhost"))
            .willReturn(aResponse().withStatus(200).withBody(body)));

        List<CrossrefWork> works = client(wm, "test@localhost").findWorksByAward(AWARDS, FROM);

        assertEquals(List.of(
            new CrossrefWork("10.2352/J.ImagingSci.Technol.2017.61.1.010405", "Article One"),
            new CrossrefWork("10.1000/notitle", null)), works);
    }

    @Test
    public void testNoMailtoSentWhenBlank(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo("/works")).willReturn(aResponse().withStatus(200).withBody(page(0, "c"))));

        assertTrue(client(wm, "").findWorksByAward(AWARDS, FROM).isEmpty());

        verify(getRequestedFor(urlPathEqualTo("/works")).withoutQueryParam("mailto"));
    }

    @Test
    public void testFollowsCursorUntilShortPage(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo("/works")).withQueryParam("cursor", equalTo("*"))
            .willReturn(aResponse().withStatus(200).withBody(page(CrossrefClient.ROWS, "second"))));
        stubFor(get(urlPathEqualTo("/works")).withQueryParam("cursor", equalTo("second"))
            .willReturn(aResponse().withStatus(200).withBody(page(3, "third"))));

        List<CrossrefWork> works = client(wm, "test@localhost").findWorksByAward(AWARDS, FROM);

        assertEquals(CrossrefClient.ROWS + 3, works.size());
        verify(2, getRequestedFor(urlPathEqualTo("/works")));
    }

    @Test
    public void testRetriesWhenRateLimited(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(urlPathEqualTo("/works")).inScenario("limit").whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(429)).willSetStateTo("ok"));
        stubFor(get(urlPathEqualTo("/works")).inScenario("limit").whenScenarioStateIs("ok")
            .willReturn(aResponse().withStatus(200).withBody(page(1, "c"))));

        assertEquals(1, client(wm, "test@localhost").findWorksByAward(AWARDS, FROM).size());
        verify(2, getRequestedFor(urlPathEqualTo("/works")));
    }

    @Test
    public void testFailsWhenStillRateLimited(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/works")).willReturn(aResponse().withStatus(429)));

        assertThrows(IOException.class, () -> client(wm, "test@localhost").findWorksByAward(AWARDS, FROM));
        verify(HttpJsonFetcher.MAX_ATTEMPTS, getRequestedFor(urlPathEqualTo("/works")));
    }

    @Test
    public void testFailsOnClientError(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/works")).willReturn(aResponse().withStatus(400)));

        assertThrows(IOException.class, () -> client(wm, "test@localhost").findWorksByAward(AWARDS, FROM));
        verify(1, getRequestedFor(urlPathEqualTo("/works")));
    }
}
