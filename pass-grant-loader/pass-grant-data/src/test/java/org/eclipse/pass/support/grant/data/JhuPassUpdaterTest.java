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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for building the {@code List} of {@code Grant}s
 *
 * @author jrm@jhu.edu
 */
@ExtendWith(MockitoExtension.class)
public class JhuPassUpdaterTest {

    @Mock
    private PassClient passClientMock;

    @BeforeEach
    public void beforeTest() {
        System.setProperty("pass.core.url", "http://test-host");
        System.setProperty("pass.core.user", "test-user");
        System.setProperty("pass.core.password", "test-password");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdatePassGrant_Success_NewGrant() throws IOException, IllegalAccessException {

        List<Map<String, String>> resultSet = buildTestInputResultSet();
        preparePassClientMockCallsGrantRelations();
        PassClientResult<PassEntity> mockGrantResult = new PassClientResult<>(Collections.emptyList(), 0);
        doReturn(mockGrantResult)
                .when(passClientMock)
                .selectObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(
                                        "localKey=='johnshopkins.edu:grant:8675309'")));

        JhuPassUpdater passUpdater = new JhuPassUpdater();
        FieldUtils.writeField(passUpdater, "passClient", passClientMock, true);
        passUpdater.updatePass(resultSet, "grant");

        Map<String, Grant> grantMap = passUpdater.getGrantResultMap();
        assertEquals(1, grantMap.size());
        Grant grant = grantMap.get("8675309");
        assertEquals(1, grant.getCoPis().size());
        Map<String, Funder> funderMap = (Map<String, Funder>) FieldUtils.readField(passUpdater, "funderMap", true);
        assertEquals(2, funderMap.size());
        assertEquals(grant.getDirectFunder(), funderMap.get("000029282"));
        assertEquals(grant.getPrimaryFunder(), funderMap.get("8675309"));
        Map<String, User> userMap = (Map<String, User>) FieldUtils.readField(passUpdater, "userMap", true);
        assertEquals(grant.getPi(), userMap.get("0000333"));
        assertEquals(grant.getCoPis().get(0), userMap.get("0000222"));

        assertEquals("12345678", grant.getAwardNumber());
        assertEquals(AwardStatus.ACTIVE, grant.getAwardStatus());
        assertEquals("johnshopkins.edu:grant:8675309", grant.getLocalKey());
        assertEquals(DateTimeUtil.createZonedDateTime("01/01/2000"), grant.getAwardDate());
        assertEquals(DateTimeUtil.createZonedDateTime("01/01/2001"), grant.getStartDate());
        assertEquals(DateTimeUtil.createZonedDateTime("01/01/2002"), grant.getEndDate());
        assertEquals("2018-01-01 00:00:00.0", passUpdater.getLatestUpdate());//latest
        assertEquals("Moo Project", grant.getProjectName());

