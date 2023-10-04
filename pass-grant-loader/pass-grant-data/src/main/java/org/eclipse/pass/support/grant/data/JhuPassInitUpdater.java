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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Init Grant Pass Updater for data sourced from Jhu Coeus.
 */
public class JhuPassInitUpdater extends JhuPassUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(JhuPassInitUpdater.class);

    @Override
    public Grant updateGrantIfNeeded(Grant system, Grant stored) {
        //adjust the system view of co-pis  by merging in the stored view of pi and co-pis
        for (User coPiUser : stored.getCoPis()) {
            if (!system.getCoPis().contains(coPiUser)) {
                system.getCoPis().add(coPiUser);
            }
        }

        //need to be careful, system pi might be null if there is no record for it
        //this is to finalize the version of the co-pi list we want to compare between
        //system and stored
        User storedPi = stored.getPi();
        if (system.getPi() != null) {
            if (!system.getPi().equals(storedPi)) {
                // stored.setPi( system.getPi() );
                if (!system.getCoPis().contains(storedPi)) {
                    system.getCoPis().add(storedPi);
                }
                system.getCoPis().remove(system.getPi());
            }
        } else { //system view is null, do not trigger update based on this field
            system.setPi(storedPi);
        }

        //now system view has all available info we want in this grant - look for update trigger
        if (this.grantNeedsUpdate(system, stored)) {
            return this.updateGrant(system, stored);
        }
        return null;
    }

    /**
     * Compare two Grant objects. Note that the Lists of Co-Pis are compared as Sets
     *
     * @param system the version of the Grant as seen in the COEUS system pull
     * @param stored the version of the Grant as read from Pass
     * @return a boolean which asserts whether the stored grant needs to be updated
     */

    private boolean grantNeedsUpdate(Grant system, Grant stored) {
        if (system.getAwardNumber() != null ? !system.getAwardNumber()
            .equals(
                stored.getAwardNumber()) : stored.getAwardNumber() != null) {
            return true;
        }
        if (system.getAwardStatus() != null ? !system.getAwardStatus()
            .equals(
                stored.getAwardStatus()) : stored.getAwardStatus() != null) {
            return true;
        }
        if (system.getLocalKey() != null ? !system.getLocalKey()
            .equals(stored.getLocalKey()) : stored.getLocalKey() != null) {
            return true;
        }
        if (system.getProjectName() != null ? !system.getProjectName()
            .equals(
                stored.getProjectName()) : stored.getProjectName() != null) {
            return true;
        }
        if (system.getPrimaryFunder() != null ? !system.getPrimaryFunder().equals(
            stored.getPrimaryFunder()) : stored.getPrimaryFunder() != null) {
            return true;
        }
        if (system.getDirectFunder() != null ? !system.getDirectFunder().equals(
            stored.getDirectFunder()) : stored.getDirectFunder() != null) {
            return true;
        }
        if (system.getPi() != null ? !system.getPi().equals(stored.getPi()) : stored.getPi() != null) {
            return true;
        }
        if (system.getCoPis() != null ? !new HashSet(system.getCoPis()).equals(
            new HashSet(stored.getCoPis())) : stored.getCoPis() != null) {
            return true;
        }
        if (system.getAwardDate() != null ? system.getAwardDate()
            .isBefore(stored.getAwardDate()) : stored.getAwardDate() != null) {
            return true;
        }
        if (system.getStartDate() != null ? system.getStartDate()
            .isBefore(stored.getStartDate()) : stored.getStartDate() != null) {
            return true;
        }
        if (system.getEndDate() != null ? system.getEndDate()
            .isAfter(stored.getEndDate()) : stored.getEndDate() != null) {
            return true;
        }
        return false;
    }

    /**
     * Update a Pass Grant object with new information from COEUS
     *
     * @param system the version of the Grant as seen in the COEUS system pull
     * @param stored the version of the Grant as read from Pass
     * @return the Grant object which represents the Pass object, with any new information from COEUS merged in
     */
    private Grant updateGrant(Grant system, Grant stored) {
        logDifferences(system, stored);
        stored.setAwardNumber(system.getAwardNumber());
        stored.setAwardStatus(system.getAwardStatus());
        stored.setLocalKey(system.getLocalKey());
        stored.setProjectName(system.getProjectName());
        stored.setPrimaryFunder(system.getPrimaryFunder());
        stored.setDirectFunder(system.getDirectFunder());
        stored.setPi(system.getPi());
        stored.setCoPis(system.getCoPis());

        //since this is essentially an initial pull, we can just take the system values
        stored.setAwardDate(system.getAwardDate());
        stored.setStartDate(system.getStartDate());
        stored.setEndDate(system.getEndDate());
        return stored;
    }

    private void logDifferences(Grant system, Grant stored) {
        LOG.info("Updated Grant with ID: " + stored.getId());
        List<String> diffs = getDifference(stored, system, stored.getId());
        diffs.forEach(LOG::info);
    }

    private List<String> getDifference(Object s1, Object s2, String grantId) {
        List<String> values = new ArrayList<>();
        try {
            for (Field field : s1.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value1 = field.get(s1);
                Object value2 = field.get(s2);
                if (value1 instanceof Funder || value2 instanceof Funder) {
                    getFunderDiffs((Funder) value1, (Funder) value2, values, field);
                } else if (field.getName().equals("coPis")) {
                    getCoPisDiffs((List<User>) value1, (List<User>) value2, values);
                } else if (!Objects.equals(value1, value2)) {
                    values.add(field.getName() + ": " + value1 + " -> " + value2);
                }
            }
        } catch (IllegalAccessException e) {
            LOG.error("Error printing diffs Grant ID: " + grantId, e);
        }
        return values;
    }

    private void getFunderDiffs(Funder value1, Funder value2, List<String> values, Field field) {
        String funder1Id = Objects.nonNull(value1) ? value1.getId() : null;
        String funder2Id = Objects.nonNull(value2) ? value2.getId() : null;
        if (!Objects.equals(funder1Id, funder2Id)) {
            values.add(field.getName() + " Funder IDs: " + funder1Id + " -> " + funder2Id);
        }
    }

    private void getCoPisDiffs(List<User> value1, List<User> value2, List<String> values) {
        Set<String> coPiIds1 = Objects.nonNull(value1) ?
            value1.stream().map(User::getId).collect(Collectors.toSet()) : null;
        Set<String> coPiIds2 = Objects.nonNull(value2) ?
            value2.stream().map(User::getId).collect(Collectors.toSet()) : null;
        if (!Objects.equals(coPiIds1, coPiIds2)) {
            values.add("coPis User IDs: " + coPiIds1 + " -> " + coPiIds2);
        }
    }

}
