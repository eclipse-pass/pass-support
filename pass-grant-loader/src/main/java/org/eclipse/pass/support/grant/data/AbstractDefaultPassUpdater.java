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

import static org.eclipse.pass.support.grant.data.DateTimeUtil.createZonedDateTime;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import lombok.Getter;
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

public abstract class AbstractDefaultPassUpdater implements PassUpdater {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDefaultPassUpdater.class);

    private static final String GRANT_ID_TYPE = "grant";
    private static final String FUNDER_ID_TYPE = "funder";
    private static final String AWARD_LO = "awardLo";
    private static final String AWARD_HI = "awardHi";
    private static final String START_LO = "startLo";
    private static final String END_HI = "endHi";
    private static final String PI_AWARD = "piAward";
    private static final String PI_END   = "piEnd";

    @Getter
    private final PassUpdateStatistics statistics = new PassUpdateStatistics();

    @Getter
    private final Map<String, Grant> grantResultMap = new HashMap<>();

    @Getter
    private final List<String> ingestRecordErrors = new ArrayList<>();

    private String domain = "default.domain";
    private String latestUpdateString = "";

    private record GrantAccumulate(GrantIngestRecord grantIngestRecord, ZonedDateTime awardDate,
                                   ZonedDateTime startDate, ZonedDateTime endDate) {
        public ZonedDateTime getLoDate() {
            return Objects.nonNull(awardDate) ? awardDate : startDate;
        }

        public ZonedDateTime getHiDate() {
            return Objects.nonNull(awardDate) ? awardDate : endDate;
        }
    }

    private final Map<String, Funder> funderMap = new HashMap<>();
    private final Map<String, User> userMap = new HashMap<>();

    private String mode;

    private final PassClient passClient;
    private final Properties funderPolicyProperties;

    public AbstractDefaultPassUpdater(PassClient passClient,
                                      Properties funderPolicyProperties) {
        this.funderPolicyProperties = funderPolicyProperties;
        this.passClient = passClient;
    }

    public void updatePass(Collection<GrantIngestRecord> results, String mode) {
        this.mode = mode;
        userMap.clear();
        funderMap.clear();
        statistics.reset();
        statistics.setType(mode);
        switch (mode) {
            case "grant" -> updateGrants(results);
            case "user" -> updateUsers(results);
            case "funder" -> updateFunders(results);
            default -> {
            }
        }
    }

    public void setDomain(String domain) {
        this.domain = domain;
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
     * Build a Collection of Grants from a ResultSet, then update the grants in Pass
     * Because we need to make sure we catch any updates to fields referenced by URIs, we construct
     * these and update these as well
     */
    private void updateGrants(Collection<GrantIngestRecord> results) {

        //a grant will have several rows in the ResultSet if there are co-pis. so we put the grant on this
        //Map and add to it as additional rows add information.
        Map<String, Grant> grantRowMap = new HashMap<>();
        Map<String,  Map<String, GrantAccumulate>> grantAggregateMap = new HashMap<>();

        LOG.warn("Processing result set with {} rows", results.size());
        boolean modeChecked = false;

        for (GrantIngestRecord grantIngestRecord : results) {

            if (!modeChecked) {
                if (Objects.isNull(grantIngestRecord.getGrantNumber())) { //we always have this for grants
                    throw new RuntimeException("Mode of grant was supplied, but data does not seem to match.");
                } else {
                    modeChecked = true;
                }
            }

            String grantLocalKey = grantIngestRecord.getGrantNumber();

            try {
                validate(grantIngestRecord);

                //get funder local keys. if a primary funder is not specified, we set it to the direct funder
                String directFunderLocalKey = grantIngestRecord.getDirectFunderCode();
                String primaryFunderLocalKey = getPrimaryFunderKey(grantIngestRecord);

                //we will need funder PASS URIs - retrieve or create them,
                //updating the info on them if necessary
                if (!funderMap.containsKey(directFunderLocalKey)) {
                    Funder directFunder = buildDirectFunder(grantIngestRecord);
                    Funder updatedFunder = updateFunderInPass(directFunder);
                    funderMap.put(directFunderLocalKey, updatedFunder);
                }

                if (!funderMap.containsKey(primaryFunderLocalKey)) {
                    Funder primaryFunder = buildPrimaryFunder(grantIngestRecord);
                    Funder updatedFunder = updateFunderInPass(primaryFunder);
                    funderMap.put(primaryFunderLocalKey, updatedFunder);
                }

                //same for any users
                String userKey = getUserKey(grantIngestRecord);
                if (!userMap.containsKey(userKey)) {
                    User rowUser = buildUser(grantIngestRecord);
                    User updateUser = updateUserInPass(rowUser);
                    userMap.put(userKey, updateUser);
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

                User user = userMap.get(userKey);
                addCoPiIfNeeded(grant, user);
                Map<String, GrantAccumulate> grantAccumulateMap = grantAggregateMap.computeIfAbsent(grantLocalKey,
                    this::createGrantAccumulateMap);
                aggregateGrantAttributes(grantIngestRecord, grantAccumulateMap);

                //we are done with this record, let's save the state of this Grant
                grantRowMap.put(grantLocalKey, grant);
                //see if this is the latest grant updated
                if (Objects.nonNull(grantIngestRecord.getUpdateTimeStamp())) {
                    String grantUpdateString = grantIngestRecord.getUpdateTimeStamp();
                    latestUpdateString =
                        latestUpdateString.isEmpty()
                                    ? grantUpdateString
                                    : DateTimeUtil.returnLaterUpdate(grantUpdateString, latestUpdateString);
                }
            } catch (Exception e) {
                String message = "Error processing Grant Row. Skipping this row: " + grantIngestRecord +
                    "\nError Message: " + e.getMessage();
                ingestRecordErrors.add(message);
                LOG.error(message);
            }
        }

        //now put updated grant objects in pass
        for (Grant grant : grantRowMap.values()) {
            String grantLocalKey = grant.getLocalKey();
            try {
                setGrantAttributes(grant, grantAggregateMap);
                Grant updatedGrant = updateGrantInPass(grant);
                grantResultMap.put(grantLocalKey, updatedGrant);
            } catch (IOException | GrantDataException e) {
                LOG.error("Error updating Grant with localKey: " + grantLocalKey, e);
            }
        }

        //success - we capture some information to report
        if (!grantResultMap.isEmpty()) {
            statistics.setLatestUpdateString(latestUpdateString);
            statistics.setReport(results.size(), grantResultMap.size());
        } else {
            LOG.warn("No records were processed in this update");
        }
    }

    private void validate(GrantIngestRecord grantIngestRecord) throws GrantDataException {
        required(grantIngestRecord.getGrantNumber(), "grantNumber");
        required(grantIngestRecord.getGrantTitle(), "grantTitle");
        required(grantIngestRecord.getAwardStart(), "awardStart");
        required(grantIngestRecord.getAwardEnd(), "awardEnd");
        required(grantIngestRecord.getDirectFunderCode(), "directFunderCode");
        required(grantIngestRecord.getDirectFunderName(), "directFunderName");
        required(grantIngestRecord.getPiFirstName(), "piFirstName");
        required(grantIngestRecord.getPiLastName(), "piLastName");
        required(grantIngestRecord.getPiRole(), "piRole");

        if (StringUtils.isBlank(grantIngestRecord.getPiEmployeeId())
            && StringUtils.isBlank(grantIngestRecord.getPiInstitutionalId())) {
            throw new GrantDataException("User has blank employeeId and institutionalId.");
        }

        // Validates date and timestamp format
        createZonedDateTime(grantIngestRecord.getAwardDate());
        createZonedDateTime(grantIngestRecord.getAwardStart());
        createZonedDateTime(grantIngestRecord.getAwardEnd());
        createZonedDateTime(grantIngestRecord.getUpdateTimeStamp());

        if (StringUtils.isNotBlank(grantIngestRecord.getAwardStatus())) {
            try {
                AwardStatus.of(grantIngestRecord.getAwardStatus().toLowerCase());
            } catch (IllegalArgumentException e) {
                throw new GrantDataException("Invalid Award Status: " + grantIngestRecord.getAwardStatus() +
                    ". Valid " + Arrays.asList(AwardStatus.values()));
            }
        }

        try {
            GrantIngestUserRole.valueOf(grantIngestRecord.getPiRole());
        } catch (IllegalArgumentException e) {
            throw new GrantDataException("Invalid Pi Role: " + grantIngestRecord.getPiRole() +
                ". Valid " + Arrays.asList(GrantIngestUserRole.values()));
        }
    }

    private void required(String requiredValue, String attributeName) throws GrantDataException {
        if (StringUtils.isBlank(requiredValue)) {
            throw new GrantDataException("Required value missing for " + attributeName);
        }
    }

    private Map<String, GrantAccumulate> createGrantAccumulateMap(String key) {
        Map<String, GrantAccumulate> map = new HashMap<>();
        map.put(AWARD_LO, null);
        map.put(AWARD_HI, null);
        map.put(START_LO, null);
        map.put(END_HI, null);
        map.put(PI_AWARD, null);
        map.put(PI_END, null);
        return map;
    }

    private String getPrimaryFunderKey(GrantIngestRecord grantIngestRecord) {
        String directFunderLocalKey = grantIngestRecord.getDirectFunderCode();
        String primaryFunderLocalKey = grantIngestRecord.getPrimaryFunderCode();
        return Objects.isNull(primaryFunderLocalKey) ? directFunderLocalKey : primaryFunderLocalKey;
    }

    private String getUserKey(GrantIngestRecord grantIngestRecord) {
        return grantIngestRecord.getPiEmployeeId() + grantIngestRecord.getPiInstitutionalId();
    }

    private void addCoPiIfNeeded(Grant grant, User userCopi) {
        if (!grant.getCoPis().contains(userCopi)) {
            grant.getCoPis().add(userCopi);
            statistics.addCoPi();
        }
    }

    /**
     * Aggregate grant attribute values in grantAggregateMap.
     * <p>
     * The aggregation rules are as follows:
     * -For all grant records for a grant, use the grant record with awardDate to compute the earliest record base on
     * awardDate to set the start attributes.
     * -For all grant records for a grant, use the grant record with awardDate to compute the latest record base on
     * awardDate to set the end attributes and PI.
     * -If all grant records for a grant have no awardDate, then the startDate and endDate will be used to compute the
     * earliest and latest records respectively.
     * -If all grant records for a grant contains records with and without awardDate, the records with awardDate will
     * take precedence in determining the earliest and latest records.
     *
     * @param grantIngestRecord the grant ingest record being processed
     * @param grantAccumulateMap the map holding the aggregation for the grant
     * @throws GrantDataException thrown if bad grant data
     */
    private void aggregateGrantAttributes(GrantIngestRecord grantIngestRecord,
                                          Map<String, GrantAccumulate> grantAccumulateMap) throws GrantDataException {
        ZonedDateTime awardDate = createZonedDateTime(grantIngestRecord.getAwardDate());
        ZonedDateTime startDate = Objects.requireNonNull(createZonedDateTime(grantIngestRecord.getAwardStart()));
        ZonedDateTime endDate = Objects.requireNonNull(createZonedDateTime(grantIngestRecord.getAwardEnd()));
        GrantIngestUserRole grantIngestUserRole = GrantIngestUserRole.valueOf(grantIngestRecord.getPiRole());
        String loKey = Objects.nonNull(awardDate) ? AWARD_LO : START_LO;
        String hiKey = Objects.nonNull(awardDate) ? AWARD_HI : END_HI;
        String piKey = Objects.nonNull(awardDate) ? PI_AWARD : PI_END;
        ZonedDateTime loGrantDate = Objects.nonNull(awardDate) ? awardDate : startDate;
        ZonedDateTime hiGrantDate = Objects.nonNull(awardDate) ? awardDate : endDate;
        GrantAccumulate accumLo = grantAccumulateMap.get(loKey);
        if (Objects.isNull(accumLo) || loGrantDate.isBefore(accumLo.getLoDate())) {
            grantAccumulateMap.put(loKey, new GrantAccumulate(grantIngestRecord, awardDate, startDate, endDate));
        }
        GrantAccumulate accumHi = grantAccumulateMap.get(hiKey);
        if (Objects.isNull(accumHi) || !hiGrantDate.isBefore(accumHi.getHiDate())) {
            grantAccumulateMap.put(hiKey, new GrantAccumulate(grantIngestRecord, awardDate, startDate, endDate));
        }
        if (grantIngestUserRole == GrantIngestUserRole.P) {
            GrantAccumulate piAccum = grantAccumulateMap.get(piKey);
            if (Objects.isNull(piAccum) || !hiGrantDate.isBefore(piAccum.getHiDate())) {
                grantAccumulateMap.put(piKey, new GrantAccumulate(grantIngestRecord, awardDate, startDate, endDate));
            }
        }
    }

    private void setGrantAttributes(Grant grant, Map<String,  Map<String, GrantAccumulate>> grantAggregateMap)
        throws GrantDataException {
        String grantLocalKey = grant.getLocalKey();
        Map<String, GrantAccumulate> grantAccumulateMap = grantAggregateMap.get(grantLocalKey);

        GrantAccumulate awardLo = grantAccumulateMap.get(AWARD_LO);
        GrantIngestRecord grantIngestRecordStart = Objects.nonNull(awardLo) ? awardLo.grantIngestRecord()
            : grantAccumulateMap.get(START_LO).grantIngestRecord();
        setGrantStartAttributes(grant, grantIngestRecordStart);

        GrantAccumulate awardHi = grantAccumulateMap.get(AWARD_HI);
        GrantIngestRecord grantIngestRecordEnd = Objects.nonNull(awardHi) ? awardHi.grantIngestRecord()
            : grantAccumulateMap.get(END_HI).grantIngestRecord();
        setGrantEndAttributes(grant, grantIngestRecordEnd);

        GrantAccumulate piAward = grantAccumulateMap.get(PI_AWARD);
        if (Objects.nonNull(piAward)) {
            GrantIngestRecord grantIngestRecordPi = piAward.grantIngestRecord();
            setPiOnGrant(grant, grantIngestRecordPi);
        } else {
            GrantAccumulate piEnd = grantAccumulateMap.get(PI_END);
            if (Objects.nonNull(piEnd)) {
                GrantIngestRecord grantIngestRecordPi = piEnd.grantIngestRecord();
                setPiOnGrant(grant, grantIngestRecordPi);
            }
        }
    }

    private void setGrantStartAttributes(Grant grant, GrantIngestRecord grantIngestRecord) throws GrantDataException {
        grant.setProjectName(grantIngestRecord.getGrantTitle());
        grant.setAwardNumber(grantIngestRecord.getAwardNumber());
        String directFunderKey = grantIngestRecord.getDirectFunderCode();
        String primaryFunderKey = getPrimaryFunderKey(grantIngestRecord);
        grant.setDirectFunder(funderMap.get(directFunderKey));
        grant.setPrimaryFunder(funderMap.get(primaryFunderKey));
        ZonedDateTime startDate = createZonedDateTime(grantIngestRecord.getAwardStart());
        grant.setStartDate(startDate);
        ZonedDateTime awardDate = createZonedDateTime(grantIngestRecord.getAwardDate());
        grant.setAwardDate(awardDate);
    }

    private void setGrantEndAttributes(Grant grant, GrantIngestRecord grantIngestRecord)
        throws GrantDataException {
        ZonedDateTime endDate = createZonedDateTime(grantIngestRecord.getAwardEnd());
        grant.setEndDate(endDate);
        AwardStatus awardStatus = StringUtils.isNotBlank(grantIngestRecord.getAwardStatus())
            ? AwardStatus.of(grantIngestRecord.getAwardStatus().toLowerCase())
            : null;
        grant.setAwardStatus(awardStatus);
    }

    private void setPiOnGrant(Grant grant, GrantIngestRecord grantIngestRecord) {
        String userKey = getUserKey(grantIngestRecord);
        User newPi = userMap.get(userKey);
        User oldPi = grant.getPi();
        grant.setPi(newPi);
        boolean removed = grant.getCoPis().remove(newPi);
        if (removed) {
            statistics.subtractCoPi();
        }
        if (oldPi == null) {
            statistics.addPi();
        } else {
            if (!oldPi.equals(newPi)) {
                addCoPiIfNeeded(grant, oldPi);
            }
        }
    }

    /**
     * this method gets called on a grant mode process if the primary funder is different from direct, and also
     * any time the updater is called in funder mode
     *
     * @param grantIngestRecord the funder data record
     * @return the funder
     */
    public Funder buildPrimaryFunder(GrantIngestRecord grantIngestRecord) {
        Funder funder = new Funder();
        funder.setName(grantIngestRecord.getPrimaryFunderName());
        funder.setLocalKey(grantIngestRecord.getPrimaryFunderCode());
        setFunderPolicyIfNeeded(funder, grantIngestRecord.getPrimaryFunderCode());
        LOG.debug("Built Funder with localKey {}", funder.getLocalKey());
        return funder;
    }

    /**
     * Returns the Pass entity ID of passEntity. This method is null-safe.
     * @param passEntity the passEntity
     * @return the ID of passEntity or null if passEntity is null or the ID is null
     */
    protected String getPassEntityId(PassEntity passEntity) {
        return Optional.ofNullable(passEntity).map(PassEntity::getId).orElse(null);
    }

    private void updateUsers(Collection<GrantIngestRecord> results) {

        boolean modeChecked = false;

        LOG.info("Processing result set with {} rows", results.size());
        int userProcessedCounter = 0;
        for (GrantIngestRecord grantIngestRecord : results) {

            if (!modeChecked) {
                if (Objects.isNull(grantIngestRecord.getPiEmployeeId())) { //we always have this for users
                    throw new RuntimeException("Mode of user was supplied, but data does not seem to match.");
                } else {
                    modeChecked = true;
                }
            }

            User rowUser = buildUser(grantIngestRecord);
            try {
                updateUserInPass(rowUser);
                userProcessedCounter++;
                if (Objects.nonNull(grantIngestRecord.getUpdateTimeStamp())) {
                    String userUpdateString = grantIngestRecord.getUpdateTimeStamp();
                    latestUpdateString =
                        latestUpdateString.isEmpty()
                                    ? userUpdateString
                                    : DateTimeUtil.returnLaterUpdate(userUpdateString, latestUpdateString);
                }
            } catch (Exception e) {
                LOG.error("Error processing User: " + rowUser, e);
            }
        }

        if (!results.isEmpty()) {
            statistics.setLatestUpdateString(latestUpdateString);
            statistics.setReport(results.size(), userProcessedCounter);
        } else {
            LOG.warn("No records were processed in this update");
        }

    }

    /**
     * This method is called for the "funder" mode - the column names will have the values for primary funders
     *
     * @param results the data records containing funder information
     */
    private void updateFunders(Collection<GrantIngestRecord> results) {

        boolean modeChecked = false;
        LOG.info("Processing result set with {} rows", results.size());
        int funderProcessedCounter = 0;
        for (GrantIngestRecord grantIngestRecord : results) {

            if (!modeChecked) {
                if (Objects.isNull(grantIngestRecord.getPrimaryFunderCode())
                    && Objects.isNull(grantIngestRecord.getPrimaryFunderName())) {
                    throw new RuntimeException("Mode of funder was supplied, but data does not seem to match.");
                } else {
                    modeChecked = true;
                }
            }

            Funder rowFunder = buildPrimaryFunder(grantIngestRecord);
            try {
                updateFunderInPass(rowFunder);
                funderProcessedCounter++;
            } catch (IOException | GrantDataException e) {
                LOG.error("Error processing Funder localKey: " + rowFunder.getLocalKey(), e);
            }
        }
        statistics.setReport(results.size(), funderProcessedCounter);
    }

    private Funder buildDirectFunder(GrantIngestRecord grantIngestRecord) {
        Funder funder = new Funder();
        if (Objects.nonNull(grantIngestRecord.getDirectFunderName())) {
            funder.setName(grantIngestRecord.getDirectFunderName());
        }
        funder.setLocalKey(grantIngestRecord.getDirectFunderCode());
        setFunderPolicyIfNeeded(funder, grantIngestRecord.getDirectFunderCode());
        LOG.debug("Built Funder with localKey {}", funder.getLocalKey());
        return funder;
    }

    private void setFunderPolicyIfNeeded(Funder funder, String funderCode) {
        if (Objects.nonNull(funderCode)) {
            String policyId = funderPolicyProperties.getProperty(funderCode);
            if (Objects.nonNull(policyId)) {
                funder.setPolicy(new Policy(policyId));
                LOG.debug("Processing Funder with localKey {} and policy {}", funder.getLocalKey(), policyId);
            }
        }
    }

    /**
     * Take a new Funder object populated as fully as possible from the Grant source system pull, and use this
     * new information to update an object for the same Funder in Pass (if it exists)
     *
     * @param systemFunder the new Funder object populated from Grant source system
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
            Funder updatedFunder = updateFunderIfNeeded(systemFunder, storedFunder);
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
     * Take a new User object populated as fully as possible from the Grant source system pull, and use this
     * new information to update an object for the same User in Pass (if it exists)
     *
     * @param systemUser the new User object populated from Grant source system
     * @return the URI for the resource representing the updated User in Pass
     */
    private User updateUserInPass(User systemUser) throws IOException {
        User passUser = systemUser.getLocatorIds().stream()
            .map(this::lookupPassUser)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        if (Objects.nonNull(passUser)) {
            User updatedUser = updateUserIfNeeded(systemUser, passUser);
            if (Objects.nonNull(updatedUser)) { //need to update
                //post Grant database processing goes here
                if (!updatedUser.getRoles().contains(UserRole.SUBMITTER)) {
                    updatedUser.getRoles().add(UserRole.SUBMITTER);
                }
                passClient.updateObject(updatedUser);
                statistics.addUsersUpdated();
                return updatedUser;
            }
        } else if (!mode.equals("user")) {
            passClient.createObject(systemUser);
            statistics.addUsersCreated();
            return systemUser;
        }
        return passUser;
    }

    private User lookupPassUser(String locatorId) {
        try {
            PassClientSelector<User> selector = new PassClientSelector<>(User.class);
            selector.setFilter(RSQL.hasMember("locatorIds", locatorId));
            PassClientResult<User> result = passClient.selectObjects(selector);
            return result.getObjects().isEmpty() ? null : getSingleObject(result, locatorId);
        } catch (IOException | GrantDataException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Take a new Grant object populated as fully as possible from the Grant source system pull, and use this
     * new information to update an object for the same Grant in Pass (if it exists)
     *
     * @param systemGrant the new Grant object populated from Grant source system
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
            Grant updatedGrant = updateGrantIfNeeded(systemGrant, storedGrant);
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

}
