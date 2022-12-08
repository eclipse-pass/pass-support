/*
 * Copyright 2022 Johns Hopkins University
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

package org.eclipse.pass.support.client.model.support;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Contributor;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.IntegrationType;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Publisher;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Source;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.client.model.UserRole;

/**
 * Creates instances of model objects needed as fields on other objects used in
 * testing
 *
 * @author Jim Martino
 *
 */
public class TestObjectCreator {

    private TestObjectCreator() {
    }

    /**
     * Creates an instance of a Contributor
     *
     * @param contributorId the id for the object to be created
     * @param userId        parameter for the user on this object
     * @return the Contributor @
     */
    public static Contributor createContributor(String contributorId, String userId) {
        Contributor contributor = new Contributor();
        contributor.setId(contributorId);
        contributor.setFirstName(TestValues.USER_FIRST_NAME);
        contributor.setMiddleName(TestValues.USER_MIDDLE_NAME);
        contributor.setLastName(TestValues.USER_LAST_NAME);
        contributor.setDisplayName(TestValues.USER_DISPLAY_NAME);
        contributor.setEmail(TestValues.USER_EMAIL);
        contributor.setOrcidId(TestValues.USER_ORCID_ID);
        contributor.setAffiliation(TestValues.USER_AFFILIATION);
        contributor.setUser(createUser(userId));
        contributor.setPublication(createPublication(TestValues.PUBLICATION_ID_1));

        return contributor;
    }

    /**
     * Creates an instance of a Deposit
     *
     * @param depositId the id for the object to be created
     * @return the Deposit @
     */
    public static Deposit createDeposit(String depositId) {
        Deposit deposit = new Deposit();
        deposit.setId(depositId);
        deposit.setDepositStatusRef(TestValues.DEPOSIT_STATUSREF);
        deposit.setDepositStatus(DepositStatus.of(TestValues.DEPOSIT_STATUS));
        deposit.setSubmission(createSubmission(TestValues.SUBMISSION_ID_1));
        deposit.setRepository(createRepository(TestValues.REPOSITORY_ID_1));
        deposit.setRepositoryCopy(createRepositoryCopy(TestValues.REPOSITORYCOPY_ID_1));

        return deposit;
    }

    /**
     * Creates an instance of a Funder
     *
     * @param funderId the id for the object to be created
     * @return the Funder @
     */
    public static Funder createFunder(String funderId) {
        Funder funder = new Funder();
        funder.setId(funderId);
        funder.setName(TestValues.FUNDER_NAME);
        funder.setUrl(URI.create(TestValues.FUNDER_URL));
        funder.setPolicy(createPolicy(TestValues.POLICY_ID_1));
        funder.setLocalKey(TestValues.FUNDER_LOCALKEY);
        return funder;
    }

    /**
     * Creates an instance of a Grant
     *
     * @param grantId the id for the object to be created
     * @return the Grant @
     */
    public static Grant createGrant(String grantId) {
        Grant grant = new Grant();
        grant.setId(grantId);
        grant.setAwardNumber(TestValues.GRANT_AWARD_NUMBER);
        grant.setAwardStatus(AwardStatus.of(TestValues.GRANT_STATUS));
        grant.setLocalKey(TestValues.GRANT_LOCALKEY);
        grant.setProjectName(TestValues.GRANT_PROJECT_NAME);
        grant.setPrimaryFunder(createFunder(TestValues.FUNDER_ID_1));
        grant.setDirectFunder(createFunder(TestValues.FUNDER_ID_2));
        grant.setPi(createUser(TestValues.USER_ID_1));
        List<User> coPis = new ArrayList<>();
        coPis.add(createUser(TestValues.USER_ID_2));
        coPis.add(createUser(TestValues.USER_ID_3));
        grant.setCoPis(coPis);

        ZonedDateTime zdt = ZonedDateTime.parse(TestValues.GRANT_AWARD_DATE_STR_1);
        grant.setAwardDate(zdt);
        zdt = ZonedDateTime.parse(TestValues.GRANT_START_DATE_STR);
        grant.setStartDate(zdt);
        zdt = ZonedDateTime.parse(TestValues.GRANT_END_DATE_STR);
        grant.setEndDate(zdt);

        return grant;
    }

