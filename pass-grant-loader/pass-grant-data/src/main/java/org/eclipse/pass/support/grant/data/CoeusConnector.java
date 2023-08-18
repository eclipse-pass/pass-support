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

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.pass.support.client.ModelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class connects to a COEUS database via the Oracle JDBC driver. The query string reflects local JHU
 * database views
 *
 * @author jrm@jhu.edu
 */
public class CoeusConnector implements GrantConnector {
    private static final Logger LOG = LoggerFactory.getLogger(CoeusConnector.class);
    //property names
    private static final String COEUS_URL = "coeus.url";
    private static final String COEUS_USER = "coeus.user";
    private static final String COEUS_PASS = "coeus.pass";

    private String coeusUrl;
    private String coeusUser;
    private String coeusPassword;

    private final Properties funderPolicyProperties;

    private DirectoryServiceUtil directoryServiceUtil;

    /**
     * Class constructor.
     * @param connectionProperties the connection props
     * @param funderPolicyProperties the funder policy props
     */
    public CoeusConnector(Properties connectionProperties, Properties funderPolicyProperties) {
        if (connectionProperties != null) {

            if (connectionProperties.getProperty(COEUS_URL) != null) {
                this.coeusUrl = connectionProperties.getProperty(COEUS_URL);
            }
            if (connectionProperties.getProperty(COEUS_USER) != null) {
                this.coeusUser = connectionProperties.getProperty(COEUS_USER);
            }
            if (connectionProperties.getProperty(COEUS_PASS) != null) {
                this.coeusPassword = connectionProperties.getProperty(COEUS_PASS);
            }
            this.directoryServiceUtil = new DirectoryServiceUtil(connectionProperties);
        }

        this.funderPolicyProperties = funderPolicyProperties;

    }

    public List<Map<String, String>> retrieveUpdates(String queryString, String mode)
        throws ClassNotFoundException, SQLException, IOException {
        if (mode.equals("user")) {
            return retrieveUserUpdates(queryString);
        } else if (mode.equals("funder")) {
            return retrieveFunderUpdates(queryString);
        } else {
            return retrieveGrantUpdates(queryString);
        }
    }

    /**
     * This method returns a {@code ResultSet} for a query for a specific set of fields in several views in COEUS.
     *
     * @param queryString the query string to the COEUS database needed to update the information
     * @return the {@code ResultSet} from the query
     */
    private List<Map<String, String>> retrieveGrantUpdates(String queryString)
        throws ClassNotFoundException, SQLException, IOException {

        List<Map<String, String>> mapList = new ArrayList<>();

        Class.forName("oracle.jdbc.driver.OracleDriver");

        try (
            Connection con = DriverManager.getConnection(coeusUrl, coeusUser, coeusPassword);
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(queryString)
        ) {
            while (rs.next()) {
                Map<String, String> rowMap = new HashMap<>();

                rowMap.put(CoeusFieldNames.C_GRANT_AWARD_NUMBER,
                        ModelUtil.normalizeAwardNumber(rs.getString(CoeusFieldNames.C_GRANT_AWARD_NUMBER)));
                rowMap.put(CoeusFieldNames.C_GRANT_AWARD_STATUS, rs.getString(CoeusFieldNames.C_GRANT_AWARD_STATUS));
                rowMap.put(CoeusFieldNames.C_GRANT_LOCAL_KEY, rs.getString(CoeusFieldNames.C_GRANT_LOCAL_KEY));
                rowMap.put(CoeusFieldNames.C_GRANT_PROJECT_NAME, rs.getString(CoeusFieldNames.C_GRANT_PROJECT_NAME));
                rowMap.put(CoeusFieldNames.C_GRANT_AWARD_DATE, rs.getString(CoeusFieldNames.C_GRANT_AWARD_DATE));
                rowMap.put(CoeusFieldNames.C_GRANT_START_DATE, rs.getString(CoeusFieldNames.C_GRANT_START_DATE));
                rowMap.put(CoeusFieldNames.C_GRANT_END_DATE, rs.getString(CoeusFieldNames.C_GRANT_END_DATE));

                rowMap.put(CoeusFieldNames.C_DIRECT_FUNDER_NAME, rs.getString(CoeusFieldNames.C_DIRECT_FUNDER_NAME));

                rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_NAME, rs.getString(CoeusFieldNames.C_PRIMARY_FUNDER_NAME));
                rowMap.put(CoeusFieldNames.C_USER_FIRST_NAME, rs.getString(CoeusFieldNames.C_USER_FIRST_NAME));
                rowMap.put(CoeusFieldNames.C_USER_MIDDLE_NAME, rs.getString(CoeusFieldNames.C_USER_MIDDLE_NAME));
                rowMap.put(CoeusFieldNames.C_USER_LAST_NAME, rs.getString(CoeusFieldNames.C_USER_LAST_NAME));
                rowMap.put(CoeusFieldNames.C_USER_EMAIL, rs.getString(CoeusFieldNames.C_USER_EMAIL));
                rowMap.put(CoeusFieldNames.C_USER_EMPLOYEE_ID, rs.getString(CoeusFieldNames.C_USER_EMPLOYEE_ID));
                rowMap.put(CoeusFieldNames.C_USER_INSTITUTIONAL_ID,
                        rs.getString(CoeusFieldNames.C_USER_INSTITUTIONAL_ID));
                rowMap.put(CoeusFieldNames.C_UPDATE_TIMESTAMP, rs.getString(CoeusFieldNames.C_UPDATE_TIMESTAMP));
                rowMap.put(CoeusFieldNames.C_ABBREVIATED_ROLE, rs.getString(CoeusFieldNames.C_ABBREVIATED_ROLE));

                String employeeId = rs.getString(CoeusFieldNames.C_USER_EMPLOYEE_ID);
                if (employeeId != null) {
                    rowMap.put(CoeusFieldNames.C_USER_HOPKINS_ID,
                            directoryServiceUtil.getHopkinsIdForEmployeeId(employeeId));
                }

                String primaryFunderLocalKey = rs.getString(CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY);
                rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY, primaryFunderLocalKey);
                if (primaryFunderLocalKey != null &&
                    funderPolicyProperties.stringPropertyNames().contains(primaryFunderLocalKey)) {
                    rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_POLICY,
                            funderPolicyProperties.getProperty(primaryFunderLocalKey));
                }

