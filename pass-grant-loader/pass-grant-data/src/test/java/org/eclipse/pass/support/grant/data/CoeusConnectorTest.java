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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for the COEUS connector
 *
 * @author jrm@jhu.edu
 */
public class CoeusConnectorTest {

    private CoeusConnector connector;

    private File policyPropertiesFile = new File(
        getClass().getClassLoader().getResource("policy.properties").getFile());

    private Properties policyProperties = new Properties();

    @BeforeEach
    public void setup() throws Exception {

        try (InputStream resourceStream = new FileInputStream(policyPropertiesFile)) {
            policyProperties.load(resourceStream);
        }
        connector = new CoeusConnector(
            null, policyProperties);
    }


    /**
     * Test that the query string produced is as expected
     */
    @Test
    public void testBuildGrantString() {

        String expectedQueryString = "SELECT A.AWARD_ID, A.AWARD_STATUS, A.GRANT_NUMBER, A.TITLE, A.AWARD_DATE," +
                                     " A.AWARD_START, A.AWARD_END, A.SPONSOR, A.SPOSNOR_CODE, A.UPDATE_TIMESTAMP, B" +
                                     ".ABBREVIATED_ROLE, B.EMPLOYEE_ID," +
                                     " C.FIRST_NAME, C.MIDDLE_NAME, C.LAST_NAME, C.EMAIL_ADDRESS, C.JHED_ID, D" +
                                     ".SPONSOR_NAME, D.SPONSOR_CODE" +
                                     " FROM" +
                                     " COEUS.JHU_FACULTY_FORCE_PROP A" +
                                     " INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN B" +
                                     " ON A.INST_PROPOSAL = B.INST_PROPOSAL" +
                                     " INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL C" +
                                     " ON B.EMPLOYEE_ID = C.EMPLOYEE_ID" +
                                     " LEFT JOIN COEUS.SWIFT_SPONSOR D" +
                                     " ON A.PRIME_SPONSOR_CODE = D.SPONSOR_CODE" +
                                     " WHERE A.UPDATE_TIMESTAMP > TIMESTAMP '2018-06-01 06:00:00.0'" +
                                     " AND TO_DATE(A.AWARD_END, 'MM/DD/YYYY') >= TO_DATE('01/01/2011', 'MM/DD/YYYY')" +
                                     " AND A.PROPOSAL_STATUS = 'Funded'" +
                                     " AND (B.ABBREVIATED_ROLE = 'P' OR B.ABBREVIATED_ROLE = 'C' OR REGEXP_LIKE " +
                                     "(UPPER(B.ROLE), '^CO ?-?INVESTIGATOR$'))" +
                                     " AND A.GRANT_NUMBER IS NOT NULL";

        assertEquals(expectedQueryString,
                            connector.buildQueryString("2018-06-01 06:00:00.0", "01/01/2011", "grant", null));

        expectedQueryString = "SELECT A.AWARD_ID, A.AWARD_STATUS, A.GRANT_NUMBER, A.TITLE, A.AWARD_DATE," +
                              " A.AWARD_START, A.AWARD_END, A.SPONSOR, A.SPOSNOR_CODE, A.UPDATE_TIMESTAMP, B" +
                              ".ABBREVIATED_ROLE, B.EMPLOYEE_ID," +
                              " C.FIRST_NAME, C.MIDDLE_NAME, C.LAST_NAME, C.EMAIL_ADDRESS, C.JHED_ID, D.SPONSOR_NAME," +
                              " D.SPONSOR_CODE" +
                              " FROM" +
                              " COEUS.JHU_FACULTY_FORCE_PROP A" +
                              " INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN B" +
                              " ON A.INST_PROPOSAL = B.INST_PROPOSAL" +
                              " INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL C" +
                              " ON B.EMPLOYEE_ID = C.EMPLOYEE_ID" +
                              " LEFT JOIN COEUS.SWIFT_SPONSOR D" +
                              " ON A.PRIME_SPONSOR_CODE = D.SPONSOR_CODE" +
                              " WHERE A.UPDATE_TIMESTAMP > TIMESTAMP '2018-06-01 06:00:00.0'" +
                              " AND TO_DATE(A.AWARD_END, 'MM/DD/YYYY') >= TO_DATE('02/03/1999', 'MM/DD/YYYY')" +
                              " AND A.PROPOSAL_STATUS = 'Funded'" +
                              " AND (B.ABBREVIATED_ROLE = 'P' OR B.ABBREVIATED_ROLE = 'C' OR REGEXP_LIKE (UPPER(B" +
                              ".ROLE), '^CO ?-?INVESTIGATOR$'))" +
                              " AND A.GRANT_NUMBER IS NOT NULL";

        assertEquals(expectedQueryString,
                            connector.buildQueryString("2018-06-01 06:00:00.0", "02/03/1999", "grant", null));

        expectedQueryString = "SELECT A.AWARD_ID, A.AWARD_STATUS, A.GRANT_NUMBER, A.TITLE, A.AWARD_DATE," +
                              " A.AWARD_START, A.AWARD_END, A.SPONSOR, A.SPOSNOR_CODE, A.UPDATE_TIMESTAMP, B" +
                              ".ABBREVIATED_ROLE, B.EMPLOYEE_ID," +
                              " C.FIRST_NAME, C.MIDDLE_NAME, C.LAST_NAME, C.EMAIL_ADDRESS, C.JHED_ID, D.SPONSOR_NAME," +
                              " D.SPONSOR_CODE" +
                              " FROM" +
                              " COEUS.JHU_FACULTY_FORCE_PROP A" +
                              " INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN B" +
                              " ON A.INST_PROPOSAL = B.INST_PROPOSAL" +
                              " INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL C" +
                              " ON B.EMPLOYEE_ID = C.EMPLOYEE_ID" +
                              " LEFT JOIN COEUS.SWIFT_SPONSOR D" +
                              " ON A.PRIME_SPONSOR_CODE = D.SPONSOR_CODE" +
                              " WHERE A.UPDATE_TIMESTAMP > TIMESTAMP '2018-06-01 06:00:00.0'" +
                              " AND TO_DATE(A.AWARD_END, 'MM/DD/YYYY') >= TO_DATE('02/03/1999', 'MM/DD/YYYY')" +
                              " AND A.PROPOSAL_STATUS = 'Funded'" +
                              " AND (B.ABBREVIATED_ROLE = 'P' OR B.ABBREVIATED_ROLE = 'C' OR REGEXP_LIKE (UPPER(B" +
                              ".ROLE), '^CO ?-?INVESTIGATOR$'))" +
                              " AND A.GRANT_NUMBER = '12345678'";

        assertEquals(expectedQueryString,
                            connector.buildQueryString("2018-06-01 06:00:00.0", "02/03/1999", "grant", "12345678"));
    }

