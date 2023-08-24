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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.eclipse.pass.entrez.PubMedEntrezRecord;
import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.support.client.PassClientResult;
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
import org.json.JSONObject;
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
    private final String awardNumber2 = "R02 CD123456";
    private final String user1 = "55";
    private final String user2 = "77";
    private final String nihmsId1 = "NIHMS987654321";
    private final String pmcid1 = "PMC12345678";
    private final String dateval = "12/12/2017";
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
    public void testNewCompliantPublication() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String grantId = createGrant(awardNumber1);

        Repository repo = new Repository(ConfigUtil.getNihmsRepositoryId());
        passClient.createObject(repo);
        System.out.println("Created Nihms repo " + repo.getId());

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
        Publication pmrRecordPub = passClient.streamObjects(pmrPubSelector2).findFirst().get();
        assertNotNull(pmrRecordPub.getId());
        pubId = pmrRecordPub.getId();

        Publication publication = passClient.getObject(Publication.class, pubId);
        //spot check publication fields
        assertEquals(doi, publication.getDoi());
        assertEquals(title, publication.getTitle());
        assertEquals(issue, publication.getIssue());

        //now make sure we wait for submission, should only be one from the test
        subSelector.setFilter(RSQL.equals("publication.id", pubId));
        submissionId = passClient.streamObjects(subSelector).findFirst().get().getId();
        assertNotNull(submissionId);
        //submissionId = pubId;
        System.out.println("TransformAndLoadCompliantIT Submission id: " + submissionId);
        Submission submission = passClient.getObject(Submission.class, submissionId);
        System.out.println("TransformAndLoadCompliantIT Submission: " + submission.getId());
        //check fields in submission
        System.out.println("TransformAndLoadCompliantIT Submission grant id: " + submission.getGrants().get(0).getId());
        assertEquals(grantId, submission.getGrants().get(0).getId());
        System.out.println("TransformAndLoadCompliantIT Submission repo id: " + submission.getRepositories().get(0).getId());
        assertEquals(ConfigUtil.getNihmsRepositoryId(), submission.getRepositories().get(0).getId());
        assertEquals(1, submission.getRepositories().size());
        assertEquals(Source.OTHER, submission.getSource());
        assertTrue(submission.getSubmitted());
        assertNotNull(submission.getSubmitter().toString());
        assertEquals(12, submission.getSubmittedDate().getMonthValue());
        assertEquals(12, submission.getSubmittedDate().getDayOfMonth());
        assertEquals(2017, submission.getSubmittedDate().getYear());
        assertEquals(SubmissionStatus.COMPLETE, submission.getSubmissionStatus());

        //now retrieve repositoryCopy
        repoCopySelector.setFilter(RSQL.equals("publication.id", pubId));
        repoCopyId = passClient.streamObjects(repoCopySelector).findFirst().get().getId();
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
    @Disabled
    public void testCompliantPublicationNewConnectedGrant() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String grantUri1 = createGrant(awardNumber1);
        String grantUri2 = createGrant(awardNumber2); // dont need to wait, will wait for publication instead

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertThrows(Exception.class, () -> {
            passClient.streamObjects(pubSelector).findAny().get().getId();
        });

        //create existing publication
        Publication publication = newPublication();
        pubId = nihmsPassClientService.createPublication(publication);

        //there is a submission for a different grant
        Submission preexistingSub = newSubmission2(grantUri2, true, SubmissionStatus.COMPLETE);
        passClient.createObject(preexistingSub);

        //wait for fake pre-existing submission to appear
        attempt(RETRIES, () -> {
            final String testId;
            subSelector.setFilter(RSQL.equals("repositories", "fake:repo"));
            try {
                testId = passClient.streamObjects(subSelector).findFirst().get().getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
        });

        //now we have an existing publication and submission for different grant/repo... do transform/load
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final String testId;
            subSelector.setFilter(RSQL.equals("grants", grantUri1));
            try {
                testId = passClient.streamObjects(subSelector).findFirst().get().getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
            submissionId = testId;
        });

        //we should have two submissions for this publication
        subSelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(2, passClient.streamObjects(subSelector).toList().size());

        Submission reloadedPreexistingSub = nihmsPassClientService.readSubmission(preexistingSub.getId());
        assertEquals(preexistingSub, reloadedPreexistingSub); //should not have been affected

        Submission newSubmission = nihmsPassClientService.readSubmission(submissionId);

        //check fields in new submission
        assertEquals(grantUri1, newSubmission.getGrants().get(0).getId());
        assertEquals(ConfigUtil.getNihmsRepositoryId(), newSubmission.getRepositories().get(0).getId());
        assertEquals(1, newSubmission.getRepositories().size());
        assertEquals(Source.OTHER, newSubmission.getSource());
        assertTrue(newSubmission.getSubmitted());
        assertEquals(user1, newSubmission.getSubmitter().toString());
        assertEquals(12, newSubmission.getSubmittedDate().getMonthValue());
        assertEquals(12, newSubmission.getSubmittedDate().getDayOfMonth());
        assertEquals(2017, newSubmission.getSubmittedDate().getYear());
        assertEquals(SubmissionStatus.COMPLETE, newSubmission.getSubmissionStatus());

        //now retrieve repositoryCopy
        attempt(RETRIES, () -> {
            final String testId;
            repoCopySelector.setFilter(RSQL.equals("publication", pubId));
            try {
                testId = passClient.streamObjects(repoCopySelector).findFirst().get().getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
            repoCopyId = testId;
        });

        //we should have one publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(pubSelector).toList().size());

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
    @Disabled
    public void testCompliantPublicationExistingSubmission() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String grantUri1 = createGrant(awardNumber1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertThrows(Exception.class, () -> {
            passClient.streamObjects(pubSelector).findAny().get().getId();
        });

        //create existing publication
        pubId = nihmsPassClientService.createPublication(newPublication());

        //create an existing submission, set status as SUBMITTED - repocopy doesnt exist yet
        Submission preexistingSub = newSubmission1(grantUri1, true, SubmissionStatus.SUBMITTED);
        passClient.createObject(preexistingSub);

        //wait for fake pre-existing submission to appear
        attempt(RETRIES, () -> {
            final String testId;
            subSelector.setFilter(RSQL.equals("@id", preexistingSub.getId()));
            try {
                testId = passClient.streamObjects(subSelector).findFirst().get().getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
        });

        //now we have an existing publication and submission for same grant/repo... do transform/load to make sure we
        // get a repocopy
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for RepositoryCopy, should only be one from the test
        attempt(RETRIES, () -> {
            final String testId;
            repoCopySelector.setFilter(RSQL.equals("externalIds", pub.getPmcId()));
            try {
                testId = passClient.streamObjects(repoCopySelector).findFirst().get().getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
            repoCopyId = testId;
        });

        Submission reloadedPreexistingSub = passClient.getObject(Submission.class, preexistingSub.getId());
        assertEquals(SubmissionStatus.COMPLETE, reloadedPreexistingSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        subSelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.streamObjects(subSelector).toList().size());

        //we should have ONLY ONE publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(pubSelector).toList().size());

        //we should have ONLY ONE repoCopy for this publication
        repoCopySelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.streamObjects(repoCopySelector).toList().size());

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
    @Disabled
    public void testCompliantPublicationExistingUnsubmittedSubmission() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String grantUri1 = createGrant(awardNumber1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertThrows(Exception.class, () -> {
            passClient.streamObjects(pubSelector).findFirst().get().getId();
        });

        //create existing publication
        Publication publication = newPublication();
        passClient.createObject(publication);

        //a submission existed but had no repocopy. The submission has not been submitted
        Submission preexistingSub = newSubmission1(grantUri1, false, SubmissionStatus.MANUSCRIPT_REQUIRED);
        preexistingSub.setSource(Source.PASS);
        preexistingSub.setSubmittedDate(null);
        passClient.createObject(preexistingSub);

        //wait for fake pre-existing submission to appear
        attempt(RETRIES, () -> {
            final String testId;
            subSelector.setFilter(RSQL.equals("@id", preexistingSub.getId()));
            try {
                testId = passClient.streamObjects(subSelector).findFirst().get().getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
        });

        //now we have an existing publication and unsubmitted submission for same grant/repo... do transform/load to
        // make sure we get a repocopy
        //and submission status changes
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for RepositoryCopy, should only be one from the test
        attempt(RETRIES, () -> {
            final String testId;
            repoCopySelector.setFilter(RSQL.equals("externalIds", pub.getPmcId()));
            try {
                testId = passClient.streamObjects(repoCopySelector).findFirst().get().getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
            repoCopyId = testId;
        });

        Submission reloadedSub = passClient.getObject(Submission.class, preexistingSub.getId());
        assertEquals(Source.OTHER, reloadedSub.getSource());
        assertEquals(true, reloadedSub.getSubmitted());
        assertEquals(12, reloadedSub.getSubmittedDate().getMonthValue());
        assertEquals(2017, reloadedSub.getSubmittedDate().getYear());
        assertEquals(12, reloadedSub.getSubmittedDate().getDayOfMonth());
        assertEquals(SubmissionStatus.COMPLETE, reloadedSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        subSelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.streamObjects(subSelector).toList().size());

        //we should have ONLY ONE publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(pubSelector).toList().size());

        //we should have ONLY ONE repoCopy for this publication
        repoCopySelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.streamObjects(repoCopySelector).toList().size());

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
    @Disabled
    public void testCompliantPublicationExistingSubmissionAndDeposit() throws Exception {
        System.out.println("Start : testCompliantPublicationExistingSubmissionAndDeposit");
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<Deposit> depoSelector = new PassClientSelector<>(Deposit.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String grantId1 = createGrant(awardNumber1);
        System.out.println("grantId1: " + grantId1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertThrows(Exception.class, () -> {
            passClient.streamObjects(pubSelector).findFirst().get().getId();
        });

        //create existing publication
        Publication publication = newPublication();
        passClient.createObject(publication);
        System.out.println("publication: " + publication.getId());

        //a submission existed but had no repocopy
        Submission preexistingSub = newSubmission1(grantId1, true, SubmissionStatus.COMPLETE);
        passClient.createObject(preexistingSub);

        Repository repo = new Repository(ConfigUtil.getNihmsRepositoryId());
        passClient.createObject(repo);

        Deposit preexistingDeposit = new Deposit();
        preexistingDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        preexistingDeposit.setRepository(repo);
        preexistingDeposit.setSubmission(preexistingSub);
        passClient.createObject(preexistingDeposit);

        assertNotNull(passClient.getObject(Deposit.class, preexistingDeposit.getId()));

        //now we have an existing publication, deposit, and submission for same grant/repo...
        //do transform/load to make sure we get a repocopy and the deposit record is updated
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        System.out.println("transformAndLoadNihmsPub");
        System.out.println("pub.getPmcId: " + pub.getPmcId());
        System.out.println("pub.getPmid: " + pub.getPmid());
        System.out.println("pub.getGrantNumber: " + pub.getGrantNumber());

        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        repoCopySelector.setFilter(RSQL.equals("externalIds", pub.getPmcId()));
        repoCopyId = passClient.streamObjects(repoCopySelector).findFirst().get().getId();
        assertNotNull(repoCopyId);

        Submission reloadedPreexistingSub = passClient.getObject(Submission.class, preexistingSub.getId());
        assertEquals(SubmissionStatus.COMPLETE, reloadedPreexistingSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        subSelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.streamObjects(subSelector).toList().size());

        //we should have ONLY ONE publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(pubSelector).toList().size());

        //we should have ONLY ONE repoCopy for this publication
        repoCopySelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.streamObjects(repoCopySelector).toList().size());

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
    @Disabled
    public void testCompliantPublicationExistingSubmissionAndRepoCopy() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String grantId1 = createGrant(awardNumber1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertThrows(Exception.class, () -> {
            passClient.streamObjects(pubSelector).findFirst().get().getId();
        });

        //create existing publication
        Publication publication = newPublication();
        passClient.createObject(publication);

        //a submission existed but had no repocopy
        Submission preexistingSub = newSubmission1(grantId1, true, SubmissionStatus.SUBMITTED);
        passClient.createObject(preexistingSub);

        RepositoryCopy preexistingRepoCopy = new RepositoryCopy();
        preexistingRepoCopy.setPublication(new Publication(pubId));
        preexistingRepoCopy.setRepository(new Repository(ConfigUtil.getNihmsRepositoryId()));
        preexistingRepoCopy.setCopyStatus(CopyStatus.IN_PROGRESS);
        List<String> externalIds = new ArrayList<>();
        externalIds.add(nihmsId1);
        preexistingRepoCopy.setExternalIds(externalIds);
        passClient.createObject(preexistingRepoCopy);

        //wait for pre-existing repoCopy to appear
        attempt(RETRIES, () -> {
            final String testId;
            repoCopySelector.setFilter(RSQL.equals("@id", repoCopyId));
            try {
                testId = passClient.streamObjects(repoCopySelector).findFirst().get().getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
        });

        //now we have an existing publication and submission for same grant/repo... do transform/load to make sure we
        // get a repocopy
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //wait for repoCopy to update
        attempt(RETRIES, () -> {
            final String testId;
            repoCopySelector.setFilter(RSQL.equals("externalIds", pub.getPmcId()));
            try {
                testId = passClient.streamObjects(repoCopySelector).findFirst().get().getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
        });

        //we should have ONLY ONE submission for this pmid
        subSelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.streamObjects(subSelector).toList().size());

        //we should have ONLY ONE publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.streamObjects(pubSelector).toList().size());

        //we should have ONLY ONE repoCopy for this publication
        repoCopySelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.streamObjects(repoCopySelector).toList().size());

        //validate the new repo copy.
        validateRepositoryCopy(repoCopyId);

        Submission submission = passClient.getObject(Submission.class, submissionId);
        assertEquals(SubmissionStatus.COMPLETE, submission.getSubmissionStatus());
    }

    private NihmsPublication newCompliantNihmsPub() {
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

    private Submission newSubmission1(String grantId1, boolean submitted, SubmissionStatus status) throws IOException {
        Submission submission1 = new Submission();
        List<Grant> grants = new ArrayList<>();
        Publication publication = new Publication();
        passClient.createObject(publication);
        User user = new User();
        passClient.createObject(user);
        grants.add(new Grant(grantId1));
        submission1.setGrants(grants);
        submission1.setPublication(publication);
        submission1.setSubmitter(user);
        submission1.setSource(Source.OTHER);
        submission1.setSubmitted(submitted);
        submission1.setSubmissionStatus(status);
        Repository repo = new Repository(ConfigUtil.getNihmsRepositoryId());
        passClient.createObject(repo);
        List<Repository> repos = new ArrayList<>();
        repos.add(repo);
        submission1.setRepositories(repos);
        return submission1;
    }

    private Submission newSubmission2(String grantUri2, boolean submitted, SubmissionStatus status) {
        Submission submission2 = new Submission();
        List<Grant> grants = new ArrayList<>();
        grants.add(new Grant(grantUri2));
        submission2.setGrants(grants);
        submission2.setPublication(new Publication(pubId));
        submission2.setSubmitter(new User(user2));
        submission2.setSource(Source.PASS);
        submission2.setSubmitted(submitted);
        submission2.setSubmissionStatus(status);
        List<Repository> repos = new ArrayList<>();
        repos.add(new Repository("fake:repo"));
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
}
