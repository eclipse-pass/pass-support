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

import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomDepositStatusExcept;
import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.eclipse.pass.deposit.messaging.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.messaging.RemedialDepositException;
import org.eclipse.pass.deposit.messaging.config.repository.DepositProcessing;
import org.eclipse.pass.deposit.messaging.config.repository.Repositories;
import org.eclipse.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.eclipse.pass.deposit.messaging.config.repository.RepositoryDepositConfig;
import org.eclipse.pass.deposit.messaging.model.Packager;
import org.eclipse.pass.deposit.messaging.policy.Policy;
import org.eclipse.pass.deposit.messaging.service.DepositTaskHelper.DepositStatusCriFunc;
import org.eclipse.pass.deposit.messaging.status.DepositStatusProcessor;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositTaskHelperTest {
    private PassClient passClient;

    private TaskExecutor taskExecutor;

    private Policy<DepositStatus> intermediateDepositStatusPolicy;

    private Policy<DepositStatus> terminalDepositStatusPolicy;

    private CriticalRepositoryInteraction cri;

    private Submission s;

    private DepositSubmission ds;

    private Repository r;

    private Packager p;

    private Deposit d;

    private DepositTaskHelper underTest;

    private Repositories repositories;


    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        passClient = mock(PassClient.class);
        taskExecutor = mock(TaskExecutor.class);
        intermediateDepositStatusPolicy = mock(Policy.class);
        terminalDepositStatusPolicy = mock(Policy.class);
        cri = mock(CriticalRepositoryInteraction.class);
        repositories = mock(Repositories.class);

        underTest = new DepositTaskHelper(passClient, taskExecutor, intermediateDepositStatusPolicy,
                                          cri, repositories);

        s = mock(Submission.class);
        ds = mock(DepositSubmission.class);
        d = mock(Deposit.class);
        r = mock(Repository.class);
        p = mock(Packager.class);
    }

    @Test
    public void j10sStatementUrlHackWithNullValues() throws Exception {
        ArgumentCaptor<DepositTask> dtCaptor = ArgumentCaptor.forClass(DepositTask.class);

        underTest.submitDeposit(s, ds, r, d, p);

        verify(taskExecutor).execute(dtCaptor.capture());

        DepositTask depositTask = dtCaptor.getValue();
        assertNotNull(depositTask);

        assertNull(depositTask.getPrefixToMatch());
        assertNull(depositTask.getReplacementPrefix());
    }

    @Test
    public void j10sStatementUrlHack() throws Exception {
        ArgumentCaptor<DepositTask> dtCaptor = ArgumentCaptor.forClass(DepositTask.class);
        String prefix = "moo";
        String replacement = "foo";

        underTest.setStatementUriPrefix(prefix);
        underTest.setStatementUriReplacement(replacement);
        underTest.submitDeposit(s, ds, r, d, p);

        verify(taskExecutor).execute(dtCaptor.capture());

        DepositTask depositTask = dtCaptor.getValue();
        assertNotNull(depositTask);

        assertEquals(prefix, depositTask.getPrefixToMatch());
        assertEquals(replacement, depositTask.getReplacementPrefix());
    }

    @Test
    public void lookupRepositoryConfigByKey() {
        String key = "repoKey";
        Repository repo = newRepositoryWithKey(key);
        Repositories repositories = newRepositoriesWithConfigFor(key);

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for key '" + key + "'"));
    }

    @Test
    public void lookupRepositoryConfigByUri() {
        String uri = "http://pass.jhu.edu/fcrepo/repositories/ab/cd/ef/gh/abcdefghilmnop";
        Repository repo = newRepositoryWithId(uri);
        Repositories repositories = newRepositoriesWithConfigFor(uri);

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for String '" + uri + "'"));
    }

    @Test
    public void lookupRepositoryConfigByUriPath() {
        String path = "/fcrepo/repositories/a-repository";
        String uri = "http://pass.jhu.edu" + path;
        Repository repo = newRepositoryWithId(uri);
        Repositories repositories = newRepositoriesWithConfigFor(path);

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for path '" + path + "'"));
    }

    @Test
    public void lookupRepositoryConfigByUriPathComponent() {
        String uri = "http://pass.jhu.edu/fcrepo/repositories/a-repository";
        Repository repo = newRepositoryWithId(uri);
        Repositories repositories = newRepositoriesWithConfigFor("a-repository");

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for path 'a-repository'"));

        repositories = newRepositoriesWithConfigFor("/a-repository");

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for path '/a-repository'"));

        repositories = newRepositoriesWithConfigFor("/fcrepo/repositories/a-repository");

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(() -> new RuntimeException(
                             "Missing expected repository config for path '/fcrepo/repositories/a-repository'"));
    }

    /**
     * When a Deposit has:
     * - an intermediate status
     * - a non-null and non-empty status ref
     * - a repository URI
     * - a repository copy
     *
     * Then the precondition should succeed.
     * @throws IOException
     */
    @Test
    public void depositCriFuncPreconditionSuccess() throws IOException {
        String repoId = randomId();
        String repoCopyId = randomId();
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);

        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));
        when(d.getDepositStatusRef()).thenReturn(randomId().toString());
        when(d.getRepository()).thenReturn(new Repository(repoId));
        when(d.getRepositoryCopy()).thenReturn(new RepositoryCopy(repoCopyId));
        when(passClient.getObject(RepositoryCopy.class, repoCopyId)).thenReturn(repoCopy);

        assertTrue(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(intermediateDepositStatusPolicy).test(any());
        verify(passClient).getObject(RepositoryCopy.class, repoCopyId);
    }

    /**
     * When a Deposit has a terminal status, the precondition should fail
     */
    @Test
    public void depositCriFuncPreconditionFailTerminalStatus() {
        when(intermediateDepositStatusPolicy.test(any())).thenReturn(false);
        when(d.getDepositStatus()).thenReturn(DepositStatus.SUBMITTED);

        // don't need any other mocking, because the test for status comes first.
        // use Mockito.verify to insure this

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));
        verify(d, times(2)).getDepositStatus(); // once for the call, once for the log message
        verify(d).getId(); // log message
        verifyNoMoreInteractions(d);
        verifyNoInteractions(passClient);
    }

    /**
     * When the deposit has an intermediate status but a null deposit status ref, the precondition should fail
     */
    @Test
    public void depositCriFuncPreconditionFailDepositStatusRef() {
        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));

        // don't need any other mocking, because null is returned by default for the status uri
        // use Mockito.verify to insure this

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(d).getDepositStatus();
        verify(d).getDepositStatusRef();
        verify(d).getId(); // log message
        verify(intermediateDepositStatusPolicy).test(any());
        verifyNoMoreInteractions(d);
        verifyNoInteractions(passClient);
    }

    /**
     * When the deposit has an intermediate status and a non-empty status ref but the Repository is null, the
     * precondition should fail.
     */
    @Test
    public void depositCriFuncPreconditionFailRepository() {
        String statusRef = randomId();

        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));
        when(d.getDepositStatusRef()).thenReturn(statusRef.toString());

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(d).getDepositStatus();
        verify(d, atLeastOnce()).getDepositStatusRef();
        verify(d).getRepository();
        verify(d).getId(); // log message

        verify(intermediateDepositStatusPolicy).test(any());
        verifyNoMoreInteractions(d);
        verifyNoInteractions(passClient);
    }

    /**
     * When the deposit has:
     * - an intermediate status
     * - non-empty status ref
     * - non-null Repository
     *
     * but the RepositoryCopy String is null, the precondition should fail
     */
    @Test
    public void depositCriFuncPreconditionFailNullRepoCopyUri() {
        String statusRef = randomId();
        String repoId = randomId();

        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));
        when(d.getDepositStatusRef()).thenReturn(statusRef.toString());
        when(d.getRepository()).thenReturn(new Repository(repoId));

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(d).getDepositStatus();
        verify(d, atLeastOnce()).getDepositStatusRef();
        verify(d).getRepository();
        verify(d).getRepository();
        verify(d).getRepositoryCopy();
        verify(d).getId(); // log message

        verify(intermediateDepositStatusPolicy).test(any());
        verifyNoMoreInteractions(d);
        verifyNoInteractions(passClient);
    }

    /***
     * When the deposit has:
     * - an intermediate status
     * - non-empty status ref
     * - non-null repository
     * - non-null repositorycopyURI
     *
     * but the RepositoryCopy is null, the precondition should fail.
     * @throws IOException
     */
    @Test
    public void depositCriFuncPreconditionFailNullRepoCopy() throws IOException {
        String statusRef = randomId();
        String repoId = randomId();
        String repoCopyId = randomId();

        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));
        when(d.getDepositStatusRef()).thenReturn(statusRef.toString());
        when(d.getRepository()).thenReturn(new Repository(repoId));
        when(d.getRepositoryCopy()).thenReturn(new RepositoryCopy(repoCopyId));

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(d).getDepositStatus();
        verify(d, atLeastOnce()).getDepositStatusRef();
        verify(d).getRepository();
        verify(d).getRepository();
        verify(d).getRepositoryCopy();
        verify(d).getId(); // log message

        verify(intermediateDepositStatusPolicy).test(any());
        verify(passClient).getObject(RepositoryCopy.class, repoCopyId);
        verifyNoMoreInteractions(d);
    }

    /**
     * If the deposit status is ACCEPTED, then the returned repository copy must have a copy status of COMPLETE, or the
     * post condition fails.
     * If the deposit status is REJECTED, then the returned repository copy must have a copy status of REJECTED, or the
     * post condition fails.
     * Otherwise, the post condition succeeds if the repository copy is non-null.
     */
    @Test
    public void depositCriFuncPostconditionSuccessAccepted() {
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);
        when(d.getDepositStatus()).thenReturn(DepositStatus.ACCEPTED);
        when(repoCopy.getCopyStatus()).thenReturn(CopyStatus.COMPLETE);

        assertTrue(DepositStatusCriFunc.postcondition().test(d, repoCopy));

        verify(repoCopy).getCopyStatus();
    }

    /**
     * If the deposit status is ACCEPTED, then the returned repository copy must have a copy status of COMPLETE, or the
     * post condition fails. If the deposit status is REJECTED, then the returned repository copy must have a copy
     * status of REJECTED, or the post condition fails. Otherwise, the post condition succeeds if the repository copy is
     * non-null.
     */
    @Test
    public void depositCriFuncPostconditionSuccessRejected() {
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);
        when(d.getDepositStatus()).thenReturn(DepositStatus.REJECTED);
        when(repoCopy.getCopyStatus()).thenReturn(CopyStatus.REJECTED);

        assertTrue(DepositStatusCriFunc.postcondition().test(d, repoCopy));

        verify(repoCopy).getCopyStatus();
    }

    /**
     * If the deposit status is ACCEPTED, then the returned repository copy must have a copy status of COMPLETE, or the
     * post condition fails. If the deposit status is REJECTED, then the returned repository copy must have a copy
     * status of REJECTED, or the post condition fails. Otherwise, the post condition succeeds if the repository copy is
     * non-null.
     */
    @Test
    public void depositCriFuncPostconditionSuccessIntermediate() {
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);
        when(d.getDepositStatus()).thenReturn(DepositStatus.SUBMITTED);

        assertTrue(DepositStatusCriFunc.postcondition().test(d, repoCopy));

        verifyNoInteractions(repoCopy);
    }

    /**
     * If the deposit status is ACCEPTED, then the returned repository copy must have a copy status of COMPLETE, or the
     * post condition fails. If the deposit status is REJECTED, then the returned repository copy must have a copy
     * status of REJECTED, or the post condition fails. Otherwise, the post condition succeeds if the repository copy is
     * non-null.
     */
    @Test
    public void depositCriFuncPostconditionFailNullRepoCopy() {
        assertFalse(DepositStatusCriFunc.postcondition().test(d, null));
        verifyNoInteractions(d);
    }

    /**
     * When the Deposit is processed as ACCEPTED, the copy status should be set to COMPLETE, and the returned
     * repository copy not null
     * @throws IOException
     */
    @Test
    public void depositCriFuncCriticalSuccessAccepted() throws IOException {
        CopyStatus expectedCopyStatus = CopyStatus.COMPLETE;
        DepositStatus statusProcessorResult = DepositStatus.ACCEPTED;

        testDepositCriFuncCriticalForStatus(expectedCopyStatus, statusProcessorResult, d, passClient);
    }

    /**
     * When the Deposit is processed as REJECTED, the copy status should be set to REJECTED, and the returned
     * repository copy not null
     * @throws IOException
     */
    @Test
    public void depositCriFuncCriticalSuccessRejected() throws IOException {
        CopyStatus expectedCopyStatus = CopyStatus.REJECTED;
        DepositStatus statusProcessorResult = DepositStatus.REJECTED;

        testDepositCriFuncCriticalForStatus(expectedCopyStatus, statusProcessorResult, d, passClient);
    }

    /**
     * When the Deposit is processed as an intermediate status, the returned RepositoryCopy must not be null in order
     * to succeed.
     * @throws IOException
     */
    @Test
    public void depositCriFuncCriticalSuccessIntermediate() throws IOException {
        DepositStatus statusProcessorResult = randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED);

        String repoId = randomId();
        String repoCopyId = randomId();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithId(repoId.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoId.toString(), statusProcessor);
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);

        when(d.getRepository()).thenReturn(new Repository(repoId));
        when(d.getRepositoryCopy()).thenReturn(new RepositoryCopy(repoCopyId));

        when(passClient.getObject(Repository.class, repoId)).thenReturn(repo);
        when(passClient.getObject(RepositoryCopy.class, repoCopyId)).thenReturn(repoCopy);

        when(statusProcessor.process(eq(d), any())).thenReturn(statusProcessorResult);

        assertSame(repoCopy, DepositStatusCriFunc.critical(repos, passClient).apply(d));

        verify(passClient).getObject(Repository.class, repoId);
        verify(passClient).getObject(RepositoryCopy.class, repoCopyId);
        verifyNoMoreInteractions(passClient);
        verify(statusProcessor).process(eq(d), any());
        verifyNoInteractions(repoCopy);
    }

    /**
     * When there is an error looking up the RepositoryConfig insure there is a proper error message
     * @throws IOException
     */
    @Test
    public void depositCriFuncCriticalMissingRepositoryConfig() throws IOException {
        String repoId = randomId();

        when(d.getRepository()).thenReturn(new Repository(repoId));
        when(passClient.getObject(Repository.class, repoId)).thenReturn(r);

        Exception e = assertThrows(RemedialDepositException.class, () -> {
            DepositStatusCriFunc.critical(repositories, passClient).apply(d);
        });

        assertTrue(e.getMessage().contains("Unable to resolve Repository Configuration for Repository"));

        verify(passClient).getObject(Repository.class, repoId);
        verifyNoMoreInteractions(passClient);
    }

    /**
     * When there is an error resolving the DepositStatusProcessor, insure there is a proper error message
     * @throws IOException
     */
    @Test
    public void depositCriFuncCriticalNullDepositConfig() throws IOException {
        String repoId = randomId();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithId(repoId);
        Repositories repos = newRepositoriesWithConfigFor(repoId.toString(), statusProcessor);

        when(d.getRepository()).thenReturn(repo);
        when(passClient.getObject(Repository.class, repoId)).thenReturn(repo);
        repos.getConfig(repoId.toString()).setRepositoryDepositConfig(null);

        verifyNullObjectInDepositStatusProcessorLookup(repoId, repos);
    }

    /**
     * When there is an error resolving the DepositStatusProcessor, insure there is a proper error message
     * @throws IOException
     */
    @Test
    public void depositCriFuncCriticalNullDepositProcessing() throws IOException {
        String repoId = randomId();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithId(repoId.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoId.toString(), statusProcessor);

        when(d.getRepository()).thenReturn(repo);
        when(passClient.getObject(Repository.class, repoId)).thenReturn(repo);
        repos.getConfig(repoId.toString()).getRepositoryDepositConfig().setDepositProcessing(null);

        verifyNullObjectInDepositStatusProcessorLookup(repoId, repos);
    }

    /**
     * When there is an error resolving the DepositStatusProcessor, insure there is a proper error message
     * @throws IOException
     */
    @Test
    public void depositCriFuncCriticalNullDepositStatusProcessor() throws IOException {
        String repoId = randomId();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithId(repoId.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoId.toString(), statusProcessor);

        when(d.getRepository()).thenReturn(repo);
        when(passClient.getObject(Repository.class, repoId)).thenReturn(repo);
        repos.getConfig(repoId.toString()).getRepositoryDepositConfig().getDepositProcessing().setProcessor(null);

        verifyNullObjectInDepositStatusProcessorLookup(repoId, repos);
    }

    /**
     * When there is an error resolving the DepositStatusProcessor, insure there is a proper error message
     * @throws IOException
     */
    @Test
    public void depositCriFuncCriticalDepositStatusProcessorProducesNullStatus() throws IOException {
        String repoId = randomId();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithId(repoId.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoId.toString(), statusProcessor);

        when(d.getRepository()).thenReturn(repo);
        when(passClient.getObject(Repository.class, repoId)).thenReturn(repo);
        when(statusProcessor.process(d, repos.getConfig(repoId.toString()))).thenReturn(null);

        Exception e = assertThrows(DepositServiceRuntimeException.class, () -> {
            DepositStatusCriFunc.critical(repositories, passClient).apply(d);
        });

        assertTrue(e.getMessage().contains("Failed to update deposit status"));

        verify(d).getRepository();
        verify(passClient).getObject(Repository.class, repoId);
        verifyNoMoreInteractions(passClient);
    }

    private void verifyNullObjectInDepositStatusProcessorLookup(String repoId, Repositories repos) throws IOException {

        Exception e = assertThrows(DepositServiceRuntimeException.class, () -> {
            DepositStatusCriFunc.critical(repos, passClient).apply(d);
        });

        assertTrue(e.getMessage().contains("parsing the status document referenced by"));

        verify(passClient).getObject(Repository.class, repoId);
        verifyNoMoreInteractions(passClient);
    }

    private static Repository newRepositoryWithKey(String key) {
        Repository repo = new Repository();
        repo.setRepositoryKey(key);
        return repo;
    }

    private static Repository newRepositoryWithId(String id) {
        Repository repo = new Repository();
        repo.setId(id);
        return repo;
    }

    private static Repositories newRepositoriesWithConfigFor(String key) {
        Repositories repos = new Repositories();
        RepositoryConfig config = new RepositoryConfig();
        config.setRepositoryKey(key);
        repos.addRepositoryConfig(key, config);
        return repos;
    }

    private static Repositories newRepositoriesWithConfigFor(String key, DepositStatusProcessor statusProcessor) {
        Repositories repos = newRepositoriesWithConfigFor(key);

        RepositoryConfig repoConfig = repos.getConfig(key);
        RepositoryDepositConfig depositConfig = new RepositoryDepositConfig();
        DepositProcessing depositProcessing = new DepositProcessing();

        repoConfig.setRepositoryDepositConfig(depositConfig);
        depositConfig.setDepositProcessing(depositProcessing);
        depositProcessing.setProcessor(statusProcessor);

        return repos;
    }

    private static void testDepositCriFuncCriticalForStatus(CopyStatus expectedCopyStatus,
                                                            DepositStatus statusProcessorResult,
                                                            Deposit deposit,
                                                            PassClient passClient) throws IOException {
        String repoId = randomId();
        String repoCopyId = randomId();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithId(repoId.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoId.toString(), statusProcessor);
        RepositoryCopy repoCopy = new RepositoryCopy(); // concrete to capture state changes performed by critical

        when(deposit.getRepository()).thenReturn(repo);
        when(deposit.getRepositoryCopy()).thenReturn(repoCopy);

        when(passClient.getObject(Repository.class, repoId)).thenReturn(repo);
        when(passClient.getObject(RepositoryCopy.class, repoCopyId)).thenReturn(repoCopy);

        when(statusProcessor.process(eq(deposit), any())).thenReturn(statusProcessorResult);

        RepositoryCopy result = DepositStatusCriFunc.critical(repos, passClient).apply(deposit);

        assertEquals(expectedCopyStatus, result.getCopyStatus());

        verify(passClient).updateObject(repoCopy);
        verify(statusProcessor).process(eq(deposit), any());
    }
}