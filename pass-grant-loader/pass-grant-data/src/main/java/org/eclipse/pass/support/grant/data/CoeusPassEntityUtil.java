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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.User;

/**
 * A utility class for handling Grants, Users or Funders. One function performed is comparison of two instances of
 * these PASS entity classes. These comparisons are reduced to only those fields which are updatable by
 * data from COEUS, so that two objects are considered "COEUS equal" iff they agree on these fields.
 * <p>
 * Another function performed by this utility class is to construct an updated version of an instance of one of these
 * classes
 * by merging a (possibly) existing Pass object with new information obtained from a COEUS data pull.
 *
 * @author jrm@jhu.edu
 */
public class CoeusPassEntityUtil implements PassEntityUtil {

    /**
     * This method takes a COEUS Funder, calculates whether it needs to be updated, and if so, returns the updated
     * object
     * to be be ingested into the repository. if not, returns null.
     *
     * @param stored the Funder as it is stored in the PASS backend
     * @param system the version of the Funder from the COEUS pull
     * @return the updated Funder - null if the Funder does not need to be updated
     */
    public Funder update(Funder system, Funder stored) {
        if (funderNeedsUpdate(system, stored)) {
            return updateFunder(system, stored);
        }
        return null;
    }

    /**
     * This method takes a COEUS User, calculates whether it needs to be updated, and if so, returns the updated object
     * to be be ingested into the repository. if not, returns null.
     *
     * @param stored the User as it is stored in the PASS backend
     * @param system the version of the user from the COEUS pull
     * @return the updated User - null if the User does not need to be updated
     */
    public User update(User system, User stored) {
        if (userNeedsUpdate(system, stored)) {
            return updateUser(system, stored);
        }
        return null;
    }

    /**
     * This method takes a COEUS Grant, calculates whether it needs to be updated, and if so, returns the updated object
     * to be be ingested into the repository. if not, returns null.
     *
     * @param stored the Grant as it is stored in the PASS backend
     * @param system the version of the Grant from the COEUS pull
     * @return the updated object - null if the Grant does not need to be updated
     */
    public Grant update(Grant system, Grant stored) {
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
        if (grantNeedsUpdate(system, stored)) {
            return updateGrant(system, stored);
        }
        return null;
    }

    /**
     * 0p
     * Compare two Funder objects
     *
     * @param system the version of the Funder as seen in the COEUS system pull
     * @param stored the version of the Funder as read from Pass
     * @return a boolean which asserts whether the two supplied Funders are "COEUS equal"
     */
    private boolean funderNeedsUpdate(Funder system, Funder stored) {

        //this adjustment handles the case where we take data from policy.properties file, which has no name info
        if (system.getName() != null && !system.getName().equals(stored.getName())) {
            return true;
        }
        if (system.getLocalKey() != null ? !system.getLocalKey()
                                                  .equals(stored.getLocalKey()) : stored.getLocalKey() != null) {
            return true;
        }
        if (system.getPolicy() != null ? !system.getPolicy().equals(stored.getPolicy()) : stored.getPolicy() != null) {
            return true;
        }
        return false;
    }

    /**
     * Update a Pass Funder object with new information from COEUS
     *
     * @param system the version of the Funder as seen in the COEUS system pull
     * @param stored the version of the Funder as read from Pass
     * @return the Funder object which represents the Pass object, with any new information from COEUS merged in
     */
    private Funder updateFunder(Funder system, Funder stored) {
        //stored.setLocalKey(system.getLocalKey());
        if (system.getName() != null) {
            stored.setName(system.getName());
        }
        if (system.getPolicy() != null) {
            stored.setPolicy(system.getPolicy());
        }
        return stored;
    }

