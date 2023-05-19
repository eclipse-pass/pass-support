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

/*import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;*/

import java.net.URI;

import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
/*import org.eclipse.pass.model.Deposit;
import org.eclipse.pass.model.Deposit.DepositStatus;
import org.eclipse.pass.model.Grant;
import org.eclipse.pass.model.Publication;
import org.eclipse.pass.model.RepositoryCopy;
import org.eclipse.pass.model.RepositoryCopy.CopyStatus;
import org.eclipse.pass.model.Submission;
import org.eclipse.pass.model.Submission.Source;
import org.eclipse.pass.model.Submission.SubmissionStatus;
import org.junit.Before;
import org.junit.Test;*/

/**
 * @author Karen Hanson
 */
public class TransformAndLoadNonCompliant extends NihmsSubmissionEtlITBase {

    private String pmid1 = "9999999999";
    private String grant1 = "R01 AB123456";
    private String grant2 = "R02 CD123456";
    private String user1 = "http://test:8080/fcrepo/rest/users/55";
    private String nihmsId1 = "NIHMS987654321";
    private String dateval = "12/12/2017";
    private String title = "Article A";
    private String doi = "10.1000/a.abcd.1234";
    private String issue = "3";

    private URI pubUri; //use this to pass a uri out of the scope of attempt()
    private URI submissionUri; //use this to pass a uri out of the scope of attempt()
    private URI repocopyUri; //use this to pass a uri out of the scope of attempt()

/*    @Before
    public void intiateMocks() {
        MockitoAnnotations.initMocks(this);
    }*/

    /**
     * Tests when the publication is completely new and is non-compliant
     * publication, submission but no RepositoryCopy is created
     *
     * @throws Exception
     */
/*
    @Test
    public void testNewNonCompliantPublication() throws Exception {
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
        NihmsPublication pub = newNonCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //now make sure we wait for submission, should only be one from the test
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
        assertEquals(ConfigUtil.getNihmsRepositoryId(), submission.getRepositories().get(0));
        assertEquals(1, submission.getRepositories().size());
        assertEquals(Source.OTHER, submission.getSource());
        assertFalse(submission.getSubmitted());
        assertEquals(user1, submission.getSubmitter().toString());
        assertEquals(null, submission.getSubmittedDate());
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED, submission.getSubmissionStatus());

        repocopyUri = client.findByAttribute(RepositoryCopy.class, "publication", pubUri);
        assertNull(repocopyUri);
    }
*/

    /**
     * Submission existed for repository/grant/user and there is a Deposit. Publication is now non-compliant.
     * This should create a repoCopy with STALLED status and associate it with Deposti
     *
     * @throws Exception
     */
/*    @Test
    public void testStalledPublicationExistingSubmissionAndDeposit() throws Exception {
        URI grantUri1 = createGrant(grant1, user1);

        //we should start with no publication for this pmid
        assertNull(client.findByAttribute(Publication.class, "pmid", pmid1));

        //create existing publication
        Publication publication = newPublication();
        pubUri = client.createResource(publication);

        //a submission existed but had no repocopy
        Submission preexistingSub = newSubmission1(grantUri1, true, SubmissionStatus.SUBMITTED);
        preexistingSub.setSubmitted(true);
        preexistingSub.setSource(Source.PASS);
        preexistingSub = client.createAndReadResource(preexistingSub, Submission.class);

        Deposit preexistingDeposit = new Deposit();
        preexistingDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        preexistingDeposit.setRepository(ConfigUtil.getNihmsRepositoryId());
        preexistingDeposit.setSubmission(preexistingSub.getId());
        preexistingDeposit = client.createAndReadResource(preexistingDeposit, Deposit.class);
        URI preexistingDepositUri = preexistingDeposit.getId();

        //wait for fake pre-existing deposit to appear
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Deposit.class, "@id", preexistingDepositUri);
            assertNotNull(uri);
        });

        //now we have an existing publication, deposit, and submission for same grant/repo...
        //do transform/load to make sure we get a stalled repocopy and the deposit record is updated
        NihmsPublication pub = newNonCompliantStalledNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(RepositoryCopy.class, "externalIds", pub.getNihmsId());
            assertNotNull(uri);
            repocopyUri = uri;
        });

        Submission reloadedPreexistingSub = client.readResource(preexistingSub.getId(), Submission.class);
        preexistingSub.setSubmissionStatus(SubmissionStatus.NEEDS_ATTENTION);
        assertEquals(preexistingSub, reloadedPreexistingSub); //should not have been affected

        //we should have ONLY ONE submission for this pmid
        assertEquals(1, client.findAllByAttribute(Submission.class, "publication", pubUri).size());

        //we should have ONLY ONE publication for this pmid
        assertEquals(1, client.findAllByAttribute(Publication.class, "pmid", pmid1).size());

        //we should have ONLY ONE repoCopy for this publication
        assertEquals(1, client.findAllByAttribute(RepositoryCopy.class, "publication", pubUri).size());

        //validate the new repo copy
        RepositoryCopy repoCopy = client.readResource(repocopyUri, RepositoryCopy.class);
        validateRepositoryCopy(repoCopy);
        assertEquals(CopyStatus.STALLED, repoCopy.getCopyStatus());

        //check repository copy link added, but status did not change... status managed by deposit service
        Deposit deposit = client.readResource(preexistingDepositUri, Deposit.class);
        assertEquals(DepositStatus.ACCEPTED, deposit.getDepositStatus());
        assertEquals(repocopyUri, deposit.getRepositoryCopy());

    }*/

