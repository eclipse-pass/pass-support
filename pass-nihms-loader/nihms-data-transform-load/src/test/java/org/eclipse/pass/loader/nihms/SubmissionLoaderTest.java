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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.eclipse.pass.client.nihms.NihmsPassClientService;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.support.client.SubmissionStatusService;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for Nihms SubmissionLoader
 *
 * @author Karen Hanson
 */
@ExtendWith(MockitoExtension.class)
public class SubmissionLoaderTest {
    @Mock
    private NihmsPassClientService clientServiceMock;

    @Mock
    private SubmissionStatusService statusServiceMock;

    private static final String submissionId = "1";
    private static final String repositoryCopyId = "1";
    private static final String depositId = "1";
    private static final String publicationId = "1";
    private static final String userId = "1";

    private static final String title = "A Title";
    private static String pmid = "12345678";

    /**
     * Check that if a Submission is provided with new Publication, new Submission and no
     * RepositoryCopy, it will do createPublication, createSubmission and not touch RepositoryCopy data
     *
     * @throws Exception
     */
    @Test
    public void testLoadNewPubNewSubmissionNoRepositoryCopy() throws Exception {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock, statusServiceMock);

        Publication publication = new Publication();
        publication.setTitle(title);
        publication.setPmid(pmid);

        User submitter = new User();
        submitter.setId(userId);

        Submission submission = new Submission();
        submission.setPublication(publication);
        submission.setSubmitter(submitter);

