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
package org.eclipse.pass.loader.nihms.model;

/**
 * Light weight model for transferring data from CSV files to processing
 *
 * @author Karen Hanson
 */
public class NihmsPublication {

    private static final String NIHMSID_PREFIX = "NIHMS";
    private static final String PMCID_PREFIX = "PMC";

    /**
     * NIHMS submission CSV field "PMID"
     */
    private String pmid;

    /**
     * NIHMS submission CSV field "Grant number"
     */
    private String grantNumber;

    /**
     * NIHMS submission CSV field "NIHMSID"
     */
    private String nihmsId;

    /**
     * NIHMS submission CSV field "PMCID"
     */
    private String pmcId;

    /**
     * Date files were deposited in NIHMS
     */
    private String fileDepositedDate;

    /**
     * Date of initial NIHMS approval
     */
    private String initialApprovalDate;

    /**
     * Date tagging of article was complete
     */
    private String taggingCompleteDate;

    /**
     * Date of final approval
     */
    private String finalApprovalDate;

    /**
     * NIHMS submission CSV field "Article Title"
     */
    private String articleTitle;

    /**
     * Status of NIHMS deposit according to spreadsheets
     */
    private NihmsStatus nihmsStatus;

    /**
     * Constructor populates the required fields for a NIHMS publication
     *
     * @param pmid
     * @param grantNumber
     * @param nihmsId
     * @param pmcId
     * @param nihmsStatus
     * @param fileDepositedDate
     * @param initialApprovalDate
     * @param taggingCompleteDate
     * @param finalApprovalDate
     * @param articleTitle
     */
    public NihmsPublication(NihmsStatus nihmsStatus, String pmid, String grantNumber, String nihmsId, String pmcId,
                            String fileDepositedDate, String initialApprovalDate, String taggingCompleteDate,
                            String finalApprovalDate, String articleTitle) {
        if (pmid == null || pmid.length() < 3) {
            throw new IllegalArgumentException(String.format("PMID \"%s\" is not valid.", pmid));
        }
        if (grantNumber == null || grantNumber.length() < 3) {
            throw new IllegalArgumentException(String.format("Grant number \"%s\" is not valid.", grantNumber));
        }
        if (nihmsStatus == null) {
            throw new IllegalArgumentException(String.format("NIHMS status cannot be null."));
        }

        this.pmid = pmid;
        this.grantNumber = grantNumber;
        this.nihmsStatus = nihmsStatus;
        if (nihmsId != null && nihmsId.length() > 0 && !nihmsId.startsWith(NIHMSID_PREFIX)) {
            nihmsId = NIHMSID_PREFIX + nihmsId;
        }
        this.nihmsId = nihmsId;
        if (pmcId != null && pmcId.length() > 0 && !pmcId.startsWith(PMCID_PREFIX)) {
            pmcId = PMCID_PREFIX + pmcId;
        }
        this.pmcId = pmcId;
        this.fileDepositedDate = fileDepositedDate;
        this.initialApprovalDate = initialApprovalDate;
        this.taggingCompleteDate = taggingCompleteDate;
        this.finalApprovalDate = finalApprovalDate;
        this.articleTitle = articleTitle;
    }

    /**
     * True returned if has tagging completion date
     *
     * @return
     */
    public boolean isTaggingComplete() {
        return taggingCompleteDate != null && taggingCompleteDate.length() > 0;
    }

    /**
     * True returned if has file deposit date
     *
     * @return
     */
    public boolean isFileDeposited() {
        return fileDepositedDate != null && fileDepositedDate.length() > 0;
    }

    /**
     * True returned if has initial approval date
     *
     * @return
     */
    public boolean hasInitialApproval() {
        return initialApprovalDate != null && initialApprovalDate.length() > 0;
    }

    /**
     * True returned if has final approval date or marked as compliant
     *
     * @return
     */
    public boolean hasFinalApproval() {
        return (nihmsStatus.equals(NihmsStatus.COMPLIANT))
               || (finalApprovalDate != null && finalApprovalDate.length() > 0);
    }

    /**
     * @return the pmid
     */
    public String getPmid() {
        return pmid;
    }

    /**
     * @param pmid the pmid to set
     */
    public void setPmid(String pmid) {
        this.pmid = pmid;
    }

    /**
     * @return the grantNumber
     */
    public String getGrantNumber() {
        return grantNumber;
    }

    /**
     * @param grantNumber the grantNumber to set
     */
    public void setGrantNumber(String grantNumber) {
        this.grantNumber = grantNumber;
    }

    /**
     * @return the nihmsStatus
     */
    public NihmsStatus getNihmsStatus() {
        return nihmsStatus;
    }

    /**
     * @param nihmsStatus the nihmsStatus to set
     */
    public void setNihmsStatus(NihmsStatus nihmsStatus) {
        this.nihmsStatus = nihmsStatus;
    }

    /**
     * @return the nihmsId
     */
    public String getNihmsId() {
        return nihmsId;
    }

    /**
     * Sets the NIHMSID, will add the NIHMS prefix if missing
     *
     * @param nihmsId the nihmsId to set
     */
    public void setNihmsId(String nihmsId) {
        if (nihmsId != null && nihmsId.length() > 0 && !nihmsId.startsWith(NIHMSID_PREFIX)) {
            nihmsId = NIHMSID_PREFIX + nihmsId;
        }
        this.nihmsId = nihmsId;
    }

    /**
     * @return the pmcId
     */
    public String getPmcId() {
        return pmcId;
    }

    /**
     * Sets the PMCID, will add the PMC prefix if missing
     *
     * @param pmcId the pmcId to set
     */
    public void setPmcId(String pmcId) {
        if (pmcId != null && pmcId.length() > 0 && !pmcId.startsWith(PMCID_PREFIX)) {
            pmcId = PMCID_PREFIX + pmcId;
        }
        this.pmcId = pmcId;
    }

    /**
     * @return the fileDepositedDate
     */
    public String getFileDepositedDate() {
        return fileDepositedDate;
    }

    /**
     * @param fileDepositedDate the fileDepositedDate to set
     */
    public void setFileDepositedDate(String fileDepositedDate) {
        this.fileDepositedDate = fileDepositedDate;
    }

    /**
     * @return the initialApprovalDate
     */
    public String getInitialApprovalDate() {
        return initialApprovalDate;
    }

    /**
     * @param initialApprovalDate the initialApprovalDate to set
     */
    public void setInitialApprovalDate(String initialApprovalDate) {
        this.initialApprovalDate = initialApprovalDate;
    }

    /**
     * @return the taggingCompleteDate
     */
    public String getTaggingCompleteDate() {
        return taggingCompleteDate;
    }

    /**
     * @param taggingCompleteDate the taggingCompleteDate to set
     */
    public void setTaggingCompleteDate(String taggingCompleteDate) {
        this.taggingCompleteDate = taggingCompleteDate;
    }

    /**
     * @return the finalApprovalDate
     */
    public String getFinalApprovalDate() {
        return finalApprovalDate;
    }

    /**
     * @param finalApprovalDate the finalApprovalDate to set
     */
    public void setFinalApprovalDate(String finalApprovalDate) {
        this.finalApprovalDate = finalApprovalDate;
    }

    /**
     * @return the articleTitle
     */
    public String getArticleTitle() {
        return articleTitle;
    }

    /**
     * @param articleTitle the articleTitle to set
     */
    public void setArticleTitle(String articleTitle) {
        this.articleTitle = articleTitle;
    }

}
