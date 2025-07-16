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

import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.grant.AbstractIntegrationTest;
import org.eclipse.pass.support.grant.data.GrantDataException;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JhuPassUpdaterOrderedIT extends AbstractIntegrationTest {

    private final String[] grantAwardNumber = {"A10000000", "A10000001", "A10000002"};
    private final String[] grantLocalKey = {"10000000", "10000000", "10000000"}; //all the same
    private final String[] grantProjectName =
        {"Awesome Research Project I", "Awesome Research Project II", "Awesome Research Project III"};
    private final String[] grantAwardDate = {"1999-01-01", "2001-01-01", "2003-01-01"};
    private final String[] grantStartDate = {"2000-07-01", "2002-07-01", "2004-07-01"};
    private final String[] grantEndDate = {"2002-06-30", "2004-06-30", "2006-06-30"};
    private final String[] grantUpdateTimestamp =
        {"2006-03-11 00:00:00.0", "2010-04-05 00:00:00.0", "2015-11-11 00:00:00.0"};
    private final String[] userEmployeeId = {"30000000", "30000001", "30000002"};
    private final String[] userInstitutionalId = {"amelon1", "aeinst1", "jjones1"};
    private final String[] userFirstName = {"Andrew", "Albert", "Junie"};
    private final String[] userMiddleName = {"Smith", "Carnegie", "Beatrice"};
    private final String[] userLastName = {"Melon", "Einstein", "Jones"};
    private final String[] userEmail = {"amelon1@jhu.edu", "aeinst1@jhu.edu", "jjones1@jhu.edu"};

    private final String grantIdPrefix = "johnshopkins.edu:grant:";

    @Autowired private JhuPassUpdater jhuPassUpdater;

    /**
     * we put an initial award for a grant into PASS, then simulate a pull of all records related
     * to this grant from the Beginning of Time (including records which created the initial object)
     *
     * We expect to see some fields retained from the initial award, and others updated. The most
     * interesting fields are the investigator fields: all CO-PIs ever on the grant should stay on the
     * co-pi field throughout iterations. If a PI is changed, they should appear on the CO-PI field
     *
     */
    @Test
    @Order(1)
    void testUpdateGrantCreate() throws IOException, GrantDataException {
        // GIVEN
        List<GrantIngestRecord> resultSet = new ArrayList<>();

        //put in initial grant - PI is Einstein
        GrantIngestRecord piRecord2 = makeGrantIngestRecord(2, 1, "P", 1);
        resultSet.add(piRecord2);

        jhuPassUpdater.updatePass(resultSet, "grant");

        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", grantIdPrefix + grantLocalKey[2]));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);

        User userGrantPi = getVerifiedUser(1);

        assertEquals(grantAwardNumber[2], passGrant.getAwardNumber());
        assertEquals(AwardStatus.ACTIVE, passGrant.getAwardStatus());
        assertEquals(grantIdPrefix + grantLocalKey[2], passGrant.getLocalKey());
        assertEquals(grantProjectName[2], passGrant.getProjectName());
        assertEquals(createZonedDateTime(grantAwardDate[2]), passGrant.getAwardDate());
        assertEquals(createZonedDateTime(grantStartDate[2]), passGrant.getStartDate());
        assertEquals(createZonedDateTime(grantEndDate[2]), passGrant.getEndDate());
        assertEquals(userGrantPi, passGrant.getPi()); //Einstein
        assertEquals(0, passGrant.getCoPis().size());

        //check statistics
        assertEquals(1, jhuPassUpdater.getStatistics().getGrantsCreated());
        assertEquals(0, jhuPassUpdater.getStatistics().getGrantsUpdated());
        assertEquals(2, jhuPassUpdater.getStatistics().getFundersCreated());
        assertEquals(0, jhuPassUpdater.getStatistics().getFundersUpdated());
        assertEquals(1, jhuPassUpdater.getStatistics().getUsersCreated());
        assertEquals(0, jhuPassUpdater.getStatistics().getUsersUpdated());
        assertEquals(1, jhuPassUpdater.getStatistics().getPisAdded());
        assertEquals(0, jhuPassUpdater.getStatistics().getCoPisAdded());
    }

    @Test
    @Order(2)
    void testUpdateGrantSecond() throws IOException, GrantDataException {
        // GIVEN ordered oldest to newest
        GrantIngestRecord piRecord0 = makeGrantIngestRecord(0, 0, "P", 1);
        GrantIngestRecord coPiRecord0 = makeGrantIngestRecord(0, 1, "C", 1);
        GrantIngestRecord piRecord1 = makeGrantIngestRecord(1, 0, "P", 1);
        GrantIngestRecord coPiRecord1 = makeGrantIngestRecord(1, 1, "C", 1);
        GrantIngestRecord newCoPiRecord1 = makeGrantIngestRecord(1, 2, "C", 1);
        GrantIngestRecord piRecord2 = makeGrantIngestRecord(2, 1, "P", 1);
        List<GrantIngestRecord> resultSet = new ArrayList<>();
        resultSet.add(piRecord0);
        resultSet.add(coPiRecord0);
        resultSet.add(piRecord1);
        resultSet.add(coPiRecord1);
        resultSet.add(newCoPiRecord1);
        resultSet.add(piRecord2);
        jhuPassUpdater.getStatistics().reset();

        // WHEN
        jhuPassUpdater.updatePass(resultSet, "grant");

        // THEN
        assertEquals(0, jhuPassUpdater.getStatistics().getGrantsCreated());
        assertEquals(1, jhuPassUpdater.getStatistics().getGrantsUpdated());
        assertEquals(0, jhuPassUpdater.getStatistics().getFundersCreated());
        assertEquals(0, jhuPassUpdater.getStatistics().getFundersUpdated());
        assertEquals(2, jhuPassUpdater.getStatistics().getUsersCreated());
        assertEquals(0, jhuPassUpdater.getStatistics().getUsersUpdated());
        assertEquals(1, jhuPassUpdater.getStatistics().getPisAdded());
        assertEquals(2, jhuPassUpdater.getStatistics().getCoPisAdded());
        verifyGrant();
    }

    @Test
    @Order(3)
    void testUpdateGrantThird_ChangeRecordOrderWithDuplicate() throws IOException, GrantDataException {
        // GIVEN ordered newest then mix
        GrantIngestRecord piRecord2 = makeGrantIngestRecord(2, 1, "P", 1);
        GrantIngestRecord newCoPiRecord1 = makeGrantIngestRecord(1, 2, "C", 1);
        GrantIngestRecord coPiRecord1 = makeGrantIngestRecord(1, 1, "C", 1);
        GrantIngestRecord coPiRecord0 = makeGrantIngestRecord(0, 1, "C", 1);
        GrantIngestRecord piRecord1 = makeGrantIngestRecord(1, 0, "P", 1);
        GrantIngestRecord piRecord0 = makeGrantIngestRecord(0, 0, "P", 1);

        List<GrantIngestRecord> resultSet = new ArrayList<>();
        resultSet.add(piRecord2);
        resultSet.add(newCoPiRecord1);
        resultSet.add(coPiRecord1);
        resultSet.add(coPiRecord0);
        resultSet.add(coPiRecord0); // simulate duplicate
        resultSet.add(piRecord1);
        resultSet.add(piRecord0);
        jhuPassUpdater.getStatistics().reset();

        // WHEN
        jhuPassUpdater.updatePass(resultSet, "grant");

        // THEN
        assertEquals(0, jhuPassUpdater.getStatistics().getGrantsCreated());
        assertEquals(1, jhuPassUpdater.getStatistics().getGrantsUpdated());
        assertEquals(0, jhuPassUpdater.getStatistics().getFundersCreated());
        assertEquals(0, jhuPassUpdater.getStatistics().getFundersUpdated());
        assertEquals(0, jhuPassUpdater.getStatistics().getUsersCreated());
        assertEquals(0, jhuPassUpdater.getStatistics().getUsersUpdated());
        assertEquals(1, jhuPassUpdater.getStatistics().getPisAdded());
        assertEquals(2, jhuPassUpdater.getStatistics().getCoPisAdded());
        verifyGrant();
    }

    private void verifyGrant() throws IOException, GrantDataException {
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", grantIdPrefix + grantLocalKey[2]));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);

        User user0 = getVerifiedUser(0);
        User user1 = getVerifiedUser(1);
        User user2 = getVerifiedUser(2);

        Mockito.verify(passClient, Mockito.times(1)).updateObject(ArgumentMatchers.any());
        assertEquals(grantAwardNumber[0], passGrant.getAwardNumber());//initial
        assertEquals(AwardStatus.ACTIVE, passGrant.getAwardStatus());
        assertEquals(grantIdPrefix + grantLocalKey[0], passGrant.getLocalKey());
        assertEquals(grantProjectName[0], passGrant.getProjectName());//initial
        assertEquals(createZonedDateTime(grantAwardDate[0]), passGrant.getAwardDate());//initial
        assertEquals(createZonedDateTime(grantStartDate[0]), passGrant.getStartDate());//initial
        assertEquals(createZonedDateTime(grantEndDate[2]), passGrant.getEndDate());//latest
        assertEquals(user1, passGrant.getPi());//Einstein
        assertEquals(2, passGrant.getCoPis().size());
        assertTrue(passGrant.getCoPis().contains(user0));//Melon
        assertTrue(passGrant.getCoPis().contains(user2));//Jones
    }

    private User getVerifiedUser(int userIndex) throws IOException {
        PassClientSelector<User> userSelector = new PassClientSelector<>(User.class);
        userSelector.setFilter(RSQL.hasMember("locatorIds", EMPLOYEE_LOCATOR_ID + userEmployeeId[userIndex]));
        PassClientResult<User> resultUser = passClient.selectObjects(userSelector);
        assertEquals(1, resultUser.getTotal());
        User user = resultUser.getObjects().get(0);
        assertEquals(2, user.getLocatorIds().size());
        assertEquals(EMPLOYEE_LOCATOR_ID + userEmployeeId[userIndex], user.getLocatorIds().get(0));
        assertEquals(JHED_LOCATOR_ID + userInstitutionalId[userIndex], user.getLocatorIds().get(1));
        return user;
    }

    /**
     * utility method to produce data as it would look coming from Grant database
     *
     * @param iteration the iteration of the (multi-award) grant
     * @param user      the user supplied in the record
     * @param abbrRole  the role: Pi ("P") or co-pi (C" or "K")
     * @return the row map for the record
     */
    private GrantIngestRecord makeGrantIngestRecord(int iteration, int user, String abbrRole, int piUser) {
        GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
        grantIngestRecord.setAwardNumber(grantAwardNumber[iteration]);
        grantIngestRecord.setAwardStatus("Active");
        grantIngestRecord.setGrantNumber(grantLocalKey[iteration]);
        grantIngestRecord.setGrantTitle(grantProjectName[iteration]);
        grantIngestRecord.setAwardDate(grantAwardDate[iteration]);
        grantIngestRecord.setAwardStart(grantStartDate[iteration]);
        grantIngestRecord.setAwardEnd(grantEndDate[iteration]);

        grantIngestRecord.setDirectFunderCode("30000000");
        grantIngestRecord.setDirectFunderName("Enormous State University");
        grantIngestRecord.setPrimaryFunderCode("30000001");
        grantIngestRecord.setPrimaryFunderName("J L Gotrocks Foundation");

        grantIngestRecord.setPiFirstName(userFirstName[user]);
        grantIngestRecord.setPiMiddleName(userMiddleName[user]);
        grantIngestRecord.setPiLastName(userLastName[user]);
        grantIngestRecord.setPiEmail(userEmail[user]);
        grantIngestRecord.setPiInstitutionalId(userInstitutionalId[user]);
        grantIngestRecord.setPiEmployeeId(userEmployeeId[user]);

        grantIngestRecord.setActivePiInstitutionalId(userInstitutionalId[piUser]);
        grantIngestRecord.setActivePiEmployeeId(userEmployeeId[piUser]);

        grantIngestRecord.setUpdateTimeStamp(grantUpdateTimestamp[iteration]);
        grantIngestRecord.setPiRole(abbrRole);

        return grantIngestRecord;
    }

}
