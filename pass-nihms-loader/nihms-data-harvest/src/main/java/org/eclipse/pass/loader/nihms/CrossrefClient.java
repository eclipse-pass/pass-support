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

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Finds journal articles in the Crossref REST API by the award numbers they acknowledge.
 */
@Component
public class CrossrefClient {

    private static final Logger LOG = LoggerFactory.getLogger(CrossrefClient.class);

    static final int ROWS = 1000;

    private final HttpJsonFetcher fetcher;
    private final HttpUrl worksUrl;
    private final String mailto;
    private final long requestIntervalMs;

    /**
     * @param okHttpClient      the HTTP client
     * @param baseUrl           the base URL of the Crossref REST API
     * @param mailto            contact address sent with each request, which Crossref uses for rate
     *                          limiting. May be blank, in which case the slower public limits apply.
     * @param requestIntervalMs time to wait after each request
     * @param retryWaitMs       time to wait before the first retry of a failed request, doubled for each
     *                          further retry
     */
    public CrossrefClient(OkHttpClient okHttpClient,
                          @Value("${nihmsetl.crossref.url}") String baseUrl,
                          @Value("${nihmsetl.crossref.mailto:}") String mailto,
                          @Value("${nihmsetl.crossref.request-interval-ms}") long requestIntervalMs,
                          @Value("${nihmsetl.http.retry-wait-ms}") long retryWaitMs) {
        this.fetcher = new HttpJsonFetcher(okHttpClient, retryWaitMs);
        this.worksUrl = HttpUrl.get(baseUrl).newBuilder().addPathSegment("works").build();
        this.mailto = mailto;
        this.requestIntervalMs = requestIntervalMs;
        if (StringUtils.isBlank(mailto)) {
            LOG.warn("nihmsetl.crossref.mailto is not set. Crossref will apply its public rate limits.");
        }
    }

    /**
     * Find the journal articles which acknowledge an award written in any of the given ways.
     *
     * @param awardNumbers the forms of the award number to match. Crossref matches whole strings,
     *                     ignoring case.
     * @param fromPubDate  only works published on or after this date are returned
     * @return the matching works, empty if there are none
     * @throws IOException if Crossref cannot be reached
     */
    public List<CrossrefWork> findWorksByAward(Collection<String> awardNumbers, LocalDate fromPubDate)
        throws IOException {
        // Repeating a filter name ORs its values. A comma would end the filter value, so drop any.
        String filter = awardNumbers.stream()
            .filter(award -> !award.contains(","))
            .map(award -> "award.number:" + award)
            .collect(Collectors.joining(","));
        if (filter.isEmpty()) {
            return List.of();
        }
        filter = filter + ",from-pub-date:" + fromPubDate + ",type:journal-article";

        List<CrossrefWork> works = new ArrayList<>();
        String cursor = "*";
        while (true) {
            HttpUrl.Builder url = worksUrl.newBuilder()
                .addQueryParameter("filter", filter)
                .addQueryParameter("select", "DOI,title")
                .addQueryParameter("rows", String.valueOf(ROWS))
                .addQueryParameter("cursor", cursor);
            if (StringUtils.isNotBlank(mailto)) {
                url.addQueryParameter("mailto", mailto);
            }

            JSONObject message = fetch(url.build());
            JSONArray items = message.optJSONArray("items");
            int count = items == null ? 0 : items.length();
            for (int i = 0; i < count; i++) {
                JSONObject item = items.getJSONObject(i);
                String doi = item.optString("DOI", null);
                if (StringUtils.isNotBlank(doi)) {
                    JSONArray titles = item.optJSONArray("title");
                    String title = titles == null || titles.isEmpty() ? null : titles.optString(0, null);
                    works.add(new CrossrefWork(doi, title));
                }
            }

            cursor = message.optString("next-cursor", null);
            if (count < ROWS || cursor == null) {
                break;
            }
        }
        return works;
    }

    private JSONObject fetch(HttpUrl url) throws IOException {
        LOG.debug("Retrieving: {}", url);
        HttpJsonFetcher.Result result = fetcher.get(url);
        HttpJsonFetcher.sleep(requestIntervalMs);
        if (!result.isSuccessful()) {
            throw new IOException(String.format("Error retrieving %s (HTTP status: %d)", url, result.code()));
        }
        JSONObject message = new JSONObject(result.body()).optJSONObject("message");
        if (message == null) {
            throw new IOException("Unexpected response from " + url);
        }
        return message;
    }
}
