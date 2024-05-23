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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.support.client.ModelUtil;
import org.eclipse.pass.support.grant.data.DateTimeUtil;
import org.eclipse.pass.support.grant.data.GrantConnector;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * This class connects to a JHU Grant database. The query string reflects local JHU
 * database views
 *
 * @author jrm@jhu.edu
 */
@Component
@Profile("jhu")
public class JhuGrantDbConnector implements GrantConnector {
    private static final Logger LOG = LoggerFactory.getLogger(JhuGrantDbConnector.class);

    static final String C_GRANT_AWARD_NUMBER = "AWARD_ID";
    static final String C_GRANT_AWARD_STATUS = "AWARD_STATUS";
    static final String C_GRANT_LOCAL_KEY = "SAP_GRANT_NUMBER";
    static final String C_GRANT_PROJECT_NAME = "TITLE";
    static final String C_GRANT_AWARD_DATE = "AWARD_DATE";
    static final String C_GRANT_START_DATE = "AWARD_START_DATE";
    static final String C_GRANT_END_DATE = "AWARD_END_DATE";

    static final String C_DIRECT_FUNDER_LOCAL_KEY = "SPONSOR_CODE";
    static final String C_DIRECT_FUNDER_NAME = "SPONSOR_NAME";
    static final String C_PRIMARY_FUNDER_LOCAL_KEY = "PRIME_SPONSOR_CODE";
    static final String C_PRIMARY_FUNDER_NAME = "PRIME_SPONSOR_NAME";

    static final String C_USER_FIRST_NAME = "FIRST_NAME";
    static final String C_USER_MIDDLE_NAME = "MIDDLE_NAME";
    static final String C_USER_LAST_NAME = "LAST_NAME";
    static final String C_USER_EMAIL = "EMAIL_ADDRESS";
    static final String C_USER_INSTITUTIONAL_ID = "JHED_ID";
    static final String C_USER_EMPLOYEE_ID = "EMPLOYEE_ID";

    //these fields are accessed for processing, but are not mapped to PASS objects
    static final String C_UPDATE_TIMESTAMP = "UPDATE_TIMESTAMP";
    static final String C_ABBREVIATED_ROLE = "ROLE";

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
        "A." + C_DIRECT_FUNDER_LOCAL_KEY + ", " +
        "A." + C_PRIMARY_FUNDER_NAME + ", " +
        "A." + C_PRIMARY_FUNDER_LOCAL_KEY + ", " +
        "A." + C_UPDATE_TIMESTAMP + ", " +
        "B." + C_ABBREVIATED_ROLE + ", " +
        "B." + C_USER_EMPLOYEE_ID + ", " +
        "C." + C_USER_FIRST_NAME + ", " +
        "C." + C_USER_MIDDLE_NAME + ", " +
        "C." + C_USER_LAST_NAME + ", " +
        "C." + C_USER_EMAIL + ", " +
        "C." + C_USER_INSTITUTIONAL_ID + " " +
        "FROM JHU_PASS_AWD_VIEW A, " +
        "JHU_FIBI_IP_INV_VIEW B, " +
        "JHU_PERSON_VIEW C " +
        "WHERE A.inst_proposal = B.inst_proposal " +
        "AND B.employee_id = C.employee_id " +
        "AND EXISTS (" +
        "    select * from JHU_PASS_AWD_VIEW EA where" +
        "        EA.UPDATE_TIMESTAMP > ? " +
        "        AND STR_TO_DATE(EA.AWARD_END_DATE, '%m/%d/%Y') >= STR_TO_DATE(?, '%m/%d/%Y') " +
        "        and EA.SAP_GRANT_NUMBER = A.SAP_GRANT_NUMBER ";

    private static final String SELECT_USER_SQL =
        "SELECT " +
            "A." + C_USER_FIRST_NAME + ", " +
            "A." + C_USER_MIDDLE_NAME + ", " +
            "A." + C_USER_LAST_NAME + ", " +
            "A." + C_USER_EMAIL + ", " +
            "A." + C_USER_INSTITUTIONAL_ID + ", " +
            "B." + C_USER_EMPLOYEE_ID + ", " +
            "A." + C_UPDATE_TIMESTAMP + " " +
            "FROM JHU_PERSON_VIEW A, " +
            "JHU_FIBI_IP_INV_VIEW B " +
            "WHERE A.employee_id = B.employee_id " +
            "and A.UPDATE_TIMESTAMP > ?";

    private static final String SELECT_FUNDER_SQL =
        "SELECT " +
            C_DIRECT_FUNDER_NAME + ", " +
            C_DIRECT_FUNDER_LOCAL_KEY + " " +
            "FROM JHU_SPONSOR_VIEW " +
            "WHERE SPONSOR_CODE IN (%s)";

    @Value("${grant.db.url}")
    private String grantDbUrl;

