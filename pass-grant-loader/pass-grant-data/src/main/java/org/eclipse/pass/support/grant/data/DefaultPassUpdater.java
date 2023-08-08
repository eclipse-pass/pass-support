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

import static java.lang.String.format;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_ABBREVIATED_ROLE;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_DIRECT_FUNDER_LOCAL_KEY;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_DIRECT_FUNDER_NAME;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_DIRECT_FUNDER_POLICY;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_GRANT_AWARD_DATE;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_GRANT_AWARD_NUMBER;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_GRANT_AWARD_STATUS;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_GRANT_END_DATE;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_GRANT_LOCAL_KEY;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_GRANT_PROJECT_NAME;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_GRANT_START_DATE;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_PRIMARY_FUNDER_NAME;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_PRIMARY_FUNDER_POLICY;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_UPDATE_TIMESTAMP;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_EMAIL;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_EMPLOYEE_ID;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_FIRST_NAME;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_LAST_NAME;
import static org.eclipse.pass.support.grant.data.CoeusFieldNames.C_USER_MIDDLE_NAME;
import static org.eclipse.pass.support.grant.data.DateTimeUtil.createZonedDateTime;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Policy;
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

public class DefaultPassUpdater implements PassUpdater {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPassUpdater.class);

    private static final String GRANT_ID_TYPE = "grant";
    private static final String EMPLOYEE_ID_TYPE = "employeeid";
    private static final String FUNDER_ID_TYPE = "funder";

    private String domain = "default.domain";
    private String latestUpdateString = "";

    private final PassClient passClient;
    private final PassUpdateStatistics statistics = new PassUpdateStatistics();
    private final PassEntityUtil passEntityUtil;

    private final Map<String, Grant> grantResultMap = new HashMap<>();

    //some entities may be referenced many times during an update, but just need to be updated the first time
    //they are encountered. these include Users and Funders. we save the overhead of redundant updates
    //of these by looking them up here; if they are on the Map, they have already been processed
    private final Map<String, Funder> funderMap = new HashMap<>();
    private final Map<String, User> userMap = new HashMap<>();

    private String mode;

    DefaultPassUpdater(PassEntityUtil passEntityUtil) {
        this.passEntityUtil = passEntityUtil;
        this.passClient = PassClient.newInstance();
    }

    //used in unit testing for injecting a mock client
    DefaultPassUpdater(PassEntityUtil passEntityUtil, PassClient passClient) {
        this.passEntityUtil = passEntityUtil;
        this.passClient = passClient;
    }

    public void updatePass(Collection<Map<String, String>> results, String mode) {
        this.mode = mode;
        userMap.clear();
        funderMap.clear();
        statistics.reset();
        statistics.setType(mode);
        switch (mode) {
            case "grant":
                updateGrants(results);
                break;
            case "user":
                updateUsers(results);
                break;
            case "funder":
                updateFunders(results);
                break;
            default:
                break;
        }
    }

    /**
     * Build a Collection of Grants from a ResultSet, then update the grants in Pass
     * Because we need to make sure we catch any updates to fields referenced by URIs, we construct
     * these and update these as well
     */
    private void updateGrants(Collection<Map<String, String>> results) {

        //a grant will have several rows in the ResultSet if there are co-pis. so we put the grant on this
        //Map and add to it as additional rows add information.
        Map<String, Grant> grantRowMap = new HashMap<>();

        LOG.warn("Processing result set with {} rows", results.size());
        boolean modeChecked = false;

        for (Map<String, String> rowMap : results) {

            if (!modeChecked) {
                if (!rowMap.containsKey(C_GRANT_LOCAL_KEY)) { //we always have this for grants
                    throw new RuntimeException("Mode of grant was supplied, but data does not seem to match.");
                } else {
                    modeChecked = true;
                }
            }

            String grantLocalKey = rowMap.get(C_GRANT_LOCAL_KEY);

            try {
                //get funder local keys. if a primary funder is not specified, we set it to the direct funder
                String directFunderLocalKey = rowMap.get(C_DIRECT_FUNDER_LOCAL_KEY);
                String primaryFunderLocalKey = rowMap.get(C_PRIMARY_FUNDER_LOCAL_KEY);
                primaryFunderLocalKey = (primaryFunderLocalKey == null ? directFunderLocalKey : primaryFunderLocalKey);

                //we will need funder PASS URIs - retrieve or create them,
                //updating the info on them if necessary
                if (!funderMap.containsKey(directFunderLocalKey)) {
                    Funder directFunder = buildDirectFunder(rowMap);
                    Funder updatedFunder = updateFunderInPass(directFunder);
                    funderMap.put(directFunderLocalKey, updatedFunder);
                }

                if (!funderMap.containsKey(primaryFunderLocalKey)) {
                    Funder primaryFunder = buildPrimaryFunder(rowMap);
                    Funder updatedFunder = updateFunderInPass(primaryFunder);
                    funderMap.put(primaryFunderLocalKey, updatedFunder);
                }

                //same for any users
                String employeeId = rowMap.get(C_USER_EMPLOYEE_ID);
                if (!userMap.containsKey(employeeId)) {
                    User rowUser = buildUser(rowMap);
                    User updateUser = updateUserInPass(rowUser);
                    userMap.put(employeeId, updateUser);
                }

                //now we know all about our user and funders for this record
                // let's get to the grant proper
                LOG.debug("Processing grant with localKey {}", grantLocalKey);

                //if this is the first record for this Grant, it will not be on the Map
                Grant grant;
                if (!grantRowMap.containsKey(grantLocalKey)) {
                    grant = new Grant();
                    grant.setLocalKey(grantLocalKey);
                    grantRowMap.put(grantLocalKey, grant);
                }

                grant = grantRowMap.get(grantLocalKey);

                String abbreviatedRole = rowMap.get(C_ABBREVIATED_ROLE);
                //anybody who was ever a co-pi in an iteration will be in this list
                if (abbreviatedRole.equals("C") || abbreviatedRole.equals("K")) {
                    User user = userMap.get(employeeId);
                    if (!grant.getCoPis().contains(user)) {
                        grant.getCoPis().add(user);
                        statistics.addCoPi();
                    }
                }

                //now do things which may depend on the date - award date is the only one that changes
                ZonedDateTime awardDate = createZonedDateTime(rowMap.getOrDefault(C_GRANT_AWARD_DATE, null));
                ZonedDateTime startDate = createZonedDateTime(rowMap.getOrDefault(C_GRANT_START_DATE, null));
                ZonedDateTime endDate = createZonedDateTime(rowMap.getOrDefault(C_GRANT_END_DATE, null));

                //set values that should match earliest iteration of the grant. we wet these on the system record
                //in case they are needed to update a stored grant record.
                //these values will not override existing stored values unless the PassEntityUtil implementation
                //allows it.
                //we mostly have awardDate, but will use start date as a fallback if not
                if ((awardDate != null && (grant.getAwardDate() == null || awardDate.isBefore(grant.getAwardDate()))) ||
                    awardDate == null && (startDate != null && (grant.getStartDate() == null || startDate.isBefore(
                        grant.getStartDate())))) {
                    grant.setProjectName(rowMap.get(C_GRANT_PROJECT_NAME));
                    grant.setAwardNumber(rowMap.get(C_GRANT_AWARD_NUMBER));
                    grant.setDirectFunder(funderMap.get(directFunderLocalKey));
                    grant.setPrimaryFunder(funderMap.get(primaryFunderLocalKey));
                    grant.setStartDate(startDate);
                    grant.setAwardDate(awardDate);
                }

                //set values that should match the latest iteration of the grant
                //use !isBefore in case more than one PI is specified, need to process more than one
                //we mostly have awardDate, but will use end date as a fallback if not
                if ((awardDate != null
                        && (grant.getAwardDate() == null || !awardDate.isBefore(grant.getAwardDate())))
                        || awardDate == null
                        && (endDate != null && (grant.getEndDate() == null || !endDate.isBefore(grant.getEndDate())))) {
                    grant.setEndDate(endDate);
                    //status should be the latest one
                    String status = rowMap.getOrDefault(C_GRANT_AWARD_STATUS, null);
                    try {
                        String lowercaseStatus = StringUtils.lowerCase(status);
                        AwardStatus awardStatus = AwardStatus.of(lowercaseStatus);
                        grant.setAwardStatus(awardStatus);
                    } catch (IllegalArgumentException e) {
                        LOG.error(format(
                                "Invalid AwardStatus %s for Grant Row %s, setting to null", status, grantLocalKey));
                        grant.setAwardStatus(null);
                    }

                    //we want the PI to be the one listed on the most recent grant iteration
                    if (abbreviatedRole.equals("P")) {
                        User user = userMap.get(employeeId);
                        User oldPiId = grant.getPi();
                        grant.setPi(user);
                        grant.getCoPis().remove(user);
                        if (oldPiId == null) {
                            statistics.addPi();
                        } else {
                            if (!oldPiId.equals(user)) {
                                if (!grant.getCoPis().contains(oldPiId)) {
                                    grant.getCoPis().add(oldPiId);
                                    statistics.addCoPi();
                                }
                            }
                        }
                    }
                }

                //we are done with this record, let's save the state of this Grant
                grantRowMap.put(grantLocalKey, grant);
                //see if this is the latest grant updated
                if (rowMap.containsKey(C_UPDATE_TIMESTAMP)) {
                    String grantUpdateString = rowMap.get(C_UPDATE_TIMESTAMP);
                    latestUpdateString =
                            latestUpdateString.length() == 0
                                    ? grantUpdateString
                                    : DateTimeUtil.returnLaterUpdate(grantUpdateString, latestUpdateString);
                }
            } catch (IOException | GrantDataException e) {
                LOG.error("Error building Grant Row with localKey: " + grantLocalKey, e);
            }
        }

        //now put updated grant objects in pass
        for (Grant grant : grantRowMap.values()) {
            String grantLocalKey = grant.getLocalKey();
            try {
                Grant updatedGrant = updateGrantInPass(grant);
                grantResultMap.put(grantLocalKey, updatedGrant);
            } catch (IOException | GrantDataException e) {
                LOG.error("Error updating Grant with localKey: " + grantLocalKey, e);
            }
        }

        //success - we capture some information to report
        if (grantResultMap.size() > 0) {
            statistics.setLatestUpdateString(latestUpdateString);
            statistics.setReport(results.size(), grantResultMap.size());
        } else {
            System.out.println("No records were processed in this update");
        }
    }

    private void updateUsers(Collection<Map<String, String>> results) {

        boolean modeChecked = false;

        LOG.info("Processing result set with {} rows", results.size());
        int userProcessedCounter = 0;
        for (Map<String, String> rowMap : results) {

            if (!modeChecked) {
                if (!rowMap.containsKey(C_USER_EMPLOYEE_ID)) { //we always have this for users
                    throw new RuntimeException("Mode of user was supplied, but data does not seem to match.");
                } else {
                    modeChecked = true;
                }
            }

            User rowUser = buildUser(rowMap);
            try {
                updateUserInPass(rowUser);
                userProcessedCounter++;
                if (rowMap.containsKey(C_UPDATE_TIMESTAMP)) {
                    String userUpdateString = rowMap.get(C_UPDATE_TIMESTAMP);
                    latestUpdateString =
                            latestUpdateString.length() == 0
                                    ? userUpdateString
                                    : DateTimeUtil.returnLaterUpdate(userUpdateString, latestUpdateString);
                }
            } catch (IOException | GrantDataException e) {
                LOG.error("Error processing User: " + rowUser, e);
            }
        }

        if (results.size() > 0) {
            statistics.setLatestUpdateString(latestUpdateString);
            statistics.setReport(results.size(), userProcessedCounter);
        } else {
            System.out.println("No records were processed in this update");
        }

    }

    /**
     * This method is called for the "funder" mode - the column names will have the values for primary funders
     *
     * @param results the data row map containing funder information
     */
    private void updateFunders(Collection<Map<String, String>> results) {

        boolean modeChecked = false;
        LOG.info("Processing result set with {} rows", results.size());
        int funderProcessedCounter = 0;
        for (Map<String, String> rowMap : results) {

            if (!modeChecked) {
                if (!rowMap.containsKey(C_PRIMARY_FUNDER_LOCAL_KEY) && !rowMap.containsKey(C_PRIMARY_FUNDER_NAME)) {
                    throw new RuntimeException("Mode of funder was supplied, but data does not seem to match.");
                } else {
                    modeChecked = true;
                }
            }

            Funder rowFunder = buildPrimaryFunder(rowMap);
            try {
                updateFunderInPass(rowFunder);
                funderProcessedCounter++;
            } catch (IOException | GrantDataException e) {
                LOG.error("Error processing Funder localKey: " + rowFunder.getLocalKey(), e);
            }
        }
        statistics.setReport(results.size(), funderProcessedCounter);
    }

    User buildUser(Map<String, String> rowMap) {
        User user = new User();
        user.setFirstName(rowMap.get(C_USER_FIRST_NAME));
        user.setMiddleName(rowMap.getOrDefault(C_USER_MIDDLE_NAME, null));
        user.setLastName(rowMap.get(C_USER_LAST_NAME));
        user.setDisplayName(rowMap.get(C_USER_FIRST_NAME) + " " + rowMap.get(C_USER_LAST_NAME));
        user.setEmail(rowMap.get(C_USER_EMAIL));
        String employeeId = rowMap.get(C_USER_EMPLOYEE_ID);
        //Build the List of locatorIds - put the most reliable ids first
        if (employeeId != null) {
            String localKey = GrantDataUtils.buildLocalKey(domain, EMPLOYEE_ID_TYPE, employeeId);
            user.getLocatorIds().add(localKey);
        }
        user.getRoles().add(UserRole.SUBMITTER);
        LOG.debug("Built user with employee ID {}", employeeId);
        return user;
    }

    /**
     * this method gets called on a grant mode process if the primary funder is different from direct, and also
     * any time the updater is called in funder mode
     *
     * @param rowMap the funder data map
     * @return the funder
     */
    Funder buildPrimaryFunder(Map<String, String> rowMap) {
        Funder funder = new Funder();
        funder.setName(rowMap.getOrDefault(C_PRIMARY_FUNDER_NAME, null));
        funder.setLocalKey(rowMap.get(C_PRIMARY_FUNDER_LOCAL_KEY));
        String policyId = rowMap.get(C_PRIMARY_FUNDER_POLICY);
        if (StringUtils.isNotEmpty(policyId)) {
            funder.setPolicy(new Policy(policyId));
            LOG.debug("Processing Funder with localKey {} and policy {}", funder.getLocalKey(), policyId);
        }
        LOG.debug("Built Funder with localKey {}", funder.getLocalKey());

        return funder;
    }

    private Funder buildDirectFunder(Map<String, String> rowMap) {
        Funder funder = new Funder();
        if (rowMap.containsKey(C_DIRECT_FUNDER_NAME)) {
            funder.setName(rowMap.get(C_DIRECT_FUNDER_NAME));
        }
        funder.setLocalKey(rowMap.get(C_DIRECT_FUNDER_LOCAL_KEY));
        String policyId = rowMap.get(C_DIRECT_FUNDER_POLICY);
        if (StringUtils.isNotEmpty(policyId)) {
            funder.setPolicy(new Policy(policyId));
            LOG.debug("Processing Funder with localKey {} and policy {}", funder.getLocalKey(), policyId);
        }
        LOG.debug("Built Funder with localKey {}", funder.getLocalKey());

        return funder;
    }

    /**
     * Take a new Funder object populated as fully as possible from the COEUS pull, and use this
     * new information to update an object for the same Funder in Pass (if it exists)
     *
     * @param systemFunder the new Funder object populated from COEUS
     * @return the localKey for the resource representing the updated Funder in Pass
     */
    private Funder updateFunderInPass(Funder systemFunder) throws IOException, GrantDataException {
        String baseLocalKey = systemFunder.getLocalKey();
        String fullLocalKey = GrantDataUtils.buildLocalKey(domain, FUNDER_ID_TYPE, baseLocalKey);
        systemFunder.setLocalKey(fullLocalKey);

        PassClientSelector<Funder> selector = new PassClientSelector<>(Funder.class);
        selector.setFilter(RSQL.equals("localKey", fullLocalKey));
        selector.setInclude("policy");
        PassClientResult<Funder> result = passClient.selectObjects(selector);

        if (!result.getObjects().isEmpty()) {
            Funder storedFunder = getSingleObject(result, fullLocalKey);
            Funder updatedFunder = passEntityUtil.update(systemFunder, storedFunder);
            if (Objects.nonNull(updatedFunder)) { //need to update
                passClient.updateObject(updatedFunder);
                statistics.addFundersUpdated();
                return updatedFunder;
            }
            return storedFunder;
        } else { //don't have a stored Funder for this URI - this one is new to Pass
            if (systemFunder.getName() != null) { //only add if we have a name
                passClient.createObject(systemFunder);
                statistics.addFundersCreated();
            }
        }
        return systemFunder;
    }

    /**
     * Take a new User object populated as fully as possible from the COEUS pull, and use this
     * new information to update an object for the same User in Pass (if it exists)
     *
     * @param systemUser the new User object populated from COEUS
     * @return the URI for the resource representing the updated User in Pass
     */
    private User updateUserInPass(User systemUser) throws IOException, GrantDataException {
        //we first check to see if the user is known by the Hopkins ID. If not, we check the employee ID.
        //last attempt is the JHED ID. this order is specified by the order of the List as constructed on updatedUser
        User passUser = null;
        ListIterator<String> idIterator = systemUser.getLocatorIds().listIterator();

        while (passUser == null && idIterator.hasNext()) {
            String id = String.valueOf(idIterator.next());
            if (id != null) {
                PassClientSelector<User> selector = new PassClientSelector<>(User.class);
                selector.setFilter(RSQL.hasMember("locatorIds", id));
                PassClientResult<User> result = passClient.selectObjects(selector);
                passUser = result.getObjects().isEmpty()
                        ? null
                        : getSingleObject(result, id);;
            }
        }

        if (Objects.nonNull(passUser)) {
            User updatedUser = passEntityUtil.update(systemUser, passUser);
            if (Objects.nonNull(updatedUser)) { //need to update
                //post COEUS processing goes here
                if (!updatedUser.getRoles().contains(UserRole.SUBMITTER)) {
                    updatedUser.getRoles().add(UserRole.SUBMITTER);
                }
                passClient.updateObject(updatedUser);
                statistics.addUsersUpdated();
                return updatedUser;
            }
        } else if (!mode.equals("user")) { //don't have a stored User for this URI - this one is new to Pass
            //but don't update if we are in user mode - just update existing users
            passClient.createObject(systemUser);
            statistics.addUsersCreated();
            return systemUser;
        }
        return passUser;
    }

    /**
     * Take a new Grant object populated as fully as possible from the COEUS pull, and use this
     * new information to update an object for the same Grant in Pass (if it exists)
     *
     * @param systemGrant the new Grant object populated from COEUS
     * @return the PASS identifier for the Grant object
     */
    private Grant updateGrantInPass(Grant systemGrant) throws IOException, GrantDataException {
        String baseLocalKey = systemGrant.getLocalKey();
        String fullLocalKey = GrantDataUtils.buildLocalKey(domain, GRANT_ID_TYPE, baseLocalKey);
        systemGrant.setLocalKey(fullLocalKey);

        LOG.debug("Looking for grant with localKey {}", fullLocalKey);
        PassClientSelector<Grant> selector = new PassClientSelector<>(Grant.class);
        selector.setFilter(RSQL.equals("localKey", fullLocalKey));
        selector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> result = passClient.selectObjects(selector);

        if (!result.getObjects().isEmpty()) {
            LOG.debug("Found grant with localKey {}", fullLocalKey);
            Grant storedGrant = getSingleObject(result, fullLocalKey);
            Grant updatedGrant = passEntityUtil.update(systemGrant, storedGrant);
            if (Objects.nonNull(updatedGrant)) { //need to update
                passClient.updateObject(updatedGrant);
                statistics.addGrantsUpdated();
                LOG.debug("Updating grant with local key {}", systemGrant.getLocalKey());
                return updatedGrant;
            }
            return storedGrant;
        } else { //don't have a stored Grant for this URI - this one is new to Pass
            passClient.createObject(systemGrant);
            statistics.addGrantsCreated();
            LOG.debug("Creating grant with local key {}", systemGrant.getLocalKey());
        }
        return systemGrant;
    }

    private <T extends PassEntity> T getSingleObject(PassClientResult<T> result, String key) throws GrantDataException {
        if (result.getObjects().size() > 1) {
            throw new GrantDataException("More than a single object returned for key: " + key);
        }
        return result.getObjects().get(0);
    }

    /**
     * This method provides the latest timestamp of all records processed. After processing, this timestamp
     * will be used to be tha base timestamp for the next run of the app
     *
     * @return the latest update timestamp string
     */
    public String getLatestUpdate() {
        return this.latestUpdateString;
    }

    /**
     * This returns the final statistics of the processing of the Grant or User Set
     *
     * @return the report
     */
    public String getReport() {
        return statistics.getReport();
    }

    /**
     * This returns the final statistics Object - useful in testing
     *
     * @return the statistics object
     */
    public PassUpdateStatistics getStatistics() {
        return statistics;
    }

    public Map<String, Grant> getGrantResultMap() {
        return grantResultMap;
    }

    //used in unit test
    Map<String, Funder> getFunderMap() {
        return funderMap;
    }

    //used in unit test
    Map<String, User> getUserMap() {
        return userMap;
    }

    void setDomain(String domain) {
        this.domain = domain;
    }

}
