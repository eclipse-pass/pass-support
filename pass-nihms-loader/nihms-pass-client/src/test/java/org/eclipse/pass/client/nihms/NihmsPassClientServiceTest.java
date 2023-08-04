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

import static org.eclipse.pass.client.nihms.NihmsPassClientService.AWARD_NUMBER_FLD;
import static org.eclipse.pass.client.nihms.NihmsPassClientService.ERR_CREATE_PUBLICATION;
import static org.eclipse.pass.client.nihms.NihmsPassClientService.PUBLICATION_FLD;
import static org.eclipse.pass.client.nihms.NihmsPassClientService.REPOSITORY_FLD;
import static org.eclipse.pass.client.nihms.NihmsPassClientService.SUBMISSION_FLD;
import static org.eclipse.pass.client.nihms.NihmsPassClientService.SUBMITTER_FLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.Util;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests many of the public methods in NihmsPassClientService.
 *
 * @author Karen Hanson
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
public class NihmsPassClientServiceTest {

    private static final String grant1Id = "1";
    private static final String submissionId = "1";
    private static final String userId = "1";
    private static final String depositId = "1";
    private static final String repositoryId = "1";
    private static final String publicationId = "1";
    private static final String repositoryCopyId = "1";
    private static final String pmid = "12345678";
    private static final String doi = "https://doi.org/10.000/abcde";
    private static final String awardNumber = "R01 CA265009";
    private static final String awardNumber1 = "K23 HL153781";
    private static final String awardNumber2 = "T32 HD094687";
    private static final String awardNumber3 = "F31 MH128079";
    private static final String awardNumber4 = "S10 OD025226";
    private static final String title = "Article Title";

    @Mock
    private PassClient mockClient;
    private NihmsPassClientService clientService;

    @BeforeEach
    public void initMocks() {
        clientService = new NihmsPassClientService(mockClient);
        clientService.clearCache();
    }

