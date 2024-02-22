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
package org.eclipse.pass.loader.nihms;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Karen Hanson
 */
@Component
public class NihmsHarvester {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsHarvester.class);

    private final UrlBuilder urlBuilder;
    private final NihmsHarvesterDownloader nihmsHarvesterDownloader;

    /**
     * Initiate harvester with required properties
     */
    public NihmsHarvester(UrlBuilder urlBuilder,
                          NihmsHarvesterDownloader nihmsHarvesterDownloader) {
        this.urlBuilder = urlBuilder;
        this.nihmsHarvesterDownloader = nihmsHarvesterDownloader;
    }

    /**
     * Retrieve files from NIHMS based on status list and startDate provided
     *
     * @param statusesToDownload list of {@code NihmsStatus} types to download from the NIHMS website
     * @param startDate          formatted as {@code yyyy-mm}, can be null to default to 1 year prior to harvest date
     */
    public void harvest(Set<NihmsStatus> statusesToDownload, String startDate) {
        if (CollectionUtils.isEmpty(statusesToDownload)) {
            throw new RuntimeException("statusesToDownload list cannot be empty");
        }
        if (!validStartDate(startDate)) {
            throw new RuntimeException(
                String.format("The startDate %s is not valid. The date must be formatted as mm-yyyy", startDate));
        }

        try {
            Map<String, String> params = new HashMap<>();

            if (StringUtils.isNotEmpty(startDate)) {
                startDate = startDate.replace("-", "/");
                LOG.info("Filtering with Start Date " + startDate);
                params.put("pdf", startDate);
            }

            if (statusesToDownload.contains(NihmsStatus.COMPLIANT)) {
                LOG.info("Goto {} list", NihmsStatus.COMPLIANT);
                URL url = urlBuilder.compliantUrl(params);
                nihmsHarvesterDownloader.download(url, NihmsStatus.COMPLIANT);
            }

            if (statusesToDownload.contains(NihmsStatus.NON_COMPLIANT)) {
                LOG.info("Goto {} list", NihmsStatus.NON_COMPLIANT);
                URL url = urlBuilder.nonCompliantUrl(params);
                nihmsHarvesterDownloader.download(url, NihmsStatus.NON_COMPLIANT);
            }

            if (statusesToDownload.contains(NihmsStatus.IN_PROCESS)) {
                LOG.info("Goto {} list", NihmsStatus.IN_PROCESS);
                URL url = urlBuilder.inProcessUrl(params);
                nihmsHarvesterDownloader.download(url, NihmsStatus.IN_PROCESS);
            }

        } catch (Exception ex) {
            throw new RuntimeException("An error occurred while downloading the NIHMS files.", ex);
        }
    }

    /**
     * null or empty are OK for start date, but a badly formatted date that does not have the format mm-yyyy should
     * return false
     *
     * @param startDate true if valid start date (empty or formatted mm-yyyy)
     * @return true if valid start date (empty or formatted mm-yyyy)
     */
    public static boolean validStartDate(String startDate) {
        return (StringUtils.isEmpty(startDate) || startDate.matches("^(0?[1-9]|1[012])-(\\d{4})$"));
    }

}