    @Value("${grant.db.username}")
    private String grantDbUser;

    @Value("${grant.db.password}")
    private String grantDbPassword;

    private final Set<String> funderIds;

    /**
     * Class constructor.
     */
    public JhuGrantDbConnector(@Qualifier("policyProperties") Properties policyProperties) {
        this.funderIds = policyProperties.stringPropertyNames();
    }

    public List<GrantIngestRecord> retrieveUpdates(String startDate, String awardEndDate, String mode, String grant)
        throws SQLException {
        if (mode.equals("user")) {
            return retrieveUserUpdates(startDate);
        } else if (mode.equals("funder")) {
            return retrieveFunderUpdates(funderIds);
        } else {
            return retrieveGrantUpdates(startDate, awardEndDate, grant);
        }
    }

    private List<GrantIngestRecord> retrieveGrantUpdates(String startDate, String awardEndDate, String grant)
        throws SQLException {

        String sql = buildGrantQueryString(grant);
        List<GrantIngestRecord> grantIngestRecords = new ArrayList<>();

        try (
            Connection con = DriverManager.getConnection(grantDbUrl, grantDbUser, grantDbPassword);
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
                    grantIngestRecord.setDirectFunderCode(rs.getString(C_DIRECT_FUNDER_LOCAL_KEY));
                    grantIngestRecord.setPrimaryFunderName(rs.getString(C_PRIMARY_FUNDER_NAME));
                    grantIngestRecord.setPrimaryFunderCode(rs.getString(C_PRIMARY_FUNDER_LOCAL_KEY));
                    grantIngestRecord.setPiFirstName(rs.getString(C_USER_FIRST_NAME));
                    grantIngestRecord.setPiMiddleName(rs.getString(C_USER_MIDDLE_NAME));
                    grantIngestRecord.setPiLastName(rs.getString(C_USER_LAST_NAME));
                    grantIngestRecord.setPiEmail(rs.getString(C_USER_EMAIL));
                    grantIngestRecord.setPiEmployeeId(rs.getString(C_USER_EMPLOYEE_ID));
                    grantIngestRecord.setPiInstitutionalId(rs.getString(C_USER_INSTITUTIONAL_ID));
                    grantIngestRecord.setUpdateTimeStamp(rs.getString(C_UPDATE_TIMESTAMP));
                    grantIngestRecord.setPiRole(rs.getString(C_ABBREVIATED_ROLE));
                    LOG.debug("Record processed: {}", grantIngestRecord);
                    if (!grantIngestRecords.contains(grantIngestRecord)) {
                        grantIngestRecords.add(grantIngestRecord);
                    }
                }
            }
        }
        LOG.info("Retrieved result set from JHU Grant DB: {} records processed", grantIngestRecords.size());
        return grantIngestRecords;
    }

    private String buildGrantQueryString(String grant) {
        return StringUtils.isEmpty(grant)
            ? SELECT_GRANT_SQL + "AND A.SAP_GRANT_NUMBER IS NOT NULL)"
            : SELECT_GRANT_SQL + "AND A.SAP_GRANT_NUMBER = ?)";
    }

    private List<GrantIngestRecord> retrieveFunderUpdates(Set<String> funderIds) throws SQLException {
        List<GrantIngestRecord> grantIngestRecords = new ArrayList<>();
        String funderSql = String.format(SELECT_FUNDER_SQL,
            funderIds.stream().map(v -> "?").collect(Collectors.joining(", ")));
        try (
            Connection con = DriverManager.getConnection(grantDbUrl, grantDbUser, grantDbPassword);
            PreparedStatement ps = con.prepareStatement(funderSql);
        ) {
            int index = 1;
            for ( String funderKey : funderIds ) {
                ps.setString(index++, funderKey);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { //these are the field names in the swift sponsor view
                    GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
                    grantIngestRecord.setPrimaryFunderCode(rs.getString(C_DIRECT_FUNDER_LOCAL_KEY));
                    grantIngestRecord.setPrimaryFunderName(rs.getString(C_DIRECT_FUNDER_NAME));
                    grantIngestRecords.add(grantIngestRecord);
                }
            }
        }
        return grantIngestRecords;
    }

    private List<GrantIngestRecord> retrieveUserUpdates(String startDate) throws SQLException {
        List<GrantIngestRecord> grantIngestRecords = new ArrayList<>();
        try (
            Connection con = DriverManager.getConnection(grantDbUrl, grantDbUser, grantDbPassword);
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
                    if (!grantIngestRecords.contains(grantIngestRecord)) {
                        grantIngestRecords.add(grantIngestRecord);
                    }
                }
            }
        }
        LOG.info("Retrieved Users result set from COEUS: {} records processed", grantIngestRecords.size());
        return grantIngestRecords;
    }

}
