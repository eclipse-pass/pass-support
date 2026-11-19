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

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GETs a URL, retrying when the service asks the client to slow down or fails temporarily.
 */
class HttpJsonFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(HttpJsonFetcher.class);

    static final int MAX_ATTEMPTS = 4;

    static final String PRODUCT = "pass-nihms-loader";

    private static final String PROJECT_URL = "https://github.com/eclipse-pass/pass-support";

    private final OkHttpClient okHttp;
    private final long retryWaitMs;
    private final String userAgent;

    HttpJsonFetcher(OkHttpClient okHttp, long retryWaitMs, String userAgent) {
        this.okHttp = okHttp;
        this.retryWaitMs = retryWaitMs;
        this.userAgent = userAgent;
    }

    /**
     * Build a User-Agent which tells a service what is calling it and who to contact about it.
     *
     * @param product name of the calling tool
     * @param contact contact email address, may be blank
     * @return e.g. {@code pass-nihms-loader/2.6.0 (https://github.com/eclipse-pass/pass-support;
     *     mailto:someone@example.org)}
     */
    static String userAgent(String product, String contact) {
        // Set in the manifest of a packaged build, absent when run from classes
        String version = HttpJsonFetcher.class.getPackage().getImplementationVersion();
        return (StringUtils.isBlank(product) ? PRODUCT : product.trim())
            + (version == null ? "" : "/" + version)
            + " (" + PROJECT_URL
            + (StringUtils.isBlank(contact) ? "" : "; mailto:" + contact.trim()) + ")";
    }

    /**
     * The outcome of a request that was answered.
     *
     * @param code    the HTTP status code
     * @param body    the response body
     * @param headers the response headers
     */
    record Result(int code, String body, Headers headers) {
        boolean isSuccessful() {
            return code >= 200 && code < 300;
        }

        /**
         * @param name a header name, in any case
         * @return the value of the header, null if the response did not have it
         */
        String header(String name) {
            return headers.get(name);
        }
    }

    /**
     * @param url the URL to GET
     * @return the final response. A 429 or 5xx response is only returned once the attempts are used up.
     * @throws IOException if the last attempt could not be completed
     */
    Result get(HttpUrl url) throws IOException {
        IOException lastFailure = null;
        Result result = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                sleep(retryWaitMs * (1L << (attempt - 2)));
            }
            Request request = new Request.Builder().get().url(url).header("User-Agent", userAgent).build();
            try (Response res = okHttp.newCall(request).execute()) {
                result = new Result(res.code(), res.body().string(), res.headers());
                lastFailure = null;
            } catch (IOException e) {
                lastFailure = e;
                result = null;
                LOG.warn("Attempt {} of {} failed for {}: {}", attempt, MAX_ATTEMPTS, url, e.getMessage());
                continue;
            }
            if (result.code() != 429 && result.code() < 500) {
                return result;
            }
            LOG.warn("Attempt {} of {} got HTTP {} for {}", attempt, MAX_ATTEMPTS, result.code(), url);
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return result;
    }

    static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }
}
