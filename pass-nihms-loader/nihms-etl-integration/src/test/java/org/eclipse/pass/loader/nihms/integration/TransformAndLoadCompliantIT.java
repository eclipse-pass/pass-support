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
package org.eclipse.pass.loader.nihms.integration;

import org.eclipse.pass.loader.nihms.NihmsTransformLoadService;
import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Source;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Karen Hanson
 */
@ExtendWith(MockitoExtension.class)
public class TransformAndLoadCompliantIT extends NihmsSubmissionEtlITBase {

    private final String pmid1 = "9999999999";
    private final String grant1 = "R01 AB123456";
    private final String grant2 = "R02 CD123456";
    private final String user1 = "http://test:8080/fcrepo/rest/users/55";
    private final String user2 = "http://test:8080/fcrepo/rest/users/77";
    private final String nihmsId1 = "NIHMS987654321";
    private final String pmcid1 = "PMC12345678";
    private final String dateval = "12/12/2017";
    private final String title = "Article A";
    private final String doi = "10.1000/a.abcd.1234";
    private final String issue = "3";

    private URI pubUri; //use this to pass a uri out of the scope of attempt()
    private URI submissionUri; //use this to pass a uri out of the scope of attempt()
    private URI repocopyUri; //use this to pass a uri out of the scope of attempt()