    /**
     * Checks findGrantByAwardNumber, which searches the database with and without space
     * returns null when not found
     */
    @Test
    public void testFindGrantByAwardNumberNoMatch() throws IOException {
        String awardFilter = Util.grantAwardNumberNormalizeSearch(awardNumber, AWARD_NUMBER_FLD);

        Stream<Grant> mockGrantResult = Stream.empty();

        doReturn(mockGrantResult)
                .when(mockClient).streamObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(awardFilter)));
        Grant grantTest = clientService.findMostRecentGrantByAwardNumber(awardNumber);
        assertNull(grantTest);
    }

    /**
     * Checks that findGrantByAwardNumber returns entity ID when one found
     */
    @Test
    public void testFindGrantByAwardNumberHasMatch() throws Exception {
        String awardFilter = Util.grantAwardNumberNormalizeSearch(awardNumber, AWARD_NUMBER_FLD);
        String awardFilter1 = Util.grantAwardNumberNormalizeSearch(awardNumber1, AWARD_NUMBER_FLD);
        String awardFilter2 = Util.grantAwardNumberNormalizeSearch(awardNumber2, AWARD_NUMBER_FLD);
        String awardFilter3 = Util.grantAwardNumberNormalizeSearch(awardNumber3, AWARD_NUMBER_FLD);
        String awardFilter4 = Util.grantAwardNumberNormalizeSearch(awardNumber4, AWARD_NUMBER_FLD);

        Grant grant1 = new Grant("1");
        grant1.setAwardNumber(awardNumber);
        grant1.setStartDate(ZonedDateTime.now().minusYears(1));
        Grant grant2 = new Grant("2");
        grant2.setAwardNumber(awardNumber);
        grant2.setStartDate(ZonedDateTime.now());

        Grant grant3 = new Grant("3");
        grant3.setAwardNumber(awardNumber1);
        grant3.setStartDate(ZonedDateTime.now());

        Grant grant4 = new Grant("4");
        grant4.setAwardNumber(awardNumber2);
        grant4.setStartDate(ZonedDateTime.now());

        Grant grant5 = new Grant("5");
        grant5.setAwardNumber(awardNumber3);
        grant5.setStartDate(ZonedDateTime.now());

        Grant grant6 = new Grant("6");
        grant6.setAwardNumber(awardNumber4);
        grant6.setStartDate(ZonedDateTime.now());

        //test with same grant number, different start dates
        Stream<Grant> mockGrantResult = List.of(grant1, grant2).stream();

        //test other standardized grant numbers
        Stream<Grant> mockGrantResult1 = List.of(grant3).stream();
        Stream<Grant> mockGrantResult2 = List.of(grant4).stream();
        Stream<Grant> mockGrantResult3 = List.of(grant5).stream();
        Stream<Grant> mockGrantResult4 = List.of(grant6).stream();

        doReturn(mockGrantResult)
                .when(mockClient).streamObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(awardFilter)));
        doReturn(mockGrantResult1)
                .when(mockClient)
                .streamObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(awardFilter1)));
        doReturn(mockGrantResult2)
                .when(mockClient)
                .streamObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(awardFilter2)));
        doReturn(mockGrantResult3)
                .when(mockClient)
                .streamObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(awardFilter3)));
        doReturn(mockGrantResult4)
                .when(mockClient)
                .streamObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(awardFilter4)));

        Grant matchedGrant = clientService.findMostRecentGrantByAwardNumber(awardNumber);
        Grant matchedGrant1 = clientService.findMostRecentGrantByAwardNumber(awardNumber1);
        Grant matchedGrant2 = clientService.findMostRecentGrantByAwardNumber(awardNumber2);
        Grant matchedGrant3 = clientService.findMostRecentGrantByAwardNumber(awardNumber3);
        Grant matchedGrant4 = clientService.findMostRecentGrantByAwardNumber(awardNumber4);

        //case with grant with same award number, different start dates
        assertEquals(grant2, matchedGrant);
        //other cases with 1 grant award number
        assertEquals(grant3, matchedGrant1);
        assertEquals(grant4, matchedGrant2);
        assertEquals(grant5, matchedGrant3);
        assertEquals(grant6, matchedGrant4);
    }

    /**
     * Checks that findGrantByAwardNumber returns entity ID when one found, using awardNumbers that can be normalized
     */
    @Test
    public void testFindGrantByAwardNumberCanNormalizeHasMatch() throws Exception {
        String awardFilter1 = Util.grantAwardNumberNormalizeSearch("K23 HL153778-1A1", AWARD_NUMBER_FLD);
        String awardFilter2 = Util.grantAwardNumberNormalizeSearch("S10 OD025226-1", AWARD_NUMBER_FLD);
        String awardFilter3 = Util.grantAwardNumberNormalizeSearch("R55 AR050026-1", AWARD_NUMBER_FLD);

        Grant grant1 = new Grant("1");
        grant1.setAwardNumber("K23 HL153778");
        grant1.setStartDate(ZonedDateTime.now());

        Grant grant2 = new Grant("2");
        grant2.setAwardNumber("S10 OD025226");
        grant2.setStartDate(ZonedDateTime.now());

        Grant grant3 = new Grant("3");
        grant3.setAwardNumber("R55 AR050026");
        grant3.setStartDate(ZonedDateTime.now());

        Stream<Grant> mockGrantResult1 = List.of(grant1).stream();
        Stream<Grant> mockGrantResult2 = List.of(grant2).stream();
        Stream<Grant> mockGrantResult3 = List.of(grant3).stream();

        doReturn(mockGrantResult1)
                .when(mockClient)
                .streamObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(awardFilter1)));
        doReturn(mockGrantResult2)
                .when(mockClient)
                .streamObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(awardFilter2)));
        doReturn(mockGrantResult3)
                .when(mockClient)
                .streamObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(awardFilter3)));

        Grant matchedGrant1 = clientService.findMostRecentGrantByAwardNumber("K23 HL153778-1A1");
        Grant matchedGrant2 = clientService.findMostRecentGrantByAwardNumber("S10 OD025226-1");
        Grant matchedGrant3 = clientService.findMostRecentGrantByAwardNumber("R55 AR050026-1");

        assertEquals(grant1, matchedGrant1);
        assertEquals(grant2, matchedGrant2);
        assertEquals(grant3, matchedGrant3);
    }

    /**
     * Checks that findPublicationById returns match based on PMID
     */
    @Test
    public void testFindPublicationByIdPmidMatch() throws Exception {
        String pmidFilter = RSQL.equals("pmid", pmid);
        Publication publication = new Publication();
        publication.setId(publicationId);
        publication.setDoi(doi);
        publication.setTitle(title);
        publication.setPmid(pmid);

        PassClientResult<Publication> mockPublicationResult = new PassClientResult<>(List.of(publication), 1);
        doReturn(mockPublicationResult)
                .when(mockClient)
                .selectObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(pmidFilter)));
        doReturn(publication)
                .when(mockClient)
                .getObject(Publication.class, publicationId);

        Publication matchedPublication = clientService.findPublicationByPmid(pmid);
        assertEquals(publication, matchedPublication);
    }

    /**
     * Checks that it findPublicationById returns match based on DOI
     */
    @Test
    public void testFindPublicationByIdDoiMatch() throws Exception {
        String doiFilter = RSQL.equals("doi", doi);
        Publication publication = new Publication();
        publication.setId(publicationId);
        publication.setDoi(doi);
        publication.setTitle(title);
        publication.setPmid(pmid);

        PassClientResult<Publication> mockPublicationResult = new PassClientResult<>(List.of(publication), 1);
        doReturn(mockPublicationResult)
                .when(mockClient)
                .selectObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(doiFilter)));
        doReturn(publication)
                .when(mockClient)
                .getObject(Publication.class, publicationId);

        Publication matchedPublication = clientService.findPublicationByDoi(doi, pmid);

        verify(mockClient).selectObjects(
                argThat(passClientSelector ->
                        passClientSelector.getFilter().equals(doiFilter)));
        assertEquals(publication, matchedPublication);
    }

    /**
     * Checks that it findPublicationById returns null when no match
     */
    @Test
    public void testFindPublicationByIdNoMatch() throws Exception {
        String pmidFilter = RSQL.equals("pmid", pmid);

        PassClientResult<Publication> mockPublicationResult = new PassClientResult<>(List.of(), 0);
        doReturn(mockPublicationResult)
                .when(mockClient)
                .selectObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(pmidFilter)));

        Publication matchedPublicationPmid = clientService.findPublicationByPmid(pmid);
        verify(mockClient, times(1)).selectObjects(
                argThat(passClientSelector ->
                        passClientSelector.getFilter().equals(pmidFilter)));
        assertNull(matchedPublicationPmid);
    }

    /**
     * Checks that it findRepositoryCopyByRepoAndPubId returns match based on repository and publication
     */
    @Test
    public void testFindRepositoryCopyByRepoAndPubIdHasMatch() throws Exception {
        RepositoryCopy repoCopy = new RepositoryCopy();
        Publication publication = new Publication();
        publication.setId(publicationId);
        repoCopy.setId(repositoryCopyId);
        repoCopy.setPublication(publication);

        String repoCopyFilter = RSQL.and(
                RSQL.equals(PUBLICATION_FLD, publicationId),
                RSQL.equals(REPOSITORY_FLD, repositoryCopyId));
        PassClientResult<RepositoryCopy> mockRepoCopyResult = new PassClientResult<>(List.of(repoCopy), 1);
        doReturn(mockRepoCopyResult)
                .when(mockClient)
                .selectObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(repoCopyFilter)));
        doReturn(repoCopy)
                .when(mockClient)
                .getObject(RepositoryCopy.class, repositoryCopyId);

        RepositoryCopy matchedRepoCopy = clientService.findNihmsRepositoryCopyForPubId(publicationId);
        assertEquals(repoCopy, matchedRepoCopy);

        //check it doesnt pull from elasticsearch the second time
        matchedRepoCopy = clientService.findNihmsRepositoryCopyForPubId(publicationId);
        verify(mockClient, times(1)).selectObjects(any());
        assertEquals(repoCopy, matchedRepoCopy);
    }

    /**
     * Checks that it findRepositoryCopyByRepoAndPubId returns null when no match
     */
    @Test
    public void testFindRepositoryCopyByRepoAndPubIdNoMatch() throws Exception {
        PassClientResult<RepositoryCopy> mockRepoCopyResult = new PassClientResult<>(List.of(), 0);
        doReturn(mockRepoCopyResult)
                .when(mockClient)
                .selectObjects(any());
        RepositoryCopy matchedRepoCopy = clientService.findNihmsRepositoryCopyForPubId(publicationId);
        assertNull(matchedRepoCopy);
    }

    /**
     * Tests the scenario where a match is found right away using publication+submitter
     *
     * @throws Exception if unable to retrieve the submission
     */
    @Test
    public void testFindExistingSubmissionPubGrantHasMatch() throws Exception {
        String subFilter = RSQL.and(
                RSQL.equals(PUBLICATION_FLD, publicationId),
                RSQL.equals(SUBMITTER_FLD, userId));
        Submission submission = new Submission(submissionId);
        Submission submission2 = new Submission(submissionId + "2");

        User user = new User(userId);
        Grant grant = new Grant(grant1Id);
        grant.setPi(user);

        PassClientResult<Submission> mockSubResult = new PassClientResult<>(List.of(submission, submission2), 2);
        doReturn(mockSubResult)
                .when(mockClient)
                .selectObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(subFilter)));

        List<Submission> matchedSubmissions = clientService.findSubmissionsByPublicationAndUserId(publicationId,
                                                                                                  userId);
        ArgumentCaptor<PassClientSelector> argumentCaptor = ArgumentCaptor.forClass(PassClientSelector.class);

        verify(mockClient).selectObjects(argumentCaptor.capture());

        assertEquals(PassClientSelector.class, argumentCaptor.getValue().getClass());

        assertEquals(submission, matchedSubmissions.get(0));
        assertEquals(submission2, matchedSubmissions.get(1));

    }

    /**
     * Tests the scenario where no match is found using publication+submitter
     *
     * @throws Exception if unable to retrieve the submission
     */
    @Test
    public void testFindExistingSubmissionPubGrantNoMatch() throws Exception {
        PassClientResult<Submission> mockSubResult = new PassClientResult<>(List.of(), 0);
        doReturn(mockSubResult)
                .when(mockClient)
                .selectObjects(any());
        List<Submission> matchedSubmissions = clientService.findSubmissionsByPublicationAndUserId(publicationId,
                                                                                                  userId);

        ArgumentCaptor<PassClientSelector> argumentCaptor = ArgumentCaptor.forClass(PassClientSelector.class);

        verify(mockClient).selectObjects(argumentCaptor.capture());
        assertEquals(PassClientSelector.class, argumentCaptor.getValue().getClass());

        assertEquals(0, matchedSubmissions.size());

    }

    /**
     * Test that given a submission containing some Deposit IDs, they are all retrieved and returned as a list
     *
     * @throws Exception if unable to retrieve the deposits
     */
    @Test
    public void testFindDepositBySubmissionAndRepositoryIdHasMatch() throws Exception {
        String depositFilter = RSQL.and(
                RSQL.equals(SUBMISSION_FLD, submissionId),
                RSQL.equals(REPOSITORY_FLD, repositoryId));
        Submission submission = new Submission(submissionId);
        Repository repository = new Repository(repositoryId);
        Deposit deposit = new Deposit(depositId);
        deposit.setSubmission(submission);
        deposit.setRepository(repository);

        PassClientResult<Deposit> mockSubResult = new PassClientResult<>(List.of(deposit), 2);
        doReturn(mockSubResult)
                .when(mockClient)
                .selectObjects(
                        argThat(passClientSelector ->
                                passClientSelector.getFilter().equals(depositFilter)));

        Deposit matchedDeposit = clientService.findNihmsDepositForSubmission(submissionId);

        verify(mockClient).selectObjects(any(PassClientSelector.class));
        assertEquals(deposit, matchedDeposit);
    }

    /**
     * Checks that exception thrown if too many deposit IDs are returned when finding Deposits related to a Submission
     * and Repository combination
     *
     */
    @Test
    public void testFindDepositBySubmissionAndRepositoryIdExtraMatch() {
        Exception exception = assertThrows(RuntimeException.class, () -> {
            String depositFilter = RSQL.and(
                    RSQL.equals(SUBMISSION_FLD, submissionId),
                    RSQL.equals(REPOSITORY_FLD, repositoryId));
            Deposit deposit = new Deposit(depositId);
            Deposit deposit2 = new Deposit(depositId + "2");

            PassClientResult<Deposit> mockSubResult = new PassClientResult<>(List.of(deposit, deposit2), 2);
            doReturn(mockSubResult)
                    .when(mockClient)
                    .selectObjects(
                            argThat(passClientSelector ->
                                    passClientSelector.getFilter().equals(depositFilter)));

            clientService.findNihmsDepositForSubmission(submissionId);

        });
        String expectedMessage = "There are multiple Deposits matching submissionId";
        assertEquals(expectedMessage, exception.getMessage().substring(0, expectedMessage.length()));
    }

    /**
     * Checks that createSubmission works as expected
     */
    @Test
    public void testCreateSubmission() throws IOException {
        User user = new User(userId);
        Publication publication = new Publication(publicationId);
        Submission submission = new Submission(submissionId);
        submission.setSubmitter(user);
        submission.setPublication(publication);

        doNothing().when(mockClient).createObject(eq(submission));
        String createdSubmissionId = clientService.createSubmission(submission);

        verify(mockClient).createObject(eq(submission));

        assertEquals(submission.getId(), createdSubmissionId);
    }

    /**
     * Checks that if there are changes an update happens in updateSubmission
     */
    @Test
    public void testUpdateSubmissionHasChanges() throws IOException {
        User user = new User(userId);
        Publication publication = new Publication(publicationId);
        Submission submission = new Submission(submissionId);
        submission.setSubmitter(user);
        submission.setPublication(publication);
        submission.setSubmitted(false);

        //make a submission that is different
        Submission submissionEdited = new Submission();
        submissionEdited.setId(submissionId);
        submissionEdited.setSubmitter(user);
        submissionEdited.setPublication(publication);
        submissionEdited.setSubmitted(true);

        doReturn(submission)
                .when(mockClient)
                .getObject(eq(Submission.class), eq(submissionId));

        clientService.updateSubmission(submissionEdited);
        verify(mockClient).updateObject(eq(submissionEdited));

    }

    /**
     * Checks that if there are no changes an update does not happen in updateSubmission
     */
    @Test
    public void testUpdateSubmissionNoChanges() throws IOException {
        Submission submission = new Submission(submissionId);

        doReturn(submission)
                .when(mockClient)
                .getObject(eq(Submission.class), eq(submissionId));
        //try to update submission with no changes
        clientService.updateSubmission(submission);
        verify(mockClient, never()).updateObject(any());
    }

    /**
     * Creating a Publication with null DOI and a non-null PMID should succeed
     */
    @Test
    public void createPublicationWithNullDoi() throws IOException {
        Publication p = new Publication();
        p.setDoi(null);
        p.setPmid("pmid");
        clientService.createPublication(p);

        verify(mockClient).createObject(p);
    }

    /**
     * Creating a Publication with non-null DOI and a null PMID should succeed
     */
    @Test
    public void createPublicationWithNullPmid() throws IOException {
        Publication p = new Publication();
        p.setDoi("doi");
        p.setPmid(null);
        clientService.createPublication(p);

        verify(mockClient).createObject(p);
    }

    /**
     * Creating a Publication with null DOI and a null PMID should fail
     */
    @Test
    public void createPublicationWithNullPmidAndNullDoi() {
        Exception exception = assertThrows(RuntimeException.class, () -> {
            Publication p = new Publication();
            p.setDoi(null);
            p.setPmid(null);
            clientService.createPublication(p);
            verify(mockClient, never()).createObject(any());
        });
        assertEquals(ERR_CREATE_PUBLICATION, exception.getMessage());
    }

}
