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
package org.eclipse.pass.client.nihms;

import static org.eclipse.pass.client.nihms.NihmsPassClientService.ERR_CREATE_PUBLICATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.pass.client.PassClient;
import org.eclipse.pass.model.Deposit;
import org.eclipse.pass.model.Grant;
import org.eclipse.pass.model.Publication;
import org.eclipse.pass.model.RepositoryCopy;
import org.eclipse.pass.model.Submission;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests many of the public methods in NihmsPassClientService. Does not currently test
 * methods that consist of only a null check and then a call to the fedora client
 *
 * @author Karen Hanson
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class NihmsPassClientServiceTest {

    private static final String sGrant1Id = "https://example.com/fedora/grants/1";
    private static final String sGrant2Id = "https://example.com/fedora/grants/2";
    private static final String sSubmissionId = "https://example.com/fedora/submissions/1";
    private static final String sUserId = "https://example.com/fedora/users/1";
    private static final String sDepositId = "https://example.com/fedora/deposits/1";
    private static final String sRepositoryId = "https://example.com/fedora/repositories/1";
    private static final String sPublicationId = "https://example.com/fedora/publications/1";
    private static final String sRepositoryCopyId = "https://example.com/fedora/repositoryCopies/1";
    private static final String pmid = "12345678";
    private static final String doi = "https://doi.org/10.000/abcde";
    private static final String awardNumber = "RH 1234";
    private static final String title = "Article Title";

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Mock
    private PassClient mockClient;

    private NihmsPassClientService clientService;

    private URI grantId;
    private URI grant2Id;
    private URI userId;
    private URI submissionId;
    private URI depositId;
    private URI repositoryId;
    private URI publicationId;
    private URI repositoryCopyId;

    @Before
    public void initMocks() throws Exception {
        MockitoAnnotations.initMocks(this);
        clientService = new NihmsPassClientService(mockClient);
        grantId = new URI(sGrant1Id);
        grant2Id = new URI(sGrant2Id);
        userId = new URI(sUserId);
        submissionId = new URI(sSubmissionId);
        depositId = new URI(sDepositId);
        repositoryId = new URI(sRepositoryId);
        publicationId = new URI(sPublicationId);
        repositoryCopyId = new URI(sRepositoryCopyId);
    }

    @After
    public void clearCache() {
        clientService.clearCache();
    }

    /**
     * Checks that it findGrantByAwardNumber searches the database with and without space
     * returns null when non found
     */
    @Test
    public void testFindGrantByAwardNumberNoMatch() {

        ArgumentCaptor<String> awardNumCaptor = ArgumentCaptor.forClass(String.class);

        when(mockClient.findAllByAttribute(eq(Grant.class), eq("awardNumber"), eq(awardNumber))).thenReturn(
            new HashSet<URI>());

        Grant grant = clientService.findMostRecentGrantByAwardNumber(awardNumber);

        verify(mockClient, times(2)).findAllByAttribute(eq(Grant.class), eq("awardNumber"), awardNumCaptor.capture());

        assertEquals(awardNumber, awardNumCaptor.getAllValues().get(0));
        assertEquals(awardNumber.replace(" ", ""), awardNumCaptor.getAllValues().get(1));
        assertEquals(null, grant);

    }

    /**
     * Checks that findGrantByAwardNumber returns URI when one found
     */
    @Test
    public void testFindGrantByAwardNumberHasMatch() throws Exception {
        Grant grant1 = new Grant();
        grant1.setId(grantId);
        grant1.setAwardNumber(awardNumber);
        grant1.setStartDate(new DateTime().minusYears(1));

        Grant grant2 = new Grant();
        grant2.setId(grant2Id);
        grant2.setAwardNumber(awardNumber);
        grant2.setStartDate(new DateTime());

        Set<URI> grantIds = new HashSet<URI>();
        grantIds.add(grantId);
        grantIds.add(grant2Id);

        when(mockClient.findAllByAttribute(eq(Grant.class), eq("awardNumber"), eq(awardNumber))).thenReturn(grantIds);
        when(mockClient.readResource(eq(grantId), eq(Grant.class))).thenReturn(grant1);
        when(mockClient.readResource(eq(grant2Id), eq(Grant.class))).thenReturn(grant2);

        Grant matchedGrant = clientService.findMostRecentGrantByAwardNumber(awardNumber);
        verify(mockClient).findAllByAttribute(eq(Grant.class), eq("awardNumber"), eq(awardNumber));

        assertEquals(grant2, matchedGrant);
    }

    /**
     * Checks that findPublicationById returns match based on PMID
     */
    @Test
    public void testFindPublicationByIdPmidMatch() throws Exception {
        Publication publication = new Publication();
        publication.setId(publicationId);
        publication.setDoi(doi);
        publication.setTitle(title);
        publication.setPmid(pmid);

        when(mockClient.findByAttribute(eq(Publication.class), eq("pmid"), eq(pmid))).thenReturn(publicationId);
        when(mockClient.readResource(eq(publicationId), eq(Publication.class))).thenReturn(publication);

        Publication matchedPublication = clientService.findPublicationByPmid(pmid);

        assertEquals(publication, matchedPublication);
    }

    /**
     * Checks that it findPublicationById returns match based on DOI
     */
    @Test
    public void testFindPublicationByIdDoiMatch() throws Exception {
        Publication publication = new Publication();
        publication.setId(publicationId);
        publication.setDoi(doi);
        publication.setTitle(title);
        publication.setPmid(pmid);

        when(mockClient.findByAttribute(Publication.class, "pmid", pmid)).thenReturn(null);
        when(mockClient.findByAttribute(Publication.class, "doi", doi)).thenReturn(publicationId);
        when(mockClient.readResource(publicationId, Publication.class)).thenReturn(publication);

        Publication matchedPublication = clientService.findPublicationByDoi(doi, pmid);

        verify(mockClient).findByAttribute(eq(Publication.class), any(), any());
        assertEquals(publication, matchedPublication);
    }

    /**
     * Checks that it findPublicationById returns null when no match
     */
    @Test
    public void testFindPublicationByIdNoMatch() throws Exception {

        when(mockClient.findByAttribute(Publication.class, "pmid", pmid)).thenReturn(null);
        when(mockClient.findByAttribute(Publication.class, "doi", doi)).thenReturn(null);

        Publication matchedPublication = clientService.findPublicationByPmid(pmid);

        verify(mockClient).findByAttribute(eq(Publication.class), Mockito.anyString(), any());
        assertNull(matchedPublication);

    }

    /**
     * Checks that it findRepositoryCopyByRepoAndPubId returns match based on repository and publication
     */
    @Test
    public void testFindRepositoryCopyByRepoAndPubIdHasMatch() throws Exception {
        RepositoryCopy repoCopy = new RepositoryCopy();
        repoCopy.setId(repositoryCopyId);
        repoCopy.setPublication(publicationId);

        Set<URI> repositoryCopyIds = new HashSet<URI>();
        repositoryCopyIds.add(repositoryCopyId);

        when(mockClient.findAllByAttributes(eq(RepositoryCopy.class), any())).thenReturn(repositoryCopyIds);
        when(mockClient.readResource(eq(repositoryCopyId), eq(RepositoryCopy.class))).thenReturn(repoCopy);

        RepositoryCopy matchedRepoCopy = clientService.findNihmsRepositoryCopyForPubId(publicationId);
        assertEquals(repoCopy, matchedRepoCopy);

        //check it doesnt pull from elasticsearch the second time
        matchedRepoCopy = clientService.findNihmsRepositoryCopyForPubId(publicationId);
        verify(mockClient, times(1)).findAllByAttributes(eq(RepositoryCopy.class), any());
        assertEquals(repoCopy, matchedRepoCopy);
    }

    /**
     * Checks that it findRepositoryCopyByRepoAndPubId returns null when no match
     */
    @Test
    public void testFindRepositoryCopyByRepoAndPubIdNoMatch() throws Exception {
        when(mockClient.findAllByAttributes(eq(RepositoryCopy.class), any())).thenReturn(null);

        RepositoryCopy matchedRepoCopy = clientService.findNihmsRepositoryCopyForPubId(publicationId);

        assertNull(matchedRepoCopy);
    }

    /**
     * Tests the scenario where a match is found right away using publication+grant
     *
     * @throws Exception
     */
    @Test
    public void testFindExistingSubmissionPubGrantHasMatch() throws Exception {

        URI submissionId2 = new URI(sSubmissionId + "2");

        Set<URI> submissions = new HashSet<URI>();
        submissions.add(submissionId);
        submissions.add(submissionId2);

        Submission submission = new Submission();
        submission.setId(submissionId);

        Submission submission2 = new Submission();
        submission.setId(submissionId2);

        Grant grant = new Grant();
        grant.setId(grantId);
        grant.setPi(userId);

        when(mockClient.findAllByAttributes(eq(Submission.class), any())).thenReturn(submissions);
        when(mockClient.readResource(any(), eq(Submission.class))).thenReturn(submission).thenReturn(submission2);

        List<Submission> matchedSubmissions = clientService.findSubmissionsByPublicationAndUserId(publicationId,
                                                                                                  userId);

        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(mockClient).findAllByAttributes(eq(Submission.class), argumentCaptor.capture());

        assertEquals(2, argumentCaptor.getValue().size());
        assertEquals(userId, argumentCaptor.getValue().get("submitter"));
        assertEquals(publicationId, argumentCaptor.getValue().get("publication"));

        assertEquals(submission, matchedSubmissions.get(0));
        assertEquals(submission2, matchedSubmissions.get(1));

    }

    /**
     * Tests the scenario where no match is found using publication+grant
     *
     * @throws Exception
     */
    @Test
    public void testFindExistingSubmissionPubGrantNoMatch() throws Exception {
        //returns null first time, then the submission the second time when search using doi

        when(mockClient.findAllByAttributes(eq(Submission.class), any())).thenReturn(new HashSet<URI>());

        List<Submission> matchedSubmissions = clientService.findSubmissionsByPublicationAndUserId(publicationId,
                                                                                                  userId);

        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(mockClient).findAllByAttributes(eq(Submission.class), argumentCaptor.capture());

        assertEquals(2, argumentCaptor.getValue().size());
        assertEquals(userId, argumentCaptor.getValue().get("submitter"));
        assertEquals(publicationId, argumentCaptor.getValue().get("publication"));

        assertTrue(matchedSubmissions.size() == 0);

    }

    /**
     * Test that given a submission containing some Deposit URIs, they are all retrieved and returned as a list
     *
     * @throws Exception
     */
    @Test
    public void testFindDepositBySubmissionAndRepositoryIdHasMatch() throws Exception {
        Set<URI> depositIds = new HashSet<URI>();
        depositIds.add(depositId);

        Deposit deposit = new Deposit();
        deposit.setId(depositId);
        deposit.setSubmission(submissionId);
        deposit.setRepository(repositoryId);

        when(mockClient.findAllByAttributes(eq(Deposit.class), any())).thenReturn(depositIds);
        when(mockClient.readResource(eq(depositId), eq(Deposit.class))).thenReturn(deposit);

        Deposit matchedDeposit = clientService.findNihmsDepositForSubmission(submissionId);

        verify(mockClient).findAllByAttributes(eq(Deposit.class), any());
        verify(mockClient).readResource(any(), eq(Deposit.class));

        assertEquals(deposit, matchedDeposit);
    }

    /**
     * Checks that exception thrown if too many deposit URIs are returned when finding Deposits related to a Submission
     * and Repository combination
     *
     * @throws Exception
     */
    @Test(expected = RuntimeException.class)
    public void testFindDepositBySubmissionAndRepositoryIdExtraMatch() throws Exception {
        URI depositId2 = new URI(sDepositId + "2");
        Set<URI> depositIds = new HashSet<URI>();
        depositIds.add(depositId);
        depositIds.add(depositId2);

        when(mockClient.findAllByAttributes(eq(Deposit.class), any())).thenReturn(depositIds);

        clientService.findNihmsDepositForSubmission(submissionId);

    }

    /**
     * Checks that createSubmission works as expected
     */
    @Test
    public void testCreateSubmission() {
        Submission submission = new Submission();
        submission.setSubmitter(userId);
        submission.setPublication(publicationId);

        when(mockClient.createResource(eq(submission))).thenReturn(submissionId);

        URI newSubmissionId = clientService.createSubmission(submission);

        verify(mockClient).createResource(eq(submission));

        assertEquals(submissionId, newSubmissionId);
    }

    /**
     * Checks that if there are changes an update happens in updateSubmission
     */
    @Test
    public void testUpdateSubmissionHasChanges() {
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setSubmitter(userId);
        submission.setPublication(publicationId);
        submission.setSubmitted(false);

        //make a submission that is different
        Submission submissionEdited = new Submission();
        submissionEdited.setId(submissionId);
        submissionEdited.setSubmitter(userId);
        submissionEdited.setPublication(publicationId);
        submissionEdited.setSubmitted(true);

        when(mockClient.readResource(eq(submissionId), eq(Submission.class))).thenReturn(submission);
        clientService.updateSubmission(submissionEdited);
        verify(mockClient).updateResource(eq(submissionEdited));

    }

    /**
     * Checks that if there are no changes an update does not happen in updateSubmission
     */
    @Test
    public void testUpdateSubmissionNoChanges() {
        Submission submission = new Submission();
        submission.setId(submissionId);

        when(mockClient.readResource(eq(submissionId), eq(Submission.class))).thenReturn(submission);
        //try to update submission with no changes
        clientService.updateSubmission(submission);
        verify(mockClient, never()).updateResource(any());
    }

    /**
     * Creating a Publication with null DOI and a non-null PMID should succeed
     */
    @Test
    public void createPublicationWithNullDoi() {
        Publication p = new Publication();
        p.setDoi(null);
        p.setPmid("pmid");
        clientService.createPublication(p);

        verify(mockClient).createResource(p);
    }

    /**
     * Creating a Publication with non-null DOI and a null PMID should succeed
     */
    @Test
    public void createPublicationWithNullPmid() {
        Publication p = new Publication();
        p.setDoi("doi");
        p.setPmid(null);
        clientService.createPublication(p);

        verify(mockClient).createResource(p);
    }

    /**
     * Creating a Publication with null DOI and a null PMID should fail
     */
    @Test
    public void createPublicationWithNullPmidAndNullDoi() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage(ERR_CREATE_PUBLICATION);

        Publication p = new Publication();
        p.setDoi(null);
        p.setPmid(null);
        clientService.createPublication(p);

        verifyZeroInteractions(mockClient);

    }
}
