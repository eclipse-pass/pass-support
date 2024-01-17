package org.eclipse.pass.support.grant.data;

import org.apache.commons.csv.CSVRecord;

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

    public String getGrantNumber() {
        return grantNumber;
    }

    public void setGrantNumber(String grantNumber) {
        this.grantNumber = grantNumber;
    }

    public String getGrantTitle() {
        return grantTitle;
    }

    public void setGrantTitle(String grantTitle) {
        this.grantTitle = grantTitle;
    }

    public String getAwardNumber() {
        return awardNumber;
    }

    public void setAwardNumber(String awardNumber) {
        this.awardNumber = awardNumber;
    }

    public String getAwardStatus() {
        return awardStatus;
    }

    public void setAwardStatus(String awardStatus) {
        this.awardStatus = awardStatus;
    }

    public String getAwardDate() {
        return awardDate;
    }

    public void setAwardDate(String awardDate) {
        this.awardDate = awardDate;
    }

    public String getAwardStart() {
        return awardStart;
    }

    public void setAwardStart(String awardStart) {
        this.awardStart = awardStart;
    }

    public String getAwardEnd() {
        return awardEnd;
    }

    public void setAwardEnd(String awardEnd) {
        this.awardEnd = awardEnd;
    }

    public String getPrimaryFunderName() {
        return primaryFunderName;
    }

    public void setPrimaryFunderName(String primaryFunderName) {
        this.primaryFunderName = primaryFunderName;
    }

    public String getPrimaryFunderCode() {
        return primaryFunderCode;
    }

    public void setPrimaryFunderCode(String primaryFunderCode) {
        this.primaryFunderCode = primaryFunderCode;
    }

    public String getPrimaryFunderPolicyId() {
        return primaryFunderPolicyId;
    }

    public void setPrimaryFunderPolicyId(String primaryFunderPolicyId) {
        this.primaryFunderPolicyId = primaryFunderPolicyId;
    }

    public String getDirectFunderName() {
        return directFunderName;
    }

    public void setDirectFunderName(String directFunderName) {
        this.directFunderName = directFunderName;
    }

    public String getDirectFunderCode() {
        return directFunderCode;
    }

    public void setDirectFunderCode(String directFunderCode) {
        this.directFunderCode = directFunderCode;
    }

    public String getDirectFunderPolicyId() {
        return directFunderPolicyId;
    }

    public void setDirectFunderPolicyId(String directFunderPolicyId) {
        this.directFunderPolicyId = directFunderPolicyId;
    }

    public String getPiFirstName() {
        return piFirstName;
    }

    public void setPiFirstName(String piFirstName) {
        this.piFirstName = piFirstName;
    }

    public String getPiMiddleName() {
        return piMiddleName;
    }

    public void setPiMiddleName(String piMiddleName) {
        this.piMiddleName = piMiddleName;
    }

    public String getPiLastName() {
        return piLastName;
    }

    public void setPiLastName(String piLastName) {
        this.piLastName = piLastName;
    }

    public String getPiEmail() {
        return piEmail;
    }

    public void setPiEmail(String piEmail) {
        this.piEmail = piEmail;
    }

    public String getPiInstitutionalId() {
        return piInstitutionalId;
    }

    public void setPiInstitutionalId(String piInstitutionalId) {
        this.piInstitutionalId = piInstitutionalId;
    }

    public String getPiEmployeeId() {
        return piEmployeeId;
    }

    public void setPiEmployeeId(String piEmployeeId) {
        this.piEmployeeId = piEmployeeId;
    }

    public String getPiRole() {
        return piRole;
    }

    public void setPiRole(String piRole) {
        this.piRole = piRole;
    }

    public String getUpdateTimeStamp() {
        return updateTimeStamp;
    }

    public void setUpdateTimeStamp(String updateTimeStamp) {
        this.updateTimeStamp = updateTimeStamp;
    }
}
