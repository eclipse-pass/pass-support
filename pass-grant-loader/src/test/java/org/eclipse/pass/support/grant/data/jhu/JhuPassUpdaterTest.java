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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.grant.GrantLoaderCLIRunner;
import org.eclipse.pass.support.grant.data.DateTimeUtil;
import org.eclipse.pass.support.grant.data.GrantConnector;
import org.eclipse.pass.support.grant.data.GrantDataException;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Test class for building the {@code List} of {@code Grant}s
 *
 * @author jrm@jhu.edu
 */
@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class JhuPassUpdaterTest {

    @MockBean private PassClient passClientMock;
    @MockBean private GrantConnector grantConnector;
    @MockBean private GrantLoaderCLIRunner grantLoaderCLIRunner;
    @Autowired private JhuPassUpdater jhuPassUpdater;
    @Autowired @Qualifier("policyProperties") private Properties policyProperties;

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdatePassGrant_Success_NewGrant() throws IOException, IllegalAccessException, GrantDataException {

        List<GrantIngestRecord> resultSet = buildTestInputResultSet();
        preparePassClientMockCallsGrantRelations();
        PassClientResult<PassEntity> mockGrantResult = new PassClientResult<>(Collections.emptyList(), 0);
        Mockito.doReturn(mockGrantResult)
                .when(passClientMock)
                .selectObjects(
                        ArgumentMatchers.argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(
                                        "localKey=='johnshopkins.edu:grant:8675309'")));

        jhuPassUpdater.updatePass(resultSet, "grant");

        Map<String, Grant> grantMap = jhuPassUpdater.getGrantResultMap();
        assertEquals(1, grantMap.size());
        Grant grant = grantMap.get("8675309");
        assertEquals(1, grant.getCoPis().size());
        Map<String, Funder> funderMap = (Map<String, Funder>) FieldUtils.readField(jhuPassUpdater, "funderMap", true);
        assertEquals(2, funderMap.size());
        assertEquals(grant.getDirectFunder(), funderMap.get("000029282"));
        assertEquals(grant.getPrimaryFunder(), funderMap.get("8675309"));
        Map<String, User> userMap = (Map<String, User>) FieldUtils.readField(jhuPassUpdater, "userMap", true);
        assertEquals(grant.getPi(), userMap.get("0000333ARECKON3"));
        assertEquals(grant.getCoPis().get(0), userMap.get("0000222MLARTZ5"));

        assertEquals("12345678", grant.getAwardNumber());
        assertEquals(AwardStatus.ACTIVE, grant.getAwardStatus());
        assertEquals("johnshopkins.edu:grant:8675309", grant.getLocalKey());
        Assertions.assertEquals(DateTimeUtil.createZonedDateTime("01/01/2000"), grant.getAwardDate());
        assertEquals(DateTimeUtil.createZonedDateTime("2001-01-01"), grant.getStartDate());
        assertEquals(DateTimeUtil.createZonedDateTime("2002-01-01"), grant.getEndDate());
        assertEquals("2018-01-01 00:00:00.0", jhuPassUpdater.getLatestUpdate());//latest
        assertEquals("Moo Project", grant.getProjectName());

        assertEquals("johnshopkins.edu:grant:8675309", grant.getLocalKey());
    }

    @Test
    public void testUpdatePassGrant_Success_SkipDuplicateGrantInPass() throws IOException {

        List<GrantIngestRecord> resultSet = buildTestInputResultSet();
        preparePassClientMockCallsGrantRelations();
        Grant grant1 = new Grant("8675309");
        Grant grant2 = new Grant("8675309");
        PassClientResult<PassEntity> mockGrantResult = new PassClientResult<>(List.of(grant1, grant2), 2);
        Mockito.doReturn(mockGrantResult)
                .when(passClientMock)
                .selectObjects(
                        ArgumentMatchers.argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(
                                        "localKey=='johnshopkins.edu:grant:8675309'")));

        jhuPassUpdater.updatePass(resultSet, "grant");

        Map<String, Grant> grantMap = jhuPassUpdater.getGrantResultMap();
        assertEquals(0, grantMap.size()); // no update to grant since pass returns duplicate
    }

    private List<GrantIngestRecord> buildTestInputResultSet() {
        List<GrantIngestRecord> resultSet = new ArrayList<>();

        String awardNumber = "12345678";
        String awardStatus = "Active";
        String localKey = "8675309";
        String projectName = "Moo Project";
        String awardDate = "01/01/2000";
        String startDate = "01/01/2001";
        String endDate = "01/01/2002";
        String directFunderId = "000029282";
        String directFunderName = "JHU Department of Synergy";
        String primaryFunderId = "8675309";
        String primaryFunderName = "J. L. Gotrocks Foundation";

        GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
        grantIngestRecord.setAwardNumber(awardNumber);
        grantIngestRecord.setAwardStatus(awardStatus);
        grantIngestRecord.setGrantNumber(localKey);
        grantIngestRecord.setGrantTitle(projectName);
        grantIngestRecord.setAwardDate(awardDate);
        grantIngestRecord.setAwardStart(startDate);
        grantIngestRecord.setAwardEnd(endDate);

        grantIngestRecord.setDirectFunderCode(directFunderId);
        grantIngestRecord.setDirectFunderName(directFunderName);
        grantIngestRecord.setPrimaryFunderCode(primaryFunderId);
        grantIngestRecord.setPrimaryFunderName(primaryFunderName);

        grantIngestRecord.setPiFirstName("Amanda");
        grantIngestRecord.setPiMiddleName("Beatrice");
        grantIngestRecord.setPiLastName("Reckondwith");
        grantIngestRecord.setPiEmail("areckon3@jhu.edu");
        grantIngestRecord.setPiInstitutionalId("ARECKON3");
        grantIngestRecord.setPiEmployeeId("0000333");

        grantIngestRecord.setUpdateTimeStamp("2018-01-01 00:00:00.0");
        grantIngestRecord.setPiRole("P");

        resultSet.add(grantIngestRecord);

        GrantIngestRecord grantIngestRecord2 = new GrantIngestRecord();
        grantIngestRecord2.setAwardNumber(awardNumber);
        grantIngestRecord2.setAwardStatus(awardStatus);
        grantIngestRecord2.setGrantNumber(localKey);
        grantIngestRecord2.setGrantTitle(projectName);
        grantIngestRecord2.setAwardDate(awardDate);
        grantIngestRecord2.setAwardStart(startDate);
        grantIngestRecord2.setAwardEnd(endDate);

        grantIngestRecord2.setDirectFunderCode(directFunderId);
        grantIngestRecord2.setDirectFunderName(directFunderName);
        grantIngestRecord2.setPrimaryFunderCode(primaryFunderId);
        grantIngestRecord2.setPrimaryFunderName(primaryFunderName);

        grantIngestRecord2.setPiFirstName("Marsha");
        grantIngestRecord2.setPiMiddleName(null);
        grantIngestRecord2.setPiLastName("Lartz");
        grantIngestRecord2.setPiEmail("alartz3@jhu.edu");
        grantIngestRecord2.setPiInstitutionalId("MLARTZ5");
        grantIngestRecord2.setPiEmployeeId("0000222");

        grantIngestRecord2.setUpdateTimeStamp("2018-01-01 00:00:00.0");
        grantIngestRecord2.setPiRole("C");

        resultSet.add(grantIngestRecord2);
        return resultSet;
    }

    private void preparePassClientMockCallsGrantRelations() throws IOException {
        Funder directFunder = new Funder("000029282");
        directFunder.setLocalKey("johnshopkins.edu:funder:000029282");
        PassClientResult<PassEntity> mockFunderResult1 = new PassClientResult<>(List.of(directFunder), 1);
        Mockito.doReturn(mockFunderResult1)
                .when(passClientMock)
                .selectObjects(ArgumentMatchers.argThat(passClientSelector ->
                        passClientSelector.getFilter().equals("localKey=='johnshopkins.edu:funder:000029282'")));

        Funder primaryFunder = new Funder("8675309");
        directFunder.setLocalKey("johnshopkins.edu:funder:8675309");
        PassClientResult<PassEntity> mockFunderResult2 = new PassClientResult<>(List.of(primaryFunder), 1);
        Mockito.doReturn(mockFunderResult2)
                .when(passClientMock)
                .selectObjects(
                        ArgumentMatchers.argThat(passClientSelector2 ->
                                passClientSelector2.getFilter().equals("localKey=='johnshopkins.edu:funder:8675309'")));

        User user1 = new User("0000333");
        PassClientResult<PassEntity> mockUserResult3 = new PassClientResult<>(List.of(user1), 1);
        Mockito.doReturn(mockUserResult3)
                .when(passClientMock)
                .selectObjects(
                        ArgumentMatchers.argThat(passClientSelector3 ->
                                passClientSelector3.getFilter().equals(
                                        "locatorIds=hasmember='johnshopkins.edu:employeeid:0000333'")));

        User user2 = new User("0000222");
        PassClientResult<PassEntity> mockUserResult4 = new PassClientResult<>(List.of(user2), 1);
        Mockito.doReturn(mockUserResult4)
                .when(passClientMock)
                .selectObjects(
                        ArgumentMatchers.argThat(passClientSelector4 ->
                                passClientSelector4.getFilter().equals(
                                        "locatorIds=hasmember='johnshopkins.edu:employeeid:0000222'")));
    }

    @Test
    public void testUserBuilding() {
        GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
        grantIngestRecord.setPiFirstName("Marsha");
        grantIngestRecord.setPiMiddleName(null);
        grantIngestRecord.setPiLastName("Lartz");
        grantIngestRecord.setPiEmail("mlartz3@jhu.edu");
        grantIngestRecord.setPiInstitutionalId("MLARTZ5");
        grantIngestRecord.setPiEmployeeId("0000222");
        grantIngestRecord.setUpdateTimeStamp("2018-01-01 0:00:00.0");

        User newUser = jhuPassUpdater.buildUser(grantIngestRecord);

        //unusual fields
        assertEquals("Marsha Lartz", newUser.getDisplayName());
        //test ids
        assertEquals("johnshopkins.edu:employeeid:0000222", newUser.getLocatorIds().get(0));
        assertEquals("johnshopkins.edu:eppn:mlartz5", newUser.getLocatorIds().get(1));
    }

    @Test
    public void testPrimaryFunderBuilding() {
        GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
        grantIngestRecord.setPrimaryFunderName( "Funder Name");
        grantIngestRecord.setPrimaryFunderCode("8675309");
        policyProperties.put("8675309", "policy1");

        Funder newFunder = jhuPassUpdater.buildPrimaryFunder(grantIngestRecord);

        assertEquals("Funder Name", newFunder.getName());
        assertEquals("8675309", newFunder.getLocalKey());
        assertEquals("policy1", newFunder.getPolicy().getId());
    }

    @Test
    public void testUpdatePassUser_Fail_ModeCheck() {
        assertThrows(RuntimeException.class, () -> {
            List<GrantIngestRecord> grantResultSet = new ArrayList<>();
            GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
            grantIngestRecord.setGrantNumber("fake-grant-number");
            grantResultSet.add(grantIngestRecord);

            jhuPassUpdater.updatePass(grantResultSet, "user");
        });
    }

    @Test
    public void testUpdatePassGrant_Fail_ModeCheck() {
        assertThrows(RuntimeException.class, () -> {
            List<GrantIngestRecord> userResultSet = new ArrayList<>();
            GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
            grantIngestRecord.setPiEmployeeId("fake-employee-id");
            userResultSet.add(grantIngestRecord);

            jhuPassUpdater.updatePass(userResultSet, "grant");
        });
    }

    @Test
    public void testUpdatePassFunder_Fail_ModeCheck() {
        assertThrows(RuntimeException.class, () -> {
            List<GrantIngestRecord> userResultSet = new ArrayList<>();
            GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
            grantIngestRecord.setPiEmployeeId("fake-employee-id");
            userResultSet.add(grantIngestRecord);

            jhuPassUpdater.updatePass(userResultSet, "funder");
        });
    }

}
