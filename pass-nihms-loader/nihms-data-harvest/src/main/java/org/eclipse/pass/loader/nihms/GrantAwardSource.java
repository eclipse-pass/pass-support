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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Grant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Finds the NIH awards held in PASS. These drive the search for publications.
 */
@Component
public class GrantAwardSource {

    private static final Logger LOG = LoggerFactory.getLogger(GrantAwardSource.class);

    // PASS rejects a date in a filter unless it is written out to the millisecond
    private static final DateTimeFormatter FILTER_DATE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final PassClient passClient;
    private final String grantFilter;

    /**
     * @param passClient        the PASS client
     * @param grantFilter       optional RSQL filter selecting the Grants considered. If blank, a filter
     *                          is built from the repository and the months given.
     * @param repositoryId      id of the PASS Repository which NIHMS data is loaded for. Used when
     *                          grantFilter is blank to select the Grants of funders whose policy
     *                          deposits to that Repository. Blank considers the Grants of all funders.
     * @param endedWithinMonths used when grantFilter is blank to select the Grants which are current
     *                          or ended in this many past months. Zero or less considers all Grants.
     */
    @Autowired
    public GrantAwardSource(PassClient passClient,
                            @Value("${nihmsetl.harvest.grant-filter:}") String grantFilter,
                            @Value("${nihmsetl.repository.id:}") String repositoryId,
                            @Value("${nihmsetl.harvest.grant-ended-within-months:36}") int endedWithinMonths) {
        this(passClient, StringUtils.isNotBlank(grantFilter) ? grantFilter
            : defaultFilter(repositoryId, endedWithinMonths, Instant.now()));
    }

    GrantAwardSource(PassClient passClient, String grantFilter) {
        this.passClient = passClient;
        this.grantFilter = grantFilter;
    }

    /**
     * Build the filter used when none is configured.
     *
     * @param repositoryId      id of the Repository a funder's policy must deposit to, blank for any
     * @param endedWithinMonths how long ago a Grant may have ended, zero or less for any
     * @param now               the time to count the months back from
     * @return the RSQL filter, null if nothing is to be filtered
     */
    static String defaultFilter(String repositoryId, int endedWithinMonths, Instant now) {
        String funderClause = null;
        if (StringUtils.isNotBlank(repositoryId)) {
            String repo = "policy.repositories.id==" + repositoryId.trim();
            funderClause = "(primaryFunder." + repo + ",directFunder." + repo + ")";
        }
        String dateClause = null;
        if (endedWithinMonths > 0) {
            Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(endedWithinMonths)
                                .truncatedTo(ChronoUnit.DAYS).toInstant();
            dateClause = "endDate>=" + FILTER_DATE.format(cutoff);
        }
        if (funderClause == null) {
            return dateClause;
        }
        return dateClause == null ? funderClause : funderClause + ";" + dateClause;
    }

    /**
     * Read the Grants in PASS and reduce them to distinct NIH awards. Grants whose award number
     * does not look like an NIH award number are ignored.
     *
     * @return the distinct NIH awards
     * @throws IOException if PASS cannot be read
     */
    public Set<NihAward> findNihAwards() throws IOException {
        PassClientSelector<Grant> selector = new PassClientSelector<>(Grant.class);
        if (StringUtils.isNotBlank(grantFilter)) {
            selector.setFilter(grantFilter);
        }
        // The Grants are read a page at a time, which is only reliable in a fixed order
        selector.setSorting("id");
        LOG.info("Reading PASS grants with filter: {}", grantFilter);

        Set<NihAward> awards = new LinkedHashSet<>();
        long grantCount = 0;
        try (Stream<Grant> grants = passClient.streamObjects(selector)) {
            for (Grant grant : (Iterable<Grant>) grants::iterator) {
                grantCount++;
                NihAward award = NihAward.parse(grant.getAwardNumber());
                if (award != null) {
                    awards.add(award);
                }
            }
        }
        LOG.info("Found {} distinct NIH awards in {} PASS grants", awards.size(), grantCount);
        return awards;
    }
}
