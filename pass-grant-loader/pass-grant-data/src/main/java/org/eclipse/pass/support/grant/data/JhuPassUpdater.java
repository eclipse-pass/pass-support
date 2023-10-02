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

import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_EMAIL;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_EMPLOYEE_ID;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_FIRST_NAME;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_INSTITUTIONAL_ID;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_LAST_NAME;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_MIDDLE_NAME;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.client.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for taking the Set of Maps derived from the ResultSet from the database query and
 * constructing a corresponding Collection of Grant or User objects, which it then sends to PASS to update.
 *
 * @author jrm@jhu.edu
 */
public class JhuPassUpdater extends AbstractDefaultPassUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(JhuPassUpdater.class);
    private static final String DOMAIN = "johnshopkins.edu";
    private static final String EMPLOYEE_ID_TYPE = "employeeid";
    private static final String HOPKINS_ID_TYPE = "unique-id";
    private static final String JHED_ID_TYPE = "eppn";

    static final String EMPLOYEE_LOCATOR_ID = DOMAIN + ":" + EMPLOYEE_ID_TYPE + ":";
    static final String HOPKINS_LOCATOR_ID = DOMAIN + ":" + HOPKINS_ID_TYPE + ":";
    static final String JHED_LOCATOR_ID = DOMAIN + ":" + JHED_ID_TYPE + ":";

    private final DirectoryServiceUtil directoryServiceUtil;

    /**
     * Constructor.
     * @param connectionProperties properties for connection to user dir service
     */
    public JhuPassUpdater(Properties connectionProperties) {
        this.directoryServiceUtil = new DirectoryServiceUtil(connectionProperties);
        setDomain(DOMAIN);
    }

    @Override
    public Funder update(Funder system, Funder stored) {
        if (funderNeedsUpdate(system, stored)) {
            return updateFunder(system, stored);
        }
        return null;
    }

    @Override
    public User update(User system, User stored) {
        if (userNeedsUpdate(system, stored)) {
            return updateUser(system, stored);
        }
        return null;
    }

    @Override
    public void setInstitutionalUserProps(User user) {
        try {
            String employeeIdLocator = getEmployeeLocatorId(user);
            String employeeId = employeeIdLocator.replace(EMPLOYEE_LOCATOR_ID, "");
            String hopkinsId = directoryServiceUtil.getHopkinsIdForEmployeeId(employeeId);
            if (StringUtils.isNotBlank(hopkinsId)) {
                user.getLocatorIds().add(1, HOPKINS_LOCATOR_ID + hopkinsId);
            } else {
                LOG.warn("Hopkins ID is null or blank for employee ID: " + employeeId);
            }
        } catch (IOException | GrantDataException e) {
            LOG.error("Error getting Hopkins ID for User: " + user.getEmail());
        }
    }

    @Override
    public String getEmployeeLocatorId(User user) throws GrantDataException {
        return user.getLocatorIds().stream()
            .filter(locatorId -> locatorId.startsWith(EMPLOYEE_LOCATOR_ID))
            .findFirst()
            .orElseThrow(() -> new GrantDataException("Unable to find employee id locator id"));
    }

    @Override
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

    @Override
    public User buildUser(Map<String, String> rowMap) {
        User user = new User();
        user.setFirstName(rowMap.get(C_USER_FIRST_NAME));
        if (rowMap.containsKey(C_USER_MIDDLE_NAME)) {
            user.setMiddleName(rowMap.get(C_USER_MIDDLE_NAME));
        }
        user.setLastName(rowMap.get(C_USER_LAST_NAME));
        user.setDisplayName(rowMap.get(C_USER_FIRST_NAME) + " " + rowMap.get(C_USER_LAST_NAME));
        user.setEmail(rowMap.get(C_USER_EMAIL));
        String employeeId = rowMap.get(C_USER_EMPLOYEE_ID);
        String jhedId = null;
        if (rowMap.get(C_USER_INSTITUTIONAL_ID) != null) {
            jhedId = rowMap.get(C_USER_INSTITUTIONAL_ID).toLowerCase();
        }
        //Build the List of locatorIds - put the most reliable ids first
        if (employeeId != null) {
            user.getLocatorIds().add(EMPLOYEE_LOCATOR_ID + employeeId);
        }
        if (jhedId != null) {
            user.getLocatorIds().add(JHED_LOCATOR_ID + jhedId);
        }
        user.getRoles().add(UserRole.SUBMITTER);
        LOG.debug("Built user with employee ID {}", employeeId);
        return user;
    }

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
        String hopkinsLocatorId = findLocatorId(stored, HOPKINS_LOCATOR_ID);
        if (Objects.isNull(hopkinsLocatorId)) {
            return true;
        }
        String systemUserJhedLocatorId = findLocatorId(system, JhuPassUpdater.JHED_LOCATOR_ID);
        if (Objects.nonNull(systemUserJhedLocatorId) && !stored.getLocatorIds().contains(systemUserJhedLocatorId)) {
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

    private String findLocatorId(User user, String locatorIdPrefix) {
        return user.getLocatorIds().stream()
            .filter(locatorId -> locatorId.startsWith(locatorIdPrefix))
            .findFirst()
            .orElse(null);
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
        String hopkinsLocatorId = findLocatorId(stored, HOPKINS_LOCATOR_ID);
        if (Objects.isNull(hopkinsLocatorId)) {
            setInstitutionalUserProps(stored);
        }
        String systemUserJhedLocatorId = findLocatorId(system, JhuPassUpdater.JHED_LOCATOR_ID);
        if (Objects.nonNull(systemUserJhedLocatorId) && !stored.getLocatorIds().contains(systemUserJhedLocatorId)) {
            stored.getLocatorIds().removeIf(locatorId -> locatorId.startsWith(JhuPassUpdater.JHED_LOCATOR_ID));
            stored.getLocatorIds().add(systemUserJhedLocatorId);
        }
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