    /**
     * Creates an instance of a Journal
     *
     * @param journalId the id for the object to be created
     * @return the Journal @
     */
    public static Journal createJournal(String journalId) {
        Journal journal = new Journal();
        journal.setId(journalId);
        journal.setJournalName(TestValues.JOURNAL_NAME);
        List<String> issns = new ArrayList<String>();
        issns.add(TestValues.JOURNAL_ISSN_1);
        issns.add(TestValues.JOURNAL_ISSN_2);
        journal.setIssns(issns);
        journal.setPublisher(createPublisher(TestValues.PUBLISHER_ID_1));
        journal.setNlmta(TestValues.JOURNAL_NLMTA);
        journal.setPmcParticipation(PmcParticipation.valueOf(TestValues.JOURNAL_PMCPARTICIPATION));
        return journal;
    }

    /**
     * Creates an instance of a Policy
     *
     * @param policyId the id for the object to be created
     * @return the Policy @
     */
    public static Policy createPolicy(String policyId) {
        Policy policy = new Policy();
        policy.setId(policyId);
        policy.setTitle(TestValues.POLICY_TITLE);
        policy.setDescription(TestValues.POLICY_DESCRIPTION);
        policy.setPolicyUrl(URI.create(TestValues.POLICY_URL));

        List<Repository> repositories = new ArrayList<>();
        repositories.add(createRepository(TestValues.REPOSITORY_ID_1));
        repositories.add(createRepository(TestValues.REPOSITORY_ID_2));
        policy.setRepositories(repositories);

        policy.setInstitution(URI.create(TestValues.INSTITUTION_ID_1));

        return policy;
    }

    /**
     * Creates an instance of a Publication
     *
     * @param publicationId the id for the object to be created
     * @return the Publication @
     */
    public static Publication createPublication(String publicationId) {
        Publication publication = new Publication();
        publication.setId(publicationId);
        publication.setTitle(TestValues.PUBLICATION_TITLE);
        publication.setPublicationAbstract(TestValues.PUBLICATION_ABSTRACT);
        publication.setDoi(TestValues.PUBLICATION_DOI);
        publication.setPmid(TestValues.PUBLICATION_PMID);
        publication.setVolume(TestValues.PUBLICATION_VOLUME);
        publication.setIssue(TestValues.PUBLICATION_ISSUE);
        publication.setJournal(createJournal(TestValues.JOURNAL_ID_1));
        return publication;
    }

    /**
     * Creates an instance of a Publisher
     *
     * @param publisherId the id for the object to be created
     * @return the Publisher @
     */
    public static Publisher createPublisher(String publisherId) {
        Publisher publisher = new Publisher();
        publisher.setId(publisherId);
        publisher.setName(TestValues.PUBLISHER_NAME);
        publisher.setPmcParticipation(PmcParticipation.valueOf(TestValues.PUBLISHER_PMCPARTICIPATION));

        return publisher;
    }

    /**
     * Creates an instance of a Repository
     *
     * @param repositoryId the id for the object to be created
     * @return the Repository @
     */
    public static Repository createRepository(String repositoryId) {
        Repository repository = new Repository();
        repository.setId(repositoryId);
        repository.setName(TestValues.REPOSITORY_NAME);
        repository.setDescription(TestValues.REPOSITORY_DESCRIPTION);
        repository.setUrl(URI.create(TestValues.REPOSITORY_URL));
        repository.setAgreementText(TestValues.REPOSITORY_AGREEMENTTEXT);
        repository.setFormSchema(TestValues.REPOSITORY_FORMSCHEMA);
        repository.setIntegrationType(IntegrationType.of(TestValues.REPOSITORY_INTEGRATION_TYPE));
        repository.setRepositoryKey(TestValues.REPOSITORY_KEY);

        return repository;
    }

