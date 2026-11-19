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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    static final String RATE_LIMIT_HEADER = "x-rate-limit-limit";
    static final String RATE_INTERVAL_HEADER = "x-rate-limit-interval";

    // A number and an optional unit, e.g. 1s
    private static final Pattern RATE_INTERVAL = Pattern.compile("(\\d+)\\s*(ms|s|m)?");

    private final HttpJsonFetcher fetcher;
    private final HttpUrl worksUrl;
    private final String mailto;
    private final long defaultRequestIntervalMs;
    private long requestIntervalMs;

    /**
     * @param okHttpClient      the HTTP client
     * @param baseUrl           the base URL of the Crossref REST API
     * @param mailto            contact address sent with each request, which Crossref uses for rate
     *                          limiting. May be blank, in which case the slower public limits apply.
     * @param requestIntervalMs time to wait after a request whose response does not state the rate
     *                          limit. When it does, the wait is taken from the response.
     * @param retryWaitMs       time to wait before the first retry of a failed request, doubled for each
     *                          further retry
     */
    public CrossrefClient(OkHttpClient okHttpClient,
                          @Value("${nihmsetl.crossref.url}") String baseUrl,
                          @Value("${nihmsetl.crossref.mailto:}") String mailto,
                          @Value("${nihmsetl.crossref.request-interval-ms}") long requestIntervalMs,
                          @Value("${nihmsetl.http.retry-wait-ms}") long retryWaitMs) {
        this.fetcher = new HttpJsonFetcher(okHttpClient, retryWaitMs,
                                           HttpJsonFetcher.userAgent(HttpJsonFetcher.PRODUCT, mailto));
        this.worksUrl = HttpUrl.get(baseUrl).newBuilder().addPathSegment("works").build();
        this.mailto = mailto;
        this.defaultRequestIntervalMs = requestIntervalMs;
        this.requestIntervalMs = requestIntervalMs;
        if (StringUtils.isBlank(mailto)) {
            LOG.warn("nihmsetl.crossref.mailto is not set. Crossref will apply its public rate limits.");
        }
    }

    /**
     * Work out how far apart requests must be from the rate limit Crossref states in a response.
     *
     * @param limit    the x-rate-limit-limit header: the number of requests allowed in the interval
     * @param interval the x-rate-limit-interval header: the length of the interval, e.g. 1s
     * @return the time to leave between requests in milliseconds, or -1 if the headers are missing or
     *     cannot be understood
     */
    static long intervalFromRateLimit(String limit, String interval) {
        if (limit == null || interval == null) {
            return -1;
        }
        Matcher matcher = RATE_INTERVAL.matcher(interval.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return -1;
        }
        try {
            long requests = Long.parseLong(limit.trim());
            long length = Long.parseLong(matcher.group(1));
            if (requests <= 0 || length <= 0) {
                return -1;
            }
            long lengthMs = switch (matcher.group(2) == null ? "s" : matcher.group(2)) {
                case "ms" -> length;
                case "m" -> length * 60_000;
                default -> length * 1000;
            };
            // Round up so that the limit is never exceeded
            return (lengthMs + requests - 1) / requests;
        } catch (NumberFormatException e) {
            return -1;
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

    long requestIntervalMs() {
        return requestIntervalMs;
    }

    private JSONObject fetch(HttpUrl url) throws IOException {
        LOG.debug("Retrieving: {}", url);
        HttpJsonFetcher.Result result = fetcher.get(url);
        long stated = intervalFromRateLimit(result.header(RATE_LIMIT_HEADER), result.header(RATE_INTERVAL_HEADER));
        long interval = stated < 0 ? defaultRequestIntervalMs : stated;
        if (interval != requestIntervalMs) {
            LOG.info("Leaving {} ms between Crossref requests (rate limit stated: {} per {})", interval,
                     result.header(RATE_LIMIT_HEADER), result.header(RATE_INTERVAL_HEADER));
            requestIntervalMs = interval;
        }
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
