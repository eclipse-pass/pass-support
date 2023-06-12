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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Karen Hanson
 */
@ExtendWith(MockitoExtension.class)
public class TransformAndLoadNonCompliantIT extends NihmsSubmissionEtlITBase {

    private final String pmid1 = "9999999999";
    private final String grant1 = "R01 AB123456";
    private final String grant2 = "R02 CD123456";
    private final String user1 = "55";
    private final String nihmsId1 = "NIHMS987654321";
    private final String dateval = "12/12/2017";
    private final String title = "Article A";
    private final String doi = "10.1000/a.abcd.1234";
    private final String issue = "3";

    private String pubId; //use this to pass a uri out of the scope of attempt()
    private String submissionId; //use this to pass a uri out of the scope of attempt()
    private String repoCopyId; //use this to pass a uri out of the scope of attempt()

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
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String grantId = createGrant(grant1, user1);
        //wait for new grant appears
        attempt(RETRIES, () -> {
            grantSelector.setFilter(RSQL.equals("@id", grantId));
            String testGrantId;
            try {
                testGrantId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testGrantId);
        });

        setMockPMRecord(pmid1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertNull(passClient.selectObjects(pubSelector).getObjects().get(0));

        //load all new publication, repo copy and submission
        NihmsPublication pub = newNonCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //wait for new publication to appear
        attempt(RETRIES, () -> {
            final String testId;
            try {
                testId = passClient.selectObjects(pubSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
            pubId = testId;
        });

        Publication publication = passClient.getObject(Publication.class, pubId);
        //spot check publication fields
        assertEquals(doi, publication.getDoi());
        assertEquals(title, publication.getTitle());
        assertEquals(issue, publication.getIssue());

        //now make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final String testId;
            subSelector.setFilter(RSQL.equals("publication", pubId));
            try {
                testId = passClient.selectObjects(subSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
            submissionId = testId;
        });

        Submission submission = passClient.getObject(Submission.class, submissionId);
        //check fields in submission
        assertEquals(grantId, submission.getGrants().get(0).getId());
        assertEquals(ConfigUtil.getNihmsRepositoryId(), submission.getRepositories().get(0).getId());
        assertEquals(1, submission.getRepositories().size());
        assertEquals(Source.OTHER, submission.getSource());
        assertFalse(submission.getSubmitted());
        assertEquals(user1, submission.getSubmitter().toString());
        assertNull(submission.getSubmittedDate());
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED, submission.getSubmissionStatus());

        repoCopySelector.setFilter(RSQL.equals("publication", pubId));
        repoCopyId = passClient.selectObjects(repoCopySelector).getObjects().get(0).getId();
        assertNull(repoCopyId);
    }

    /**
     * Submission existed for repository/grant/user and there is a Deposit. Publication is now non-compliant.
     * This should create a repoCopy with STALLED status and associate it with Deposti
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testStalledPublicationExistingSubmissionAndDeposit() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Deposit> depoSelector = new PassClientSelector<>(Deposit.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        String grantUri1 = createGrant(grant1, user1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertNull(passClient.selectObjects(pubSelector).getObjects().get(0));

        //create existing publication
        Publication publication = newPublication();
        passClient.createObject(publication);

        //a submission existed but had no repocopy
        Submission preexistingSub = newSubmission1(grantUri1, true, SubmissionStatus.SUBMITTED);
        preexistingSub.setSubmitted(true);
        preexistingSub.setSource(Source.PASS);
        passClient.createObject(preexistingSub);

        Deposit preexistingDeposit = new Deposit();
        preexistingDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        preexistingDeposit.setRepository(new Repository(ConfigUtil.getNihmsRepositoryId()));
        preexistingDeposit.setSubmission(new Submission(preexistingSub.getId()));
        passClient.createObject(preexistingDeposit);

        //wait for fake pre-existing deposit to appear
        attempt(RETRIES, () -> {
            final String testId;
            depoSelector.setFilter(RSQL.equals("@id", preexistingDeposit.getId()));
            try {
                testId = passClient.selectObjects(depoSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
        });

        //now we have an existing publication, deposit, and submission for same grant/repo...
        //do transform/load to make sure we get a stalled repocopy and the deposit record is updated
        NihmsPublication pub = newNonCompliantStalledNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final String testId;
            repoCopySelector.setFilter(RSQL.equals("externalIds", pub.getNihmsId()));
            try {
                testId = passClient.selectObjects(repoCopySelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
            repoCopyId = testId;
        });

        Submission reloadedPreexistingSub = passClient.getObject(Submission.class,  preexistingSub.getId());
        preexistingSub.setSubmissionStatus(SubmissionStatus.NEEDS_ATTENTION);
        assertEquals(preexistingSub, reloadedPreexistingSub); //should not have been affected

        //we should have ONLY ONE submission for this pmid
        subSelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.selectObjects(subSelector).getObjects().size());

        //we should have ONLY ONE publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.selectObjects(pubSelector).getObjects().size());

        //we should have ONLY ONE repoCopy for this publication
        repoCopySelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.selectObjects(repoCopySelector).getObjects().size());

        //validate the new repo copy
        RepositoryCopy repoCopy = passClient.getObject(RepositoryCopy.class, repoCopyId);
        validateRepositoryCopy(repoCopy);
        assertEquals(CopyStatus.STALLED, repoCopy.getCopyStatus());

        //check repository copy link added, but status did not change... status managed by deposit service
        Deposit deposit = passClient.getObject(Deposit.class, preexistingDeposit.getId());
        assertEquals(DepositStatus.ACCEPTED, deposit.getDepositStatus());
        assertEquals(repoCopyId, deposit.getRepositoryCopy().getId());

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
        String grantUri1 = createGrant(grant1, user1);
        String grantUri2 = createGrant(grant2, user1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertNull(passClient.selectObjects(pubSelector).getObjects().get(0).getId());

        //create existing publication
        Publication publication = newPublication();
        passClient.createObject(publication);

        //a submission existed for the user/pub combo and is unsubmitted, but has a different grant/repo
        Submission preexistingSub = newSubmission1(grantUri1, false, SubmissionStatus.MANUSCRIPT_REQUIRED);
        preexistingSub.setSubmitted(false);
        preexistingSub.setSource(Source.PASS);
        List<Grant> grants = new ArrayList<>();
        grants.add(new Grant(grantUri2));
        preexistingSub.setGrants(grants);
        List<Repository> repos = new ArrayList<>();
        repos.add(new Repository("fake:repo"));
        preexistingSub.setRepositories(repos);
        passClient.createObject(preexistingSub);

        //now make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final String testId;
            subSelector.setFilter(RSQL.equals("publication", pubId));
            try {
                testId = passClient.selectObjects(subSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
            submissionId = testId;
        });

        //now we have an existing publication, submission for same user/publication...
        //do transform/load to make sure we get an updated submission that includes grant/repo
        NihmsPublication pub = newNonCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            subSelector.setFilter(RSQL.equals("repositories", ConfigUtil.getNihmsRepositoryId()));
            final String testId;
            try {
                testId = passClient.selectObjects(subSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
        });

        Submission reloadedPreexistingSub = passClient.getObject(Submission.class, preexistingSub.getId());
        assertFalse(reloadedPreexistingSub.getSubmitted());
        assertTrue(reloadedPreexistingSub.getRepositories()
                .contains(new Repository(ConfigUtil.getNihmsRepositoryId())));
        assertTrue(reloadedPreexistingSub.getRepositories().contains(new Repository("fake:repo")));
        assertEquals(2, reloadedPreexistingSub.getRepositories().size());
        assertTrue(reloadedPreexistingSub.getGrants().contains(new Grant(grantUri1)));
        assertTrue(reloadedPreexistingSub.getGrants().contains(new Grant(grantUri2)));
        assertEquals(2, reloadedPreexistingSub.getGrants().size());
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED, reloadedPreexistingSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        subSelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.selectObjects(subSelector).getObjects().size());

        //we should have ONLY ONE publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.selectObjects(pubSelector).getObjects().size());

        //we should have ONLY ONE repoCopy for this publication
        repoCopySelector.setFilter(RSQL.equals("publication", pubId));
        assertEquals(1, passClient.selectObjects(repoCopySelector).getObjects().size());

    }

    private NihmsPublication newNonCompliantNihmsPub() {
        return new NihmsPublication(NihmsStatus.NON_COMPLIANT, pmid1, grant1, null, null, null, null, null, null,
                                    title);
    }

    private NihmsPublication newNonCompliantStalledNihmsPub() {
        return new NihmsPublication(NihmsStatus.NON_COMPLIANT, pmid1, grant1, nihmsId1, null, dateval, dateval, null,
                                    null, title);
    }

    private Publication newPublication() throws Exception {
        Publication publication = new Publication();
        publication.setDoi(doi);
        publication.setPmid(pmid1);
        publication.setIssue(issue);
        publication.setTitle(title);
        return publication;
    }

    private Submission newSubmission1(String grantUri1, boolean submitted, SubmissionStatus status) throws Exception {
        Submission submission1 = new Submission();
        List<Grant> grants = new ArrayList<>();
        grants.add(new Grant(grantUri1));
        submission1.setGrants(grants);
        submission1.setPublication(new Publication(pubId));
        submission1.setSubmitter(new User(user1));
        submission1.setSource(Source.OTHER);
        submission1.setSubmitted(submitted);
        submission1.setSubmissionStatus(status);
        List<Repository> repos = new ArrayList<>();
        repos.add(new Repository(ConfigUtil.getNihmsRepositoryId()));
        submission1.setRepositories(repos);
        return submission1;
    }

    //this validation does not check repo copy status as it varies for non-compliant
    private void validateRepositoryCopy(RepositoryCopy repoCopy) {
        //check fields in repoCopy
        assertNotNull(repoCopy);
        assertEquals(1, repoCopy.getExternalIds().size());
        assertEquals(nihmsId1, repoCopy.getExternalIds().get(0));
        assertEquals(ConfigUtil.getNihmsRepositoryId(), repoCopy.getRepository().getId());
        assertNull(repoCopy.getAccessUrl());
    }

}
