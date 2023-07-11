/*
 * Copyright 2019 Johns Hopkins University
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
package org.eclipse.pass.deposit.provider.bagit;

import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.User;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class BagModel {

    /**
     * Canonical URI of the {@link #submission}
     *
     * (Convenience field, same as {@link Submission#getId()} or {@link DepositSubmission#getId()})
     */
    private String submissionUri;

    /**
     * Submission associated with this Bag
     */
    private Submission submission;

    /**
     * Submission in the Deposit Services model
     */
    private DepositSubmission depositSubmission;

    /**
     * Metadata JSON "blob" associated with the {@link #submission}.  Metadata are described by schemas, for examples
     * see https://github.com/OA-PASS/metadata-schemas/blob/master/examples/jhu/full.json.
     *
     * (Convenience field, same as {@link Submission#getMetadata()})
     */
    private String submissionMetadata;

    /**
     * Identifier assigned by the publisher to the Submission.  A convenience field that is parsed from the {@link
     * #submissionMetadata}, may be {@code null}.  Typically this will be DOI.
     */
    private String publisherId;

    /**
     * The current date, in YYYY-MM-DD format.
     */
    private String currentDate;

    /**
     * The sum of the size of the <em>custodial</em> content in the Bag (i.e. the size of the {@code data/} payload
     * directory).  Does not include non-custodial content like tag files.  Unlike {@link #bagSizeHumanReadable} the
     * units of this field are bytes and represents an exact byte count of the Bag payload.
     */
    private long bagSizeBytes;

    /**
     * The sum of the size of the <em>custodial</em> content in the Bag (i.e. the size of the {@code data/} payload
     * directory).  Does not include non-custodial content like tag files.  Unlike {@link #bagSizeBytes} this field is
     * a human-readable string, and may not be an exact byte count of the Bag payload.
     */
    private String bagSizeHumanReadable;

    /**
     * The number of custodial files in the Bag (i.e. the number of files under the {@code data/} payload directory).
     */
    private long custodialFileCount;

    /**
     * The BagIt version associated with this Bag
     */
    private String bagItVersion;

    /**
     * Date of the {@link #submission}.
     *
     * (Convenience field, string version of {@link Submission#getSubmittedDate()})
     */
    private String submissionDate;

    /**
     * Full name of the user who performed the submission
     */
    private String submissionUserFullName;

    /**
     * Email address of the user who performed the submission
     */
    private String submissionUserEmail;

    /**
     * URI of the {@link org.eclipse.pass.support.client.model.User} who performed the submission
     */
    private String submissionUserUri;

    private User submissionUser;

    private String contactName;

    private String contactEmail;

    private String contactPhone;

    private String sourceOrganization;

    private String organizationAddress;

    public String getSubmissionUri() {
        return submissionUri;
    }

    public void setSubmissionUri(String submissionUri) {
        this.submissionUri = submissionUri;
    }

    public Submission getSubmission() {
        return submission;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public DepositSubmission getDepositSubmission() {
        return depositSubmission;
    }

    public void setDepositSubmission(DepositSubmission depositSubmission) {
        this.depositSubmission = depositSubmission;
    }

    public String getSubmissionMetadata() {
        return submissionMetadata;
    }

    public void setSubmissionMetadata(String submissionMetadata) {
        this.submissionMetadata = submissionMetadata;
    }

    public String getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(String publisherId) {
        this.publisherId = publisherId;
    }

    public String getCurrentDate() {
        return currentDate;
    }

    public void setCurrentDate(String currentDate) {
        this.currentDate = currentDate;
    }

    public long getBagSizeBytes() {
        return bagSizeBytes;
    }

    public void setBagSizeBytes(long bagSizeBytes) {
        this.bagSizeBytes = bagSizeBytes;
    }

    public String getBagSizeHumanReadable() {
        return bagSizeHumanReadable;
    }

    public void setBagSizeHumanReadable(String bagSizeHumanReadable) {
        this.bagSizeHumanReadable = bagSizeHumanReadable;
    }

    public long getCustodialFileCount() {
        return custodialFileCount;
    }

    public void setCustodialFileCount(long custodialFileCount) {
        this.custodialFileCount = custodialFileCount;
    }

    public String getBagItVersion() {
        return bagItVersion;
    }

    public void setBagItVersion(String bagItVersion) {
        this.bagItVersion = bagItVersion;
    }

    public String getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(String submissionDate) {
        this.submissionDate = submissionDate;
    }

    public String getSubmissionUserFullName() {
        return submissionUserFullName;
    }

    public void setSubmissionUserFullName(String submissionUserFullName) {
        this.submissionUserFullName = submissionUserFullName;
    }

    public String getSubmissionUserEmail() {
        return submissionUserEmail;
    }

    public void setSubmissionUserEmail(String submissionUserEmail) {
        this.submissionUserEmail = submissionUserEmail;
    }

    public String getSubmissionUserUri() {
        return submissionUserUri;
    }

    public void setSubmissionUserUri(String submissionUserUri) {
        this.submissionUserUri = submissionUserUri;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getSourceOrganization() {
        return sourceOrganization;
    }

    public void setSourceOrganization(String sourceOrganization) {
        this.sourceOrganization = sourceOrganization;
    }

    public String getOrganizationAddress() {
        return organizationAddress;
    }

    public void setOrganizationAddress(String organizationAddress) {
        this.organizationAddress = organizationAddress;
    }

    public User getSubmissionUser() {
        return submissionUser;
    }

    public void setSubmissionUser(User submissionUser) {
        this.submissionUser = submissionUser;
    }
}