        SubmissionDTO dto = new SubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);

        when(clientServiceMock.createPublication(publication)).thenReturn(publication.getId());
        when(clientServiceMock.createSubmission(submission)).thenReturn(submission.getId());

        loader.load(dto);

        ArgumentCaptor<Publication> publicationCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(clientServiceMock).createPublication(publicationCaptor.capture());
        assertEquals(publication, publicationCaptor.getValue());

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(clientServiceMock).createSubmission(submissionCaptor.capture());
        submission.setPublication(publication);
        assertEquals(submission, submissionCaptor.getValue());

        //no repo copy so shouldn't touch RepositoryCopy create/update
        verify(clientServiceMock, never()).createRepositoryCopy(any());
        verify(clientServiceMock, never()).updateRepositoryCopy(any());
        verify(clientServiceMock, never()).updateDeposit(any());
    }

    /**
     * Check that if a Submission is provided with existing Publication, new Submission and no
     * RepositoryCopy, it will do updatePublication, createSubmission and not touch RepositoryCopy data
     *
     * @throws Exception
     */
    @Test
    public void testLoadExistingPubNewSubmissionNoRepositoryCopy() throws Exception {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock, statusServiceMock);

        Publication publication = new Publication();
        publication.setTitle(title);
        publication.setPmid(pmid);

        User submitter = new User(userId);

        Submission submission = new Submission();
        submission.setPublication(publication);
        submission.setSubmitter(submitter);

        SubmissionDTO dto = new SubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);

        loader.load(dto);

        verify(clientServiceMock, never()).updatePublication(any());

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(clientServiceMock).createSubmission(submissionCaptor.capture());
        assertEquals(submission, submissionCaptor.getValue());

        //no repo copy so shouldn't touch RepositoryCopy create/update
        verify(clientServiceMock, never()).createRepositoryCopy(any());
        verify(clientServiceMock, never()).updateRepositoryCopy(any());
        verify(clientServiceMock, never()).updateDeposit(any());
    }

    /**
     * Check that if a Submission is provided with existing Publication, existing Submission and no
     * RepositoryCopy, it will do updatePublication, updateSubmission and not touch RepositoryCopy data
     *
     * @throws Exception
     */
    @Test
    public void testLoadExistingPubExistingSubmissionNoRepoCopy() throws Exception {

        Publication publication = new Publication(publicationId);
        publication.setTitle(title);
        publication.setPmid(pmid);

        User submitter = new User(userId);

        Submission submission = new Submission(submissionId);
        submission.setPublication(publication);
        submission.setSubmitter(submitter);

        SubmissionDTO dto = new SubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);

        SubmissionLoader loader = new SubmissionLoader(clientServiceMock, statusServiceMock);

        loader.load(dto);

        //should not update anything
        verify(clientServiceMock, never()).updatePublication(any());
        verify(clientServiceMock, never()).updateSubmission(any());
        verify(clientServiceMock, never()).createRepositoryCopy(any());
        verify(clientServiceMock, never()).updateRepositoryCopy(any());
        verify(clientServiceMock, never()).updateDeposit(any());
    }

    /**
     * Check that if Submission has existing publication, new submission, new RepositoryCopy
     * does updatePublication, createSubmission, createRepositoryCopy
     *
     * @throws Exception
     */
    @Test
    public void testLoadExistingPublicationNewSubmissionNewRepoCopy() throws Exception {
        Publication publication = new Publication(publicationId);
        publication.setTitle(title);
        publication.setPmid(pmid);

        User submitter = new User(userId);

        Submission submission = new Submission(submissionId);
        submission.setPublication(publication);
        submission.setSubmitter(submitter);

        Repository nihmsRepo = new Repository(ConfigUtil.getNihmsRepositoryId());

        RepositoryCopy repositoryCopy = new RepositoryCopy();
        repositoryCopy.setPublication(publication);
        repositoryCopy.setRepository(nihmsRepo);
        repositoryCopy.setCopyStatus(CopyStatus.ACCEPTED);

        SubmissionDTO dto = new SubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);
        dto.setRepositoryCopy(repositoryCopy);

        SubmissionLoader loader = new SubmissionLoader(clientServiceMock, statusServiceMock);

        //run it
        loader.load(dto);

        verify(clientServiceMock, never()).updatePublication(any());

        ArgumentCaptor<RepositoryCopy> repoCopyCaptor = ArgumentCaptor.forClass(RepositoryCopy.class);
        verify(clientServiceMock).createRepositoryCopy(repoCopyCaptor.capture());
        assertEquals(repositoryCopy, repoCopyCaptor.getValue());

        verify(clientServiceMock, never()).updateDeposit(any());
    }

    /**
     * Check that if Submission has existing publication, existing submission, new RepositoryCopy,
     * existing Deposit that has no RepoCopy that it does updatePublication, updateSubmission,
     * createRepositoryCopy, and updates Deposit link
     *
     * @throws Exception
     */
    @Test
    public void testLoadExistingPubExistingSubmissionNewRepoCopy() throws Exception {

        Publication publication = new Publication(publicationId);
        publication.setTitle(title);
        publication.setPmid(pmid);

        User submitter = new User(userId);

        Submission submission = new Submission(submissionId);
        submission.setPublication(publication);
        submission.setSubmitter(submitter);

        Repository nihmsRepo = new Repository(ConfigUtil.getNihmsRepositoryId());

        RepositoryCopy repositoryCopy = new RepositoryCopy();
        repositoryCopy.setPublication(publication);
        repositoryCopy.setRepository(nihmsRepo);
        repositoryCopy.setCopyStatus(CopyStatus.ACCEPTED);

        Deposit deposit = new Deposit(depositId);
        deposit.setDepositStatus(DepositStatus.SUBMITTED);
        deposit.setRepository(repositoryCopy.getRepository());

        SubmissionDTO dto = new SubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);
        dto.setRepositoryCopy(repositoryCopy);

        SubmissionLoader loader = new SubmissionLoader(clientServiceMock, statusServiceMock);

        when(clientServiceMock.findNihmsDepositForSubmission(any())).thenReturn(deposit);

        //run it
        loader.load(dto);

        verify(clientServiceMock, never()).updatePublication(any());

        verify(clientServiceMock, never()).updateSubmission(any());

        ArgumentCaptor<RepositoryCopy> repoCopyCaptor = ArgumentCaptor.forClass(RepositoryCopy.class);
        verify(clientServiceMock).createRepositoryCopy(repoCopyCaptor.capture());
        assertEquals(repositoryCopy, repoCopyCaptor.getValue());

        ArgumentCaptor<Deposit> depositCaptor = ArgumentCaptor.forClass(Deposit.class);
        verify(clientServiceMock).updateDeposit(depositCaptor.capture());
        deposit.setRepositoryCopy(repositoryCopy);
        deposit.setDepositStatus(DepositStatus.ACCEPTED);
        assertEquals(deposit, depositCaptor.getValue());

    }

    /**
     * Checks an exception is thrown when a null DTO is passed into the loader
     */
    @Test
    public void testLoadThrowExceptionWhenNullDTO() {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock, statusServiceMock);

        Exception exception = assertThrows(RuntimeException.class, () -> loader.load(null));

        assertEquals("A null Submission object was passed to the loader.", exception.getMessage());

        verifyNoMoreInteractions(clientServiceMock);
    }

    /**
     * Checks an exception is thrown when a DTO is passed without the Submission object
     */
    @Test
    public void testLoadThrowExceptionWhenNoSubmission() {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock, statusServiceMock);
        SubmissionDTO dto = new SubmissionDTO();

        Exception exception = assertThrows(RuntimeException.class, () -> loader.load(dto));

        assertEquals("A null Submission object was passed to the loader.", exception.getMessage());
        verifyNoMoreInteractions(clientServiceMock);
    }
}
