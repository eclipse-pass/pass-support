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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Source;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Karen Hanson
 */
@ExtendWith(MockitoExtension.class)
public class TransformAndLoadCompliantIT extends NihmsSubmissionEtlITBase {

    private final String pmid1 = "11111111";
    private final String awardNumber1 = "R01 AB123456";
    private final String nihmsId1 = "NIHMS987654321";
    private final String pmcid1 = "PMC12345678";
    private final String title = "Article A";
    private final String doi = "10.1000/a.abcd.1234";
    private final String issue = "3";

    private String pubId;
    private String submissionId;
    private String repoCopyId;


    /**
     * Tests when the publication is completely new and is compliant
     * publication, submission and repoCopy are all created
     *
     * @throws Exception if test error
     */
    @Test
    //@Disabled("Working, disabled while working on other tests")
    public void testNewCompliantPublication() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        User user = new User();
        passClient.createObject(user);
        String grantId = createGrant(awardNumber1, user);

        Repository repo = new Repository(ConfigUtil.getNihmsRepositoryId());
        passClient.createObject(repo);

        Grant testGrant = passClient.getObject(Grant.class, grantId);
        assertNotNull(testGrant);

        setMockPMRecord(pmid1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //load all new publication, repo copy and submission
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //The pubmeb publication should be created during the transformAndLoadNihmsPub
        PassClientSelector<Publication> pmrPubSelector2 = new PassClientSelector<>(Publication.class);
        pmrPubSelector2.setFilter(RSQL.equals("pmid", pmid1));
        Publication pmrRecordPub = passClient.streamObjects(pmrPubSelector2).findFirst().orElseThrow();
        assertNotNull(pmrRecordPub.getId());
        pubId = pmrRecordPub.getId();

        Publication publication = passClient.getObject(Publication.class, pubId);
        //spot check publication fields
        assertEquals(doi, publication.getDoi());
        assertEquals(title, publication.getTitle());
        assertEquals(issue, publication.getIssue());

        //now make sure we wait for submission, should only be one from the test
        subSelector.setFilter(RSQL.equals("publication.id", pubId));
        submissionId = passClient.streamObjects(subSelector).findFirst().orElseThrow().getId();
        assertNotNull(submissionId);
        Submission submission = passClient.getObject(Submission.class, submissionId);
        //check fields in submission
        assertEquals(grantId, submission.getGrants().get(0).getId());
        assertEquals(ConfigUtil.getNihmsRepositoryId(), submission.getRepositories().get(0).getId());
        assertEquals(1, submission.getRepositories().size());
        assertEquals(Source.OTHER, submission.getSource());
        assertTrue(submission.getSubmitted());
        assertNotNull(submission.getSubmitter());
        assertEquals(12, submission.getSubmittedDate().getMonthValue());
        assertEquals(12, submission.getSubmittedDate().getDayOfMonth());
        assertEquals(2017, submission.getSubmittedDate().getYear());
        assertEquals(SubmissionStatus.COMPLETE, submission.getSubmissionStatus());

        //now retrieve repositoryCopy
        repoCopySelector.setFilter(RSQL.equals("publication.id", pubId));
        repoCopyId = passClient.streamObjects(repoCopySelector).findAny().orElseThrow().getId();
        assertNotNull(repoCopyId);

        RepositoryCopy repoCopy = passClient.getObject(RepositoryCopy.class, repoCopyId);
        //check fields in repoCopy
        assertEquals(CopyStatus.COMPLETE, repoCopy.getCopyStatus());
        assertTrue(repoCopy.getExternalIds().contains(pub.getNihmsId()));
        assertTrue(repoCopy.getExternalIds().contains(pub.getPmcId()));
        assertEquals(ConfigUtil.getNihmsRepositoryId(), repoCopy.getRepository().getId());
        assertTrue(repoCopy.getAccessUrl().toString().contains(pub.getPmcId()));

    }

    /**
     * Tests when the publication is in PASS and has a different submission, but the grant and repo are
     * not yet associated with that submission. There is also no existing repocopy for the publication
     *
     * @throws Exception if errors occurs
     */
    @Test
    //@Disabled("Working, disabled while working on other tests")
    public void testCompliantPublicationNewConnectedGrant() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String awardNumber2 = "R02 CD123456";

