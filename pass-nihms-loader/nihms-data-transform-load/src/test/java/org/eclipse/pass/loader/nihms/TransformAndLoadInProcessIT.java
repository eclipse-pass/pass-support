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
public class TransformAndLoadInProcessIT extends NihmsSubmissionEtlITBase {

    private String pmid1 = "9999999999";
    private String grant1 = "R01 AB123456";
    private String user1 = "55";
    private String nihmsId1 = "NIHMS987654321";
    private String dateval = "12/12/2017";
    private String title = "Article A";
    private String doi = "10.1000/a.abcd.1234";
    private String issue = "3";

    private String pubId; //use this to pass a uri out of the scope of attempt()
    private String submissionId; //use this to pass a uri out of the scope of attempt()
    private String repoCopyId; //use this to pass a uri out of the scope of attempt()


    /**
     * Tests when the publication is completely new and is an in-process
     * publication, submission and RepositoryCopy are created
     *
     * @throws Exception
     */
    @Test
    public void testNewInProcessPublication() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String grantUri = createGrant(grant1, user1);
        //wait for new grant appears
        attempt(RETRIES, () -> {
            final String testId;
            try {
                testId = passClient.getObject(Grant.class, grantUri).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(testId);
        });

        setMockPMRecord(pmid1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertNull(passClient.selectObjects(pubSelector).getObjects().get(0).getId());

        //load all new publication, repo copy and submission
        NihmsPublication pub = newInProcessNihmsPub();
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //wait for publication to appear
        attempt(RETRIES, () -> {
            pubSelector.setFilter(RSQL.equals("pmid", pmid1));
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

        //now make sure we wait for submission to appear
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
        assertEquals(grantUri, submission.getGrants().get(0));
        assertEquals(ConfigUtil.getNihmsRepositoryId(), submission.getRepositories().get(0));
        assertEquals(1, submission.getRepositories().size());
        assertEquals(Source.OTHER, submission.getSource());
        assertTrue(submission.getSubmitted());
        assertEquals(user1, submission.getSubmitter().toString());
        assertEquals(12, submission.getSubmittedDate().getMonthValue());
        assertEquals(12, submission.getSubmittedDate().getDayOfMonth());
        assertEquals(2017, submission.getSubmittedDate().getYear());
        assertEquals(SubmissionStatus.SUBMITTED, submission.getSubmissionStatus());

        repoCopySelector.setFilter(RSQL.equals("publication", pubId));
        repoCopyId = passClient.selectObjects(repoCopySelector).getObjects().get(0).getId();
        RepositoryCopy repoCopy = passClient.getObject(RepositoryCopy.class, repoCopyId);
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
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Deposit> depoSelector = new PassClientSelector<>(Deposit.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        String grantUri1 = createGrant(grant1, user1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        assertNull(passClient.selectObjects(pubSelector).getObjects().get(0).getId());

        //create existing publication
        Publication publication = newPublication();
        passClient.createObject(publication);

        //a submission existed but had no repocopy
        Submission preexistingSub = newSubmission1(grantUri1, true, SubmissionStatus.SUBMITTED);
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
        //do transform/load to make sure we get a repocopy and the deposit record is updated
        NihmsPublication pub = newInProcessNihmsPub();
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

        Submission reloadedPreexistingSub = passClient.getObject(Submission.class, preexistingSub.getId());
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

        RepositoryCopy repositoryCopy = passClient.getObject(RepositoryCopy.class, repoCopyId);
        //validate the new repo copy
        validateRepositoryCopy(repositoryCopy);

        //check repository copy link added, but status did not change... status managed by deposit service
        Deposit deposit = passClient.getObject(Deposit.class, preexistingDeposit.getId());
        assertEquals(DepositStatus.ACCEPTED, deposit.getDepositStatus());
        assertEquals(repoCopyId, deposit.getRepositoryCopy());

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

    private Submission newSubmission1(String grantUri1, boolean submitted, SubmissionStatus status) throws Exception {
        Submission submission1 = new Submission();
        List<Grant> grants = new ArrayList<>();
        grants.add(new Grant(grantUri1));
        submission1.setGrants(grants);
        submission1.setPublication(new Publication(pubId));
        submission1.setSubmitter(new User(user1));
        submission1.setSource(Source.PASS);
        submission1.setSubmitted(submitted);
        submission1.setSubmissionStatus(status);
        List<Repository> repos = new ArrayList<>();
        repos.add(new Repository(ConfigUtil.getNihmsRepositoryId()));
        submission1.setRepositories(repos);
        return submission1;
    }

    private void validateRepositoryCopy(RepositoryCopy repoCopy) {
        //check fields in repoCopy
        assertNotNull(repoCopy);
        assertEquals(1, repoCopy.getExternalIds().size());
        assertEquals(nihmsId1, repoCopy.getExternalIds().get(0));
        assertEquals(ConfigUtil.getNihmsRepositoryId(), repoCopy.getRepository());
        assertEquals(CopyStatus.IN_PROGRESS, repoCopy.getCopyStatus());
        assertNull(repoCopy.getAccessUrl());
    }

}
