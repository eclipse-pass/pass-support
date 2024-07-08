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
package org.eclipse.pass.loader.nihms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Source;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Karen Hanson
 */
@ExtendWith(MockitoExtension.class)
public class TransformAndLoadNonCompliantIT extends NihmsSubmissionEtlITBase {
    private final String nihmsId1 = "NIHMS987654321";

    /**
     * Tests when the publication is completely new and is non-compliant
     * publication, submission but no RepositoryCopy is created
     *
     * @throws Exception if an error occurs
     */

    @Test
    public void testNewNonCompliantPublication() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);

        User user = new User();
        passClient.createObject(user);

        String grant1_award_num = UUID.randomUUID().toString();
        Grant grant1 = createGrant(grant1_award_num, user);

        String pmid1 = UUID.randomUUID().toString();
        setMockPMRecord(pmid1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //load all new publication, repo copy and submission
        NihmsPublication pub = newNonCompliantNihmsPub(pmid1, grant1_award_num);
        nihmsTransformLoadService.transformAndLoadNihmsPub(pub);

        //wait for new publication to appear
        PassClientSelector<Publication> testPubSelector = new PassClientSelector<>(Publication.class);
        testPubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Publication publication = passClient.streamObjects(testPubSelector).findFirst().orElseThrow();

        //spot check publication fields
        assertEquals(doi, publication.getDoi());
        assertEquals(title, publication.getTitle());
        assertEquals(issue, publication.getIssue());

        //now make sure we wait for submission, should only be one from the test
        subSelector.setFilter(RSQL.equals("publication.id", publication.getId()));
        List<Submission> testSub = passClient.streamObjects(subSelector).toList();
        assertEquals(1, testSub.size());

        Submission submission = testSub.get(0);

        //check fields in submission
        assertEquals(grant1.getId(), submission.getGrants().get(0).getId());
        assertEquals(nihmsRepoId, submission.getRepositories().get(0).getId());
        assertEquals(1, submission.getRepositories().size());
        assertEquals(Source.OTHER, submission.getSource());
        assertFalse(submission.getSubmitted());
        assertEquals(user.getId(), submission.getSubmitter().getId());
        assertNull(submission.getSubmittedDate());
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED, submission.getSubmissionStatus());

        repoCopySelector.setFilter(RSQL.equals("publication.id", publication.getId()));
        Optional<RepositoryCopy> repoCopy = passClient.streamObjects(repoCopySelector).findAny();
        assertFalse(repoCopy.isPresent());
    }

    /**
     * Submission existed for repository/grant/user and there is a Deposit. Publication is now non-compliant.
     * This should create a repoCopy with STALLED status and associate it with Deposit
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testStalledPublicationExistingSubmissionAndDeposit() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        User user = new User();
        passClient.createObject(user);

        String grant1_award_num = UUID.randomUUID().toString();
        Grant grant1 = createGrant(grant1_award_num, user);

        String pmid1 = UUID.randomUUID().toString();

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //create existing publication
        Publication publication = newPublication(pmid1);
        passClient.createObject(publication);

        //a submission existed but had no repocopy
        Submission preexistingSub = newSubmission1(grant1, publication, user, true,
                SubmissionStatus.SUBMITTED);
        preexistingSub.setSubmitted(true);
        preexistingSub.setSource(Source.PASS);
        passClient.createObject(preexistingSub);

        Deposit preexistingDeposit = new Deposit();
        preexistingDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        preexistingDeposit.setRepository(new Repository(nihmsRepoId));
        preexistingDeposit.setSubmission(preexistingSub);
        passClient.createObject(preexistingDeposit);

        //now we have an existing publication, deposit, and submission for same grant/repo...
        //do transform/load to make sure we get a stalled repocopy and the deposit record is updated
        NihmsPublication pub = newNonCompliantStalledNihmsPub(pmid1, grant1_award_num);
        nihmsTransformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        repoCopySelector.setFilter(RSQL.hasMember("externalIds", pub.getNihmsId()));
        Optional<RepositoryCopy> testRepoCopy = passClient.streamObjects(repoCopySelector).findAny();
        assertTrue(testRepoCopy.isPresent());

        Submission reloadedPreexistingSub = nihmsPassClientService.readSubmission(preexistingSub.getId());
        preexistingSub.setSubmissionStatus(SubmissionStatus.NEEDS_ATTENTION);

        verifySubmission(reloadedPreexistingSub, preexistingSub); //should not have been affected

        //we should have ONLY ONE submission for this pmid
        PassClientSelector<Submission> subSelOne = new PassClientSelector<>(Submission.class);
        subSelOne.setFilter(RSQL.equals("publication.id", publication.getId()));
        assertEquals(1, passClient.streamObjects(subSelOne).toList().size());

        //we should have ONLY ONE publication for this pmid
        PassClientSelector<Publication> pubSelOne = new PassClientSelector<>(Publication.class);
        pubSelOne.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(pubSelOne).toList().size());

        //we should have ONLY ONE repoCopy for this publication
        PassClientSelector<RepositoryCopy> repoCopySelOne = new PassClientSelector<>(RepositoryCopy.class);
        repoCopySelOne.setFilter(RSQL.equals("publication.id", publication.getId()));
        assertEquals(1, passClient.streamObjects(repoCopySelOne).toList().size());

        //validate the new repo copy
        RepositoryCopy repoCopy = passClient.getObject(testRepoCopy.orElseThrow(), "repository");
        validateRepositoryCopy(repoCopy);
        assertEquals(CopyStatus.STALLED, repoCopy.getCopyStatus());

        //check repository copy link added, but status did not change... status managed by deposit service
        Deposit deposit = passClient.getObject(Deposit.class, preexistingDeposit.getId());
        assertEquals(DepositStatus.ACCEPTED, deposit.getDepositStatus());
        assertEquals(testRepoCopy.orElseThrow().getId(), deposit.getRepositoryCopy().getId());
    }

    /**
     * Submission exists for publication/user combo but not yet submitted and does not list Grant or NIHMS Repo.
     * Make sure NIHMS Repo and Grant added.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testAddingToExistingUnsubmittedSubmission() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        User user1 = new User();
        passClient.createObject(user1);
        User user2 = new User();
        passClient.createObject(user2);

        String grant1_award_num = UUID.randomUUID().toString();
        Grant grant1 = createGrant(grant1_award_num, user1);
        String grant2_award_num = "R02 CD123456";
        Grant grant2 = createGrant(grant2_award_num, user2);

        String pmid1 = UUID.randomUUID().toString();

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //create existing publication
        Publication publication = newPublication(pmid1);
        passClient.createObject(publication);

        //a submission existed for the user/pub combo and is unsubmitted, but has a different grant/repo
        Submission preexistingSub = newSubmission1(grant1, publication, user1, false,
                SubmissionStatus.MANUSCRIPT_REQUIRED);
        preexistingSub.setSubmitted(false);
        preexistingSub.setSource(Source.PASS);
        preexistingSub.setGrants(List.of(grant1, grant2));

        Repository testRepo = new Repository();
        testRepo.setRepositoryKey("test");
        passClient.createObject(testRepo);

        preexistingSub.setRepositories(List.of(testRepo));
        passClient.createObject(preexistingSub);

        //now make sure we wait for submission, should only be one from the test
        subSelector.setFilter(RSQL.equals("publication.id", publication.getId()));
        assertEquals(1, passClient.streamObjects(subSelector).toList().size());

        //now we have an existing publication, submission for same user/publication...
        //do transform/load to make sure we get an updated submission that includes grant/repo
        NihmsPublication pub = newNonCompliantNihmsPub(pmid1, grant1_award_num);
        nihmsTransformLoadService.transformAndLoadNihmsPub(pub);

        Submission reloadedPreexistingSub = nihmsPassClientService.readSubmission(preexistingSub.getId());
        assertFalse(reloadedPreexistingSub.getSubmitted());

        assertTrue(reloadedPreexistingSub.getRepositories().stream().map(PassEntity::getId).
                anyMatch(k -> k.equals(nihmsRepoId)));

        assertTrue(reloadedPreexistingSub.getRepositories().contains(testRepo));
        assertEquals(2, reloadedPreexistingSub.getRepositories().size());
        assertTrue(reloadedPreexistingSub.getGrants().stream().map(Grant::getId).toList()
                .contains(grant1.getId()));
        assertTrue(reloadedPreexistingSub.getGrants().stream().map(Grant::getId).toList()
                .contains(grant2.getId()));
        assertEquals(2, reloadedPreexistingSub.getGrants().size());
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED, reloadedPreexistingSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        PassClientSelector<Submission> subSelOne = new PassClientSelector<>(Submission.class);
        subSelOne.setFilter(RSQL.equals("publication.id", publication.getId()));
        assertEquals(1, passClient.selectObjects(subSelOne).getObjects().size());

        //we should have ONLY ONE publication for this pmid
        PassClientSelector<Publication> pubSelOne = new PassClientSelector<>(Publication.class);
        pubSelOne.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.selectObjects(pubSelOne).getObjects().size());

        //we should have ZERO Repository Copies for this publication
        repoCopySelector.setFilter(RSQL.equals("publication.id", publication.getId()));
        assertEquals(0, passClient.selectObjects(repoCopySelector).getObjects().size());
    }

    private NihmsPublication newNonCompliantNihmsPub(String pmid1, String grant1_award_num) {
        return new NihmsPublication(NihmsStatus.NON_COMPLIANT, pmid1, grant1_award_num, null, null,
                null, null, null, null, title);
    }

    private NihmsPublication newNonCompliantStalledNihmsPub(String pmid1, String grant1_award_num) {
        String dateval = "12/12/2017";
        return new NihmsPublication(NihmsStatus.NON_COMPLIANT, pmid1, grant1_award_num, nihmsId1,
                null, dateval, dateval, null, null, title);
    }

    private Publication newPublication(String pmid1) throws Exception {
        Publication publication = new Publication();
        publication.setDoi(doi);
        publication.setPmid(pmid1);
        publication.setIssue(issue);
        publication.setTitle(title);
        return publication;
    }

    private Submission newSubmission1(Grant grant, Publication pub, User user, boolean submitted,
                                      SubmissionStatus status) throws Exception {
        Submission submission1 = new Submission();

        submission1.setGrants(List.of(grant));
        submission1.setPublication(pub);
        submission1.setSubmitter(user);
        submission1.setSource(Source.OTHER);
        submission1.setSubmitted(submitted);
        submission1.setSubmissionStatus(status);
        submission1.setRepositories(List.of(new Repository(nihmsRepoId)));

        return submission1;
    }

    //this validation does not check repo copy status as it varies for non-compliant
    private void validateRepositoryCopy(RepositoryCopy repoCopy) {
        //check fields in repoCopy
        assertNotNull(repoCopy);
        assertEquals(1, repoCopy.getExternalIds().size());
        assertEquals(nihmsId1, repoCopy.getExternalIds().get(0));
        assertEquals(nihmsRepoId, repoCopy.getRepository().getId());
        assertNull(repoCopy.getAccessUrl());
    }

}
