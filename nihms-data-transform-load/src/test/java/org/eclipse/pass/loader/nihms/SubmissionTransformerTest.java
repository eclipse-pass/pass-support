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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.pass.client.nihms.NihmsPassClientService;
import org.eclipse.pass.entrez.PmidLookup;
import org.eclipse.pass.entrez.PubMedEntrezRecord;
import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.model.Grant;
import org.eclipse.pass.model.Publication;
import org.eclipse.pass.model.RepositoryCopy.CopyStatus;
import org.eclipse.pass.model.Submission;
import org.eclipse.pass.model.Submission.Source;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for NIHMS Transformer code
 *
 * @author Karen Hanson
 */
public class SubmissionTransformerTest {

    private static final String sGrantUri = "https://example.com/fedora/grants/1";
    private static final String sSubmissionUri = "https://example.com/fedora/submissions/1";
    private static final String sNihmsRepositoryUri = "https://example.com/fedora/repositories/2";
    private static final String sJournalUri = "https://example.com/fedora/journals/1";
    private static final String sPublicationUri = "https://example.com/fedora/publications/1";
    private static final String sUserUri = "https://example.com/fedora/users/1";

    private static final String nihmsId = "abcdefg";
    private static final String pmcId = "9876543";
    private static final String depositDate = "12/12/2018";

    //PubMedEntrezRecord fields
    private static final String doi = "https://doi.org/10.001/0101ab";
    private static final String issn = "1234-5678";
    private static final String pmid = "123456";
    private static final String title = "Test Title";
    private static final String issue = "3";
    private static final String volume = "5";
    private static final String awardNumber = "AB 12345";

    private static final String pmcIdTemplateUrl = "https://example.com/pmc/pmc%s";

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Mock
    private NihmsPassClientService clientServiceMock;

    @Mock
    private PmidLookup pmidLookupMock;

    @Mock
    private PubMedEntrezRecord pubMedRecordMock;

    private NihmsPublicationToSubmission transformer;

    @Before
    public void init() {
        System.setProperty("nihms.pass.uri", sNihmsRepositoryUri);
        System.setProperty("pmc.url.template", pmcIdTemplateUrl);
        MockitoAnnotations.initMocks(this);
        transformer = new NihmsPublicationToSubmission(clientServiceMock, pmidLookupMock);
    }

    /**
     * Tests the scenario where there is no current Publication or Submission in PASS for the article, and no
     * need for a new RepositoryCopy. The returned object should have a Publication and Submission object without
     * a URI and no RepositoryCopies
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
        when(clientServiceMock.findJournalByIssn(issn)).thenReturn(new URI(sJournalUri));

        when(pmidLookupMock.retrievePubMedRecord(Mockito.anyString())).thenReturn(pubMedRecordMock);

        pmrMockWhenValues();

        SubmissionDTO dto = transformer.transform(pub);

        checkPmrValues(dto);
        assertEquals(ConfigUtil.getNihmsRepositoryUri(), dto.getSubmission().getRepositories().get(0));
        assertNull(dto.getPublication().getId());
        assertNull(dto.getSubmission().getId());
        assertNull(dto.getRepositoryCopy());
    }

    /**
     * Tests the scenario where there is no current Publication or Submission in PASS for the article, and a
     * new RepositoryCopy is needed. The returned object should have a Publication, Submission and RepositoryCopy
     * objects
     * all without a URI
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
        when(clientServiceMock.findJournalByIssn(issn)).thenReturn(new URI(sJournalUri));
        when(pmidLookupMock.retrievePubMedRecord(pmid)).thenReturn(pubMedRecordMock);

        pmrMockWhenValues();

        SubmissionDTO dto = transformer.transform(pub);

        checkPmrValues(dto);
        assertEquals(ConfigUtil.getNihmsRepositoryUri(), dto.getSubmission().getRepositories().get(0));

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

        List<Submission> submissions = new ArrayList<Submission>();
        submissions.add(submission);

        Grant grant = newTestGrant();
        when(clientServiceMock.findMostRecentGrantByAwardNumber(awardNumber)).thenReturn(grant);

        when(clientServiceMock.findPublicationByPmid(pmid)).thenReturn(publication);
        when(clientServiceMock.findSubmissionsByPublicationAndUserId(publication.getId(), grant.getPi())).thenReturn(
            submissions);

        when(pmidLookupMock.retrievePubMedRecord(pmid)).thenReturn(pubMedRecordMock);

        pmrMockWhenValues();

        SubmissionDTO dto = transformer.transform(pub);

        checkPmrValues(dto);
        assertEquals(ConfigUtil.getNihmsRepositoryUri(), dto.getSubmission().getRepositories().get(0));

        assertEquals(true, dto.getSubmission().getSubmitted());
        assertNotNull(dto.getSubmission().getSubmittedDate());

        assertNull(dto.getRepositoryCopy().getId());
        assertEquals(CopyStatus.COMPLETE, dto.getRepositoryCopy().getCopyStatus());
        assertTrue(dto.getRepositoryCopy().getExternalIds().contains("NIHMS" + nihmsId));
        assertTrue(dto.getRepositoryCopy().getExternalIds().contains("PMC" + pmcId));
        assertEquals(2, dto.getRepositoryCopy().getExternalIds().size());

    }

    /**
     * Tests the scenario where there is already a Publication and Submission in PASS for the article,
     * The grant and repo were not included on the Submission, but it is not submitted so we can add it.
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
        submission.setRepositories(new ArrayList<URI>());
        submission.setGrants(new ArrayList<URI>());

        List<Submission> submissions = new ArrayList<Submission>();
        submissions.add(submission);

        Grant grant = newTestGrant();
        when(clientServiceMock.findMostRecentGrantByAwardNumber(awardNumber)).thenReturn(grant);

        when(clientServiceMock.findPublicationByPmid(pmid)).thenReturn(publication);
        when(clientServiceMock.findSubmissionsByPublicationAndUserId(publication.getId(), grant.getPi())).thenReturn(
            submissions);

        when(pmidLookupMock.retrievePubMedRecord(pmid)).thenReturn(pubMedRecordMock);

        pmrMockWhenValues();

        SubmissionDTO dto = transformer.transform(pub);

        checkPmrValues(dto);

        assertEquals(false, dto.getSubmission().getSubmitted());

        assertTrue(dto.getSubmission().getRepositories().contains(ConfigUtil.getNihmsRepositoryUri()));

        assertTrue(dto.getSubmission().getGrants().contains(grant.getId()));

        assertNull(dto.getSubmission().getSubmittedDate());

        assertNull(dto.getRepositoryCopy());

    }

    /**
     * Makes sure exception is thrown if no matching grant is found for the Award Number
     */
    @Test
    public void testTransformNoMatchingGrantThrowsException() {

        when(clientServiceMock.findMostRecentGrantByAwardNumber(Mockito.anyObject())).thenReturn(null);
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("No Grant matching award number");

        NihmsPublication pub = newTestPub();

        transformer.transform(pub);

    }