        User user1 = new User();
        passClient.createObject(user1);
        User user2 = new User();
        passClient.createObject(user2);

        String grantId1 = createGrant(awardNumber1, user1);
        String grantId2 = createGrant(awardNumber2, user2); // dont need to wait, will wait for publication instead

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //create existing publication
        Publication publication = newPublication();
        pubId = nihmsPassClientService.createPublication(publication);

        //there is a submission for a different grant
        Submission preexistingSub = newSubmission2(grantId2, publication, true, SubmissionStatus.COMPLETE);
        nihmsPassClientService.createSubmission(preexistingSub);

        //now we have an existing publication and submission for different grant/repo... do transform/load
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //get the submission, should only be one from the test
        subSelector.setFilter(RSQL.equals("grants.id", grantId1));
        List<Submission> submissionsTest = passClient.streamObjects(subSelector).toList();
        assertEquals(1,submissionsTest.size());
        submissionId = submissionsTest.get(0).getId();
        assertNotNull(submissionId);

        //we should have two submissions for this publication
        PassClientSelector<Submission> subTwoSel = new PassClientSelector<>(Submission.class);
        subTwoSel.setFilter(RSQL.equals("publication.id", pubId));
        List<Submission> submissions = passClient.streamObjects(subTwoSel).toList();
        assertEquals(2, submissions.size());

        Submission reloadedPreexistingSub = nihmsPassClientService.readSubmission(preexistingSub.getId());
        // should not have been affected, spot checking specific fields since readSubmission does not inflate
        // all relationships
        verifySubmission(preexistingSub, reloadedPreexistingSub);

        Submission newSubmission = nihmsPassClientService.readSubmission(submissionId);

        //check fields in new submission
        assertEquals(grantId1, newSubmission.getGrants().get(0).getId());
        assertEquals(ConfigUtil.getNihmsRepositoryId(), newSubmission.getRepositories().get(0).getId());
        assertEquals(1, newSubmission.getRepositories().size());
        assertEquals(Source.OTHER, newSubmission.getSource());
        assertTrue(newSubmission.getSubmitted());
        assertNotNull(newSubmission.getSubmitter());
        assertEquals(12, newSubmission.getSubmittedDate().getMonthValue());
        assertEquals(12, newSubmission.getSubmittedDate().getDayOfMonth());
        assertEquals(2017, newSubmission.getSubmittedDate().getYear());
        assertEquals(SubmissionStatus.COMPLETE, newSubmission.getSubmissionStatus());

        repoCopySelector.setFilter(RSQL.equals("publication.id", pubId));
        repoCopyId = passClient.streamObjects(repoCopySelector).findFirst().orElseThrow().getId();
        assertNotNull(repoCopyId);

