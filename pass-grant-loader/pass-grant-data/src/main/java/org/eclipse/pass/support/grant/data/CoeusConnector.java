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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
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

    private static final String SELECT_GRANT_SQL =
        "SELECT " +
        "A." + CoeusFieldNames.C_GRANT_AWARD_NUMBER + ", " +
        "A." + CoeusFieldNames.C_GRANT_AWARD_STATUS + ", " +
        "A." + CoeusFieldNames.C_GRANT_LOCAL_KEY + ", " +
        "A." + CoeusFieldNames.C_GRANT_PROJECT_NAME + ", " +
        "A." + CoeusFieldNames.C_GRANT_AWARD_DATE + ", " +
        "A." + CoeusFieldNames.C_GRANT_START_DATE + ", " +
        "A." + CoeusFieldNames.C_GRANT_END_DATE + ", " +
        "A." + CoeusFieldNames.C_DIRECT_FUNDER_NAME + ", " +
        "A." + CoeusFieldNames.C_DIRECT_FUNDER_LOCAL_KEY + ", " + //"SPOSNOR_CODE"
        "A." + CoeusFieldNames.C_UPDATE_TIMESTAMP + ", " +
        "B." + CoeusFieldNames.C_ABBREVIATED_ROLE + ", " +
        "B." + CoeusFieldNames.C_USER_EMPLOYEE_ID + ", " +
        "C." + CoeusFieldNames.C_USER_FIRST_NAME + ", " +
        "C." + CoeusFieldNames.C_USER_MIDDLE_NAME + ", " +
        "C." + CoeusFieldNames.C_USER_LAST_NAME + ", " +
        "C." + CoeusFieldNames.C_USER_EMAIL + ", " +
        "C." + CoeusFieldNames.C_USER_INSTITUTIONAL_ID + ", " +
        "D." + CoeusFieldNames.C_PRIMARY_FUNDER_NAME + ", " +
        "D." + CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY + " " +
        "FROM " +
        "COEUS.JHU_FACULTY_FORCE_PROP A " +
        "INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN B ON A.INST_PROPOSAL = B.INST_PROPOSAL " +
        "INNER JOIN COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL C ON B.EMPLOYEE_ID = C.EMPLOYEE_ID " +
        "LEFT JOIN COEUS.SWIFT_SPONSOR D ON A.PRIME_SPONSOR_CODE = D.SPONSOR_CODE " +
        "WHERE A.UPDATE_TIMESTAMP > ? " +
        "AND TO_DATE(A.AWARD_END, 'MM/DD/YYYY') >= TO_DATE(?, 'MM/DD/YYYY') " +
        "AND A.PROPOSAL_STATUS = 'Funded' " +
        "AND (B.ABBREVIATED_ROLE = 'P' OR B.ABBREVIATED_ROLE = 'C' " +
            "OR REGEXP_LIKE (UPPER(B.ROLE), '^CO ?-?INVESTIGATOR$')) ";

    private static final String SELECT_USER_SQL =
        "SELECT " +
            CoeusFieldNames.C_USER_FIRST_NAME + ", " +
            CoeusFieldNames.C_USER_MIDDLE_NAME + ", " +
            CoeusFieldNames.C_USER_LAST_NAME + ", " +
            CoeusFieldNames.C_USER_EMAIL + ", " +
            CoeusFieldNames.C_USER_INSTITUTIONAL_ID + ", " +
            CoeusFieldNames.C_USER_EMPLOYEE_ID + ", " +
            CoeusFieldNames.C_UPDATE_TIMESTAMP + " " +
            "FROM COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL " +
            "WHERE UPDATE_TIMESTAMP > ?";

    private static final String SELECT_FUNDER_SQL =
        "SELECT " +
            CoeusFieldNames.C_PRIMARY_FUNDER_NAME + ", " +
            CoeusFieldNames.C_PRIMARY_FUNDER_LOCAL_KEY + " " +
            "FROM COEUS.SWIFT_SPONSOR " +
            "WHERE SPONSOR_CODE IN (%s)";


    private String coeusUrl;
    private String coeusUser;
    private String coeusPassword;

    private final Properties funderPolicyProperties;

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
        }

        this.funderPolicyProperties = funderPolicyProperties;

    }

    public List<Map<String, String>> retrieveUpdates(String startDate, String awardEndDate, String mode, String grant)
        throws SQLException {
        if (mode.equals("user")) {
            return retrieveUserUpdates(startDate);
        } else if (mode.equals("funder")) {
            return retrieveFunderUpdates();
        } else {
            return retrieveGrantUpdates(startDate, awardEndDate, grant);
        }
    }

    private List<Map<String, String>> retrieveGrantUpdates(String startDate, String awardEndDate, String grant)
        throws SQLException {

        String sql = buildGrantQueryString(grant);
        List<Map<String, String>> mapList = new ArrayList<>();

        try (
            Connection con = DriverManager.getConnection(coeusUrl, coeusUser, coeusPassword);
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            LocalDateTime startLd = LocalDateTime.from(DateTimeUtil.DATE_TIME_FORMATTER.parse(startDate));
            ps.setTimestamp(1, Timestamp.valueOf(startLd));
            ps.setString(2, awardEndDate);
            if (StringUtils.isNotEmpty(grant)) {
                ps.setString(3, grant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> rowMap = new HashMap<>();
                    rowMap.put(CoeusFieldNames.C_GRANT_AWARD_NUMBER,
                        ModelUtil.normalizeAwardNumber(rs.getString(CoeusFieldNames.C_GRANT_AWARD_NUMBER)));
                    rowMap.put(CoeusFieldNames.C_GRANT_AWARD_STATUS,
                        rs.getString(CoeusFieldNames.C_GRANT_AWARD_STATUS));
                    rowMap.put(CoeusFieldNames.C_GRANT_LOCAL_KEY, rs.getString(CoeusFieldNames.C_GRANT_LOCAL_KEY));
                    rowMap.put(CoeusFieldNames.C_GRANT_PROJECT_NAME,
                        rs.getString(CoeusFieldNames.C_GRANT_PROJECT_NAME));
                    rowMap.put(CoeusFieldNames.C_GRANT_AWARD_DATE, rs.getString(CoeusFieldNames.C_GRANT_AWARD_DATE));
                    rowMap.put(CoeusFieldNames.C_GRANT_START_DATE, rs.getString(CoeusFieldNames.C_GRANT_START_DATE));
                    rowMap.put(CoeusFieldNames.C_GRANT_END_DATE, rs.getString(CoeusFieldNames.C_GRANT_END_DATE));
                    rowMap.put(CoeusFieldNames.C_DIRECT_FUNDER_NAME,
                        rs.getString(CoeusFieldNames.C_DIRECT_FUNDER_NAME));
                    rowMap.put(CoeusFieldNames.C_PRIMARY_FUNDER_NAME,
                        rs.getString(CoeusFieldNames.C_PRIMARY_FUNDER_NAME));
                    rowMap.put(CoeusFieldNames.C_USER_FIRST_NAME, rs.getString(CoeusFieldNames.C_USER_FIRST_NAME));
                    rowMap.put(CoeusFieldNames.C_USER_MIDDLE_NAME, rs.getString(CoeusFieldNames.C_USER_MIDDLE_NAME));
                    rowMap.put(CoeusFieldNames.C_USER_LAST_NAME, rs.getString(CoeusFieldNames.C_USER_LAST_NAME));
                    rowMap.put(CoeusFieldNames.C_USER_EMAIL, rs.getString(CoeusFieldNames.C_USER_EMAIL));
                    rowMap.put(CoeusFieldNames.C_USER_EMPLOYEE_ID, rs.getString(CoeusFieldNames.C_USER_EMPLOYEE_ID));
                    rowMap.put(CoeusFieldNames.C_USER_INSTITUTIONAL_ID,
                        rs.getString(CoeusFieldNames.C_USER_INSTITUTIONAL_ID));
                    rowMap.put(CoeusFieldNames.C_UPDATE_TIMESTAMP, rs.getString(CoeusFieldNames.C_UPDATE_TIMESTAMP));
                    rowMap.put(CoeusFieldNames.C_ABBREVIATED_ROLE, rs.getString(CoeusFieldNames.C_ABBREVIATED_ROLE));
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
        }
        LOG.info("Retrieved result set from COEUS: {} records processed", mapList.size());
        return mapList;
    }

    private String buildGrantQueryString(String grant) {
        return StringUtils.isEmpty(grant)
            ? SELECT_GRANT_SQL + "AND A.GRANT_NUMBER IS NOT NULL"
            : SELECT_GRANT_SQL + "AND A.GRANT_NUMBER = ?";
    }

    private List<Map<String, String>> retrieveFunderUpdates() throws SQLException {
        List<Map<String, String>> mapList = new ArrayList<>();
        String funderSql = String.format(SELECT_FUNDER_SQL,
            funderPolicyProperties.stringPropertyNames().stream()
                .map(v -> "?")
                .collect(Collectors.joining(", ")));
        try (
            Connection con = DriverManager.getConnection(coeusUrl, coeusUser, coeusPassword);
            PreparedStatement ps = con.prepareStatement(funderSql);
        ) {
            int index = 1;
            for ( String funderKey : funderPolicyProperties.stringPropertyNames() ) {
                ps.setString(index++, funderKey);
            }
            try (ResultSet rs = ps.executeQuery()) {
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
        }
        return mapList;
    }

    private List<Map<String, String>> retrieveUserUpdates(String startDate) throws SQLException {
        List<Map<String, String>> mapList = new ArrayList<>();
        try (
            Connection con = DriverManager.getConnection(coeusUrl, coeusUser, coeusPassword);
            PreparedStatement ps = con.prepareStatement(SELECT_USER_SQL);
        ) {
            LocalDateTime startLd = LocalDateTime.from(DateTimeUtil.DATE_TIME_FORMATTER.parse(startDate));
            ps.setTimestamp(1, Timestamp.valueOf(startLd));
            try (ResultSet rs = ps.executeQuery()) {
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
                    LOG.debug("Record processed: {}", rowMap);
                    if (!mapList.contains(rowMap)) {
                        mapList.add(rowMap);
                    }
                }
            }
        }
        LOG.info("Retrieved Users result set from COEUS: {} records processed", mapList.size());
        return mapList;
    }

}