    private void checkPmrValues(SubmissionDTO dto) {
        assertEquals(title, dto.getPublication().getTitle());
        assertEquals(volume, dto.getPublication().getVolume());
        assertEquals(issue, dto.getPublication().getIssue());
        assertEquals(pmid, dto.getPublication().getPmid());
        assertEquals(doi, dto.getPublication().getDoi());

        assertEquals(sGrantUri, dto.getSubmission().getGrants().get(0).toString());
        assertEquals(Source.OTHER, dto.getSubmission().getSource());
        assertEquals(sUserUri, dto.getSubmission().getSubmitter().toString());
    }

    private void pmrMockWhenValues() {
        when(pubMedRecordMock.getPmid()).thenReturn(pmid);
        when(pubMedRecordMock.getDoi()).thenReturn(doi);
        when(pubMedRecordMock.getIssn()).thenReturn(issn);
        when(pubMedRecordMock.getEssn()).thenReturn(null);
        when(pubMedRecordMock.getIssue()).thenReturn(issue);
        when(pubMedRecordMock.getVolume()).thenReturn(volume);
        when(pubMedRecordMock.getTitle()).thenReturn(title);
    }

    private Grant newTestGrant() throws Exception {
        Grant grant = new Grant();
        grant.setId(new URI(sGrantUri));
        grant.setPi(new URI(sUserUri));
        grant.setAwardNumber(awardNumber);
        return grant;
    }

    private Publication newTestPublication() throws Exception {
        Publication publication = new Publication();
        publication.setId(new URI(sPublicationUri));
        publication.setPmid(pmid);
        publication.setDoi(doi);
        publication.setTitle(title);
        publication.setJournal(new URI(sJournalUri));
        publication.setVolume(volume);
        publication.setIssue(issue);
        return publication;
    }

    private Submission newTestSubmission() throws Exception {
        Submission submission = new Submission();

        submission.setId(new URI(sSubmissionUri));

        List<URI> grants = new ArrayList<URI>();
        grants.add(new URI(sGrantUri));

        submission.setGrants(grants);
        submission.setSource(Source.OTHER);
        submission.setSubmitted(true);
        submission.setSubmittedDate(new DateTime());
        submission.setPublication(new URI(sPublicationUri));

        List<URI> repositories = new ArrayList<URI>();
        repositories.add(ConfigUtil.getNihmsRepositoryUri());

        submission.setRepositories(repositories);

        submission.setSubmitter(new URI(sUserUri));

        return submission;
    }

    private NihmsPublication newTestPub() {
        return new NihmsPublication(NihmsStatus.COMPLIANT, pmid, awardNumber, null, null, null, null, null, null, null);
    }

}