        //we should have one publication for this pmid
        PassClientSelector<Publication> onePubSel = new PassClientSelector<>(Publication.class);
        onePubSel.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(onePubSel).toList().size());

        //validate the new repo copy.
        validateRepositoryCopy(repoCopyId);
    }

    /**
     * Submission existed for repository/grant/user, but no deposit. Publication is now compliant.
     * This should create a repoCopy with compliant status and associate with publication
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    //@Disabled("Working, disabled while working on other tests")
    public void testCompliantPublicationExistingSubmission() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        User user = new User();
        passClient.createObject(user);
        String grantId = createGrant(awardNumber1, user);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //create existing publication
        Publication newExistingPub = newPublication();
        pubId = nihmsPassClientService.createPublication(newExistingPub);
        assertEquals(newExistingPub.getPmid(), pmid1);

        //create an existing submission, set status as SUBMITTED - repocopy doesnt exist yet
        Submission preexistingSub = newSubmission1(grantId, user, pubId, true, SubmissionStatus.SUBMITTED);
        nihmsPassClientService.createSubmission(preexistingSub);

        //now we have an existing publication and submission for same grant/repo... do transform/load to make sure we
        // get a repocopy
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for RepositoryCopy, should only be one from the test
        repoCopySelector.setFilter(RSQL.hasMember("externalIds", pub.getPmcId()));
        List<RepositoryCopy> repoCopy = passClient.streamObjects(repoCopySelector).toList();
        assertEquals(1, repoCopy.size());
        repoCopyId = repoCopy.get(0).getId();
        assertNotNull(repoCopyId);

        Submission reloadedPreexistingSub = passClient.getObject(Submission.class, preexistingSub.getId());
        assertEquals(SubmissionStatus.COMPLETE, reloadedPreexistingSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        PassClientSelector subOneSel = new PassClientSelector<>(Submission.class);
        subOneSel.setFilter(RSQL.equals("publication.id", pubId));
        assertEquals(1, passClient.streamObjects(subOneSel).toList().size());

        //we should have ONLY ONE publication for this pmid
        PassClientSelector<Publication> onePubSel = new PassClientSelector<>(Publication.class);
        onePubSel.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(onePubSel).toList().size());

        //we should have ONLY ONE repoCopy for this publication
        PassClientSelector<RepositoryCopy> oneRepoCopySel = new PassClientSelector<>(RepositoryCopy.class);
        oneRepoCopySel.setFilter(RSQL.equals("publication.id", pubId));
        assertEquals(1, passClient.streamObjects(oneRepoCopySel).toList().size());

        //validate the new repo copy.
        validateRepositoryCopy(repoCopyId);
    }

    /**
     * Submission existed for repository/grant/user, but no deposit. Has not been submitted yet but publication is
     * now compliant.
     * This should create a repoCopy with compliant status and associate with publication. It should also set the
     * Submission to
     * submitted=true, source=OTHER
     *
     * @throws Exception if an error occurs
     */
    @Test
    //@Disabled("Working, disabled while working on other tests")
    public void testCompliantPublicationExistingUnsubmittedSubmission() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        User user = new User();
        passClient.createObject(user);
        String grantId = createGrant(awardNumber1, user);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //create existing publication
        Publication newExistingPub = newPublication();
        pubId = nihmsPassClientService.createPublication(newExistingPub);

        //a submission existed but had no repocopy. The submission has not been submitted
        Submission preexistingSub = newSubmission1(grantId, user, newExistingPub.getId(), false,
                SubmissionStatus.MANUSCRIPT_REQUIRED);
        preexistingSub.setSource(Source.PASS);
        preexistingSub.setSubmittedDate(null);
        nihmsPassClientService.createSubmission(preexistingSub);

        // now we have an existing publication and unsubmitted submission for same grant/repo... do transform/load to
        // make sure we get a repocopy and submission status changes
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for RepositoryCopy, should only be one from the test
        repoCopySelector.setFilter(RSQL.hasMember("externalIds", pub.getPmcId()));
        List<RepositoryCopy> repositoryCopies = passClient.streamObjects(repoCopySelector).toList();
        assertEquals(1, repositoryCopies.size());
        repoCopyId = repositoryCopies.get(0).getId();
        assertNotNull(repoCopyId);

        Submission reloadedSub = passClient.getObject(Submission.class, preexistingSub.getId());
        assertEquals(Source.OTHER, reloadedSub.getSource());
        assertEquals(true, reloadedSub.getSubmitted());
        assertEquals(12, reloadedSub.getSubmittedDate().getMonthValue());
        assertEquals(2017, reloadedSub.getSubmittedDate().getYear());
        assertEquals(12, reloadedSub.getSubmittedDate().getDayOfMonth());
        assertEquals(SubmissionStatus.COMPLETE, reloadedSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        PassClientSelector<Submission> subSelOne = new PassClientSelector<>(Submission.class);
        subSelOne.setFilter(RSQL.equals("publication.id", pubId));
        assertEquals(1, passClient.streamObjects(subSelOne).toList().size());

        //we should have ONLY ONE publication for this pmid
        PassClientSelector<Publication> pubSelOne = new PassClientSelector<>(Publication.class);
        pubSelOne.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(pubSelOne).toList().size());

        //we should have ONLY ONE repoCopy for this publication
        PassClientSelector<RepositoryCopy> repoCopySelOne = new PassClientSelector<>(RepositoryCopy.class);
        repoCopySelOne.setFilter(RSQL.equals("publication.id", pubId));
        assertEquals(1, passClient.streamObjects(repoCopySelOne).toList().size());

        //validate the new repo copy.
        validateRepositoryCopy(repoCopyId);
    }

    /**
     * Submission existed for repository/grant/user and there is a Deposit. Publication is now compliant.
     * This should create a repoCopy with compliant status and associate with publication and Deposit
     *
     * @throws Exception if an error occurs
     */
    @Test
    //@Disabled
    public void testCompliantPublicationExistingSubmissionAndDeposit() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        User user = new User();
        passClient.createObject(user);
        String grantId1 = createGrant(awardNumber1, user);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //create existing publication
        Publication newExistingPub = newPublication();
        pubId = nihmsPassClientService.createPublication(newExistingPub);

        //a submission existed but had no repocopy
        Submission preexistingSub = newSubmission1(grantId1, user, newExistingPub.getId(), true,
                SubmissionStatus.COMPLETE);
        nihmsPassClientService.createSubmission(preexistingSub);

        PassClientSelector<Repository> nihmsRepoSel = new PassClientSelector<>(Repository.class);
        nihmsRepoSel.setFilter(RSQL.equals("id", ConfigUtil.getNihmsRepositoryId()));
        Repository nihmsRepo = passClient.streamObjects(nihmsRepoSel).findAny().orElseThrow();

        Deposit preexistingDeposit = new Deposit();
        preexistingDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        preexistingDeposit.setRepository(nihmsRepo);
        preexistingDeposit.setSubmission(preexistingSub);
        passClient.createObject(preexistingDeposit);

        assertNotNull(passClient.getObject(Deposit.class, preexistingDeposit.getId()));

        //now we have an existing publication, deposit, and submission for same grant/repo...
        //do transform/load to make sure we get a repocopy and the deposit record is updated
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for repoCopy, should only be one from the test
        repoCopySelector.setFilter(RSQL.hasMember("externalIds", pub.getPmcId()));
        List<RepositoryCopy> repoCopy = passClient.streamObjects(repoCopySelector).toList();
        assertEquals(1, repoCopy.size());
        repoCopyId = repoCopy.get(0).getId();
        assertNotNull(repoCopyId);

        Submission reloadedPreexistingSub = passClient.getObject(Submission.class, preexistingSub.getId());
        assertEquals(SubmissionStatus.COMPLETE, reloadedPreexistingSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        PassClientSelector<Submission> subSelOne = new PassClientSelector<>(Submission.class);
        subSelOne.setFilter(RSQL.equals("publication.id", pubId));
        assertEquals(1, passClient.streamObjects(subSelOne).toList().size());

        //we should have ONLY ONE publication for this pmid
        PassClientSelector<Publication> pubSelOne = new PassClientSelector<>(Publication.class);
        pubSelOne.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(pubSelOne).toList().size());

        //we should have ONLY ONE repoCopy for this publication
        PassClientSelector<RepositoryCopy> repoCopySelOne = new PassClientSelector<>(RepositoryCopy.class);
        repoCopySelOne.setFilter(RSQL.equals("publication.id", pubId));
        assertEquals(1, passClient.streamObjects(repoCopySelOne).toList().size());

        //validate the new repo copy.
        validateRepositoryCopy(repoCopyId);

        //check repository copy link added, but status did not change... status managed by deposit service
        Deposit deposit = passClient.getObject(Deposit.class, preexistingDeposit.getId());
        assertEquals(DepositStatus.ACCEPTED, deposit.getDepositStatus());
        assertEquals(repoCopyId, deposit.getRepositoryCopy().getId());
    }

    /**
     * Submission existed for repository/grant/user. RepoCopy also existed but no deposit.
     * RepoCopy was previously in process but is now compliant. This should update RepoCopy
     * status to reflect completion. There is also a new PMCID where previously there was only NIHMSID
     *
     * @throws Exception if an error occurs
     */
    @Test
    //@Disabled
    public void testCompliantPublicationExistingSubmissionAndRepoCopy() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        User user = new User();
        passClient.createObject(user);
        String grantId1 = createGrant(awardNumber1, user);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //create existing publication
        Publication publication = newPublication();
        passClient.createObject(publication);

        //a submission existed but had no repocopy
        Submission preexistingSub = newSubmission1(grantId1, user, publication.getId(), true,
                SubmissionStatus.SUBMITTED);
        nihmsPassClientService.createSubmission(preexistingSub);

        RepositoryCopy preexistingRepoCopy = new RepositoryCopy();
        Publication preexistingPub = new Publication();
        passClient.createObject(preexistingPub);
        preexistingRepoCopy.setPublication(preexistingPub);
        preexistingRepoCopy.setRepository(getNihmsRepo());
        preexistingRepoCopy.setCopyStatus(CopyStatus.IN_PROGRESS);
        List<String> externalIds = new ArrayList<>();
        externalIds.add(nihmsId1);
        preexistingRepoCopy.setExternalIds(externalIds);
        passClient.createObject(preexistingRepoCopy);

        // now we have an existing publication and submission for same grant/repo... do transform/load to make sure we
        // get a repocopy
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //wait for repoCopy to update
        repoCopySelector.setFilter(RSQL.hasMember("externalIds", pub.getPmcId()));
        List<RepositoryCopy> repoCopy = passClient.streamObjects(repoCopySelector).toList();
        assertEquals(1, repoCopy.size());
        repoCopyId = repoCopy.get(0).getId();
        assertNotNull(repoCopyId);

        //we should have ONLY ONE submission for this pmid
        PassClientSelector<Submission> subSelOne = new PassClientSelector<>(Submission.class);
        //TODO : expected: <1> but was: <0>
        subSelOne.setFilter(RSQL.equals("publication.id", preexistingPub.getId()));
        assertEquals(1, passClient.streamObjects(subSelOne).toList().size());

        //we should have ONLY ONE publication for this pmid
        PassClientSelector<Publication> pubSelOne = new PassClientSelector<>(Publication.class);
        pubSelOne.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(pubSelOne).toList().size());

        //we should have ONLY ONE repoCopy for this publication
        PassClientSelector<RepositoryCopy> repoCopySelOne = new PassClientSelector<>(RepositoryCopy.class);
        repoCopySelOne.setFilter(RSQL.equals("publication.id", preexistingPub.getId()));
        assertEquals(1, passClient.streamObjects(repoCopySelOne).toList().size());

        //validate the new repo copy.
        validateRepositoryCopy(repoCopyId);

        Submission submission = passClient.getObject(Submission.class, submissionId);
        assertEquals(SubmissionStatus.COMPLETE, submission.getSubmissionStatus());
    }

    private NihmsPublication newCompliantNihmsPub() {
        String dateval = "12/12/2017";
        return new NihmsPublication(NihmsStatus.COMPLIANT, pmid1, awardNumber1, nihmsId1, pmcid1, dateval, dateval,
                dateval, dateval, title);
    }

    private Publication newPublication() {
        Publication publication = new Publication();
        publication.setDoi(doi);
        publication.setPmid(pmid1);
        publication.setIssue(issue);
        publication.setTitle(title);
        return publication;
    }

    private Submission newSubmission1(String grantId1, User user, String pubId, boolean submitted,
                                      SubmissionStatus status) throws IOException {
        Submission submission1 = new Submission();
        List<Grant> grants = new ArrayList<>();
        PassClientSelector<Publication> pubSelect = new PassClientSelector<>(Publication.class);
        pubSelect.setFilter(RSQL.equals("id", pubId));
        Publication pub = passClient.streamObjects(pubSelect).findFirst().orElseThrow();
        PassClientSelector<Grant> grantSelect = new PassClientSelector<>(Grant.class);
        grantSelect.setFilter(RSQL.equals("id", grantId1));
        Grant grant = passClient.streamObjects(grantSelect).findFirst().orElseThrow();
        grants.add(grant);
        submission1.setGrants(grants);
        submission1.setPublication(pub);
        submission1.setSubmitter(user);
        submission1.setSource(Source.OTHER);
        submission1.setSubmitted(submitted);
        submission1.setSubmissionStatus(status);
        PassClientSelector<Repository> repoSelect = new PassClientSelector<>(Repository.class);
        repoSelect.setFilter(RSQL.equals("id", ConfigUtil.getNihmsRepositoryId()));
        Repository repo = passClient.streamObjects(repoSelect).findFirst().orElseThrow();
        List<Repository> repos = new ArrayList<>();
        repos.add(repo);
        submission1.setRepositories(repos);
        return submission1;
    }

    private Submission newSubmission2(String grantId, Publication pub, boolean submitted, SubmissionStatus status)
            throws IOException {
        Submission submission2 = new Submission();
        List<Grant> grants = new ArrayList<>();
        PassClientSelector<Grant> selector = new PassClientSelector<>(Grant.class);
        selector.setFilter(RSQL.equals("id", grantId));
        Grant grantToAdd = passClient.streamObjects(selector).findFirst().orElseThrow();
        assertNotNull(grantToAdd.getId());
        grants.add(grantToAdd);
        User user = new User();
        passClient.createObject(user);
        submission2.setGrants(grants);
        submission2.setPublication(pub);
        submission2.setSubmitter(user);
        submission2.setSource(Source.PASS);
        submission2.setSubmitted(submitted);
        submission2.setSubmissionStatus(status);
        List<Repository> repos = new ArrayList<>();
        Repository repo = new Repository();
        passClient.createObject(repo);
        assertNotNull(repo.getId());
        repos.add(repo);
        submission2.setRepositories(repos);
        return submission2;
    }

    private void validateRepositoryCopy(String entityId) throws IOException {
        RepositoryCopy repoCopy = passClient.getObject(RepositoryCopy.class, entityId);
        //check fields in repoCopy
        assertNotNull(repoCopy);
        assertEquals(CopyStatus.COMPLETE, repoCopy.getCopyStatus());
        assertTrue(repoCopy.getExternalIds().contains(nihmsId1));
        assertTrue(repoCopy.getExternalIds().contains(pmcid1));
        assertEquals(ConfigUtil.getNihmsRepositoryId(), repoCopy.getRepository().getId());
        assertTrue(repoCopy.getAccessUrl().toString().contains(pmcid1));
    }

    /**
     * Spot check fields of a submission, to compare two submission objects that should be equal.
     */
    private void verifySubmission(Submission preexistingSub, Submission reloadedSub) {
        assertEquals(preexistingSub.getId(), reloadedSub.getId());
        assertEquals(preexistingSub.getSource(), reloadedSub.getSource());
        assertEquals(preexistingSub.getSubmitted(), reloadedSub.getSubmitted());
        assertEquals(preexistingSub.getSubmittedDate(), reloadedSub.getSubmittedDate());
        assertEquals(preexistingSub.getAggregatedDepositStatus(), reloadedSub.getAggregatedDepositStatus());

        //test publications
        Publication preexistingPub = preexistingSub.getPublication();
        Publication reloadedPub = reloadedSub.getPublication();
        assertEquals(preexistingPub.getId(), reloadedPub.getId());
        assertEquals(preexistingPub.getTitle(), reloadedPub.getTitle());
        assertEquals(preexistingPub.getDoi(), reloadedPub.getDoi());
        assertEquals(preexistingPub.getPmid(), reloadedPub.getPmid());

        //test the first grant
        Grant preexistingGrants = preexistingSub.getGrants().get(0);
        Grant reloadedGrants = reloadedSub.getGrants().get(0);
        assertEquals(preexistingGrants.getId(), reloadedGrants.getId());
        assertEquals(preexistingGrants.getAwardNumber(), reloadedGrants.getAwardNumber());
        assertEquals(preexistingGrants.getAwardDate(), reloadedGrants.getAwardDate());
        assertEquals(preexistingGrants.getAwardStatus(), reloadedGrants.getAwardStatus());
        assertEquals(preexistingGrants.getLocalKey(), reloadedGrants.getLocalKey());
    }

    private Repository getNihmsRepo() throws IOException {
        PassClientSelector<Repository> nihmsRepoSel = new PassClientSelector<>(Repository.class);
        nihmsRepoSel.setFilter(RSQL.equals("id", ConfigUtil.getNihmsRepositoryId()));
        return passClient.streamObjects(nihmsRepoSel).findFirst().orElseThrow();
    }
}