    /**
     * Submission exists for publication/user combo but not yet submitted and does not list Grant or NIHMS Repo.
     * Make sure NIHMS Repo and Grant added.
     *
     * @throws Exception
     */
/*
    @Test
    public void testAddingToExistingUnsubmittedSubmission() throws Exception {
        URI grantUri1 = createGrant(grant1, user1);
        URI grantUri2 = createGrant(grant2, user1);

        //we should start with no publication for this pmid
        assertNull(client.findByAttribute(Publication.class, "pmid", pmid1));

        //create existing publication
        Publication publication = newPublication();
        pubUri = client.createResource(publication);

        //a submission existed for the user/pub combo and is unsubmitted, but has a different grant/repo
        Submission preexistingSub = newSubmission1(grantUri1, false, SubmissionStatus.MANUSCRIPT_REQUIRED);
        preexistingSub.setSubmitted(false);
        preexistingSub.setSource(Source.PASS);
        List<URI> grants = new ArrayList<URI>();
        grants.add(grantUri2);
        preexistingSub.setGrants(grants);
        List<URI> repos = new ArrayList<URI>();
        repos.add(new URI("fake:repo"));
        preexistingSub.setRepositories(repos);
        URI preexistingSubUri = client.createResource(preexistingSub);

        //now make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Submission.class, "publication", pubUri);
            assertNotNull(uri);
            submissionUri = uri;
        });

        //now we have an existing publication, submission for same user/publication...
        //do transform/load to make sure we get an updated submission that includes grant/repo
        NihmsPublication pub = newNonCompliantNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Submission.class, "repositories",
                                                   ConfigUtil.getNihmsRepositoryId());
            assertNotNull(uri);
        });

        Submission reloadedPreexistingSub = client.readResource(preexistingSubUri, Submission.class);
        assertFalse(reloadedPreexistingSub.getSubmitted());
        assertTrue(reloadedPreexistingSub.getRepositories().contains(ConfigUtil.getNihmsRepositoryId()));
        assertTrue(reloadedPreexistingSub.getRepositories().contains(new URI("fake:repo")));
        assertEquals(2, reloadedPreexistingSub.getRepositories().size());
        assertTrue(reloadedPreexistingSub.getGrants().contains(grantUri1));
        assertTrue(reloadedPreexistingSub.getGrants().contains(grantUri2));
        assertEquals(2, reloadedPreexistingSub.getGrants().size());
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED, reloadedPreexistingSub.getSubmissionStatus());

        //we should have ONLY ONE submission for this pmid
        assertEquals(1, client.findAllByAttribute(Submission.class, "publication", pubUri).size());

        //we should have ONLY ONE publication for this pmid
        assertEquals(1, client.findAllByAttribute(Publication.class, "pmid", pmid1).size());

        //we should have ONLY ONE repoCopy for this publication
        assertEquals(0, client.findAllByAttribute(RepositoryCopy.class, "publication", pubUri).size());

    }
*/

    private NihmsPublication newNonCompliantNihmsPub() {
        return new NihmsPublication(NihmsStatus.NON_COMPLIANT, pmid1, grant1, null, null, null, null, null, null,
                                    title);
    }

    private NihmsPublication newNonCompliantStalledNihmsPub() {
        return new NihmsPublication(NihmsStatus.NON_COMPLIANT, pmid1, grant1, nihmsId1, null, dateval, dateval, null,
                                    null, title);
    }

/*    private Publication newPublication() throws Exception {
        Publication publication = new Publication();
        publication.setDoi(doi);
        publication.setPmid(pmid1);
        publication.setIssue(issue);
        publication.setTitle(title);
        return publication;
    }*/

/*    private Submission newSubmission1(URI grantUri1, boolean submitted, SubmissionStatus status) throws Exception {
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
        repos.add(ConfigUtil.getNihmsRepositoryId());
        submission1.setRepositories(repos);
        return submission1;
    }*/

    //this validation does not check repo copy status as it varies for non-compliant
/*    private void validateRepositoryCopy(RepositoryCopy repoCopy) {
        //check fields in repoCopy
        assertNotNull(repoCopy);
        assertEquals(1, repoCopy.getExternalIds().size());
        assertEquals(nihmsId1, repoCopy.getExternalIds().get(0));
        assertEquals(ConfigUtil.getNihmsRepositoryId(), repoCopy.getRepository());
        assertNull(repoCopy.getAccessUrl());
    }*/

}