    /**
     * Compare two User objects. We only care about those fields for which COEUS is the authoritative source
     * After recent changes. this method would be more accurately named "storedUserDoesNotNeedToBeUpdated"
     *
     * @param system the version of the User as seen in the COEUS system pull
     * @param stored the version of the User as read from Pass
     * @return a boolean which asserts whether the two supplied Users are "COEUS equal"
     */
    private boolean userNeedsUpdate(User system, User stored) {
        //first the fields for which COEUS is authoritative
        if (system.getFirstName() != null ? !system.getFirstName()
                                                   .equals(stored.getFirstName()) : stored.getFirstName() != null) {
            return true;
        }
        if (system.getMiddleName() != null ? !system.getMiddleName()
                                                    .equals(stored.getMiddleName()) : stored.getMiddleName() != null) {
            return true;
        }
        if (system.getLastName() != null ? !system.getLastName()
                                                  .equals(stored.getLastName()) : stored.getLastName() != null) {
            return true;
        }
        // TODO employeeid and unique-id never change, only update jhedid if needed
        if (system.getLocatorIds() != null ? !stored.getLocatorIds().containsAll(
            system.getLocatorIds()) : stored.getLocatorIds() != null) {
            return true;
        }
        //next, other fields which require some reasoning to decide whether an update is necessary
        if (system.getEmail() != null && stored.getEmail() == null) {
            return true;
        }
        if (system.getDisplayName() != null && stored.getDisplayName() == null) {
            return true;
        }
        return false;
    }

    /**
     * Update a Pass User object with new information from COEUS. We check only those fields for which COEUS is
     * authoritative. Other fields will be managed by other providers (Shibboleth for example). The exceptions are
     * the localKey, which this application and Shibboleth both rely on; and  email, which this application only
     * populates
     * if Shib hasn't done so already.
     *
     * @param system the version of the User as seen in the COEUS system pull
     * @param stored the version of the User as read from Pass
     * @return the User object which represents the Pass object, with any new information from COEUS merged in
     */
    private User updateUser(User system, User stored) {
        stored.setFirstName(system.getFirstName());
        stored.setMiddleName(system.getMiddleName());
        stored.setLastName(system.getLastName());
        //combine the locatorIds from both objects
        Set<String> idSet = new HashSet<>();
        idSet.addAll(stored.getLocatorIds());
        idSet.addAll(system.getLocatorIds());
        // TODO employeeid and unique-id never change, only update jhedid if needed
        stored.setLocatorIds(idSet.stream().collect(Collectors.toList()));
        //populate null fields if we can
        if ((stored.getEmail() == null) && (system.getEmail() != null)) {
            stored.setEmail(system.getEmail());
        }
        if ((stored.getDisplayName() == null && system.getDisplayName() != null)) {
            stored.setDisplayName(system.getDisplayName());
        }
        return stored;
    }

    /**
     * Compare two Grant objects. Note that the Lists of Co-Pis are compared as Sets
     *
     * @param system the version of the Grant as seen in the COES system pull
     * @param stored the version of the Grant as read from Pass
     * @return a boolean which asserts whether the two supplied Grants are "COEUS equal"
     */
    private boolean grantNeedsUpdate(Grant system, Grant stored) {
        if (system.getAwardStatus() != null ? !system.getAwardStatus()
                                                     .equals(
                                                         stored.getAwardStatus()) : stored.getAwardStatus() != null) {
            return true;
        }
        if (system.getPi() != null ? !system.getPi().equals(stored.getPi()) : stored.getPi() != null) {
            return true;
        }
        if (system.getCoPis() != null ? !new HashSet(system.getCoPis()).equals(
            new HashSet(stored.getCoPis())) : stored.getCoPis() != null) {
            return true;
        }
        if (system.getEndDate() != null ? system.getEndDate()
                                                .isAfter(stored.getEndDate()) : stored.getEndDate() != null) {
            return true;
        }
        return false;
    }

    /**
     * Update a Pass Grant object with new information from COEUS - only updatable fields are considered.
     * the PASS version is authoritative for the rest
     *
     * @param system the version of the Grant as seen in the COEUS system pull
     * @param stored the version of the Grant as read from Pass
     * @return the Grant object which represents the Pass object, with any new information from COEUS merged in
     */
    private Grant updateGrant(Grant system, Grant stored) {
        stored.setAwardStatus(system.getAwardStatus());
        stored.setPi(system.getPi());
        stored.setCoPis(system.getCoPis());
        stored.setEndDate(system.getEndDate());
        return stored;
    }

}
