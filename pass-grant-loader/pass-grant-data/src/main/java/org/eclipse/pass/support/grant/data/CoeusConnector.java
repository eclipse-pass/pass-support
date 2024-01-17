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

    static final String C_GRANT_AWARD_NUMBER = "AWARD_ID";
    static final String C_GRANT_AWARD_STATUS = "AWARD_STATUS";
    static final String C_GRANT_LOCAL_KEY = "GRANT_NUMBER";
    static final String C_GRANT_PROJECT_NAME = "TITLE";
    static final String C_GRANT_AWARD_DATE = "AWARD_DATE";
    static final String C_GRANT_START_DATE = "AWARD_START";
    static final String C_GRANT_END_DATE = "AWARD_END";

    static final String C_DIRECT_FUNDER_LOCAL_KEY = "SPOSNOR_CODE";// misspelling in COEUS view - if this gets
    // corrected
    //it will collide with C_PRIMARY_SPONSOR_CODE below - this field will then have to be aliased in order to
    //access it in the ResultSet
    static final String C_DIRECT_FUNDER_NAME = "SPONSOR";
    static final String C_PRIMARY_FUNDER_LOCAL_KEY = "SPONSOR_CODE";
    static final String C_PRIMARY_FUNDER_NAME = "SPONSOR_NAME";

    static final String C_USER_FIRST_NAME = "FIRST_NAME";
    static final String C_USER_MIDDLE_NAME = "MIDDLE_NAME";
    static final String C_USER_LAST_NAME = "LAST_NAME";
    static final String C_USER_EMAIL = "EMAIL_ADDRESS";
    static final String C_USER_INSTITUTIONAL_ID = "JHED_ID";
    static final String C_USER_EMPLOYEE_ID = "EMPLOYEE_ID";
    //static final String C_USER_AFFILIATION = "";
    //static final String C_USER_ORCID_ID = "";

    //these fields are accessed for processing, but are not mapped to PASS objects
    static final String C_UPDATE_TIMESTAMP = "UPDATE_TIMESTAMP";
    static final String C_ABBREVIATED_ROLE = "ABBREVIATED_ROLE";

    //this is not a COEUS field, but is a place in our row map to put a hopkins id if it exists
    static final String C_USER_HOPKINS_ID = "HOPKINS_ID";
    //also not a field name, but something provided in a properties file
    static final String C_PRIMARY_FUNDER_POLICY = "PRIMARY_FUNDER_POLICY";
    static final String C_DIRECT_FUNDER_POLICY = "DIRECT_FUNDER_POLICY";

    private static final String SELECT_GRANT_SQL =
        "SELECT " +
        "A." + C_GRANT_AWARD_NUMBER + ", " +
        "A." + C_GRANT_AWARD_STATUS + ", " +
        "A." + C_GRANT_LOCAL_KEY + ", " +
        "A." + C_GRANT_PROJECT_NAME + ", " +
        "A." + C_GRANT_AWARD_DATE + ", " +
        "A." + C_GRANT_START_DATE + ", " +
        "A." + C_GRANT_END_DATE + ", " +
        "A." + C_DIRECT_FUNDER_NAME + ", " +
        "A." + C_DIRECT_FUNDER_LOCAL_KEY + ", " + //"SPOSNOR_CODE"
        "A." + C_UPDATE_TIMESTAMP + ", " +
        "B." + C_ABBREVIATED_ROLE + ", " +
        "B." + C_USER_EMPLOYEE_ID + ", " +
        "C." + C_USER_FIRST_NAME + ", " +
        "C." + C_USER_MIDDLE_NAME + ", " +
        "C." + C_USER_LAST_NAME + ", " +
        "C." + C_USER_EMAIL + ", " +
        "C." + C_USER_INSTITUTIONAL_ID + ", " +
        "D." + C_PRIMARY_FUNDER_NAME + ", " +
        "D." + C_PRIMARY_FUNDER_LOCAL_KEY + " " +
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
            C_USER_FIRST_NAME + ", " +
            C_USER_MIDDLE_NAME + ", " +
            C_USER_LAST_NAME + ", " +
            C_USER_EMAIL + ", " +
            C_USER_INSTITUTIONAL_ID + ", " +
            C_USER_EMPLOYEE_ID + ", " +
            C_UPDATE_TIMESTAMP + " " +
            "FROM COEUS.JHU_FACULTY_FORCE_PRSN_DETAIL " +
            "WHERE UPDATE_TIMESTAMP > ?";

    private static final String SELECT_FUNDER_SQL =
        "SELECT " +
            C_PRIMARY_FUNDER_NAME + ", " +
            C_PRIMARY_FUNDER_LOCAL_KEY + " " +
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

    public List<GrantIngestRecord> retrieveUpdates(String startDate, String awardEndDate, String mode, String grant)
        throws SQLException {
        if (mode.equals("user")) {
            return retrieveUserUpdates(startDate);
        } else if (mode.equals("funder")) {
            return retrieveFunderUpdates();
        } else {
            return retrieveGrantUpdates(startDate, awardEndDate, grant);
        }
    }

    private List<GrantIngestRecord> retrieveGrantUpdates(String startDate, String awardEndDate, String grant)
        throws SQLException {

        String sql = buildGrantQueryString(grant);
        List<GrantIngestRecord> grantIngestRecords = new ArrayList<>();

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
                    GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
                    grantIngestRecord.setAwardNumber(
                        ModelUtil.normalizeAwardNumber(rs.getString(C_GRANT_AWARD_NUMBER)));
                    grantIngestRecord.setAwardStatus(rs.getString(C_GRANT_AWARD_STATUS));
                    grantIngestRecord.setGrantNumber(rs.getString(C_GRANT_LOCAL_KEY));
                    grantIngestRecord.setGrantTitle(rs.getString(C_GRANT_PROJECT_NAME));
                    grantIngestRecord.setAwardDate(rs.getString(C_GRANT_AWARD_DATE));
                    grantIngestRecord.setAwardStart(rs.getString(C_GRANT_START_DATE));
                    grantIngestRecord.setAwardEnd(rs.getString(C_GRANT_END_DATE));
                    grantIngestRecord.setDirectFunderName(rs.getString(C_DIRECT_FUNDER_NAME));
                    grantIngestRecord.setPrimaryFunderName(rs.getString(C_PRIMARY_FUNDER_NAME));
                    grantIngestRecord.setPiFirstName(rs.getString(C_USER_FIRST_NAME));
                    grantIngestRecord.setPiMiddleName(rs.getString(C_USER_MIDDLE_NAME));
                    grantIngestRecord.setPiLastName(rs.getString(C_USER_LAST_NAME));
                    grantIngestRecord.setPiEmail(rs.getString(C_USER_EMAIL));
                    grantIngestRecord.setPiEmployeeId(rs.getString(C_USER_EMPLOYEE_ID));
                    grantIngestRecord.setPiInstitutionalId(rs.getString(C_USER_INSTITUTIONAL_ID));
                    grantIngestRecord.setUpdateTimeStamp(rs.getString(C_UPDATE_TIMESTAMP));
                    grantIngestRecord.setPiRole(rs.getString(C_ABBREVIATED_ROLE));
                    String primaryFunderLocalKey = rs.getString(C_PRIMARY_FUNDER_LOCAL_KEY);
                    grantIngestRecord.setPrimaryFunderCode(primaryFunderLocalKey);
                    if (primaryFunderLocalKey != null &&
                        funderPolicyProperties.stringPropertyNames().contains(primaryFunderLocalKey)) {
                        grantIngestRecord.setPrimaryFunderPolicyId(
                            funderPolicyProperties.getProperty(primaryFunderLocalKey));
                    }
                    String directFunderLocalKey = rs.getString(C_DIRECT_FUNDER_LOCAL_KEY);
                    grantIngestRecord.setDirectFunderCode(directFunderLocalKey);
                    if (directFunderLocalKey != null &&
                        funderPolicyProperties.stringPropertyNames().contains(directFunderLocalKey)) {
                        grantIngestRecord.setDirectFunderPolicyId(
                            funderPolicyProperties.getProperty(directFunderLocalKey));
                    }
                    LOG.debug("Record processed: {}", grantIngestRecord);
                    // TODO is this needed?  So what if there is a duplicate, logic would work it out i think
                    grantIngestRecords.add(grantIngestRecord);
//                    if (!mapList.contains(rowMap)) {
//                        mapList.add(rowMap);
//                    }
                }
            }
        }
        LOG.info("Retrieved result set from COEUS: {} records processed", grantIngestRecords.size());
        return grantIngestRecords;
    }

    private String buildGrantQueryString(String grant) {
        return StringUtils.isEmpty(grant)
            ? SELECT_GRANT_SQL + "AND A.GRANT_NUMBER IS NOT NULL"
            : SELECT_GRANT_SQL + "AND A.GRANT_NUMBER = ?";
    }

    private List<GrantIngestRecord> retrieveFunderUpdates() throws SQLException {
        List<GrantIngestRecord> grantIngestRecords = new ArrayList<>();
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
                    GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
                    grantIngestRecord.setPrimaryFunderCode(rs.getString(C_PRIMARY_FUNDER_LOCAL_KEY));
                    grantIngestRecord.setPrimaryFunderName(rs.getString(C_PRIMARY_FUNDER_NAME));
                    grantIngestRecord.setPrimaryFunderPolicyId(
                        funderPolicyProperties.getProperty(rs.getString(C_PRIMARY_FUNDER_LOCAL_KEY)));
                    // TODO is this needed?  So what if there is a duplicate, logic would work it out i think
                    grantIngestRecords.add(grantIngestRecord);
                }
            }
        }
        return grantIngestRecords;
    }

    private List<GrantIngestRecord> retrieveUserUpdates(String startDate) throws SQLException {
        List<GrantIngestRecord> grantIngestRecords = new ArrayList<>();
        try (
            Connection con = DriverManager.getConnection(coeusUrl, coeusUser, coeusPassword);
            PreparedStatement ps = con.prepareStatement(SELECT_USER_SQL);
        ) {
            LocalDateTime startLd = LocalDateTime.from(DateTimeUtil.DATE_TIME_FORMATTER.parse(startDate));
            ps.setTimestamp(1, Timestamp.valueOf(startLd));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
                    grantIngestRecord.setPiFirstName(rs.getString(C_USER_FIRST_NAME));
                    grantIngestRecord.setPiMiddleName(rs.getString(C_USER_MIDDLE_NAME));
                    grantIngestRecord.setPiLastName(rs.getString(C_USER_LAST_NAME));
                    grantIngestRecord.setPiEmail(rs.getString(C_USER_EMAIL));
                    grantIngestRecord.setPiInstitutionalId(rs.getString(C_USER_INSTITUTIONAL_ID));
                    grantIngestRecord.setPiEmployeeId(rs.getString(C_USER_EMPLOYEE_ID));
                    grantIngestRecord.setUpdateTimeStamp(rs.getString(C_UPDATE_TIMESTAMP));
                    LOG.debug("Record processed: {}", grantIngestRecord);
                    grantIngestRecords.add(grantIngestRecord);
                }
            }
        }
        LOG.info("Retrieved Users result set from COEUS: {} records processed", grantIngestRecords.size());
        return grantIngestRecords;
    }

}
