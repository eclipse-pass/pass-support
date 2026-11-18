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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Grant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Finds the NIH awards held in PASS. These drive the search for publications.
 */
@Component
public class GrantAwardSource {

    private static final Logger LOG = LoggerFactory.getLogger(GrantAwardSource.class);

    private final PassClient passClient;
    private final String grantFilter;

    /**
     * @param passClient  the PASS client
     * @param grantFilter optional RSQL filter limiting the Grants considered, blank for all Grants
     */
    public GrantAwardSource(PassClient passClient,
                            @Value("${nihmsetl.harvest.grant-filter:}") String grantFilter) {
        this.passClient = passClient;
        this.grantFilter = grantFilter;
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
