/*
 * Copyright 2018 Johns Hopkins University
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
package org.eclipse.pass.deposit.messaging.service;

import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomAggregatedDepositStatusExcept;
import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomId;
import static org.eclipse.pass.deposit.messaging.service.SubmissionProcessor.CriFunc.critical;
import static org.eclipse.pass.deposit.messaging.service.SubmissionProcessor.CriFunc.postCondition;
import static org.eclipse.pass.deposit.messaging.service.SubmissionProcessor.CriFunc.preCondition;
import static org.eclipse.pass.deposit.messaging.service.SubmissionProcessor.getLookupKeys;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

import org.eclipse.pass.deposit.builder.DepositSubmissionModelBuilder;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction.CriticalResult;
import org.eclipse.pass.deposit.messaging.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.messaging.config.repository.Repositories;
import org.eclipse.pass.deposit.messaging.model.Packager;
import org.eclipse.pass.deposit.messaging.model.Registry;
import org.eclipse.pass.deposit.messaging.policy.Policy;
import org.eclipse.pass.deposit.messaging.policy.SubmissionPolicy;
import org.eclipse.pass.deposit.model.DepositFile;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.IntegrationType;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionProcessorTest {
    private PassClient passClient;
    private DepositSubmissionModelBuilder depositSubmissionModelBuilder;
    private Registry<Packager> packagerRegistry;
    private SubmissionPolicy submissionPolicy;
    private TaskExecutor taskExecutor;
    private CriticalRepositoryInteraction cri;
    private SubmissionProcessor submissionProcessor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        passClient = mock(PassClient.class);
        depositSubmissionModelBuilder = mock(DepositSubmissionModelBuilder.class);
        packagerRegistry = mock(Registry.class);
        submissionPolicy = mock(SubmissionPolicy.class);
        Policy<DepositStatus> intermediateDepositStatusPolicy = mock(Policy.class);
        taskExecutor = mock(TaskExecutor.class);
        cri = mock(CriticalRepositoryInteraction.class);
        Repositories repositories = mock(Repositories.class);
        DepositTaskHelper depositTaskHelper = new DepositTaskHelper(passClient, taskExecutor,
            intermediateDepositStatusPolicy, cri, repositories);
        submissionProcessor =
            new SubmissionProcessor(passClient, depositSubmissionModelBuilder, packagerRegistry, submissionPolicy,
                depositTaskHelper, cri);
    }

    /**
     * Verifies the actions of SubmissionProcessor when a Submission is successful.  The SubmissionProcessor:
     * <ol>
     *     <li>Updates the aggregated deposit status of the Submission to IN-PROGRESS</li>
     *     <li>Builds a DepositSubmission from the Submission</li>
     *     <li>Creates a Deposit resource in the Fedora repository for each Repository associated with the
     *     Submission</li>
     *     <li>Resolves the Packager (used to perform the transfer of custodial content to a downstream repository)
     *     for each Repository</li>
     *     <li>Composes and submits a DepositTask to the queue for each Deposit</li>
     * </ol>
     * The issue with this test is that it doesn't test the CRI in SubmissionProcessor that updates the status of the
     * incoming Submission to "IN-PROGRESS".  The CRI is completely mocked and so:
     * <ul>
     *     <li>CRI precondition that uses the SubmissionPolicy is untested</li>
     *     <li>CRI postcondition is not tested</li>
     *     <li>The critical update that builds the DepositSubmission and sets the Submission aggregated deposit
     *         status is not tested</li>
     * </ul>
     * Not sure yet what the strategy is to test a CRI implementation.
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void submissionAcceptSuccess() throws Exception {
        // GIVEN
        // Mock the Repositories that the submission is going to
        Repository repository1 = mock(Repository.class);
        when(repository1.getName()).thenReturn("repo-1-name");
        Repository repository2 = mock(Repository.class);
        when(repository2.getName()).thenReturn("repo-2-name");
        List<Repository> repositories = List.of(repository1, repository2);

        // Set the Repositories on the Submission, and create a DepositSubmission (the Submission mapped to the
        // Deposit Services' model).
        Submission submission = new Submission();
        submission.setId("test-submision-id");
        submission.setRepositories(repositories);
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.
        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.result()).thenReturn(Optional.of(depositSubmission));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        // Mock the interactions with the repository that create Deposit resources, insuring the SubmissionProcessor
        // sets the correct state on newly created Deposits.
        repositories.forEach(repo -> {
            try {
                when(passClient.getObject(repo)).thenReturn(repo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            when(packagerRegistry.get(repo.getName())).thenReturn(mock(Packager.class));
        });

        // WHEN
        submissionProcessor.accept(submission);

        // THEN
        // Verify the CRI executed successfully and the results obtained properly
        verify(cri).performCritical(any(), any(), any(), any(BiPredicate.class), any());
        verify(criResult).success();
        verify(criResult).resource();
        verify(criResult).result();

        // Verify we created a Deposit for each Repository
        verify(passClient, times(submission.getRepositories().size()))
            .createObject(any(Deposit.class));

        // Verify that each Repository was read from the Fedora repository, and that a Packager for each Repository was
        // resolved from the PackagerRegistry
        repositories.forEach(repo -> {
            try {
                verify(passClient).getObject(repo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            verify(packagerRegistry).get(repo.getName());
        });

        // Insure that a DepositTask was submitted for each Deposit (number of Repositories == number of Deposits)
        verify(taskExecutor, times(submission.getRepositories().size())).execute(any(DepositTask.class));
    }

    /**
     * Insures that the DepositTaskHelper does not process any [Submission, Deposit, Repository] tuples where the
     * Repository has an IntegrationType of "web-link".
     */
    @Test
    @SuppressWarnings("unchecked")
    public void filterRepositoryByIntegrationType() {
        // GIVEN
        // Mock a DepositTaskHelper for this test.
        DepositTaskHelper mockHelper = mock(DepositTaskHelper.class);
        submissionProcessor = new SubmissionProcessor(passClient, depositSubmissionModelBuilder, packagerRegistry,
            submissionPolicy, mockHelper, cri);

        // Mock the Repositories that the submission is going to
        Repository repository1 = mock(Repository.class);
        when(repository1.getName()).thenReturn("repo-1-name");
        when(repository1.getIntegrationType()).thenReturn(IntegrationType.ONE_WAY);
        Repository repository2 = mock(Repository.class);
        when(repository2.getName()).thenReturn("repo-2-name");
        when(repository2.getIntegrationType()).thenReturn(IntegrationType.WEB_LINK);
        List<Repository> repositories = List.of(repository1, repository2);

        // Set the Repositories on the Submission, and create a DepositSubmission (the Submission mapped to the
        // Deposit Services' model).
        Submission submission = new Submission();
        submission.setId("test-submision-id");
        submission.setRepositories(repositories);
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.
        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.result()).thenReturn(Optional.of(depositSubmission));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        // Mock the interactions with the repository that create Deposit resources, insuring the SubmissionProcessor
        // sets the correct state on newly created Deposits.
        repositories.forEach(repo -> {
            try {
                when(passClient.getObject(repo)).thenReturn(repo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            when(packagerRegistry.get(repo.getName())).thenReturn(mock(Packager.class));
        });

        // WHEN
        // Invoke the SubmissionProcessor
        submissionProcessor.accept(submission);

        // THEN
        // Verify the DepositTaskHelper was called once for each *non-web-link* Repository
        verify(mockHelper, times(1))
            .submitDeposit(
                eq(submission),
                any(DepositSubmission.class),
                eq(repository1),
                any(Deposit.class),
                any(Packager.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void missingCriResource() {
        Submission submission = new Submission();
        submission.setId("test-submission-id");

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.result()).thenReturn(Optional.of(new DepositSubmission()));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        // This should never happen, but Deposit Services checks to be sure that the resource() isn't empty.
        when(criResult.resource()).thenReturn(Optional.empty());

        DepositServiceRuntimeException exception = assertThrows(DepositServiceRuntimeException.class, () -> {
            submissionProcessor.accept(submission);
        });

        assertEquals("Missing expected Submission " + submission.getId(), exception.getMessage());

        // Verify the CRI execution failed
        verify(cri).performCritical(any(), any(), any(), any(BiPredicate.class), any());
        verify(criResult).success();

        // Exception thrown before this is called
        verify(criResult, times(0)).result();

        // Verify nothing was sent to the DepositTask task executor
        verifyNoInteractions(taskExecutor);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void missingCriResult() {
        Submission submission = new Submission();
        submission.setId("test-submission-id");

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.success()).thenReturn(true);
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        // This should never happen, but Deposit Services checks to be sure that the resource() isn't empty.
        when(criResult.result()).thenReturn(Optional.empty());

        DepositServiceRuntimeException exception = assertThrows(DepositServiceRuntimeException.class, () -> {
            submissionProcessor.accept(submission);
        });

        assertEquals("Missing expected DepositSubmission", exception.getMessage());

        // Verify the CRI execution failed
        verify(cri).performCritical(any(), any(), any(), any(BiPredicate.class), any());
        verify(criResult).success();
        verify(criResult).result();

        // Verify nothing was sent to the DepositTask task executor
        verifyNoInteractions(taskExecutor);
    }

    /**
     * If the CRI responsible for updating Submission status and building the DepositSubmission fails, then a
     * DepositServiceRuntimeException should be thrown which contains the Submission.
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void submissionCriFailure() {
        Submission submission = new Submission();
        submission.setId("test-submission-id");

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.
        // In this test the CRI fails, for whatever reason.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(false);
        Exception expectedCause = new Exception("Failed CRI");
        when(criResult.throwable()).thenReturn(Optional.of(expectedCause));
        when(criResult.result()).thenReturn(Optional.of(new DepositSubmission()));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        DepositServiceRuntimeException exception = assertThrows(DepositServiceRuntimeException.class, () -> {
            submissionProcessor.accept(submission);
        });

        assertEquals("Unable to update status of " + submission.getId() + " to 'IN_PROGRESS': Failed CRI",
            exception.getMessage());

        // Verify the CRI execution failed
        verify(cri).performCritical(any(), any(), any(), any(BiPredicate.class), any());
        verify(criResult).success();
        verify(criResult, times(0)).result();

        // Verify nothing was sent to the DepositTask task executor
        verifyNoInteractions(taskExecutor);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void depositCreationFailure() {
        // Set the Repositories on the Submission, and create a DepositSubmission (the Submission mapped to the
        // Deposit Services' model).

        Repository repository1 = mock(Repository.class);
        when(repository1.getName()).thenReturn("repo-1-name");
        List<Repository> repositories = List.of(repository1);
        Submission submission = new Submission();
        submission.setId("test-submission-id");
        submission.setRepositories(repositories);
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.result()).thenReturn(Optional.of(depositSubmission));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        RuntimeException expectedCause = new RuntimeException("Error saving Deposit resource.");
        repositories.forEach(repo -> {
            try {
                when(passClient.getObject(repo)).thenReturn(repo);
                doThrow(expectedCause).when(passClient).createObject(any(Deposit.class));
                when(packagerRegistry.get(repo.getName())).thenReturn(mock(Packager.class));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        DepositServiceRuntimeException exception = assertThrows(DepositServiceRuntimeException.class, () -> {
            submissionProcessor.accept(submission);
        });

        assertEquals("Failed to process Deposit for tuple [test-submission-id, null, null]: " +
                "Error saving Deposit resource.", exception.getMessage());
        verifyNoInteractions(taskExecutor);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void taskRejection() throws Exception {
        // Set the Repositories on the Submission, and create a DepositSubmission (the Submission mapped to the
        // Deposit Services' model).

        Repository repository1 = mock(Repository.class);
        when(repository1.getName()).thenReturn("repo-1-name");
        List<Repository> repositories = List.of(repository1);
        Submission submission = new Submission();
        submission.setId("test-submission-id");
        submission.setRepositories(repositories);
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.result()).thenReturn(Optional.of(depositSubmission));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        repositories.forEach(repo -> {
            try {
                when(passClient.getObject(repo)).thenReturn(repo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            when(packagerRegistry.get(repo.getName())).thenReturn(mock(Packager.class));
        });

        doThrow(TaskRejectedException.class).when(taskExecutor).execute(any(DepositTask.class));

        DepositServiceRuntimeException exception = assertThrows(DepositServiceRuntimeException.class, () -> {
            submissionProcessor.accept(submission);
        });

        assertEquals("Failed to process Deposit for tuple [test-submission-id, null, null]: null",
            exception.getMessage());
        assertEquals(exception.getCause().getClass(), TaskRejectedException.class);
        verify(taskExecutor).execute(any(DepositTask.class));
    }

    /**
     * When a DepositSubmission is successfully built, a Packager is looked up from the Registry in order to create a
     * DepositTask.  If the Packager cannot be looked up, a DepositServiceRuntimeException should be thrown, a
     * DepositTask should <em>not</em> be created and <em>no</em> interaction should occur with the task executor.
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void missingPackagerFromRegistry() throws Exception {
        // Set the Repositories on the Submission, and create a DepositSubmission (the Submission mapped to the
        // Deposit Services' model).

        Repository repository1 = mock(Repository.class);
        when(repository1.getName()).thenReturn("repo-1-name");
        List<Repository> repositories = List.of(repository1);
        Submission submission = new Submission();
        submission.setId("test-submission-id");
        submission.setRepositories(repositories);
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.result()).thenReturn(Optional.of(depositSubmission));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        repositories.forEach(repo -> {
            try {
                when(passClient.getObject(repo)).thenReturn(repo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // Packagers are looked up by name of the repository
            // Return 'null' to mock an error in resolving the Packager
            when(packagerRegistry.get(repo.getName())).thenReturn(null);
        });

        DepositServiceRuntimeException exception = assertThrows(DepositServiceRuntimeException.class, () -> {
            submissionProcessor.accept(submission);
        });

        assertEquals("Failed to process Deposit for tuple [test-submission-id, null, null]: " +
                "No Packager found for tuple [test-submission-id, null, null]: Missing Packager for Repository " +
                "named 'repo-1-name' (key: null)",
            exception.getMessage());
        assertEquals(exception.getCause().getClass(), NullPointerException.class);
        verify(packagerRegistry).get(any());
        verify(passClient, times(0)).createObject(any(Deposit.class));
        verifyNoInteractions(taskExecutor);
    }

    /* Assure the right repo keys are used for lookup */
    @Test
    public void lookupTest() {
        Repository repo = new Repository();
        repo.setId("test-repo-id");
        repo.setName("test-name");
        repo.setRepositoryKey("test-key");

        Collection<String> keys = getLookupKeys(repo);

        assertTrue(keys.contains(repo.getId()));
        assertTrue(keys.contains(repo.getName()));
        assertTrue(keys.contains(repo.getRepositoryKey()));
        assertTrue(keys.contains("test-repo-id"));
        assertTrue(keys.contains("test-name"));
        assertTrue(keys.contains("test-key"));
    }

    /* Just to make sure things don't blow up with null values */
    @Test
    public void lookupNullsTest() {
        Repository repo = new Repository();

        Collection<String> keys = getLookupKeys(repo);

        assertTrue(keys.isEmpty());
    }

    /**
     * The submission is accepted if the submission policy supplied to the precondition accepts the submission
     */
    @Test
    public void criFuncPreconditionSuccess() {
        Submission s = mock(Submission.class);
        when(submissionPolicy.test(s)).thenReturn(true);

        assertTrue(preCondition(submissionPolicy).test(s));

        verify(submissionPolicy).test(s);
    }

    /**
     * The submission is rejected if the submission policy supplied to the precondition rejects the submission
     */
    @Test
    public void criFuncPreconditionFail() {
        Submission s = mock(Submission.class);
        when(submissionPolicy.test(s)).thenReturn(false);

        assertFalse(preCondition(submissionPolicy).test(s));

        verify(submissionPolicy).test(s);
    }

    /**
     * Postcondition succeeds when:
     * - The Submission aggregate deposit status is IN_PROGRESS
     * - The DepositSubmission has at least one file
     * - The DepositFile has a non-empty location
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void criFuncPostconditionSuccess() {
        Submission s = mock(Submission.class);
        DepositSubmission ds = mock(DepositSubmission.class);
        DepositFile df = mock(DepositFile.class);

        when(s.getAggregatedDepositStatus()).thenReturn(AggregatedDepositStatus.IN_PROGRESS);
        when(ds.getFiles()).thenReturn(Collections.singletonList(df));
        when(df.getLocation()).thenReturn(randomId());

        assertTrue(postCondition().test(s, ds));

        verify(s).getAggregatedDepositStatus();
        verify(ds, atLeastOnce()).getFiles();
        verify(df, atLeastOnce()).getLocation();
    }

    /**
     * Postcondition fails when the AggregatedDepositStatus is not IN_PROGRESS
     */
    @Test
    public void criFuncPostconditionFailsAggregateDepositStatus() {
        Submission submission = mock(Submission.class);
        DepositSubmission depositSubmission = mock(DepositSubmission.class);

        AggregatedDepositStatus aggregatedDepositStatus = randomAggregatedDepositStatusExcept(
            AggregatedDepositStatus.IN_PROGRESS);
        when(submission.getAggregatedDepositStatus()).thenReturn(aggregatedDepositStatus);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            postCondition().test(submission, depositSubmission);
        });

        assertEquals("Update postcondition failed for null: expected status 'IN_PROGRESS' " +
                "but actual status is '" + aggregatedDepositStatus + "'",
            exception.getMessage());
        verify(submission, times(2)).getAggregatedDepositStatus();
        verifyNoInteractions(depositSubmission);
    }

    /**
     * Postcondition fails when there are no DepositFiles
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void criFuncPostconditionFailsNoDepositFiles() {
        Submission submission = mock(Submission.class);
        DepositSubmission depositSubmission = mock(DepositSubmission.class);

        when(submission.getAggregatedDepositStatus()).thenReturn(AggregatedDepositStatus.IN_PROGRESS);
        when(depositSubmission.getFiles()).thenReturn(Collections.emptyList());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            postCondition().test(submission, depositSubmission);
        });

        assertEquals("Update postcondition failed for null: the DepositSubmission has no files attached! " +
                "(Hint: check the incoming links to the Submission)",
            exception.getMessage());
        verify(submission).getAggregatedDepositStatus();
        verify(depositSubmission).getFiles();
    }

    /**
     * Postcondition fails if any of the DepositFiles is missing a location
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void criFuncPostconditionFailsDepositFileLocation() {
        Submission submission = mock(Submission.class);
        DepositSubmission depositSubmission = mock(DepositSubmission.class);
        DepositFile file1 = mock(DepositFile.class);
        DepositFile file2 = mock(DepositFile.class);

        when(submission.getAggregatedDepositStatus()).thenReturn(AggregatedDepositStatus.IN_PROGRESS);
        when(depositSubmission.getFiles()).thenReturn(List.of(file1, file2));
        when(file1.getLocation()).thenReturn(randomId().toString());
        when(file2.getLocation()).thenReturn("  ");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            postCondition().test(submission, depositSubmission);
        });

        assertEquals("Update postcondition failed for null: the following DepositFiles are missing URIs " +
                "referencing their binary content: null",
            exception.getMessage());
        verify(submission).getAggregatedDepositStatus();
        verify(depositSubmission, times(2)).getFiles();
        verify(file1, times(2)).getLocation();
        verify(file2, times(2)).getLocation();
    }

    @Test
    public void criFuncCriticalSuccess() throws IOException {
        String submissionId = randomId();
        Submission submission = mock(Submission.class);
        DepositSubmission depositSubmission = mock(DepositSubmission.class);

        when(submission.getId()).thenReturn(submissionId);
        when(depositSubmissionModelBuilder.build(submissionId)).thenReturn(depositSubmission);

        assertSame(depositSubmission, critical(depositSubmissionModelBuilder).apply(submission));

        verify(depositSubmissionModelBuilder).build(submissionId);
        verify(submission).setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
    }

    @Test
    public void criFuncCriticalFailsModelBuilderException() throws IOException {
        String submissionId = randomId();
        Submission submission = mock(Submission.class);

        when(submission.getId()).thenReturn(submissionId);
        when(depositSubmissionModelBuilder.build(submissionId)).thenThrow(RuntimeException.class);

        assertThrows(RuntimeException.class, () -> {
            critical(depositSubmissionModelBuilder).apply(submission);
        });

        verify(depositSubmissionModelBuilder).build(submissionId);
        verify(submission).getId();
        verifyNoMoreInteractions(submission);
    }
}