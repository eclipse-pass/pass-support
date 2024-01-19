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

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.csv.CSVRecord;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Getter
@Setter
public class GrantIngestRecord {
    private String grantNumber;
    private String grantTitle;
    private String awardNumber;
    private String awardStatus;
    private String awardDate;
    private String awardStart;
    private String awardEnd;
    private String primaryFunderName;
    private String primaryFunderCode;
    private String primaryFunderPolicyId;
    private String directFunderName;
    private String directFunderCode;
    private String directFunderPolicyId;
    private String piFirstName;
    private String piMiddleName;
    private String piLastName;
    private String piEmail;
    private String piInstitutionalId;
    private String piEmployeeId;
    private String piRole;
    private String updateTimeStamp;

    public static GrantIngestRecord parse(CSVRecord csvRecord) {
        GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
        grantIngestRecord.setGrantNumber(csvRecord.get(GrantIngestCsvHeaders.GRANT_NUMBER));
        grantIngestRecord.setGrantTitle(csvRecord.get(GrantIngestCsvHeaders.GRANT_TITLE));
        grantIngestRecord.setAwardNumber(csvRecord.get(GrantIngestCsvHeaders.AWARD_NUMBER));
        grantIngestRecord.setAwardStatus(csvRecord.get(GrantIngestCsvHeaders.AWARD_STATUS));
        grantIngestRecord.setAwardDate(csvRecord.get(GrantIngestCsvHeaders.AWARD_DATE));
        grantIngestRecord.setAwardStart(csvRecord.get(GrantIngestCsvHeaders.AWARD_START));
        grantIngestRecord.setAwardEnd(csvRecord.get(GrantIngestCsvHeaders.AWARD_END));
        grantIngestRecord.setPrimaryFunderName(csvRecord.get(GrantIngestCsvHeaders.PRIMARY_FUNDER_NAME));
        grantIngestRecord.setPrimaryFunderCode(csvRecord.get(GrantIngestCsvHeaders.PRIMARY_FUNDER_CODE));
        grantIngestRecord.setDirectFunderName(csvRecord.get(GrantIngestCsvHeaders.DIRECT_FUNDER_NAME));
        grantIngestRecord.setDirectFunderCode(csvRecord.get(GrantIngestCsvHeaders.DIRECT_FUNDER_CODE));
        grantIngestRecord.setPiFirstName(csvRecord.get(GrantIngestCsvHeaders.PI_FIRST_NAME));
        grantIngestRecord.setPiMiddleName(csvRecord.get(GrantIngestCsvHeaders.PI_MIDDLE_NAME));
        grantIngestRecord.setPiLastName(csvRecord.get(GrantIngestCsvHeaders.PI_LAST_NAME));
        grantIngestRecord.setPiEmail(csvRecord.get(GrantIngestCsvHeaders.PI_EMAIL));
        grantIngestRecord.setPiInstitutionalId(csvRecord.get(GrantIngestCsvHeaders.PI_INSTITUTIONAL_ID));
        grantIngestRecord.setPiEmployeeId(csvRecord.get(GrantIngestCsvHeaders.PI_EMPLOYEE_ID));
        grantIngestRecord.setPiRole(csvRecord.get(GrantIngestCsvHeaders.PI_ROLE));
        grantIngestRecord.setUpdateTimeStamp(csvRecord.get(GrantIngestCsvHeaders.UPDATE_TIMESTAMP));
        return grantIngestRecord;
    }

}
