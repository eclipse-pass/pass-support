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
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.grant.AbstractIntegrationTest;
import org.eclipse.pass.support.grant.TestUtil;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JhuPassInitUpdaterIT extends AbstractIntegrationTest {

    private final String[] grantAwardNumber = {"A10000000", "A10000001", "A10000002"};
    private final String[] grantLocalKey = {"10000000", "10000000", "10000000"}; //all the same
    private final String[] grantProjectName =
        {"Awesome Research Project I", "Awesome Research Project II", "Awesome Research Project III"};
    private final String[] grantAwardDate = {"01/01/1999", "01/01/2001", "01/01/2003"};
    private final String[] grantStartDate = {"07/01/2000", "07/01/2002", "07/01/2004"};
    private final String[] grantEndDate = {"06/30/2002", "06/30/2004", "06/30/2006"};
    private final String[] grantUpdateTimestamp =
        {"2006-03-11 00:00:00.0", "2010-04-05 00:00:00.0", "2015-11-11 00:00:00.0"};
    private final String[] userEmployeeId = {"30000000", "30000001", "30000002"};
    private final String[] userInstitutionalId = {"amelon1", "aeinst1", "jjones1"};
    private final String[] userFirstName = {"Andrew", "Albert", "Junie"};
    private final String[] userMiddleName = {"Smith", "Carnegie", "Beatrice"};
    private final String[] userLastName = {"Melon", "Einstein", "Jones"};
    private final String[] userEmail = {"amelon1@jhu.edu", "aeinst1@jhu.edu", "jjones1@jhu.edu"};

    private final String grantIdPrefix = "johnshopkins.edu:grant:";
    //private final String funderIdPrefix = "johnshopkins.edu:funder:";

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
    public void processInitGrantIT() throws IOException {
        // GIVEN
        List<GrantIngestRecord> resultSet = new ArrayList<>();

        //put in last iteration as existing record - PI is Einstein
        GrantIngestRecord piRecord2 = makeGrantIngestRecord(2, 1, "P");
        resultSet.add(piRecord2);

        Properties policyProperties = TestUtil.loaderPolicyProperties();
        JhuPassInitUpdater passUpdater = new JhuPassInitUpdater(policyProperties);
        passUpdater.updatePass(resultSet, "grant");

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
        assertEquals(1, passUpdater.getStatistics().getGrantsCreated());
        assertEquals(1, passUpdater.getStatistics().getUsersCreated());
        assertEquals(1, passUpdater.getStatistics().getPisAdded());
        assertEquals(0, passUpdater.getStatistics().getCoPisAdded());

        //now simulate a complete pull from the Beginning of Time and adjust the stored grant
        //we add a new co-pi Jones in the "1" iteration, and change the pi to Einstein in the "2" iteration
        //we drop co-pi jones in the last iteration
        GrantIngestRecord piRecord0 = makeGrantIngestRecord(0, 0, "P");
        GrantIngestRecord coPiRecord0 = makeGrantIngestRecord(0, 1, "C");
        GrantIngestRecord piRecord1 = makeGrantIngestRecord(1, 0, "P");
        GrantIngestRecord coPiRecord1 = makeGrantIngestRecord(1, 1, "C");
        GrantIngestRecord newCoPiRecord1 = makeGrantIngestRecord(1, 2, "C");

        //in the initial pull, we will find all of the records (check?)
        resultSet.clear();
        resultSet.add(piRecord0);
        resultSet.add(coPiRecord0);
        resultSet.add(piRecord1);
        resultSet.add(coPiRecord1);
        resultSet.add(newCoPiRecord1);
        resultSet.add(piRecord2);

        passUpdater.updatePass(resultSet, "grant");

        resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant updatePassGrant = resultGrant.getObjects().get(0);

        User user0 = getVerifiedUser(0);
        User user1 = getVerifiedUser(1);
        User user2 = getVerifiedUser(2);

        assertEquals(grantAwardNumber[0], updatePassGrant.getAwardNumber());//initial
        assertEquals(AwardStatus.ACTIVE, updatePassGrant.getAwardStatus());
        assertEquals(grantIdPrefix + grantLocalKey[0], updatePassGrant.getLocalKey());
        assertEquals(grantProjectName[0], updatePassGrant.getProjectName());//initial
        assertEquals(createZonedDateTime(grantAwardDate[0]), updatePassGrant.getAwardDate());//initial
        assertEquals(createZonedDateTime(grantStartDate[0]), updatePassGrant.getStartDate());//initial
        assertEquals(createZonedDateTime(grantEndDate[2]), updatePassGrant.getEndDate());//latest
        assertEquals(user1, updatePassGrant.getPi());//Einstein
        assertEquals(2, updatePassGrant.getCoPis().size());
        assertTrue(updatePassGrant.getCoPis().contains(user0));//Melon
        assertTrue(updatePassGrant.getCoPis().contains(user2));//Jones
    }

    @Test
    @Order(2)
    public void processInitGrantIT_DoesNotUpdateWithNoChange() throws IOException, IllegalAccessException {
        // GIVEN
        GrantIngestRecord piRecord0 = makeGrantIngestRecord(0, 0, "P");
        GrantIngestRecord coPiRecord0 = makeGrantIngestRecord(0, 1, "C");
        GrantIngestRecord piRecord1 = makeGrantIngestRecord(1, 0, "P");
        GrantIngestRecord coPiRecord1 = makeGrantIngestRecord(1, 1, "C");
        GrantIngestRecord newCoPiRecord1 = makeGrantIngestRecord(1, 2, "C");
        GrantIngestRecord piRecord2 = makeGrantIngestRecord(2, 1, "P");

        //in the initial pull, we will find all of the records (check?)
        List<GrantIngestRecord> resultSet = new ArrayList<>();
        resultSet.add(piRecord0);
        resultSet.add(coPiRecord0);
        resultSet.add(piRecord1);
        resultSet.add(coPiRecord1);
        resultSet.add(newCoPiRecord1);
        resultSet.add(piRecord2);

        PassClient spyPassClient = Mockito.spy(passClient);
        Properties policyProperties = TestUtil.loaderPolicyProperties();
        JhuPassInitUpdater passUpdater = new JhuPassInitUpdater(policyProperties);
        FieldUtils.writeField(passUpdater, "passClient", spyPassClient, true);

        // WHEN
        passUpdater.updatePass(resultSet, "grant");

        // THEN
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", grantIdPrefix + grantLocalKey[2]));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);

        User user0 = getVerifiedUser(0);
        User user1 = getVerifiedUser(1);
        User user2 = getVerifiedUser(2);

        Mockito.verify(spyPassClient, Mockito.times(0)).updateObject(ArgumentMatchers.any());
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
     * utility method to produce data as it would look coming from COEUS
     *
     * @param iteration the iteration of the (multi-award) grant
     * @param user      the user supplied in the record
     * @param abbrRole  the role: Pi ("P") or co-pi (C" or "K")
     * @return the row map for the record
     */
    private GrantIngestRecord makeGrantIngestRecord(int iteration, int user, String abbrRole) throws IOException {
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

        grantIngestRecord.setUpdateTimeStamp(grantUpdateTimestamp[iteration]);
        grantIngestRecord.setPiRole(abbrRole);

        return grantIngestRecord;
    }

}