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

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Harvests the publications which acknowledge the NIH awards held in PASS, and whether they are in PMC.
 * <p>
 * The NIH awards are read from PASS, the publications acknowledging each award are found in Crossref,
 * and the PMC ID Converter says which of those publications are in PMC. A publication in PMC is written
 * out as compliant, any other as non-compliant. Neither service can tell that a manuscript is on its
 * way through NIHMS, so no in-process records are produced.
 *
 * @author Karen Hanson
 */
@Component
public class NihmsHarvester {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsHarvester.class);

    static final int DEFAULT_HARVEST_MONTHS = 12;

    private final GrantAwardSource grantAwardSource;
    private final CrossrefClient crossrefClient;
    private final PmcIdConverterClient pmcIdConverterClient;
    private final NihmsCsvWriter nihmsCsvWriter;

    /**
     * Initiate harvester with required properties
     *
     * @param grantAwardSource     supplies the NIH awards held in PASS
     * @param crossrefClient       finds the publications acknowledging an award
     * @param pmcIdConverterClient finds the PMC identifiers of publications
     * @param nihmsCsvWriter       writes the harvested data for the transform and load step
     */
    public NihmsHarvester(GrantAwardSource grantAwardSource, CrossrefClient crossrefClient,
                          PmcIdConverterClient pmcIdConverterClient, NihmsCsvWriter nihmsCsvWriter) {
        this.grantAwardSource = grantAwardSource;
        this.crossrefClient = crossrefClient;
        this.pmcIdConverterClient = pmcIdConverterClient;
        this.nihmsCsvWriter = nihmsCsvWriter;
    }

    /**
     * Harvest publication data and write a file for each of the statuses asked for.
     *
     * @param statusesToHarvest   the {@code NihmsStatus} types to write files for
     * @param harvestPeriodMonths only publications published in this many past months are harvested
     */
    public void harvest(Set<NihmsStatus> statusesToHarvest, int harvestPeriodMonths) {
        if (statusesToHarvest == null || statusesToHarvest.isEmpty()) {
            throw new RuntimeException("statusesToHarvest list cannot be empty");
        }

        try {
            LocalDate startDate = LocalDate.now().minusMonths(harvestPeriodMonths);
            LOG.info("Harvesting publications published since {}", startDate);

            Set<NihAward> awards = grantAwardSource.findNihAwards();

            // Lower case DOI to title, and the awards each DOI acknowledges
            Map<String, String> titles = new LinkedHashMap<>();
            Map<String, Set<NihAward>> awardsByDoi = new LinkedHashMap<>();
            int failedAwards = 0;
            for (NihAward award : awards) {
                List<CrossrefWork> works;
                try {
                    works = crossrefClient.findWorksByAward(award.searchVariants(), startDate);
                } catch (IOException | RuntimeException e) {
                    failedAwards++;
                    LOG.error("Publications could not be retrieved for award {}", award.core(), e);
                    continue;
                }
                LOG.debug("Found {} publications for award {}", works.size(), award.core());
                for (CrossrefWork work : works) {
                    String doi = work.doi().toLowerCase(Locale.ROOT);
                    titles.putIfAbsent(doi, work.title());
                    awardsByDoi.computeIfAbsent(doi, key -> new LinkedHashSet<>()).add(award);
                }
            }
            if (failedAwards > 0) {
                if (failedAwards == awards.size()) {
                    throw new IOException("Publications could not be retrieved for any award");
                }
                LOG.warn("Publications could not be retrieved for {} of {} awards", failedAwards, awards.size());
            }
            LOG.info("Found {} publications for {} awards", awardsByDoi.size(), awards.size());

            Map<String, PmcIds> pmcIds = pmcIdConverterClient.lookupDois(awardsByDoi.keySet());

            List<HarvestRecord> compliant = new ArrayList<>();
            List<HarvestRecord> nonCompliant = new ArrayList<>();
            awardsByDoi.forEach((doi, doiAwards) -> {
                PmcIds ids = pmcIds.get(doi);
                for (NihAward award : doiAwards) {
                    if (ids == null) {
                        nonCompliant.add(new HarvestRecord(null, null, null, award.core(), titles.get(doi), doi));
                    } else {
                        compliant.add(new HarvestRecord(ids.pmid(), ids.pmcid(), ids.nihmsId(), award.core(),
                                                        titles.get(doi), doi));
                    }
                }
            });

            if (statusesToHarvest.contains(NihmsStatus.COMPLIANT)) {
                nihmsCsvWriter.write(NihmsStatus.COMPLIANT, compliant);
            }
            if (statusesToHarvest.contains(NihmsStatus.NON_COMPLIANT)) {
                nihmsCsvWriter.write(NihmsStatus.NON_COMPLIANT, nonCompliant);
            }
            if (statusesToHarvest.contains(NihmsStatus.IN_PROCESS)) {
                LOG.info("No {} data is available from the current sources, so no file is written for it",
                         NihmsStatus.IN_PROCESS);
            }
        } catch (Exception ex) {
            throw new RuntimeException("An error occurred while harvesting the NIHMS data.", ex);
        }
    }

}
