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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.pass.loader.nihms.client.NihmsPassClientService;
import org.eclipse.pass.loader.nihms.entrez.PmidLookup;
import org.eclipse.pass.loader.nihms.entrez.PubMedEntrezRecord;
import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Source;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for NIHMS Transformer code
 *
 * @author Karen Hanson
 */
@ExtendWith(MockitoExtension.class)
public class SubmissionTransformerTest {

    private static final String grantId = "1";
    private static final String submissionId = "1";
    private static final String nihmsRepositoryId = "1";
    private static final String journalId = "1";
    private static final String publicationId = "1";
    private static final String userId = "1";

    private static final String nihmsId = "abcdefg";
    private static final String pmcId = "9876543";
    private static final String depositDate = "12/12/2018";

    //PubMedEntrezRecord fields
    private static final String doi = "https://doi.org/10.001/0101ab";
    private static final String pmid = "123456";
    private static final String title = "Test Title";
    private static final String issue = "3";
    private static final String volume = "5";
    private static final String awardNumber = "AB 12345";

    private static final String pmcIdTemplateUrl = "https://example.com/pmc/pmc%s";

    @Mock private NihmsPassClientService clientServiceMock;
    @Mock private PmidLookup pmidLookupMock;
    @Mock private PubMedEntrezRecord pubMedRecordMock;

    private NihmsPublicationToSubmission transformer;

    @BeforeEach
    public void init() {
        transformer = new NihmsPublicationToSubmission(clientServiceMock, pmidLookupMock);
        ReflectionTestUtils.setField(transformer, "nihmsRepositoryId", nihmsRepositoryId);
        ReflectionTestUtils.setField(transformer, "pmcUrlTemplate", pmcIdTemplateUrl);
    }

    /**
     * Tests the scenario where there is no current Publication or Submission in PASS for the article, and no
     * need for a new RepositoryCopy. The returned object should have a Publication and Submission object without
     * an ID and no RepositoryCopies
     */
    @Test
    public void testTransformNewPubNewSubNoRepoCopy() throws Exception {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);

        Grant grant = newTestGrant();

        //Mocking that we have a valid grant URI, PMID, and DOI to use.
        when(clientServiceMock.findMostRecentGrantByAwardNumber(awardNumber)).thenReturn(grant);
        when(clientServiceMock.findPublicationByPmid(pmid)).thenReturn(null);
        when(clientServiceMock.findPublicationByDoi(doi, pmid)).thenReturn(null);
        when(clientServiceMock.readRepository(nihmsRepositoryId)).thenReturn(new Repository(nihmsRepositoryId));

        when(pmidLookupMock.retrievePubMedRecord(Mockito.anyString())).thenReturn(pubMedRecordMock);

        when(pubMedRecordMock.getDoi()).thenReturn(doi);
        when(pubMedRecordMock.getIssue()).thenReturn(issue);
        when(pubMedRecordMock.getVolume()).thenReturn(volume);
        when(pubMedRecordMock.getTitle()).thenReturn(title);

        SubmissionDTO dto = transformer.transform(pub);

