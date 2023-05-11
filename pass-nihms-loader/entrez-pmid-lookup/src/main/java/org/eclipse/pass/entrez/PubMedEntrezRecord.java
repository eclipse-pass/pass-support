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

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Class is instantiated by passing in a JSONObject from Entrez representing a PubMed
 * article record. Using getters you can then pull fields from that JSONObject
 *
 * @author Karen Hanson
 */
public class PubMedEntrezRecord {

    //Various keys to retrieve data from JSON
    private static final String JSON_PMID_KEY = "uid";
    private static final String JSON_IDTYPE_KEY = "idtype";
    private static final String JSON_IDTYPE_DOI = "doi";
    private static final String JSON_IDVALUE_KEY = "value";
    private static final String JSON_ARTICLEIDS_KEY = "articleids";
    private static final String JSON_TITLE_KEY = "title";
    private static final String JSON_VOLUME_KEY = "volume";
    private static final String JSON_ISSUE_KEY = "issue";
    private static final String JSON_ISSN_KEY = "issn";
    private static final String JSON_ESSN_KEY = "essn";
    private static final String VALID_DOI_CONTAINS = "10.";

    /**
     * JSONObject for single PubMed record from Entrez API
     */
    private JSONObject entrezJson;

    /**
     * Instantiate a PubMedRecord by passing in a JSONObject representing a single result from the
     * PubMed database of the Entrez API. The root of this record starts at the PMID if the results JSON. Here is a
     * sample record:
     * <a href="https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&amp;retmode=json&amp;rettype=full&amp;id=27771272">https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&amp;retmode=json&amp;rettype=full&amp;id=27771272</a>
     *
     * @param entrezJson JSONObject for single PubMed record from Entrez API.
     */
    public PubMedEntrezRecord(JSONObject entrezJson) {
        if (entrezJson == null) {
            throw new IllegalArgumentException("entrezJson cannot be null");
        }
        this.entrezJson = entrezJson;
    }

    /**
     * Extract DOI from Entrez JSON as https://doi.org/10....
     *
     * @return the doi
     */
    public String getDoi() {
        String doi = null;
        JSONArray ids = entrezJson.getJSONArray(JSON_ARTICLEIDS_KEY);
        for (Object oid : ids) {
            JSONObject id = (JSONObject) oid;
            if (id.getString(JSON_IDTYPE_KEY).equals(JSON_IDTYPE_DOI)) {
                doi = id.getString(JSON_IDVALUE_KEY);
                if (doi != null && doi.length() > 0 && doi.contains(VALID_DOI_CONTAINS)) {
                    doi = doi.trim();
                }
            }
        }
        return doi;
    }

    /**
     * Extracts PMID from Entrez JSON record and returns it
     *
     * @return the title
     */
    public String getPmid() {
        return entrezJson.getString(JSON_PMID_KEY);
    }

    /**
     * Extracts title from Entrez JSON record and returns it
     *
     * @return the title
     */
    public String getTitle() {
        return entrezJson.getString(JSON_TITLE_KEY);
    }

    /**
     * Extracts volume from Entrez JSON record and returns it
     *
     * @return the volume
     */
    public String getVolume() {
        return entrezJson.getString(JSON_VOLUME_KEY);
    }

    /**
     * Extracts issue from Entrez JSON record and returns it
     *
     * @return the issue
     */
    public String getIssue() {
        return entrezJson.getString(JSON_ISSUE_KEY);
    }

    /**
     * Extracts ISSN from Entrez JSON record and returns it
     *
     * @return the ISSN
     */
    public String getIssn() {
        return entrezJson.getString(JSON_ISSN_KEY);
    }

    /**
     * Extracts ESSN from Entrez JSON record and returns it
     *
     * @return the ESSN
     */
    public String getEssn() {
        return entrezJson.getString(JSON_ESSN_KEY);
    }

}
