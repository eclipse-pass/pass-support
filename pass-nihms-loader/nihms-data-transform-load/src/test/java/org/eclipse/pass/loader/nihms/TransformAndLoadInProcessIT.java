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
    private final String nihmsId1 = "NIHMS987654321";
    private final String title = "Article A";
    private final String doi = "10.1000/a.abcd.1234";
    private final String issue = "3";

    /**
     * Tests when the publication is completely new and is an in-process
     * publication, submission and RepositoryCopy are created
     *
     * @throws Exception if there is an error
     */
    @Test
    public void testNewInProcessPublication() throws Exception {
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        User user = new User();
        passClient.createObject(user);

        String grant1 = UUID.randomUUID().toString();
        String pmid1 = UUID.randomUUID().toString();

        Grant grant = createGrant(grant1, user);

        setMockPMRecord(pmid1);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //load all new publication, repo copy and submission
        NihmsPublication pub = newInProcessNihmsPub(grant1, pmid1);
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //wait for publication to appear
        PassClientSelector<Publication> testPubSelector = new PassClientSelector<>(Publication.class);
        testPubSelector.setFilter(RSQL.equals("pmid", pmid1));
        List<Publication> testPubs = passClient.streamObjects(testPubSelector).toList();
        assertNotNull(testPubs.get(0));

        Publication publication = passClient.getObject(Publication.class, testPubs.get(0).getId());
        //spot check publication fields
        assertEquals(doi, publication.getDoi());
        assertEquals(title, publication.getTitle());
        assertEquals(issue, publication.getIssue());

        //now make sure we wait for submission to appear
        subSelector.setFilter(RSQL.equals("publication.id", testPubs.get(0).getId()));
        Submission submission = passClient.streamObjects(subSelector).findFirst().orElseThrow();

        //check fields in submission
        assertEquals(grant.getId(), submission.getGrants().get(0).getId());
        assertEquals(ConfigUtil.getNihmsRepositoryId(), submission.getRepositories().get(0).getId());
        assertEquals(1, submission.getRepositories().size());
        assertEquals(Source.OTHER, submission.getSource());
        assertTrue(submission.getSubmitted());
        assertEquals(user.getId(), submission.getSubmitter().getId());
        assertEquals(12, submission.getSubmittedDate().getMonthValue());

        // TODO The NihmsPublication date is parsed using the local date
        // and then turned to UTC at the start of the day
        // assertEquals(10, submission.getSubmittedDate().getDayOfMonth());

        assertEquals(2001, submission.getSubmittedDate().getYear());
        assertEquals(SubmissionStatus.SUBMITTED, submission.getSubmissionStatus());

        repoCopySelector.setFilter(RSQL.equals("publication.id", testPubs.get(0).getId()));
        RepositoryCopy repoCopy = passClient.streamObjects(repoCopySelector).findAny().orElseThrow();

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
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        User user = new User();
        passClient.createObject(user);

        String grant1 = UUID.randomUUID().toString();
        String pmid1 = UUID.randomUUID().toString();

        Grant grant = createGrant(grant1, user);

        //we should start with no publication for this pmid
        pubSelector.setFilter(RSQL.equals("pmid", pmid1));
        Optional<Publication> testPub = passClient.streamObjects(pubSelector).findAny();
        assertFalse(testPub.isPresent());

        //create existing publication
        Publication publication = newPublication(pmid1);
        passClient.createObject(publication);

        //a submission existed but had no repocopy
        Submission preexistingSub = newSubmission1(grant, publication, user, true,
                SubmissionStatus.SUBMITTED);
        passClient.createObject(preexistingSub);

        Deposit preexistingDeposit = new Deposit();
        preexistingDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        preexistingDeposit.setRepository(new Repository(ConfigUtil.getNihmsRepositoryId()));
        preexistingDeposit.setSubmission(new Submission(preexistingSub.getId()));
        passClient.createObject(preexistingDeposit);

        //now we have an existing publication, deposit, and submission for same grant/repo...
        //do transform/load to make sure we get a repocopy and the deposit record is updated
        NihmsPublication pub = newInProcessNihmsPub(grant1, pmid1);
        NihmsTransformLoadService transformLoadService = new NihmsTransformLoadService(nihmsPassClientService,
                                                                                       mockPmidLookup, statusService);
        transformLoadService.transformAndLoadNihmsPub(pub);

        //make sure we wait for submission, should only be one from the test
        repoCopySelector.setFilter(RSQL.hasMember("externalIds", pub.getNihmsId()));
        String repoCopyId = passClient.selectObjects(repoCopySelector).getObjects().get(0).getId();
        assertNotNull(repoCopyId);

        Submission reloadedPreexistingSub = nihmsPassClientService.readSubmission(preexistingSub.getId());
        verifySubmission(preexistingSub, reloadedPreexistingSub);

        //we should have ONLY ONE submission for this pmid
        subSelector.setFilter(RSQL.equals("publication.id", publication.getId()));
        assertEquals(1, passClient.selectObjects(subSelector).getObjects().size());

        //we should have ONLY ONE publication for this pmid
        PassClientSelector<Publication> pubSelOne = new PassClientSelector<>(Publication.class);
        pubSelOne.setFilter(RSQL.equals("pmid", pmid1));
        assertEquals(1, passClient.selectObjects(pubSelOne).getObjects().size());

        //we should have ONLY ONE repoCopy for this publication
        repoCopySelector.setFilter(RSQL.equals("publication.id", publication.getId()));
        assertEquals(1, passClient.selectObjects(repoCopySelector).getObjects().size());

        RepositoryCopy repositoryCopy = passClient.getObject(RepositoryCopy.class, repoCopyId);
        //validate the new repo copy
        validateRepositoryCopy(repositoryCopy);

        //check repository copy link added, but status did not change... status managed by deposit service
        Deposit deposit = passClient.getObject(Deposit.class, preexistingDeposit.getId());
        assertEquals(DepositStatus.ACCEPTED, deposit.getDepositStatus());
        assertEquals(repoCopyId, deposit.getRepositoryCopy().getId());

    }

    private NihmsPublication newInProcessNihmsPub(String grant1, String pmid1) {
        String dateval = "12/10/2001";
        return new NihmsPublication(NihmsStatus.IN_PROCESS, pmid1, grant1, nihmsId1, null, dateval, dateval, null, null,
                                    title);
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
        submission1.setSource(Source.PASS);
        submission1.setSubmitted(submitted);
        submission1.setSubmissionStatus(status);
        submission1.setRepositories(List.of(new Repository(ConfigUtil.getNihmsRepositoryId())));

        return submission1;
    }

    private void validateRepositoryCopy(RepositoryCopy repoCopy) {
        //check fields in repoCopy
        assertNotNull(repoCopy);
        assertEquals(1, repoCopy.getExternalIds().size());
        assertEquals(nihmsId1, repoCopy.getExternalIds().get(0));
        assertEquals(ConfigUtil.getNihmsRepositoryId(), repoCopy.getRepository().getId());
        assertEquals(CopyStatus.IN_PROGRESS, repoCopy.getCopyStatus());
        assertNull(repoCopy.getAccessUrl());
    }

}
