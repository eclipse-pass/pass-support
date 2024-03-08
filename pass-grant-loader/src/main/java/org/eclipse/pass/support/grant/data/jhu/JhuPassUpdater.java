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
package org.eclipse.pass.support.grant.data.jhu;

import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.client.model.UserRole;
import org.eclipse.pass.support.grant.data.AbstractDefaultPassUpdater;
import org.eclipse.pass.support.grant.data.DifferenceLogger;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for taking the Set of Maps derived from the ResultSet from the database query and
 * constructing a corresponding Collection of Grant or User objects, which it then sends to PASS to update.
 *
 * @author jrm@jhu.edu
 */
@Component
@Profile("jhu")
public class JhuPassUpdater extends AbstractDefaultPassUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(JhuPassUpdater.class);
    private static final String DOMAIN = "johnshopkins.edu";
    private static final String EMPLOYEE_ID_TYPE = "employeeid";
    private static final String JHED_ID_TYPE = "eppn";

    static final String EMPLOYEE_LOCATOR_ID = DOMAIN + ":" + EMPLOYEE_ID_TYPE + ":";
    static final String JHED_LOCATOR_ID = DOMAIN + ":" + JHED_ID_TYPE + ":";

    private final DifferenceLogger differenceLogger;

    /**
     * Constructor.
     * @param policyProperties the policy props
     */
    public JhuPassUpdater(DifferenceLogger differenceLogger,
                          PassClient passClient,
                          @Qualifier("policyProperties") Properties policyProperties) {
        super(passClient, policyProperties);
        this.differenceLogger = differenceLogger;
        setDomain(DOMAIN);
    }

    @Override
    public Grant updateGrantIfNeeded(Grant system, Grant stored) {
        if (Objects.nonNull(system) && Objects.nonNull(stored)) {
            return updateGrant(system, stored);
        }
        return null;
    }

    @Override
    public Funder updateFunderIfNeeded(Funder system, Funder stored) {
        if (funderNeedsUpdate(system, stored)) {
            return updateFunder(system, stored);
        }
        return null;
    }

    @Override
    public User updateUserIfNeeded(User system, User stored) {
        if (userNeedsUpdate(system, stored)) {
            return updateUser(system, stored);
        }
        return null;
    }

    @Override
    public User buildUser(GrantIngestRecord grantIngestRecord) {
        User user = new User();
        user.setFirstName(grantIngestRecord.getPiFirstName());
        if (Objects.nonNull(grantIngestRecord.getPiMiddleName())) {
            user.setMiddleName(grantIngestRecord.getPiMiddleName());
        }
        user.setLastName(grantIngestRecord.getPiLastName());
        user.setDisplayName(grantIngestRecord.getPiFirstName() + " " + grantIngestRecord.getPiLastName());
        user.setEmail(grantIngestRecord.getPiEmail());
        String employeeId = grantIngestRecord.getPiEmployeeId();
        //Build the List of locatorIds - put the most reliable ids first
        if (StringUtils.isNotBlank(employeeId)) {
            user.getLocatorIds().add(EMPLOYEE_LOCATOR_ID + employeeId);
        }
        if (StringUtils.isNotBlank(grantIngestRecord.getPiInstitutionalId())) {
            String jhedId = grantIngestRecord.getPiInstitutionalId().toLowerCase();
            user.getLocatorIds().add(JHED_LOCATOR_ID + jhedId);
        }
        user.getRoles().add(UserRole.SUBMITTER);
        LOG.debug("Built user with employee ID {}", employeeId);
        return user;
    }

    private Grant updateGrant(Grant system, Grant stored) {
        differenceLogger.log(stored, system);
        stored.setAwardNumber(system.getAwardNumber());
        stored.setAwardStatus(system.getAwardStatus());
        stored.setLocalKey(system.getLocalKey());
        stored.setProjectName(system.getProjectName());
        stored.setPrimaryFunder(system.getPrimaryFunder());
        stored.setDirectFunder(system.getDirectFunder());
        stored.setPi(system.getPi());
        stored.setCoPis(system.getCoPis());
        stored.setAwardDate(system.getAwardDate());
        stored.setStartDate(system.getStartDate());
        stored.setEndDate(system.getEndDate());
        return stored;
    }

    private boolean funderNeedsUpdate(Funder system, Funder stored) {
        //this adjustment handles the case where we take data from policy.properties file, which has no name info
        if (Objects.nonNull(system.getName()) && !system.getName().equals(stored.getName())) {
            return true;
        }
        if (!Objects.equals(system.getLocalKey(), stored.getLocalKey())) {
            return true;
        }
        if (!Objects.equals(getPassEntityId(system.getPolicy()), getPassEntityId(stored.getPolicy()))) {
            return true;
        }
        return false;
    }

    private Funder updateFunder(Funder system, Funder stored) {
        if (Objects.nonNull(system.getName())) {
            stored.setName(system.getName());
        }
        if (Objects.nonNull(system.getPolicy())) {
            stored.setPolicy(system.getPolicy());
        }
        return stored;
    }

    private boolean userNeedsUpdate(User system, User stored) {
        //first the fields for which COEUS is authoritative
        if (!Objects.equals(system.getFirstName(), stored.getFirstName())) {
            return true;
        }
        if (!Objects.equals(system.getMiddleName(), stored.getMiddleName())) {
            return true;
        }
        if (!Objects.equals(system.getLastName(), stored.getLastName())) {
            return true;
        }
        String systemUserJhedLocatorId = findLocatorId(system, JhuPassUpdater.JHED_LOCATOR_ID);
        if (Objects.nonNull(systemUserJhedLocatorId) && !stored.getLocatorIds().contains(systemUserJhedLocatorId)) {
            return true;
        }
        //next, other fields which require some reasoning to decide whether an update is necessary
        if (Objects.nonNull(system.getEmail()) && Objects.isNull(stored.getEmail())) {
            return true;
        }
        if (Objects.nonNull(system.getDisplayName()) && Objects.isNull(stored.getDisplayName())) {
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

}