    @Test
    public void testBuildUserQueryString() {

        String expectedQueryString = "SELECT FIRST_NAME, MIDDLE_NAME, LAST_NAME, EMAIL_ADDRESS, JHED_ID, EMPLOYEE_ID," +
                                     " " +
                                     "UPDATE_TIMESTAMP FROM COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL " +
                                     "WHERE UPDATE_TIMESTAMP > TIMESTAMP '2018-13-14 06:00:00.0'";
        assertEquals(expectedQueryString,
                            connector.buildQueryString("2018-13-14 06:00:00.0", "01/01/2011", "user", null));

    }

    @Test
    public void testBuildFunderQueryString() {

        String expectedQueryString1 =
            "SELECT SPONSOR_NAME, SPONSOR_CODE FROM COEUS.SWIFT_SPONSOR WHERE SPONSOR_CODE IN (moo, baa)";
        String expectedQueryString2 =
            "SELECT SPONSOR_NAME, SPONSOR_CODE FROM COEUS.SWIFT_SPONSOR WHERE SPONSOR_CODE IN (baa, moo)";
        String actualQueryString = connector.buildQueryString(null, null, "funder", null);
        assertTrue(expectedQueryString1.equals(actualQueryString) ||
                          expectedQueryString2.equals(actualQueryString));
    }

}


