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
package org.eclipse.pass.entrez;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to retrieve a PMID records from Entrez. If you prefer to avoid dealing with JSON,
 * there is an option to retrieve a PubMedRecord object where you can use standard getters to
 * retrieve article details.
 *
 * @author Karen Hanson
 * @version $Id$
 */
public class PmidLookup {

    private static final Logger LOG = LoggerFactory.getLogger(PmidLookup.class);

    /**
     * The default Entrez path does not include an API path. This property can be overridden with a System Property
     * Note that as of May 2018, Entrez supports 3 seconds per second without an API key, above this will result in
     * delayed responses.
     * https://www.ncbi.nlm.nih.gov/books/NBK25497/
     */
    private static final String DEFAULT_ENTREZ_PATH = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary" +
                                                      ".fcgi?db=pubmed&retmode=json&rettype=abstract&id=%s";

    private static final String ENTREZ_PATH_KEY = "entrez.pmid.path";

    private static final String JSON_ERROR_KEY = "error";
    private static final String JSON_RESULT_KEY = "result";

    private String entrezPath;

    public PmidLookup() {
        entrezPath = System.getProperty(ENTREZ_PATH_KEY, DEFAULT_ENTREZ_PATH);
    }

    /**
     * Retrieve PubMedRecord object for PMID record from NIH's Entrez API service.
     *
     * @param pmid pub med id
     * @return the record
     */
    public PubMedEntrezRecord retrievePubMedRecord(String pmid) {
        JSONObject jsonObj = retrievePubMedRecordAsJson(pmid);
        PubMedEntrezRecord pmr = (jsonObj != null ? new PubMedEntrezRecord(jsonObj) : null);
        return pmr;
    }

    /**
     * Retrieve JSON for PMID record from NIH's Entrez API service. Returns JSON object containing the record
     * or null if no match found. Note that "no match found" means there is no record for that pmid, whereas
     * a RuntimeException means communication with the service failed, and the client app can decide what
     * to do about those.
     *
     * @param pmid pub med id
     * @return the record as a JSON object
     */
    public JSONObject retrievePubMedRecordAsJson(String pmid) {
        if (pmid == null) {
            throw new IllegalArgumentException("pmid cannot be null");
        }
        JSONObject jsonRecord = null;

        try {
            jsonRecord = retrieveJsonFromApi(pmid);
            if (jsonRecord == null) {
                // pause and retry once to allow for API limitations
                LOG.info("Pausing before trying to pull PMID {} from Entrez again", pmid);
                TimeUnit.MILLISECONDS.sleep(400);
                jsonRecord = retrieveJsonFromApi(pmid);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("A problem occurred while waiting to retry Entrez API call", e);
        }

        return jsonRecord;
    }

    /**
     * Calls the Entrez API and finds the root of the JSON record
     *
     * @param pmid pub med id
     * @return the root JSON record
     */
    private JSONObject retrieveJsonFromApi(String pmid) {
        JSONObject root = null;
        String path = String.format(entrezPath, pmid);
        try {
            HttpClient client = HttpClientBuilder
                .create()
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, false))
                .build();
            HttpGet httpget = new HttpGet(new URI(path));
            HttpResponse response = client.execute(httpget);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                root = walkToJsonRoot(result, pmid);

                if (root.has(JSON_ERROR_KEY)) {
                    //if there is an error key, something went wrong. Log error and return null.
                    String error = root.getString(JSON_ERROR_KEY);
                    LOG.warn("Could not retrieve PMID {} from Entrez. Error: {}", pmid, error);
                    root = null;
                }
            } else {
                LOG.warn("Could not retrieve PMID {} from Entrez. Returned empty value.", pmid);
                root = null;
            }

        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not convert convert path to URL: " + path, e);
        } catch (IllegalStateException | IOException e) {
            LOG.warn("Could not retrieve PMID {} from Entrez. Error: {}", pmid, e);
            throw new RuntimeException("Error while retrieving content from Entrez at URL: " + path, e);
        }

        return root;
    }

    /**
     * Converts to JSONObject then walks to the root of the JSON content. This could be a PMID record
     * or an error message
     *
     * @param jsonEntrezRecord as string
     * @param pmid             pub med id
     * @return the root JSON object
     * @throws IOException if there's an error reading the record
     */
    private JSONObject walkToJsonRoot(String jsonEntrezRecord, String pmid) throws IOException {
        JSONObject root = new JSONObject(jsonEntrezRecord);

        if (root.has(JSON_RESULT_KEY)) {
            root = root.getJSONObject(JSON_RESULT_KEY);
            if (root.has(pmid)) {
                root = (JSONObject) root.getJSONObject(pmid);
            }
        }

        return root;
    }

}