        checkPmrValues(dto);
        assertEquals(nihmsRepositoryId, dto.getSubmission().getRepositories().get(0).getId());
        assertNull(dto.getPublication().getId());
        assertNull(dto.getSubmission().getId());
        assertNull(dto.getRepositoryCopy());
    }

    /**
     * Tests the scenario where there is no current Publication or Submission in PASS for the article, and a
     * new RepositoryCopy is needed. The returned object should have a Publication, Submission and RepositoryCopy
     * objects all without an ID
     */
    @Test
    public void testTransformNewPubNewSubNewRepoCopy() throws Exception {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setNihmsId(nihmsId);
        pub.setFileDepositedDate(depositDate);
        pub.setInitialApprovalDate(depositDate);
        pub.setTaggingCompleteDate(depositDate);

        Grant grant = newTestGrant();
        when(clientServiceMock.findMostRecentGrantByAwardNumber(awardNumber)).thenReturn(grant);
        when(clientServiceMock.findPublicationByPmid(pmid)).thenReturn(null);
        when(clientServiceMock.findPublicationByDoi(doi, pmid)).thenReturn(null);
        when(clientServiceMock.readRepository(nihmsRepositoryId)).thenReturn(new Repository(nihmsRepositoryId));

        when(pmidLookupMock.retrievePubMedRecord(pmid)).thenReturn(pubMedRecordMock);

        when(pubMedRecordMock.getDoi()).thenReturn(doi);
        when(pubMedRecordMock.getIssue()).thenReturn(issue);
        when(pubMedRecordMock.getVolume()).thenReturn(volume);
        when(pubMedRecordMock.getTitle()).thenReturn(title);

        SubmissionDTO dto = transformer.transform(pub);

        checkPmrValues(dto);
        assertEquals(nihmsRepositoryId, dto.getSubmission().getRepositories().get(0).getId());

        assertNull(dto.getPublication().getId());
        assertNull(dto.getSubmission().getId());
        assertNull(dto.getRepositoryCopy().getId());
        assertEquals(CopyStatus.IN_PROGRESS, dto.getRepositoryCopy().getCopyStatus());
        assertEquals("NIHMS" + nihmsId, dto.getRepositoryCopy().getExternalIds().get(0));
        assertEquals(true, dto.getSubmission().getSubmitted());
        assertNotNull(dto.getSubmission().getSubmittedDate());

    }

    /**
     * Tests the scenario where there is already a Publication and Submission in PASS for the article,
     * but now there is a compliant repo copy
     */
    @Test
    public void testTransformUpdatePubUpdateSubNewCompliantRepoCopy() throws Exception {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.COMPLIANT);
        pub.setNihmsId(nihmsId);
        pub.setFileDepositedDate(depositDate);
        pub.setInitialApprovalDate(depositDate);
        pub.setTaggingCompleteDate(depositDate);
        pub.setPmcId(pmcId);

        Publication publication = newTestPublication();
        Submission submission = newTestSubmission();

        List<Submission> submissions = new ArrayList<>();
        submissions.add(submission);

        Grant grant = newTestGrant();
        when(clientServiceMock.findMostRecentGrantByAwardNumber(awardNumber)).thenReturn(grant);

        when(clientServiceMock.findPublicationByPmid(pmid)).thenReturn(publication);
        when(clientServiceMock.findSubmissionsByPublicationAndUserId(publication.getId(), grant.getPi().getId()))
                .thenReturn(submissions);
        when(clientServiceMock.readRepository(nihmsRepositoryId)).thenReturn(new Repository(nihmsRepositoryId));

        SubmissionDTO dto = transformer.transform(pub);

        checkPmrValues(dto);
        assertEquals(nihmsRepositoryId, dto.getSubmission().getRepositories().get(0).getId());

        assertEquals(true, dto.getSubmission().getSubmitted());
        assertNotNull(dto.getSubmission().getSubmittedDate());

        assertNull(dto.getRepositoryCopy().getId());
        assertEquals(CopyStatus.COMPLETE, dto.getRepositoryCopy().getCopyStatus());
        assertTrue(dto.getRepositoryCopy().getExternalIds().contains("NIHMS" + nihmsId));
        assertTrue(dto.getRepositoryCopy().getExternalIds().contains("PMC" + pmcId));
        assertEquals(2, dto.getRepositoryCopy().getExternalIds().size());

    }

    /**
     * Tests the scenario where there is a Deposit with a reference to a NihmsId. There will already
     * be a Submission, Publication, and RepositoryCopy.
     */
    @Test
    public void testTransformWithNihmsIdDepositStatusRef() throws Exception {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.COMPLIANT);
        pub.setNihmsId(nihmsId);
        pub.setFileDepositedDate(depositDate);
        pub.setInitialApprovalDate(depositDate);
        pub.setTaggingCompleteDate(depositDate);
        pub.setPmcId(pmcId);

        Publication publication = newTestPublication();

        // Assume no DOI was given
        publication.setDoi(null);
        // No PMID because it comes through PASS
        publication.setPmid(null);

        Submission submission = newTestSubmission();
        submission.setPublication(publication);
        submission.setSource(Source.PASS);
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);

        RepositoryCopy rc = new RepositoryCopy("rc1");
        rc.setPublication(publication);
        rc.setCopyStatus(CopyStatus.IN_PROGRESS);
        rc.setRepository(submission.getRepositories().get(0));

        Deposit deposit = new Deposit("deposit1");
        deposit.setDepositStatusRef(NihmsPassClientService.NIHMS_DEP_STATUS_REF_PREFIX + nihmsId);
        deposit.setSubmission(submission);
        deposit.setRepositoryCopy(rc);
        deposit.setDepositStatus(DepositStatus.SUBMITTED);

        List<Submission> submissions = new ArrayList<>();
        submissions.add(submission);

        Grant grant = newTestGrant();
        when(clientServiceMock.findMostRecentGrantByAwardNumber(awardNumber)).thenReturn(grant);
        when(clientServiceMock.findPublicationByPmid(pmid)).thenReturn(null);

        when(pmidLookupMock.retrievePubMedRecord(Mockito.anyString())).thenReturn(pubMedRecordMock);
        when(pubMedRecordMock.getDoi()).thenReturn(doi);

        when(clientServiceMock.findNihmsDepositbyNihmsId(nihmsId)).thenReturn(deposit);
        when(clientServiceMock.findNihmsRepositoryCopyForPubId(publicationId)).thenReturn(rc);
        when(clientServiceMock.findSubmissionsByPublicationAndUserId(publication.getId(), grant.getPi().getId()))
                .thenReturn(submissions);

        SubmissionDTO dto = transformer.transform(pub);

        assertEquals(title, dto.getPublication().getTitle());
        assertEquals(volume, dto.getPublication().getVolume());
        assertEquals(issue, dto.getPublication().getIssue());
        assertEquals(pmid, dto.getPublication().getPmid());
        assertEquals(doi, dto.getPublication().getDoi());
        assertEquals(grantId, dto.getSubmission().getGrants().get(0).getId());
        assertEquals(Source.PASS, dto.getSubmission().getSource());
        assertEquals(userId, dto.getSubmission().getSubmitter().getId());
        assertEquals(nihmsRepositoryId, dto.getSubmission().getRepositories().get(0).getId());

        assertEquals(true, dto.getSubmission().getSubmitted());
        assertNotNull(dto.getSubmission().getSubmittedDate());

        assertEquals(CopyStatus.COMPLETE, dto.getRepositoryCopy().getCopyStatus());
        assertTrue(dto.getRepositoryCopy().getExternalIds().contains("NIHMS" + nihmsId));
        assertTrue(dto.getRepositoryCopy().getExternalIds().contains("PMC" + pmcId));
        assertEquals(2, dto.getRepositoryCopy().getExternalIds().size());

        assertEquals(SubmissionStatus.COMPLETE, dto.getSubmission().getSubmissionStatus());
        assertEquals(DepositStatus.ACCEPTED, dto.getDeposit().getDepositStatus());
        assertEquals(AggregatedDepositStatus.IN_PROGRESS, dto.getSubmission().getAggregatedDepositStatus());
    }

    /**
     * Tests the scenario where there is already a Publication, Submission, Deposit, and RepositoryCopy
     * in PASS for the article because a PASS submission was done by a user.
     * The NIHMS submission is done and now the corresponding PASS submission should be updated.
     */
    @Test
    public void testTransformUpdatePubAddGrantRepoToSubNoRepoCopy() throws Exception {
        NihmsPublication pub = newTestPub();
        //if its compliant it will be marked as submitted regardless of whether there is a repoCopy,
        // so make this pub in-process.
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);

        Publication publication = newTestPublication();

        Submission submission = newTestSubmission();
        submission.setSubmitted(false);
        submission.setSubmittedDate(null);
        //no repos or grants yet
        submission.setRepositories(new ArrayList<>());
        submission.setGrants(new ArrayList<>());

        List<Submission> submissions = new ArrayList<>();
        submissions.add(submission);

        Grant grant = newTestGrant();
        when(clientServiceMock.findMostRecentGrantByAwardNumber(awardNumber)).thenReturn(grant);

        when(clientServiceMock.findPublicationByPmid(pmid)).thenReturn(publication);
        when(clientServiceMock.findSubmissionsByPublicationAndUserId(publication.getId(), grant.getPi().getId()))
                .thenReturn(submissions);
        when(clientServiceMock.readGrant(grantId)).thenReturn(grant);
        when(clientServiceMock.readRepository(nihmsRepositoryId)).thenReturn(new Repository(nihmsRepositoryId));

        SubmissionDTO dto = transformer.transform(pub);

        checkPmrValues(dto);

        assertEquals(false, dto.getSubmission().getSubmitted());
        assertEquals(nihmsRepositoryId, dto.getSubmission().getRepositories().get(0).getId());
        assertEquals(grant.getId(), dto.getSubmission().getGrants().get(0).getId());
        assertNull(dto.getSubmission().getSubmittedDate());
        assertNull(dto.getRepositoryCopy());
    }

    /**
     * Makes sure exception is thrown if no matching grant is found for the Award Number
     */
    @Test
    public void testTransformNoMatchingGrantThrowsException() throws IOException {
        when(clientServiceMock.findMostRecentGrantByAwardNumber(any())).thenReturn(null);

        NihmsPublication pub = newTestPub();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            transformer.transform(pub);
        });

        String expectedMessage = "No Grant matching award number";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

    private void checkPmrValues(SubmissionDTO dto) {
        assertEquals(title, dto.getPublication().getTitle());
        assertEquals(volume, dto.getPublication().getVolume());
        assertEquals(issue, dto.getPublication().getIssue());
        assertEquals(pmid, dto.getPublication().getPmid());
        assertEquals(doi, dto.getPublication().getDoi());
        assertEquals(grantId, dto.getSubmission().getGrants().get(0).getId());
        assertEquals(Source.OTHER, dto.getSubmission().getSource());
        assertEquals(userId, dto.getSubmission().getSubmitter().getId());
    }

    private Grant newTestGrant() throws Exception {
        User user = new User(userId);
        Grant grant = new Grant(grantId);
        grant.setPi(user);
        grant.setAwardNumber(awardNumber);
        return grant;
    }

    private Publication newTestPublication() throws Exception {
        Journal journal = new Journal(journalId);
        Publication publication = new Publication(publicationId);
        publication.setPmid(pmid);
        publication.setDoi(doi);
        publication.setTitle(title);
        publication.setJournal(journal);
        publication.setVolume(volume);
        publication.setIssue(issue);
        return publication;
    }

    private Submission newTestSubmission() throws Exception {
        Submission submission = new Submission(submissionId);
        Publication publication = new Publication(publicationId);
        User user = new User(userId);

        List<Grant> grants = new ArrayList<>();
        Grant grant = new Grant(grantId);
        grants.add(grant);

        submission.setGrants(grants);
        submission.setSource(Source.OTHER);
        submission.setSubmitted(true);
        submission.setSubmittedDate(ZonedDateTime.now());
        submission.setPublication(publication);

        Repository repository = new Repository(nihmsRepositoryId);
        List<Repository> repositories = new ArrayList<>();
        repositories.add(repository);
        submission.setRepositories(repositories);

        submission.setSubmitter(user);

        return submission;
    }

    private NihmsPublication newTestPub() {
        return new NihmsPublication(NihmsStatus.COMPLIANT, pmid, awardNumber, null, null, null, null, null, null, null);
    }

}
