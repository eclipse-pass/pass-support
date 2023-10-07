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
package org.eclipse.pass.support.grant.data;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.pass.support.client.model.PassEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class DifferenceLogger {

    private static final Logger LOG = LoggerFactory.getLogger(DifferenceLogger.class);

    /**
     * Logs the difference in attribute values between the source and target PassEntities.
     *
     * @param source the existing PassEntity state
     * @param target the updated PassEntity state
     */
    public void log(PassEntity source, PassEntity target) {
        LOG.info("Updated " + source.getClass().getSimpleName() + " with ID: " + source.getId());
        if (LOG.isInfoEnabled()) {
            List<String> diffs = getDifference(source, target, source.getId());
            diffs.forEach(LOG::info);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getDifference(Object s1, Object s2, String grantId) {
        List<String> values = new ArrayList<>();
        try {
            for (Field field : s1.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value1 = field.get(s1);
                Object value2 = field.get(s2);
                if (value1 instanceof PassEntity || value2 instanceof PassEntity) {
                    getPassEntityDiffs((PassEntity) value1, (PassEntity) value2, values, field);
                } else if (value1 instanceof List || value2 instanceof List) {
                    getListDiffs((List<PassEntity>) value1, (List<PassEntity>) value2, values, field);
                } else if (!field.getName().equals("id")
                    && !Objects.equals(value1, value2)) {
                    values.add(field.getName() + ": " + value1 + " -> " + value2);
                }
            }
        } catch (IllegalAccessException e) {
            LOG.error("Error printing diffs Grant ID: " + grantId, e);
        }
        return values;
    }

    private void getPassEntityDiffs(PassEntity value1, PassEntity value2, List<String> values, Field field) {
        String funder1Id = Objects.nonNull(value1) ? value1.getId() : null;
        String funder2Id = Objects.nonNull(value2) ? value2.getId() : null;
        if (!Objects.equals(funder1Id, funder2Id)) {
            values.add(field.getName() + " IDs: " + funder1Id + " -> " + funder2Id);
        }
    }

    private void getListDiffs(List<PassEntity> value1, List<PassEntity> value2, List<String> values, Field field) {
        Set<String> coPiIds1 = Objects.nonNull(value1) ?
            value1.stream().map(PassEntity::getId).collect(Collectors.toSet()) : null;
        Set<String> coPiIds2 = Objects.nonNull(value2) ?
            value2.stream().map(PassEntity::getId).collect(Collectors.toSet()) : null;
        if (!Objects.equals(coPiIds1, coPiIds2)) {
            values.add(field.getName() + " IDs: " + coPiIds1 + " -> " + coPiIds2);
        }
    }

}
