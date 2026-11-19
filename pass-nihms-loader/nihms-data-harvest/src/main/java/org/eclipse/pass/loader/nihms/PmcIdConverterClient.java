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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 * Looks DOIs up in the PMC ID Converter API to find out which are in PMC and under which identifiers.
 */
@Component
public class PmcIdConverterClient {

    private static final Logger LOG = LoggerFactory.getLogger(PmcIdConverterClient.class);

    /** The most IDs the service accepts in one request. */
    static final int MAX_IDS = 200;

    private static final String NIHMS_PREFIX = "NIHMS";

    private final HttpJsonFetcher fetcher;
    private final HttpUrl serviceUrl;
    private final String tool;
    private final String email;
    private final long requestIntervalMs;

    /**
     * @param okHttpClient      the HTTP client
     * @param serviceUrl        the URL of the ID Converter API
     * @param tool              name of this tool, sent with each request
     * @param email             contact address sent with each request, may be blank
     * @param requestIntervalMs time to wait after each request
     * @param retryWaitMs       time to wait before the first retry of a failed request, doubled for each
     *                          further retry
     */
    public PmcIdConverterClient(OkHttpClient okHttpClient,
                                @Value("${nihmsetl.idconv.url}") String serviceUrl,
                                @Value("${nihmsetl.idconv.tool}") String tool,
                                @Value("${nihmsetl.idconv.email:}") String email,
                                @Value("${nihmsetl.idconv.request-interval-ms}") long requestIntervalMs,
                                @Value("${nihmsetl.http.retry-wait-ms}") long retryWaitMs) {
        this.fetcher = new HttpJsonFetcher(okHttpClient, retryWaitMs, HttpJsonFetcher.userAgent(tool, email));
        this.serviceUrl = HttpUrl.get(serviceUrl);
        this.tool = tool;
        this.email = email;
        this.requestIntervalMs = requestIntervalMs;
    }

    /**
     * Look up DOIs in PMC.
     *
     * @param dois the DOIs to look up
     * @return the PMC identifiers of the DOIs which are in PMC, keyed by lower case DOI. A DOI which is
     * not in PMC has no entry.
     * @throws IOException if the service cannot be reached
     */
    public Map<String, PmcIds> lookupDois(Collection<String> dois) throws IOException {
        List<String> ids = new ArrayList<>();
        for (String doi : dois) {
            // IDs are sent as a comma separated list, so a DOI containing a comma cannot be asked for
            if (doi.contains(",")) {
                LOG.warn("DOI {} cannot be looked up in PMC because it contains a comma", doi);
            } else {
                ids.add(doi);
            }
        }

        Map<String, PmcIds> found = new HashMap<>();
        for (int start = 0; start < ids.size(); start += MAX_IDS) {
            lookupBatch(ids.subList(start, Math.min(start + MAX_IDS, ids.size())), found);
        }
        return found;
    }

    private void lookupBatch(List<String> dois, Map<String, PmcIds> found) throws IOException {
        HttpUrl.Builder url = serviceUrl.newBuilder()
            .addQueryParameter("ids", String.join(",", dois))
            .addQueryParameter("idtype", "doi")
            .addQueryParameter("format", "json")
            .addQueryParameter("versions", "yes")
            .addQueryParameter("tool", tool);
        if (StringUtils.isNotBlank(email)) {
            url.addQueryParameter("email", email);
        }

        LOG.debug("Looking up {} DOIs in PMC", dois.size());
        HttpJsonFetcher.Result result = fetcher.get(url.build());
        HttpJsonFetcher.sleep(requestIntervalMs);

        if (result.code() == 400) {
            // One ID the service does not accept as a DOI fails the whole request. Narrow it down.
            if (dois.size() == 1) {
                LOG.warn("PMC ID Converter rejected DOI {}, treating it as not in PMC", dois.get(0));
                return;
            }
            int middle = dois.size() / 2;
            lookupBatch(dois.subList(0, middle), found);
            lookupBatch(dois.subList(middle, dois.size()), found);
            return;
        }
        if (!result.isSuccessful()) {
            throw new IOException(String.format("Error looking up DOIs in PMC (HTTP status: %d)", result.code()));
        }

        JSONArray records = new JSONObject(result.body()).optJSONArray("records");
        if (records == null) {
            throw new IOException("Unexpected response from the PMC ID Converter");
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject rec = records.getJSONObject(i);
            String pmcid = rec.optString("pmcid", null);
            // A DOI which is not in PMC comes back as a record with status "error"
            if ("error".equals(rec.optString("status")) || StringUtils.isBlank(pmcid)) {
                continue;
            }
            String doi = rec.optString("requested-id", rec.optString("doi", null));
            if (doi == null) {
                continue;
            }
            // pmid is a JSON number
            String pmid = rec.has("pmid") && !rec.isNull("pmid") ? String.valueOf(rec.get("pmid")) : null;
            found.put(doi.toLowerCase(Locale.ROOT), new PmcIds(pmid, pmcid, findNihmsId(rec)));
        }
    }

    /**
     * The manuscript ID is only given on the versions of the article, and not necessarily on the
     * current one.
     */
    private static String findNihmsId(JSONObject rec) {
        JSONArray versions = rec.optJSONArray("versions");
        if (versions == null) {
            return null;
        }
        for (int i = 0; i < versions.length(); i++) {
            String mid = versions.getJSONObject(i).optString("mid", null);
            if (mid != null && mid.toUpperCase(Locale.ROOT).startsWith(NIHMS_PREFIX)) {
                return mid.toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }
}
