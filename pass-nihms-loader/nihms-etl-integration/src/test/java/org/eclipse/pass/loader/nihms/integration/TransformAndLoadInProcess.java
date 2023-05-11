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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.pass.loader.nihms.NihmsTransformLoadService;
import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.model.Deposit;
import org.eclipse.pass.model.Deposit.DepositStatus;
import org.eclipse.pass.model.Grant;
import org.eclipse.pass.model.Publication;
import org.eclipse.pass.model.RepositoryCopy;
import org.eclipse.pass.model.RepositoryCopy.CopyStatus;
import org.eclipse.pass.model.Submission;
import org.eclipse.pass.model.Submission.Source;
import org.eclipse.pass.model.Submission.SubmissionStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

/**
 * @author Karen Hanson
 */
public class TransformAndLoadInProcess extends NihmsSubmissionEtlITBase {

    private String pmid1 = "9999999999";
    private String grant1 = "R01 AB123456";
    private String user1 = "http://test:8080/fcrepo/rest/users/55";
    private String nihmsId1 = "NIHMS987654321";
    private String dateval = "12/12/2017";
    private String title = "Article A";
    private String doi = "10.1000/a.abcd.1234";
    private String issue = "3";

    private URI pubUri; //use this to pass a uri out of the scope of attempt()
    private URI submissionUri; //use this to pass a uri out of the scope of attempt()
    private URI repocopyUri; //use this to pass a uri out of the scope of attempt()

    @Before
    public void intiateMocks() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Tests when the publication is completely new and is an in-process
     * publication, submission and RepositoryCopy are created
     *
     * @throws Exception
     */
    @Test
    public void testNewInProcessPublication() throws Exception {
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
        NihmsPublication pub = newInProcessNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //wait for publication to appear
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

        //now make sure we wait for submission to appear
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
        assertEquals(SubmissionStatus.SUBMITTED, submission.getSubmissionStatus());

        repocopyUri = client.findByAttribute(RepositoryCopy.class, "publication", pubUri);
        RepositoryCopy repoCopy = client.readResource(repocopyUri, RepositoryCopy.class);
        //check fields in repoCopy
        validateRepositoryCopy(repoCopy);
    }

    /**
     * Tests scenario where there is an existing Submission submitted via PASS
     * so there is a Deposit record. This is the first time we are seeing a record
     * in-process. This should create a repo-copy with NihmsId and update the Deposit
     * to link RepositoryCopy
     */
    @Test
    public void testInProcessExistingSubmissionDeposit() throws Exception {
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
        NihmsPublication pub = newInProcessNihmsPub();
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
        assertEquals(preexistingSub, reloadedPreexistingSub); //should not have been affected

        //we should have ONLY ONE submission for this pmid
        assertEquals(1, client.findAllByAttribute(Submission.class, "publication", pubUri).size());

        //we should have ONLY ONE publication for this pmid
        assertEquals(1, client.findAllByAttribute(Publication.class, "pmid", pmid1).size());

        //we should have ONLY ONE repoCopy for this publication
        assertEquals(1, client.findAllByAttribute(RepositoryCopy.class, "publication", pubUri).size());

        RepositoryCopy repositoryCopy = client.readResource(repocopyUri, RepositoryCopy.class);
        //validate the new repo copy
        validateRepositoryCopy(repositoryCopy);

        //check repository copy link added, but status did not change... status managed by deposit service
        Deposit deposit = client.readResource(preexistingDepositUri, Deposit.class);
        assertEquals(DepositStatus.ACCEPTED, deposit.getDepositStatus());
        assertEquals(repocopyUri, deposit.getRepositoryCopy());

    }

    private NihmsPublication newInProcessNihmsPub() {
        return new NihmsPublication(NihmsStatus.IN_PROCESS, pmid1, grant1, nihmsId1, null, dateval, dateval, null, null,
                                    title);
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
        submission1.setSource(Source.PASS);
        submission1.setSubmitted(submitted);
        submission1.setSubmissionStatus(status);
        List<URI> repos = new ArrayList<URI>();
        repos.add(ConfigUtil.getNihmsRepositoryUri());
        submission1.setRepositories(repos);
        return submission1;
    }

    private void validateRepositoryCopy(RepositoryCopy repoCopy) {
        //check fields in repoCopy
        assertNotNull(repoCopy);
        assertEquals(1, repoCopy.getExternalIds().size());
        assertEquals(nihmsId1, repoCopy.getExternalIds().get(0));
        assertEquals(ConfigUtil.getNihmsRepositoryUri(), repoCopy.getRepository());
        assertEquals(CopyStatus.IN_PROGRESS, repoCopy.getCopyStatus());
        assertNull(repoCopy.getAccessUrl());
    }

}