    /**
     * Tests when the publication is completely new and is compliant
     * publication, submission and repoCopy are all created
     *
     * @throws Exception
     */
    @Test
    public void testNewCompliantPublication() throws Exception {
        URI grantUri = createGrant(grant1, user1);
        //wait for new grant appears
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Grant.class, "@id", grantUri);
            assertNotNull(uri);
        });

        setMockPMRecord(pmid1);

        //we should start with no publication for this pmid
        assertNull(client.findByAttribute(Publication.class, "pmid", pmid1));

        //load all new publication, repo copy and submission
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //wait for new publication to appear
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Publication.class, "pmid", pmid1);
            assertNotNull(uri);
            pubUri = uri;
        });

        Publication publication = client.readResource(pubUri, Publication.class);
        //spot check publication fields
        assertEquals(doi, publication.getDoi());
        assertEquals(title, publication.getTitle());
        assertEquals(issue, publication.getIssue());

        //now make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Submission.class, "publication", pubUri);
            assertNotNull(uri);
            submissionUri = uri;
        });

        Submission submission = client.readResource(submissionUri, Submission.class);
        //check fields in submission
        assertEquals(grantUri, submission.getGrants().get(0));
        assertEquals(ConfigUtil.getNihmsRepositoryUri(), submission.getRepositories().get(0));
        assertEquals(1, submission.getRepositories().size());
        assertEquals(Source.OTHER, submission.getSource());
        assertTrue(submission.getSubmitted());
        assertEquals(user1, submission.getSubmitter().toString());
        assertEquals(12, submission.getSubmittedDate().getMonthOfYear());
        assertEquals(12, submission.getSubmittedDate().getDayOfMonth());
        assertEquals(2017, submission.getSubmittedDate().getYear());
        assertEquals(SubmissionStatus.COMPLETE, submission.getSubmissionStatus());

        //now retrieve repositoryCopy
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(RepositoryCopy.class, "publication", pubUri);
            assertNotNull(uri);
            repocopyUri = uri;
        });

        RepositoryCopy repoCopy = client.readResource(repocopyUri, RepositoryCopy.class);
        //check fields in repoCopy
        assertEquals(CopyStatus.COMPLETE, repoCopy.getCopyStatus());
        assertTrue(repoCopy.getExternalIds().contains(pub.getNihmsId()));
        assertTrue(repoCopy.getExternalIds().contains(pub.getPmcId()));
        assertEquals(ConfigUtil.getNihmsRepositoryUri(), repoCopy.getRepository());
        assertTrue(repoCopy.getAccessUrl().toString().contains(pub.getPmcId()));

    }

    /**
     * Tests when the publication is in PASS and has a different submission, but the grant and repo are
     * not yet associated with that submission. There is also no existing repocopy for the publication
     *
     * @throws Exception
     */
    @Test
    public void testCompliantPublicationNewConnectedGrant() throws Exception {
        URI grantUri1 = createGrant(grant1, user1);
        URI grantUri2 = createGrant(grant2, user2); // dont need to wait, will wait for publication instead

        //we should start with no publication for this pmid
        assertNull(client.findByAttribute(Publication.class, "pmid", pmid1));

        //create existing publication
        Publication publication = newPublication();
        pubUri = client.createResource(publication);

        //there is a submission for a different grant
        Submission preexistingSub = newSubmission2(grantUri2, true, SubmissionStatus.COMPLETE);
        preexistingSub = client.createAndReadResource(preexistingSub, Submission.class);

        //wait for fake pre-existing submission to appear
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Submission.class, "repositories", "fake:repo");
            assertNotNull(uri);
        });

        //now we have an existing publication and submission for different grant/repo... do transform/load
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Submission.class, "grants", grantUri1);
            assertNotNull(uri);
            submissionUri = uri;
        });

        //we should have two submissions for this publication
        assertEquals(2, client.findAllByAttribute(Submission.class, "publication", pubUri).size());

        Submission reloadedPreexistingSub = client.readResource(preexistingSub.getId(), Submission.class);
        assertEquals(preexistingSub, reloadedPreexistingSub); //should not have been affected

        Submission newSubmission = client.readResource(submissionUri, Submission.class);
        //check fields in new submission
        assertEquals(grantUri1, newSubmission.getGrants().get(0));
        assertEquals(ConfigUtil.getNihmsRepositoryUri(), newSubmission.getRepositories().get(0));
        assertEquals(1, newSubmission.getRepositories().size());
        assertEquals(Source.OTHER, newSubmission.getSource());
        assertTrue(newSubmission.getSubmitted());
        assertEquals(user1, newSubmission.getSubmitter().toString());
        assertEquals(12, newSubmission.getSubmittedDate().getMonthOfYear());
        assertEquals(12, newSubmission.getSubmittedDate().getDayOfMonth());
        assertEquals(2017, newSubmission.getSubmittedDate().getYear());
        assertEquals(SubmissionStatus.COMPLETE, newSubmission.getSubmissionStatus());

        //now retrieve repositoryCopy
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(RepositoryCopy.class, "publication", pubUri);
            assertNotNull(uri);
            repocopyUri = uri;
        });

        //we should have one publication for this pmid
        assertEquals(1, client.findAllByAttribute(Publication.class, "pmid", pmid1).size());

        //validate the new repo copy.
        validateRepositoryCopy(repocopyUri);

    }

    /**
     * Submission existed for repository/grant/user, but no deposit. Publication is now compliant.
     * This should create a repoCopy with compliant status and associate with publication
     *
     * @throws Exception
     */
    @Test
    public void testCompliantPublicationExistingSubmission() throws Exception {
        URI grantUri1 = createGrant(grant1, user1);

        //we should start with no publication for this pmid
        assertNull(client.findByAttribute(Publication.class, "pmid", pmid1));

        //create existing publication
        pubUri = client.createResource(newPublication());

        //create an existing submission, set status as SUBMITTED - repocopy doesnt exist yet
        Submission preexistingSub = client.createAndReadResource(
            newSubmission1(grantUri1, true, SubmissionStatus.SUBMITTED), Submission.class);

        //wait for fake pre-existing submission to appear
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Submission.class, "@id", preexistingSub.getId().toString());
            assertNotNull(uri);
        });

        //now we have an existing publication and submission for same grant/repo... do transform/load to make sure we
        // get a repocopy
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for RepositoryCopy, should only be one from the test
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(RepositoryCopy.class, "externalIds", pub.getPmcId());
            assertNotNull(uri);
            repocopyUri = uri;
        });

        Submission reloadedPreexistingSub = client.readResource(preexistingSub.getId(), Submission.class);
        assertEquals(SubmissionStatus.COMPLETE, reloadedPreexistingSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        assertEquals(1, client.findAllByAttribute(Submission.class, "publication", pubUri).size());

        //we should have ONLY ONE publication for this pmid
        assertEquals(1, client.findAllByAttribute(Publication.class, "pmid", pmid1).size());

        //we should have ONLY ONE repoCopy for this publication
        assertEquals(1, client.findAllByAttribute(RepositoryCopy.class, "publication", pubUri).size());

        //validate the new repo copy.
        validateRepositoryCopy(repocopyUri);

    }

    /**
     * Submission existed for repository/grant/user, but no deposit. Has not been submitted yet but publication is
     * now compliant.
     * This should create a repoCopy with compliant status and associate with publication. It should also set the
     * Submission to
     * submitted=true, source=OTHER
     *
     * @throws Exception
     */
    @Test
    public void testCompliantPublicationExistingUnsubmittedSubmission() throws Exception {
        URI grantUri1 = createGrant(grant1, user1);

        //we should start with no publication for this pmid
        assertNull(client.findByAttribute(Publication.class, "pmid", pmid1));

        //create existing publication
        Publication publication = newPublication();
        pubUri = client.createResource(publication);

        //a submission existed but had no repocopy. The submission has not been submitted
        Submission preexistingSub = newSubmission1(grantUri1, false, SubmissionStatus.MANUSCRIPT_REQUIRED);
        preexistingSub.setSource(Source.PASS);
        preexistingSub.setSubmittedDate(null);
        URI preexistingSubUri = client.createResource(preexistingSub);

        //wait for fake pre-existing submission to appear
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Submission.class, "@id", preexistingSubUri.toString());
            assertNotNull(uri);
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
            final URI uri = client.findByAttribute(RepositoryCopy.class, "externalIds", pub.getPmcId());
            assertNotNull(uri);
            repocopyUri = uri;
        });

        Submission reloadedSub = client.readResource(preexistingSubUri, Submission.class);
        assertEquals(Source.OTHER, reloadedSub.getSource());
        assertEquals(true, reloadedSub.getSubmitted());
        assertEquals(12, reloadedSub.getSubmittedDate().getMonthOfYear());
        assertEquals(2017, reloadedSub.getSubmittedDate().getYear());
        assertEquals(12, reloadedSub.getSubmittedDate().getDayOfMonth());
        assertEquals(SubmissionStatus.COMPLETE, reloadedSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        assertEquals(1, client.findAllByAttribute(Submission.class, "publication", pubUri).size());

        //we should have ONLY ONE publication for this pmid
        assertEquals(1, client.findAllByAttribute(Publication.class, "pmid", pmid1).size());

        //we should have ONLY ONE repoCopy for this publication
        assertEquals(1, client.findAllByAttribute(RepositoryCopy.class, "publication", pubUri).size());

        //validate the new repo copy.
        validateRepositoryCopy(repocopyUri);

    }

    /**
     * Submission existed for repository/grant/user and there is a Deposit. Publication is now compliant.
     * This should create a repoCopy with compliant status and associate with publication and Deposit
     *
     * @throws Exception
     */
    @Test
    public void testCompliantPublicationExistingSubmissionAndDeposit() throws Exception {
        URI grantUri1 = createGrant(grant1, user1);

        //we should start with no publication for this pmid
        assertNull(client.findByAttribute(Publication.class, "pmid", pmid1));

        //create existing publication
        Publication publication = newPublication();
        pubUri = client.createResource(publication);

        //a submission existed but had no repocopy
        Submission preexistingSub = client.createAndReadResource(
            newSubmission1(grantUri1, true, SubmissionStatus.SUBMITTED), Submission.class);

        Deposit preexistingDeposit = new Deposit();
        preexistingDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        preexistingDeposit.setRepository(ConfigUtil.getNihmsRepositoryUri());
        preexistingDeposit.setSubmission(preexistingSub.getId());
        URI preexistingDepositUri = client.createResource(preexistingDeposit);

        //wait for fake pre-existing deposit to appear
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Deposit.class, "@id", preexistingDepositUri);
            assertNotNull(uri);
        });

        //now we have an existing publication, deposit, and submission for same grant/repo...
        //do transform/load to make sure we get a repocopy and the deposit record is updated
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(RepositoryCopy.class, "externalIds", pub.getPmcId());
            assertNotNull(uri);
            repocopyUri = uri;
        });

        Submission reloadedPreexistingSub = client.readResource(preexistingSub.getId(), Submission.class);
        assertEquals(SubmissionStatus.COMPLETE, reloadedPreexistingSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        assertEquals(1, client.findAllByAttribute(Submission.class, "publication", pubUri).size());

        //we should have ONLY ONE publication for this pmid
        assertEquals(1, client.findAllByAttribute(Publication.class, "pmid", pmid1).size());

        //we should have ONLY ONE repoCopy for this publication
        assertEquals(1, client.findAllByAttribute(RepositoryCopy.class, "publication", pubUri).size());

        //validate the new repo copy
        validateRepositoryCopy(repocopyUri);

        //check repository copy link added, but status did not change... status managed by deposit service
        Deposit deposit = client.readResource(preexistingDepositUri, Deposit.class);
        assertEquals(DepositStatus.ACCEPTED, deposit.getDepositStatus());
        assertEquals(repocopyUri, deposit.getRepositoryCopy());

    }

    /**
     * Submission existed for repository/grant/user. RepoCopy also existed but no deposit.
     * RepoCopy was previously in process but is now compliant. This should update RepoCopy
     * status to reflect completion. There is also a new PMCID where previously there was only NIHMSID
     *
     * @throws Exception
     */
    @Test
    public void testCompliantPublicationExistingSubmissionAndRepoCopy() throws Exception {
        URI grantUri1 = createGrant(grant1, user1);

        //we should start with no publication for this pmid
        assertNull(client.findByAttribute(Publication.class, "pmid", pmid1));

        //create existing publication
        Publication publication = newPublication();
        pubUri = client.createResource(publication);

        //a submission existed but had no repocopy
        URI submissionuri = client.createResource(newSubmission1(grantUri1, true, SubmissionStatus.SUBMITTED));

        RepositoryCopy preexistingRepoCopy = new RepositoryCopy();
        preexistingRepoCopy.setPublication(pubUri);
        preexistingRepoCopy.setRepository(ConfigUtil.getNihmsRepositoryUri());
        preexistingRepoCopy.setCopyStatus(CopyStatus.IN_PROGRESS);
        List<String> externalIds = new ArrayList<String>();
        externalIds.add(nihmsId1);
        preexistingRepoCopy.setExternalIds(externalIds);
        URI repocopyUri = client.createResource(preexistingRepoCopy);

        //wait for pre-existing repoCopy to appear
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(RepositoryCopy.class, "@id", repocopyUri);
            assertNotNull(uri);
        });

        //now we have an existing publication and submission for same grant/repo... do transform/load to make sure we
        // get a repocopy
        NihmsPublication pub = newCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //wait for repoCopy to update
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(RepositoryCopy.class, "externalIds", pub.getPmcId());
            assertNotNull(uri);
        });

        //we should have ONLY ONE submission for this pmid
        assertEquals(1, client.findAllByAttribute(Submission.class, "publication", pubUri).size());

        //we should have ONLY ONE publication for this pmid
        assertEquals(1, client.findAllByAttribute(Publication.class, "pmid", pmid1).size());

        //we should have ONLY ONE repoCopy for this publication
        assertEquals(1, client.findAllByAttribute(RepositoryCopy.class, "publication", pubUri).size());

        //validate the repo copy to make sure it was updated.
        validateRepositoryCopy(repocopyUri);

        Submission submission = client.readResource(submissionuri, Submission.class);
        assertEquals(SubmissionStatus.COMPLETE, submission.getSubmissionStatus());
    }

    private NihmsPublication newCompliantNihmsPub() {
        return new NihmsPublication(NihmsStatus.COMPLIANT, pmid1, grant1, nihmsId1, pmcid1, dateval, dateval, dateval,
                                    dateval, title);
    }

    private Publication newPublication() throws Exception {
        Publication publication = new Publication();
        publication.setDoi(doi);
        publication.setPmid(pmid1);
        publication.setIssue(issue);
        publication.setTitle(title);
        return publication;
    }

    private Submission newSubmission1(URI grantUri1, boolean submitted, SubmissionStatus status) throws Exception {
        Submission submission1 = new Submission();
        List<URI> grants = new ArrayList<URI>();
        grants.add(grantUri1);
        submission1.setGrants(grants);
        submission1.setPublication(pubUri);
        submission1.setSubmitter(new URI(user1));
        submission1.setSource(Source.OTHER);
        submission1.setSubmitted(submitted);
        submission1.setSubmissionStatus(status);
        List<URI> repos = new ArrayList<URI>();
        repos.add(ConfigUtil.getNihmsRepositoryUri());
        submission1.setRepositories(repos);
        return submission1;
    }

    private Submission newSubmission2(URI grantUri2, boolean submitted, SubmissionStatus status) throws Exception {
        Submission submission2 = new Submission();
        List<URI> grants = new ArrayList<URI>();
        grants.add(grantUri2);
        submission2.setGrants(grants);
        submission2.setPublication(pubUri);
        submission2.setSubmitter(new URI(user2));
        submission2.setSource(Source.PASS);
        submission2.setSubmitted(submitted);
        submission2.setSubmissionStatus(status);
        List<URI> repos = new ArrayList<URI>();
        repos.add(new URI("fake:repo"));
        submission2.setRepositories(repos);
        return submission2;
    }

    private void validateRepositoryCopy(URI uri) {
        RepositoryCopy repoCopy = client.readResource(uri, RepositoryCopy.class);
        //check fields in repoCopy
        assertNotNull(repoCopy);
        assertEquals(CopyStatus.COMPLETE, repoCopy.getCopyStatus());
        assertTrue(repoCopy.getExternalIds().contains(nihmsId1));
        assertTrue(repoCopy.getExternalIds().contains(pmcid1));
        assertEquals(ConfigUtil.getNihmsRepositoryUri(), repoCopy.getRepository());
        assertTrue(repoCopy.getAccessUrl().toString().contains(pmcid1));
    }

}
