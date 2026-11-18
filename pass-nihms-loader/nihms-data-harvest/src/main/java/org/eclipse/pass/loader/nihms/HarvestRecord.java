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

/**
 * One row of harvested data: a publication and an award it acknowledges.
 *
 * @param pmid        the PubMed ID, null if unknown
 * @param pmcid       the PMC ID, null if the publication is not in PMC
 * @param nihmsId     the NIHMS manuscript ID, null if unknown
 * @param grantNumber the award number
 * @param title       the article title, may be null
 * @param doi         the DOI
 */
public record HarvestRecord(String pmid, String pmcid, String nihmsId, String grantNumber, String title,
                            String doi) {
}