        assertEquals("johnshopkins.edu:grant:8675309", grant.getLocalKey());
    }

    @Test
    public void testUpdatePassGrant_Success_SkipDuplicateGrantInPass() throws IOException, IllegalAccessException {

        List<Map<String, String>> resultSet = buildTestInputResultSet();
        preparePassClientMockCallsGrantRelations();
        Grant grant1 = new Grant("8675309");
        Grant grant2 = new Grant("8675309");
        PassClientResult<PassEntity> mockGrantResult = new PassClientResult<>(List.of(grant1, grant2), 2);
        doReturn(mockGrantResult)
                .when(passClientMock)
                .selectObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(
                                        "localKey=='johnshopkins.edu:grant:8675309'")));

        JhuPassUpdater passUpdater = new JhuPassUpdater();
        FieldUtils.writeField(passUpdater, "passClient", passClientMock, true);
        passUpdater.updatePass(resultSet, "grant");

        Map<String, Grant> grantMap = passUpdater.getGrantResultMap();
        assertEquals(0, grantMap.size()); // no update to grant since pass returns duplicate
    }

    private List<Map<String, String>> buildTestInputResultSet() {
        List<Map<String, String>> resultSet = new ArrayList<>();

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
        String primaryFunderPolicy = "policy1";
        String directFunderPolicy = "policy2";

        Map<String, String> rowMap = new HashMap<>();
        rowMap.put(CoeusFieldNames.C_GRANT_AWARD_NUMBER, awardNumber);
        rowMap.put(CoeusFieldNames.C_GRANT_AWARD_STATUS, awardStatus);
        rowMap.put(CoeusFieldNames.C_GRANT_LOCAL_KEY, localKey);
        rowMap.put(CoeusFieldNames.C_GRANT_PROJECT_NAME, projectName);
        rowMap.put(CoeusFieldNames.C_GRANT_AWARD_DATE, awardDate);
        rowMap.put(CoeusFieldNames.C_GRANT_START_DATE, startDate);
        rowMap.put(CoeusFieldNames.C_GRANT_END_DATE, endDate);

        rowMap.put(CoeusFieldNames.C_DIRECT_FUNDER_LOCAL_KEY, directFunderId);
        rowMap.put(CoeusFieldNames.C_DIRECT_FUNDER_NAME, directFunderName);
        rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY, primaryFunderId);
        rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_NAME, primaryFunderName);

        rowMap.put(CoeusFieldNames.C_USER_FIRST_NAME, "Amanda");
        rowMap.put(CoeusFieldNames.C_USER_MIDDLE_NAME, "Beatrice");
        rowMap.put(CoeusFieldNames.C_USER_LAST_NAME, "Reckondwith");
        rowMap.put(CoeusFieldNames.C_USER_EMAIL, "areckon3@jhu.edu");
        rowMap.put(CoeusFieldNames.C_USER_INSTITUTIONAL_ID, "ARECKON3");
        rowMap.put(CoeusFieldNames.C_USER_EMPLOYEE_ID, "0000333");
        rowMap.put(CoeusFieldNames.C_USER_HOPKINS_ID, "B2B2B2");

        rowMap.put(CoeusFieldNames.C_UPDATE_TIMESTAMP, "2018-01-01 00:00:00.0");
        rowMap.put(CoeusFieldNames.C_ABBREVIATED_ROLE, "P");

        resultSet.add(rowMap);

        rowMap = new HashMap<>();
        rowMap.put(CoeusFieldNames.C_GRANT_AWARD_NUMBER, awardNumber);
        rowMap.put(CoeusFieldNames.C_GRANT_AWARD_STATUS, awardStatus);
        rowMap.put(CoeusFieldNames.C_GRANT_LOCAL_KEY, localKey);
        rowMap.put(CoeusFieldNames.C_GRANT_PROJECT_NAME, projectName);
        rowMap.put(CoeusFieldNames.C_GRANT_AWARD_DATE, awardDate);
        rowMap.put(CoeusFieldNames.C_GRANT_START_DATE, startDate);
        rowMap.put(CoeusFieldNames.C_GRANT_END_DATE, endDate);

        rowMap.put(CoeusFieldNames.C_DIRECT_FUNDER_LOCAL_KEY, directFunderId);
        rowMap.put(CoeusFieldNames.C_DIRECT_FUNDER_NAME, directFunderName);
        rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY, primaryFunderId);
        rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_NAME, primaryFunderName);

        rowMap.put(CoeusFieldNames.C_USER_FIRST_NAME, "Marsha");
        rowMap.put(CoeusFieldNames.C_USER_MIDDLE_NAME, null);
        rowMap.put(CoeusFieldNames.C_USER_LAST_NAME, "Lartz");
        rowMap.put(CoeusFieldNames.C_USER_EMAIL, "alartz3@jhu.edu");
        rowMap.put(CoeusFieldNames.C_USER_INSTITUTIONAL_ID, "MLARTZ5");
        rowMap.put(CoeusFieldNames.C_USER_EMPLOYEE_ID, "0000222");
        rowMap.put(CoeusFieldNames.C_USER_HOPKINS_ID, "A1A1A1");

        rowMap.put(CoeusFieldNames.C_UPDATE_TIMESTAMP, "2018-01-01 00:00:00.0");
        rowMap.put(CoeusFieldNames.C_ABBREVIATED_ROLE, "C");

        rowMap.put(CoeusFieldNames.C_DIRECT_FUNDER_POLICY, primaryFunderPolicy);
        rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_POLICY, directFunderPolicy);

        resultSet.add(rowMap);
        return resultSet;
    }

    private void preparePassClientMockCallsGrantRelations() throws IOException {
        Funder directFunder = new Funder("000029282");
        directFunder.setLocalKey("johnshopkins.edu:funder:000029282");
        PassClientResult<PassEntity> mockFunderResult1 = new PassClientResult<>(List.of(directFunder), 1);
        doReturn(mockFunderResult1)
                .when(passClientMock)
                .selectObjects(argThat(passClientSelector ->
                        passClientSelector.getFilter().equals("localKey=='johnshopkins.edu:funder:000029282'")));

        Funder primaryFunder = new Funder("8675309");
        directFunder.setLocalKey("johnshopkins.edu:funder:8675309");
        PassClientResult<PassEntity> mockFunderResult2 = new PassClientResult<>(List.of(primaryFunder), 1);
        doReturn(mockFunderResult2)
                .when(passClientMock)
                .selectObjects(
                        argThat(passClientSelector2 ->
                                passClientSelector2.getFilter().equals("localKey=='johnshopkins.edu:funder:8675309'")));

        User user1 = new User("0000333");
        PassClientResult<PassEntity> mockUserResult3 = new PassClientResult<>(List.of(user1), 1);
        doReturn(mockUserResult3)
                .when(passClientMock)
                .selectObjects(
                        argThat(passClientSelector3 ->
                                passClientSelector3.getFilter().equals(
                                        "locatorIds=hasmember='johnshopkins.edu:employeeid:0000333'")));

        User user2 = new User("0000222");
        PassClientResult<PassEntity> mockUserResult4 = new PassClientResult<>(List.of(user2), 1);
        doReturn(mockUserResult4)
                .when(passClientMock)
                .selectObjects(
                        argThat(passClientSelector4 ->
                                passClientSelector4.getFilter().equals(
                                        "locatorIds=hasmember='johnshopkins.edu:employeeid:0000222'")));
    }

    @Test
    public void testUserBuilding() {

        Map<String, String> rowMap = new HashMap<>();
        rowMap.put(CoeusFieldNames.C_USER_FIRST_NAME, "Marsha");
        rowMap.put(CoeusFieldNames.C_USER_MIDDLE_NAME, null);
        rowMap.put(CoeusFieldNames.C_USER_LAST_NAME, "Lartz");
        rowMap.put(CoeusFieldNames.C_USER_EMAIL, "mlartz3@jhu.edu");
        rowMap.put(CoeusFieldNames.C_USER_INSTITUTIONAL_ID, "MLARTZ5");
        rowMap.put(CoeusFieldNames.C_USER_EMPLOYEE_ID, "0000222");
        rowMap.put(CoeusFieldNames.C_UPDATE_TIMESTAMP, "2018-01-01 0:00:00.0");

        JhuPassUpdater passUpdater = new JhuPassUpdater();
        User newUser = passUpdater.buildUser(rowMap);

        //unusual fields
        assertEquals("Marsha Lartz", newUser.getDisplayName());
        //test ids
        assertEquals("johnshopkins.edu:employeeid:0000222", newUser.getLocatorIds().get(0));
        assertEquals("johnshopkins.edu:eppn:mlartz5", newUser.getLocatorIds().get(1));
    }

    @Test
    public void testPrimaryFunderBuilding() {
        Map<String, String> rowMap = new HashMap<>();
        rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_NAME, "Funder Name");
        rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY, "8675309");
        rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_POLICY, "policy1");

        JhuPassUpdater passUpdater = new JhuPassUpdater();
        Funder newFunder = passUpdater.buildPrimaryFunder(rowMap);

        assertEquals("Funder Name", newFunder.getName());
        assertEquals("8675309", newFunder.getLocalKey());
        assertEquals("policy1", newFunder.getPolicy().getId());

    }

    @Test
    public void testUpdatePassUser_Fail_ModeCheck() {
        assertThrows(RuntimeException.class, () -> {
            List<Map<String, String>> grantResultSet = new ArrayList<>();
            Map<String, String> rowMap = new HashMap<>();
            rowMap.put(CoeusFieldNames.C_GRANT_LOCAL_KEY, CoeusFieldNames.C_GRANT_LOCAL_KEY);
            grantResultSet.add(rowMap);

            JhuPassUpdater passUpdater = new JhuPassUpdater();

            passUpdater.updatePass(grantResultSet, "user");
        });
    }

    @Test
    public void testUpdatePassGrant_Fail_ModeCheck() {
        assertThrows(RuntimeException.class, () -> {
            List<Map<String, String>> userResultSet = new ArrayList<>();
            Map<String, String> rowMap = new HashMap<>();
            rowMap.put(CoeusFieldNames.C_USER_EMPLOYEE_ID, CoeusFieldNames.C_USER_EMPLOYEE_ID);
            userResultSet.add(rowMap);

            JhuPassUpdater passUpdater = new JhuPassUpdater();

            passUpdater.updatePass(userResultSet, "grant");
        });
    }

    @Test
    public void testUpdatePassFunder_Fail_ModeCheck() {
        assertThrows(RuntimeException.class, () -> {
            List<Map<String, String>> userResultSet = new ArrayList<>();
            Map<String, String> rowMap = new HashMap<>();
            rowMap.put(CoeusFieldNames.C_USER_EMPLOYEE_ID, CoeusFieldNames.C_USER_EMPLOYEE_ID);
            userResultSet.add(rowMap);

            JhuPassUpdater passUpdater = new JhuPassUpdater();

            passUpdater.updatePass(userResultSet, "funder");
        });
    }

}