                String directFunderLocalKey = rs.getString(CoeusFieldNames.C_DIRECT_FUNDER_LOCAL_KEY);
                rowMap.put(CoeusFieldNames.C_DIRECT_FUNDER_LOCAL_KEY, directFunderLocalKey);
                if (directFunderLocalKey != null &&
                    funderPolicyProperties.stringPropertyNames().contains(directFunderLocalKey)) {
                    rowMap.put(CoeusFieldNames.C_DIRECT_FUNDER_POLICY,
                            funderPolicyProperties.getProperty(directFunderLocalKey));
                }
                LOG.debug("Record processed: {}", rowMap);
                if (!mapList.contains(rowMap)) {
                    mapList.add(rowMap);
                }
            }
        }
        LOG.info("Retrieved result set from COEUS: {} records processed", mapList.size());
        return mapList;
    }

    private List<Map<String, String>> retrieveFunderUpdates(String queryString)
        throws ClassNotFoundException, SQLException {

        List<Map<String, String>> mapList = new ArrayList<>();

        if (queryString != null) { //we will go to COEUS for the info

            Class.forName("oracle.jdbc.driver.OracleDriver");

            try (
                Connection con = DriverManager.getConnection(coeusUrl, coeusUser, coeusPassword);
                Statement stmt = con.createStatement();
                ResultSet rs = stmt.executeQuery(queryString)
            ) {
                while (rs.next()) { //these are the field names in the swift sponsor view
                    Map<String, String> rowMap = new HashMap<>();
                    rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY,
                            rs.getString(CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY));
                    rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_NAME,
                            rs.getString(CoeusFieldNames.C_PRIMARY_FUNDER_NAME));
                    rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_POLICY,
                               funderPolicyProperties.getProperty(
                                       rs.getString(CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY)));
                    mapList.add(rowMap);
                }

            }

        } else { //we will prepare partial Funder from the properties file

            for (Object localKey : funderPolicyProperties.keySet()) {
                Map<String, String> rowMap = new HashMap<>();
                rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY, localKey.toString());
                rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_POLICY,
                        funderPolicyProperties.getProperty(localKey.toString()));
                mapList.add(rowMap);
            }
        }

        return mapList;
    }

    private List<Map<String, String>> retrieveUserUpdates(String queryString)
        throws ClassNotFoundException, SQLException {

        List<Map<String, String>> mapList = new ArrayList<>();

        Class.forName("oracle.jdbc.driver.OracleDriver");

        try (
            Connection con = DriverManager.getConnection(coeusUrl, coeusUser, coeusPassword);
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(queryString)
        ) {
            while (rs.next()) {
                Map<String, String> rowMap = new HashMap<>();
                rowMap.put(CoeusFieldNames.C_USER_FIRST_NAME, rs.getString(CoeusFieldNames.C_USER_FIRST_NAME));
                rowMap.put(CoeusFieldNames.C_USER_MIDDLE_NAME, rs.getString(CoeusFieldNames.C_USER_MIDDLE_NAME));
                rowMap.put(CoeusFieldNames.C_USER_LAST_NAME, rs.getString(CoeusFieldNames.C_USER_LAST_NAME));
                rowMap.put(CoeusFieldNames.C_USER_EMAIL, rs.getString(CoeusFieldNames.C_USER_EMAIL));
                rowMap.put(CoeusFieldNames.C_USER_INSTITUTIONAL_ID,
                        rs.getString(CoeusFieldNames.C_USER_INSTITUTIONAL_ID));
                rowMap.put(CoeusFieldNames.C_USER_EMPLOYEE_ID, rs.getString(CoeusFieldNames.C_USER_EMPLOYEE_ID));
                rowMap.put(CoeusFieldNames.C_UPDATE_TIMESTAMP, rs.getString(CoeusFieldNames.C_UPDATE_TIMESTAMP));
                String employeeId = rs.getString(CoeusFieldNames.C_USER_EMPLOYEE_ID);
                if (employeeId != null) {
                    rowMap.put(CoeusFieldNames.C_USER_HOPKINS_ID,
                            directoryServiceUtil.getHopkinsIdForEmployeeId(employeeId));
                }
                LOG.debug("Record processed: {}", rowMap);
                if (!mapList.contains(rowMap)) {
                    mapList.add(rowMap);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.info("Retrieved result set from COEUS: {} records processed", mapList.size());
        return mapList;
    }

    public String buildQueryString(String startDate, String awardEndDate, String mode, String grant) {
        if (mode.equals("user")) {
            return buildUserQueryString(startDate);
        } else if (mode.equals("funder")) {
            return buildFunderQueryString();
        } else {
            return buildGrantQueryString(startDate, awardEndDate, grant);
        }
    }

    /**
     * Method for building the query string against the COEUS database. We draw from four views.
     * Dates are stored in the views as strings, except for the UPDATE_TIMESTAMP, which is a timestamp.
     * We will pull all records which have been updated since the last update timestamp - this value becomes out
     * startDate.
     *
     * Because we are only interested in the latest update for any grant number, we restrict the search to the latest
     * update timestamp for grants, even if these correspond to different institutional proposal numbers. This is
     * because we
     * only need the granularity of grant number for the purposes of publication submission.
     *
     * NB: the join of the PROP view with the PRSN view will result in one row in the ResultSet for each investigator
     * on the grant. if there are co-pis in addition to a pi, there will be multiple rows.
     *
     * COEUS.JHU_FACULTY_FORCE_PROP aliased to A
     * COEUS.JHU_FACULTY_FORCE_PRSN aliased to B
     * COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL aliased to C
     * COEUS.SWIFT_SPONSOR aliased to D
     *
     * @param startDate - the date we want to start the query against UPDATE_TIMESTAMP
     * @return the SQL query string
     */
    private String buildGrantQueryString(String startDate, String awardEndDate, String grant) {

        String[] viewFields = {
            "A." + CoeusFieldNames.C_GRANT_AWARD_NUMBER,
            "A." + CoeusFieldNames.C_GRANT_AWARD_STATUS,
            "A." + CoeusFieldNames.C_GRANT_LOCAL_KEY,
            "A." + CoeusFieldNames.C_GRANT_PROJECT_NAME,
            "A." + CoeusFieldNames.C_GRANT_AWARD_DATE,
            "A." + CoeusFieldNames.C_GRANT_START_DATE,
            "A." + CoeusFieldNames.C_GRANT_END_DATE,
            "A." + CoeusFieldNames.C_DIRECT_FUNDER_NAME,
            "A." + CoeusFieldNames.C_DIRECT_FUNDER_LOCAL_KEY, //"SPOSNOR_CODE"
            "A." + CoeusFieldNames.C_UPDATE_TIMESTAMP,

            "B." + CoeusFieldNames.C_ABBREVIATED_ROLE,
            "B." + CoeusFieldNames.C_USER_EMPLOYEE_ID,

            "C." + CoeusFieldNames.C_USER_FIRST_NAME,
            "C." + CoeusFieldNames.C_USER_MIDDLE_NAME,
            "C." + CoeusFieldNames.C_USER_LAST_NAME,
            "C." + CoeusFieldNames.C_USER_EMAIL,
            "C." + CoeusFieldNames.C_USER_INSTITUTIONAL_ID,

            "D." + CoeusFieldNames.C_PRIMARY_FUNDER_NAME,
            "D." + CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY};

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        sb.append(String.join(", ", viewFields));
        sb.append(" FROM");
        sb.append(" COEUS.JHU_FACULTY_FORCE_PROP A");
        // sb.append(" INNER JOIN ");
        // sb.append(" (SELECT GRANT_NUMBER, MAX(UPDATE_TIMESTAMP) AS MAX_UPDATE_TIMESTAMP");
        // sb.append(" FROM COEUS.JHU_FACULTY_FORCE_PROP GROUP BY GRANT_NUMBER) LATEST");
        // sb.append(" ON A.UPDATE_TIMESTAMP = LATEST.MAX_UPDATE_TIMESTAMP");
        // sb.append(" AND A.GRANT_NUMBER = LATEST.GRANT_NUMBER");
        sb.append(" INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN B ON A.INST_PROPOSAL = B.INST_PROPOSAL");
        sb.append(" INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL C ON B.EMPLOYEE_ID = C.EMPLOYEE_ID");
        sb.append(" LEFT JOIN COEUS.SWIFT_SPONSOR D ON A.PRIME_SPONSOR_CODE = D.SPONSOR_CODE");
        sb.append(" WHERE A.UPDATE_TIMESTAMP > TIMESTAMP '");
        sb.append(startDate);
        sb.append("' ");
        sb.append("AND TO_DATE(A.AWARD_END, 'MM/DD/YYYY') >= TO_DATE('" + awardEndDate + "', 'MM/DD/YYYY') ");
        sb.append("AND A.PROPOSAL_STATUS = 'Funded' ");
        sb.append(
            "AND (B.ABBREVIATED_ROLE = 'P' OR B.ABBREVIATED_ROLE = 'C' OR REGEXP_LIKE (UPPER(B.ROLE), '^CO " +
            "?-?INVESTIGATOR$')) ");
        if (grant == null || grant.isEmpty()) {
            sb.append("AND A.GRANT_NUMBER IS NOT NULL");
        } else { // have a specifig grant to process
            sb.append("AND A.GRANT_NUMBER = '" + grant + "'");
        }
        String queryString = sb.toString();

        LOG.debug("Query string is: {}", queryString);
        return queryString;
    }

    private String buildUserQueryString(String startDate) {
        String[] viewFields = {
            CoeusFieldNames.C_USER_FIRST_NAME,
            CoeusFieldNames.C_USER_MIDDLE_NAME,
            CoeusFieldNames.C_USER_LAST_NAME,
            CoeusFieldNames.C_USER_EMAIL,
            CoeusFieldNames.C_USER_INSTITUTIONAL_ID,
            CoeusFieldNames.C_USER_EMPLOYEE_ID,
            CoeusFieldNames.C_UPDATE_TIMESTAMP};

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        sb.append(String.join(", ", viewFields));
        sb.append(" FROM");
        sb.append(" COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL");
        sb.append(" WHERE UPDATE_TIMESTAMP > TIMESTAMP '");
        sb.append(startDate);
        sb.append("'");

        String queryString = sb.toString();

        LOG.debug("Query string is: {}", queryString);
        return queryString;
    }

    private String buildFunderQueryString() {

        String[] viewFields = { //doesn't matter whether the funder is primary or direct - these are the column names
                                // in the SWIFT_SPONSOR view
                                CoeusFieldNames.C_PRIMARY_FUNDER_NAME,
                                CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY};

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        sb.append(String.join(", ", viewFields));
        sb.append(" FROM");
        sb.append(" COEUS.SWIFT_SPONSOR");
        sb.append(" WHERE");
        sb.append(" SPONSOR_CODE IN (");
        List<String> keyList = new ArrayList<>();
        sb.append(String.join(", ", (funderPolicyProperties.stringPropertyNames())));
        sb.append(")");
        String queryString = sb.toString();

        LOG.debug("Query string is: {} ", queryString);
        return queryString;

    }
}