    /**
     * Creates an instance of a RepositoryCopy
     *
     * @param repositoryCopyId the id for the object to be created
     * @return the Repository Copy @
     */
    public static RepositoryCopy createRepositoryCopy(String repositoryCopyId) {
        RepositoryCopy repositoryCopy = new RepositoryCopy();
        repositoryCopy.setId(repositoryCopyId);
        repositoryCopy.setCopyStatus(CopyStatus.of(TestValues.REPOSITORYCOPY_STATUS));
        repositoryCopy.setAccessUrl(URI.create(TestValues.REPOSITORYCOPY_ACCESSURL));
        repositoryCopy.setPublication(createPublication(TestValues.PUBLICATION_ID_1));
        repositoryCopy.setRepository(createRepository(TestValues.REPOSITORY_ID_1));

        List<String> externalIds = new ArrayList<String>();
        externalIds.add(TestValues.REPOSITORYCOPY_EXTERNALID_1);
        externalIds.add(TestValues.REPOSITORYCOPY_EXTERNALID_2);
        repositoryCopy.setExternalIds(externalIds);

        return repositoryCopy;
    }

    /**
     * Creates an instance of a Submission
     *
     * @param submissionId the id for the object to be created
     * @return the Submission @
     */
    public static Submission createSubmission(String submissionId) {
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setSubmissionStatus(SubmissionStatus.of(TestValues.SUBMISSION_STATUS));
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.of(TestValues.SUBMISSION_AGG_DEPOSIT_STATUS));
        submission.setMetadata(TestValues.SUBMISSION_METADATA);
        submission.setSubmitted(TestValues.SUBMISSION_SUBMITTED);
        submission.setPublication(createPublication(TestValues.PUBLICATION_ID_1));
        submission.setSubmitter(createUser(TestValues.USER_ID_1));
        submission.setSubmitterName(TestValues.SUBMISSION_SUBMITTERNAME);
        submission.setSubmitterEmail(URI.create(TestValues.SUBMISSION_SUBMITTEREMAIL));
        submission.setSource(Source.PASS);

        List<User> preparers = new ArrayList<User>();
        preparers.add(createUser(TestValues.USER_ID_2));
        submission.setPreparers(preparers);

        List<Repository> repositories = new ArrayList<>();
        repositories.add(createRepository(TestValues.REPOSITORY_ID_1));
        repositories.add(createRepository(TestValues.REPOSITORY_ID_2));
        submission.setRepositories(repositories);

        List<Grant> grants = new ArrayList<>();
        grants.add(createGrant(TestValues.GRANT_ID_1));
        grants.add(createGrant(TestValues.GRANT_ID_2));
        submission.setGrants(grants);

        ZonedDateTime zdt = ZonedDateTime.parse(TestValues.SUBMISSION_DATE_STR);
        submission.setSubmittedDate(zdt);

        return submission;
    }

    /**
     * Creates an instance of a User
     *
     * @param userId the id for the object to be created
     * @return the User @
     */
    public static User createUser(String userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername(TestValues.USER_NAME);
        user.setFirstName(TestValues.USER_FIRST_NAME);
        user.setMiddleName(TestValues.USER_MIDDLE_NAME);
        user.setLastName(TestValues.USER_LAST_NAME);
        user.setDisplayName(TestValues.USER_DISPLAY_NAME);
        user.setEmail(TestValues.USER_EMAIL);
        user.setAffiliation(TestValues.USER_AFFILIATION);
        user.setOrcidId(TestValues.USER_ORCID_ID);

        List<String> locatorIds = new ArrayList<String>();
        locatorIds.add(TestValues.USER_LOCATORID1);
        locatorIds.add(TestValues.USER_LOCATORID2);
        user.setLocatorIds(locatorIds);

        List<UserRole> roles = new ArrayList<UserRole>();
        roles.add(UserRole.of(TestValues.USER_ROLE_1));
        roles.add(UserRole.of(TestValues.USER_ROLE_2));
        user.setRoles(roles);

        return user;
    }
}
