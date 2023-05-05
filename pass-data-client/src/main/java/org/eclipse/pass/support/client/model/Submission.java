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
package org.eclipse.pass.support.client.model;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jsonapi.Id;
import jsonapi.Resource;
import jsonapi.ToMany;
import jsonapi.ToOne;

/**
 * Submission model. Contains details of work being submitted, where it is being
 * deposited to, related Grants etc.
 *
 * @author Karen Hanson
 */

@Resource(type = "submission")
public class Submission implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * Stringified JSON representation of metadata captured by the relevant
     * repository forms
     */
    private String metadata;

    /**
     * Source of Submission record
     */
    private Source source;

    /**
     * When true, this value signals that the Submission will no longer be edited by
     * the User. It indicates to Deposit services that it can generate Deposits for
     * any Repositories that need one.
     */
    private Boolean submitted;

    /**
     * Date the record was submitted by the User through PASS
     */
    private ZonedDateTime submittedDate;

    /**
     * Status of Submission. Focused on informing User of current state of
     * Submission.
     */
    private SubmissionStatus submissionStatus;

    /**
     * Overall status of Submission's Deposits
     */
    private AggregatedDepositStatus aggregatedDepositStatus;

    /**
     * The Publication associated with the Submission
     */
    @ToOne(name = "publication")
    private Publication publication;

    /**
     * List of repositories that the submission will be deposited to Note that the
     * order of the list does not carry any particular significance
     */
    @ToMany(name = "repositories")
    private List<Repository> repositories = new ArrayList<>();

    /**
     * The User responsible for managing and submitting the Submission.
     */
    @ToOne(name = "submitter")
    private User submitter;

    /**
     * Name of the submitter. Used with submitterEmail as a temporary store for user
     * information in the absence of a User record
     */
    private String submitterName;

    /**
     * Email of the submitter as URI e.g. "mailto:j.smith@example.com". Used with
     * submitterName as a temporary store of user information in the absence of a
     * User record
     */
    private URI submitterEmail;

    /**
     * The User(s) who prepared, or who could contribute to the preparation of, the
     * Submission. Prepares can edit the content of the Submission (describe the
     * Publication, add Grants, add Files, select Repositories) but cannot approve
     * any Repository agreements or submit the Publication. Note that the order of
     * the list does not carry any particular significance
     */
    @ToMany(name = "preparers")
    private List<User> preparers = new ArrayList<>();

    /**
     * List of grants associated with the submission Note that the order of the list
     * does not carry any particular significance
     */
    @ToMany(name = "grants")
    private List<Grant> grants = new ArrayList<>();

    /**
     * List of the Policy resources being satisfied upon submission
     */
    @ToMany(name = "effectivePolicies")
    private List<Policy> effectivePolicies = new ArrayList<>();

    /**
     * Submission constructor
     */
    public Submission() {
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier to set
     */
    public Submission(String id) {
        this.id = id;
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the
     * new object
     *
     * @param submission the submission to copy
     */
    public Submission(Submission submission) {
        this.id = submission.id;
        this.metadata = submission.metadata;
        this.source = submission.source;
        this.submitted = submission.submitted;
        this.submittedDate = submission.submittedDate;
        this.submissionStatus = submission.submissionStatus;
        this.aggregatedDepositStatus = submission.aggregatedDepositStatus;
        this.publication = submission.publication;
        this.repositories = new ArrayList<Repository>(submission.repositories);
        this.submitter = submission.submitter;
        this.submitterName = submission.submitterName;
        this.submitterEmail = submission.submitterEmail;
        this.preparers = new ArrayList<User>(submission.preparers);
        this.grants = new ArrayList<Grant>(submission.grants);
        this.effectivePolicies = new ArrayList<>(submission.effectivePolicies);
    }

    /**
     * @return the metadata
     */
    public String getMetadata() {
        return metadata;
    }

    /**
     * @param metadata the metadata to set
     */
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    /**
     * @return the source
     */
    public Source getSource() {
        return source;
    }

    /**
     * @param source the source to set
     */
    public void setSource(Source source) {
        this.source = source;
    }

    /**
     * @return the submitted
     */
    public Boolean calculate() {
        return submitted;
    }

    /**
     * @return Boolean indicating submitted
     */
    public Boolean getSubmitted() {
        return submitted;
    }

    /**
     * @param submitted the submitted to set
     */
    public void setSubmitted(Boolean submitted) {
        this.submitted = submitted;
    }

    /**
     * @return the submittedDate
     */
    public ZonedDateTime getSubmittedDate() {
        return submittedDate;
    }

    /**
     * @param submittedDate the submittedDate to set
     */
    public void setSubmittedDate(ZonedDateTime submittedDate) {
        this.submittedDate = submittedDate;
    }

    /**
     * @return the submissionStatus
     */
    public SubmissionStatus getSubmissionStatus() {
        return submissionStatus;
    }

    /**
     * @return the aggregatedDepositStatus
     */
    public AggregatedDepositStatus getAggregatedDepositStatus() {
        return aggregatedDepositStatus;
    }

    /**
     * @param aggregatedDepositStatus the aggregatedDepositStatus to set
     */
    public void setAggregatedDepositStatus(AggregatedDepositStatus aggregatedDepositStatus) {
        this.aggregatedDepositStatus = aggregatedDepositStatus;
    }

    /**
     * @param submissionStatus the submissionStatus to set
     */
    public void setSubmissionStatus(SubmissionStatus submissionStatus) {
        this.submissionStatus = submissionStatus;
    }

    /**
     * @return the publication
     */
    public Publication getPublication() {
        return publication;
    }

    /**
     * @param publication the publication to set
     */
    public void setPublication(Publication publication) {
        this.publication = publication;
    }

    /**
     * @return the repositories
     */
    public List<Repository> getRepositories() {
        return repositories;
    }

    /**
     * @param repositories the repositories to set
     */
    public void setRepositories(List<Repository> repositories) {
        this.repositories = repositories == null ? new ArrayList<>() : repositories;
    }

    /**
     * @return the submitter
     */
    public User getSubmitter() {
        return submitter;
    }

    /**
     * Set the submitter
     *
     * @param submitter the submitter to set
     */
    public void setSubmitter(User submitter) {
        this.submitter = submitter;
    }

    /**
     * @return the submitter name
     */
    public String getSubmitterName() {
        return submitterName;
    }

    /**
     * Set the submitter name
     *
     * @param submitterName the submitter name to set
     */
    public void setSubmitterName(String submitterName) {
        this.submitterName = submitterName;
    }

    /**
     * @return the submitter email
     */
    public URI getSubmitterEmail() {
        return submitterEmail;
    }

    /**
     * Set the submitter email
     *
     * @param submitterEmail the submitter email to set
     */
    public void setSubmitterEmail(URI submitterEmail) {
        this.submitterEmail = submitterEmail;
    }

    /**
     * Gets the list of preparers
     *
     * @return the preparers
     */
    public List<User> getPreparers() {
        return preparers;
    }

    /**
     * @param preparers the preparers to set
     */
    public void setPreparers(List<User> preparers) {
        this.preparers = preparers == null ? new ArrayList<>() : preparers;
    }

    /**
     * @return the grants
     */
    public List<Grant> getGrants() {
        return grants;
    }

    /**
     * @param grants the grants to set
     */
    public void setGrants(List<Grant> grants) {
        this.grants = grants == null ? new ArrayList<>() : grants;
    }

    /**
     * @return the policies being satisfied upon submission
     */
    public List<Policy> getEffectivePolicies() {
        return effectivePolicies;
    }

    /**
     * @param effectivePolicies the policies being satisfied upon submission
     */
    public void setEffectivePolicies(List<Policy> effectivePolicies) {
        this.effectivePolicies = effectivePolicies == null ? new ArrayList<>() : effectivePolicies;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Submission other = (Submission) obj;
        return aggregatedDepositStatus == other.aggregatedDepositStatus
                && Objects.equals(effectivePolicies, other.effectivePolicies) && Objects.equals(grants, other.grants)
                && Objects.equals(id, other.id) && Objects.equals(metadata, other.metadata)
                && Objects.equals(preparers, other.preparers) && Objects.equals(publication, other.publication)
                && Objects.equals(repositories, other.repositories) && source == other.source
                && submissionStatus == other.submissionStatus && Objects.equals(submitted, other.submitted)
                && Objects.equals(submittedDate == null ? null : submittedDate.toInstant(),
                        other.submittedDate == null ? null : other.submittedDate.toInstant())
                && Objects.equals(submitter, other.submitter) && Objects.equals(submitterEmail, other.submitterEmail)
                && Objects.equals(submitterName, other.submitterName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, metadata, submitterName);
    }

    @Override
    public String toString() {
        return "Submission [id=" + id + ", metadata=" + metadata + ", source=" + source + ", submitted=" + submitted
                + ", submittedDate=" + submittedDate + ", submissionStatus=" + submissionStatus
                + ", aggregatedDepositStatus=" + aggregatedDepositStatus + ", publication=" + publication
                + ", repositories=" + repositories + ", submitter=" + submitter + ", submitterName=" + submitterName
                + ", submitterEmail=" + submitterEmail + ", preparers=" + preparers + ", grants=" + grants
                + ", effectivePolicies=" + effectivePolicies + "]";
    }
}
