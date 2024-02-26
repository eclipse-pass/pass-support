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

import static org.eclipse.pass.support.grant.data.DateTimeUtil.createZonedDateTime;
import static org.eclipse.pass.support.grant.data.jhu.JhuPassUpdater.EMPLOYEE_LOCATOR_ID;
import static org.eclipse.pass.support.grant.data.jhu.JhuPassUpdater.JHED_LOCATOR_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.grant.AbstractIntegrationTest;
import org.eclipse.pass.support.grant.TestUtil;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class JhuPassUpdaterIT extends AbstractIntegrationTest {

    private final String grantIdPrefix = "johnshopkins.edu:grant:";

    @Autowired private JhuPassUpdater jhuPassUpdater;

    /**
     * we put an initial award for a grant into PASS, then simulate a pull of all subsequent records
     * <p>
     * We expect to see some fields retained from the initial award, and others updated. The most
     * interesting fields are the investigator fields: all CO-PIs ever on the grant should stay on the
     * co-pi field throughout iterations. If a PI is changed, they should appear on the CO-PI field
     *
     */
    @Test
    public void processGrantIT() throws IOException {
        // GIVEN
        Policy policy = getTestPolicy();

        //put in initial iteration as a correct existing record - PI is Reckondwith, Co-pi is Class
        GrantIngestRecord piRecord0 = TestUtil.makeGrantIngestRecord(0, 0, "P");
        GrantIngestRecord coPiRecord0 = TestUtil.makeGrantIngestRecord(0, 1, "C");

        List<GrantIngestRecord> resultSet = new ArrayList<>();
        resultSet.add(piRecord0);
        resultSet.add(coPiRecord0);

        Properties policyProperties = TestUtil.loaderPolicyProperties();
        policyProperties.put("20000000", policy.getId());
        policyProperties.put("20000001", policy.getId());

        // WHEN
        jhuPassUpdater.updatePass(resultSet, "grant");

        // THEN
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", grantIdPrefix + TestUtil.grantLocalKey[0]));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);

        User user0 = getVerifiedUser(0);
        User user1 = getVerifiedUser(1);

        assertEquals(TestUtil.grantAwardNumber[0], passGrant.getAwardNumber());
        assertEquals(AwardStatus.ACTIVE, passGrant.getAwardStatus());
        assertEquals(grantIdPrefix + TestUtil.grantLocalKey[0], passGrant.getLocalKey());
        assertEquals(TestUtil.grantProjectName[0], passGrant.getProjectName());
        assertEquals(createZonedDateTime(TestUtil.grantAwardDate[0]), passGrant.getAwardDate());
        assertEquals(createZonedDateTime(TestUtil.grantStartDate[0]), passGrant.getStartDate());
        assertEquals(createZonedDateTime(TestUtil.grantEndDate[0]), passGrant.getEndDate());

        Funder primaryFunder = passClient.getObject(passGrant.getPrimaryFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:20000001", primaryFunder.getLocalKey());
        assertEquals("J L Gotrocks Foundation", primaryFunder.getName());
        assertEquals(policy.getId(), primaryFunder.getPolicy().getId());

        Funder directFunder = passClient.getObject(passGrant.getDirectFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:20000000", directFunder.getLocalKey());
        assertEquals("Enormous State University",directFunder.getName());
        assertEquals(policy.getId(), directFunder.getPolicy().getId());

        assertEquals(TestUtil.grantUpdateTimestamp[0], jhuPassUpdater.getLatestUpdate());//latest
        assertEquals(user0, passGrant.getPi()); //Reckondwith
        assertEquals(1, passGrant.getCoPis().size());
        assertEquals(user1, passGrant.getCoPis().get(0));

        //check statistics
        assertEquals(1, jhuPassUpdater.getStatistics().getGrantsCreated());
        assertEquals(2, jhuPassUpdater.getStatistics().getUsersCreated());
        assertEquals(1, jhuPassUpdater.getStatistics().getPisAdded());
        assertEquals(1, jhuPassUpdater.getStatistics().getCoPisAdded());

        // WHEN
        //now simulate an incremental pull since the initial,  adjust the stored grant
        //we add a new co-pi Jones in the "1" iteration, and change the pi to Einstein in the "2" iteration
        //we drop co-pi jones in the last iteration
        GrantIngestRecord piRecord1 = TestUtil.makeGrantIngestRecord(1, 0, "P");
        GrantIngestRecord coPiRecord1 = TestUtil.makeGrantIngestRecord(1, 1, "C");
        GrantIngestRecord newCoPiRecord1 = TestUtil.makeGrantIngestRecord(1, 2, "C");
        GrantIngestRecord piRecord2 = TestUtil.makeGrantIngestRecord(2, 1, "P");

        //add in everything since the initial pull
        resultSet.clear();
        resultSet.add(piRecord1);
        resultSet.add(coPiRecord1);
        resultSet.add(newCoPiRecord1);
        resultSet.add(piRecord2);

        jhuPassUpdater.updatePass(resultSet, "grant");

        // THEN
        resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant updatePassGrant = resultGrant.getObjects().get(0);

        User user2 = getVerifiedUser(2);

        assertEquals(TestUtil.grantAwardNumber[0], updatePassGrant.getAwardNumber());//initial
        assertEquals(AwardStatus.ACTIVE, updatePassGrant.getAwardStatus());
        assertEquals(grantIdPrefix + TestUtil.grantLocalKey[0], updatePassGrant.getLocalKey());
        assertEquals(TestUtil.grantProjectName[0], updatePassGrant.getProjectName());//initial
        assertEquals(createZonedDateTime(TestUtil.grantAwardDate[0]), updatePassGrant.getAwardDate());//initial
        assertEquals(createZonedDateTime(TestUtil.grantStartDate[0]), updatePassGrant.getStartDate());//initial
        assertEquals(createZonedDateTime(TestUtil.grantEndDate[2]), updatePassGrant.getEndDate());//latest
        assertEquals(TestUtil.grantUpdateTimestamp[2], jhuPassUpdater.getLatestUpdate());//latest
        assertEquals(user1, updatePassGrant.getPi());//Class
        assertEquals(2, updatePassGrant.getCoPis().size());
        assertTrue(updatePassGrant.getCoPis().contains(user0));//Reckondwith
        assertTrue(updatePassGrant.getCoPis().contains(user2));//Gunn
    }

    @Test
    public void processGrantIT_DoesNotUpdateWithNoChange() throws IOException, IllegalAccessException {
        // GIVEN
        Policy policy = getTestPolicy();
        GrantIngestRecord piRecord0 = TestUtil.makeGrantIngestRecord(3, 3, "P");
        GrantIngestRecord coPiRecord0 = TestUtil.makeGrantIngestRecord(3, 3, "C");

        List<GrantIngestRecord> resultSet = new ArrayList<>();
        resultSet.add(piRecord0);
        resultSet.add(coPiRecord0);

        PassClient spyPassClient = Mockito.spy(passClient);
        Properties policyProperties = TestUtil.loaderPolicyProperties();
        policyProperties.put("20000000", policy.getId());
        policyProperties.put("20000001", policy.getId());
        FieldUtils.writeField(jhuPassUpdater, "passClient", spyPassClient, true);

        // WHEN
        jhuPassUpdater.updatePass(resultSet, "grant");

        // THEN
        Mockito.verify(spyPassClient, Mockito.times(1)).createObject(ArgumentMatchers.any(Grant.class));
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", grantIdPrefix + TestUtil.grantLocalKey[3]));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);

        User user3 = getVerifiedUser(3);

        assertEquals(TestUtil.grantAwardNumber[3], passGrant.getAwardNumber());
        assertEquals(AwardStatus.ACTIVE, passGrant.getAwardStatus());
        assertEquals(grantIdPrefix + TestUtil.grantLocalKey[3], passGrant.getLocalKey());
        assertEquals(TestUtil.grantProjectName[3], passGrant.getProjectName());
        assertEquals(createZonedDateTime(TestUtil.grantAwardDate[3]), passGrant.getAwardDate());
        assertEquals(createZonedDateTime(TestUtil.grantStartDate[3]), passGrant.getStartDate());
        assertEquals(createZonedDateTime(TestUtil.grantEndDate[3]), passGrant.getEndDate());
        assertEquals(TestUtil.grantUpdateTimestamp[3], jhuPassUpdater.getLatestUpdate());//latest
        assertEquals(user3, passGrant.getPi()); //Reckondwith
        assertEquals(1, passGrant.getCoPis().size());
        assertEquals(user3, passGrant.getCoPis().get(0));

        //check statistics
        assertEquals(1, jhuPassUpdater.getStatistics().getGrantsCreated());
        assertEquals(1, jhuPassUpdater.getStatistics().getUsersCreated());
        assertEquals(1, jhuPassUpdater.getStatistics().getPisAdded());
        assertEquals(1, jhuPassUpdater.getStatistics().getCoPisAdded());

        // WHEN
        jhuPassUpdater.updatePass(resultSet, "grant");

        // THEN
        Mockito.verify(spyPassClient, Mockito.times(0)).updateObject(ArgumentMatchers.any());
        PassClientResult<Grant> resultGrant2 = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant2.getTotal());
        Grant passGrant2 = resultGrant2.getObjects().get(0);

        User user3_2 = getVerifiedUser(3);

        assertEquals(TestUtil.grantAwardNumber[3], passGrant2.getAwardNumber());
        assertEquals(AwardStatus.ACTIVE, passGrant2.getAwardStatus());
        assertEquals(grantIdPrefix + TestUtil.grantLocalKey[3], passGrant2.getLocalKey());
        assertEquals(TestUtil.grantProjectName[3], passGrant2.getProjectName());
        assertEquals(createZonedDateTime(TestUtil.grantAwardDate[3]), passGrant2.getAwardDate());
        assertEquals(createZonedDateTime(TestUtil.grantStartDate[3]), passGrant2.getStartDate());
        assertEquals(createZonedDateTime(TestUtil.grantEndDate[3]), passGrant2.getEndDate());
        assertEquals(user3_2, passGrant2.getPi()); //Reckondwith
        assertEquals(1, passGrant2.getCoPis().size());
        assertEquals(user3_2, passGrant2.getCoPis().get(0));
        assertEquals(TestUtil.grantUpdateTimestamp[3], jhuPassUpdater.getLatestUpdate());//latest

        //check statistics
        assertEquals(0, jhuPassUpdater.getStatistics().getGrantsCreated());
        assertEquals(0, jhuPassUpdater.getStatistics().getGrantsUpdated());
        assertEquals(0, jhuPassUpdater.getStatistics().getUsersCreated());
    }

    @Test
    public void processGrantIT_UpdateUserLocatorsJhed() throws IOException {
        // GIVEN
        Policy policy = getTestPolicy();
        GrantIngestRecord piRecord0 = TestUtil.makeGrantIngestRecord(4, 4, "P");

        List<GrantIngestRecord> resultSet = new ArrayList<>();
        resultSet.add(piRecord0);

        Properties policyProperties = TestUtil.loaderPolicyProperties();
        policyProperties.put("20000000", policy.getId());
        policyProperties.put("20000001", policy.getId());

        // WHEN
        jhuPassUpdater.updatePass(resultSet, "grant");

        // THEN
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", grantIdPrefix + TestUtil.grantLocalKey[4]));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);

        PassClientSelector<User> user2Selector = new PassClientSelector<>(User.class);
        user2Selector.setFilter(RSQL.hasMember("locatorIds", EMPLOYEE_LOCATOR_ID +
            TestUtil.userEmployeeId[4]));
        PassClientResult<User> resultUser2 = passClient.selectObjects(user2Selector);
        assertEquals(1, resultUser2.getTotal());
        User addedUser = resultUser2.getObjects().get(0);
        assertEquals(2, addedUser.getLocatorIds().size());
        assertEquals(EMPLOYEE_LOCATOR_ID + TestUtil.userEmployeeId[4], addedUser.getLocatorIds().get(0));
        assertEquals(JHED_LOCATOR_ID + TestUtil.userInstitutionalId[4], addedUser.getLocatorIds().get(1));

        assertEquals(TestUtil.grantAwardNumber[4], passGrant.getAwardNumber());
        assertEquals(AwardStatus.ACTIVE, passGrant.getAwardStatus());
        assertEquals(grantIdPrefix + TestUtil.grantLocalKey[4], passGrant.getLocalKey());
        assertEquals(TestUtil.grantProjectName[4], passGrant.getProjectName());
        assertEquals(createZonedDateTime(TestUtil.grantAwardDate[4]), passGrant.getAwardDate());
        assertEquals(createZonedDateTime(TestUtil.grantStartDate[4]), passGrant.getStartDate());
        assertEquals(createZonedDateTime(TestUtil.grantEndDate[4]), passGrant.getEndDate());
        assertEquals(TestUtil.grantUpdateTimestamp[4], jhuPassUpdater.getLatestUpdate());//latest
        assertEquals(addedUser, passGrant.getPi());
        assertEquals(0, passGrant.getCoPis().size());

        //check statistics
        assertEquals(1, jhuPassUpdater.getStatistics().getGrantsCreated());
        assertEquals(1, jhuPassUpdater.getStatistics().getUsersCreated());
        assertEquals(1, jhuPassUpdater.getStatistics().getPisAdded());
        assertEquals(0, jhuPassUpdater.getStatistics().getCoPisAdded());

        // WHEN
        // JHED ID and Hopkins ID update from coeus
        GrantIngestRecord piRecordUpdate = TestUtil.makeGrantIngestRecord(4, 4, "P");
        piRecordUpdate.setPiInstitutionalId("newjdoe1jhed");

        //add in everything since the initial pull
        resultSet.clear();
        resultSet.add(piRecordUpdate);

        jhuPassUpdater.updatePass(resultSet, "grant");

        // THEN
        resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant updatePassGrant = resultGrant.getObjects().get(0);

        PassClientSelector<User> updatedUserSelector = new PassClientSelector<>(User.class);
        updatedUserSelector.setFilter(RSQL.hasMember("locatorIds", EMPLOYEE_LOCATOR_ID +
            TestUtil.userEmployeeId[4]));
        PassClientResult<User> resultUpdateUser = passClient.selectObjects(updatedUserSelector);
        assertEquals(1, resultUpdateUser.getTotal());
        User updatedUser = resultUpdateUser.getObjects().get(0);
        assertEquals(2, updatedUser.getLocatorIds().size());
        assertEquals(EMPLOYEE_LOCATOR_ID + TestUtil.userEmployeeId[4], updatedUser.getLocatorIds().get(0));
        assertEquals(JHED_LOCATOR_ID + "newjdoe1jhed", updatedUser.getLocatorIds().get(1));

        assertEquals(TestUtil.grantAwardNumber[4], updatePassGrant.getAwardNumber());//initial
        assertEquals(AwardStatus.ACTIVE, updatePassGrant.getAwardStatus());
        assertEquals(grantIdPrefix + TestUtil.grantLocalKey[4], updatePassGrant.getLocalKey());
        assertEquals(TestUtil.grantProjectName[4], updatePassGrant.getProjectName());//initial
        assertEquals(createZonedDateTime(TestUtil.grantAwardDate[4]), updatePassGrant.getAwardDate());//initial
        assertEquals(createZonedDateTime(TestUtil.grantStartDate[4]), updatePassGrant.getStartDate());//initial
        assertEquals(createZonedDateTime(TestUtil.grantEndDate[4]), updatePassGrant.getEndDate());//latest
        assertEquals(TestUtil.grantUpdateTimestamp[4], jhuPassUpdater.getLatestUpdate());//latest
        assertEquals(updatedUser, updatePassGrant.getPi());//Class
        assertEquals(0, updatePassGrant.getCoPis().size());
    }

    private User getVerifiedUser(int userIndex) throws IOException {
        PassClientSelector<User> userSelector = new PassClientSelector<>(User.class);
        userSelector.setFilter(RSQL.hasMember("locatorIds", EMPLOYEE_LOCATOR_ID +
            TestUtil.userEmployeeId[userIndex]));
        PassClientResult<User> resultUser = passClient.selectObjects(userSelector);
        assertEquals(1, resultUser.getTotal());
        User user = resultUser.getObjects().get(0);
        assertEquals(2, user.getLocatorIds().size());
        assertEquals(EMPLOYEE_LOCATOR_ID + TestUtil.userEmployeeId[userIndex], user.getLocatorIds().get(0));
        assertEquals(JHED_LOCATOR_ID + TestUtil.userInstitutionalId[userIndex], user.getLocatorIds().get(1));
        return user;
    }

    private Policy getTestPolicy() throws IOException {
        PassClientSelector<Policy> policySelector = new PassClientSelector<>(Policy.class);
        policySelector.setFilter(RSQL.equals("title", "test policy jhu pass updater IT" ));
        PassClientResult<Policy> result = passClient.selectObjects(policySelector);
        if (result.getObjects().isEmpty()) {
            Policy policy = new Policy();
            policy.setTitle("test policy jhu pass updater IT");
            passClient.createObject(policy);
            return policy;
        }
        return result.getObjects().get(0);
    }
}
