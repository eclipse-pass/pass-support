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
package org.eclipse.pass.deposit.service;

import static org.eclipse.pass.deposit.DepositMessagingTestUtil.randomDepositStatusExcept;
import static org.eclipse.pass.deposit.DepositMessagingTestUtil.randomId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.config.repository.DepositProcessing;
import org.eclipse.pass.deposit.config.repository.Repositories;
import org.eclipse.pass.deposit.config.repository.RepositoryConfig;
import org.eclipse.pass.deposit.config.repository.RepositoryDepositConfig;
import org.eclipse.pass.deposit.service.DepositTaskHelper.DepositStatusCriFunc;
import org.eclipse.pass.deposit.status.DepositStatusProcessor;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
class DepositTaskHelperTest {
    private PassClient passClient;
    private Deposit deposit;

    @BeforeEach
    public void setUp() throws Exception {
        passClient = mock(PassClient.class);
        deposit = mock(Deposit.class);
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
    void depositCriFuncPreconditionSuccess() {
        String repoKey = randomId();
        String repoCopyId = randomId();

        when(deposit.getDepositStatus()).thenReturn(
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED, DepositStatus.FAILED));
        when(deposit.getDepositStatusRef()).thenReturn(randomId());
        when(deposit.getRepository()).thenReturn(new Repository(repoKey));
        when(deposit.getRepositoryCopy()).thenReturn(new RepositoryCopy(repoCopyId));

        assertTrue(DepositStatusCriFunc.precondition().test(deposit));
    }

    /**
     * When a Deposit has a terminal status, the precondition should fail
     */
    @Test
    void depositCriFuncPreconditionFailTerminalStatus() {
        when(deposit.getDepositStatus()).thenReturn(DepositStatus.ACCEPTED);

        // don't need any other mocking, because the test for status comes first.
        // use Mockito.verify to insure this

        assertFalse(DepositStatusCriFunc.precondition().test(deposit));
        verify(deposit, times(2)).getDepositStatus(); // once for the call, once for the log message
        verify(deposit).getId(); // log message
        verifyNoMoreInteractions(deposit);
        verifyNoInteractions(passClient);
    }

    /**
     * When the deposit has an intermediate status but a null deposit status ref, the precondition should fail
     */
    @Test
    void depositCriFuncPreconditionFailDepositStatusRef() {
        when(deposit.getDepositStatus()).thenReturn(
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED, DepositStatus.FAILED));

        // don't need any other mocking, because null is returned by default for the status uri
        // use Mockito.verify to insure this

        assertFalse(DepositStatusCriFunc.precondition().test(deposit));

        verify(deposit).getDepositStatus();
        verify(deposit).getDepositStatusRef();
        verify(deposit).getId(); // log message
        verifyNoMoreInteractions(deposit);
        verifyNoInteractions(passClient);
    }

    /**
     * When the deposit has an intermediate status and a non-empty status ref but the Repository is null, the
     * precondition should fail.
     */
    @Test
    void depositCriFuncPreconditionFailRepository() {
        String statusRef = randomId();
        when(deposit.getDepositStatus()).thenReturn(
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED, DepositStatus.FAILED));
        when(deposit.getDepositStatusRef()).thenReturn(statusRef);

        assertFalse(DepositStatusCriFunc.precondition().test(deposit));

        verify(deposit).getDepositStatus();
        verify(deposit, atLeastOnce()).getDepositStatusRef();
        verify(deposit).getRepository();
        verify(deposit).getId(); // log message

        verifyNoMoreInteractions(deposit);
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
    void depositCriFuncPreconditionFailNullRepoCopyUri() {
        String statusRef = randomId();
        String repoKey = randomId();
        when(deposit.getDepositStatus()).thenReturn(
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED, DepositStatus.FAILED));
        when(deposit.getDepositStatusRef()).thenReturn(statusRef);
        when(deposit.getRepository()).thenReturn(new Repository(repoKey));

        assertFalse(DepositStatusCriFunc.precondition().test(deposit));

        verify(deposit).getDepositStatus();
        verify(deposit, atLeastOnce()).getDepositStatusRef();
        verify(deposit).getRepository();
        verify(deposit).getRepository();
        verify(deposit).getRepositoryCopy();
        verify(deposit).getId(); // log message

        verifyNoMoreInteractions(deposit);
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
    void depositCriFuncPreconditionFailNullRepoCopy() {
        String statusRef = randomId();
        String repoKey = randomId();
        when(deposit.getDepositStatus()).thenReturn(
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED, DepositStatus.FAILED));
        when(deposit.getDepositStatusRef()).thenReturn(statusRef);
        when(deposit.getRepository()).thenReturn(new Repository(repoKey));

        assertFalse(DepositStatusCriFunc.precondition().test(deposit));

        verify(deposit).getDepositStatus();
        verify(deposit, atLeastOnce()).getDepositStatusRef();
        verify(deposit).getRepository();
        verify(deposit).getRepository();
        verify(deposit).getRepositoryCopy();
        verify(deposit).getId(); // log message

        verifyNoInteractions(passClient);
        verifyNoMoreInteractions(deposit);
    }

    @Test
    void depositCriFuncPostconditionSuccess() {
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);
        when(deposit.getRepositoryCopy()).thenReturn(repoCopy);

        assertTrue(DepositStatusCriFunc.postcondition().test(deposit, deposit));
    }

    @Test
    void depositCriFuncPostconditionFailure() {
        when(deposit.getRepositoryCopy()).thenReturn(null);

        assertFalse(DepositStatusCriFunc.postcondition().test(deposit, deposit));
    }

    /**
     * If the deposit status is ACCEPTED, then the returned repository copy must have a copy status of COMPLETE, or the
     * post condition fails. If the deposit status is REJECTED, then the returned repository copy must have a copy
     * status of REJECTED, or the post condition fails. Otherwise, the post condition succeeds if the repository copy is
     * non-null.
     */
    @Test
    void depositCriFuncPostconditionFailNullRepoCopy() {
        Deposit testResult = new Deposit();
        assertFalse(DepositStatusCriFunc.postcondition().test(deposit, testResult));
        verifyNoInteractions(deposit);
    }

    @Test
    void depositCriFuncCriticalSuccessAccepted() {
        Deposit testDeposit = new Deposit();
        testDepositCriFuncCriticalForStatus(DepositStatus.ACCEPTED, testDeposit, passClient);
    }

    @Test
    void depositCriFuncCriticalSuccessRejected() throws IOException {
        Deposit testDeposit = new Deposit();
        testDepositCriFuncCriticalForStatus(DepositStatus.REJECTED, testDeposit, passClient);
    }

    /**
     * When the Deposit is processed as an intermediate status, the returned RepositoryCopy must not be null in order
     * to succeed.
     * @throws IOException
     */
    @Test
    void depositCriFuncCriticalSuccessIntermediate() {
        DepositStatus statusProcessorResult = randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED,
            DepositStatus.FAILED);

        String repoKey = randomId();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        newRepositoryWithKey(repoKey);
        Repositories repos = newRepositoriesWithConfigFor(repoKey, statusProcessor);
        RepositoryConfig repositoryConfig = repos.getConfig(repoKey);
        when(statusProcessor.process(eq(deposit), any())).thenReturn(statusProcessorResult);

        assertSame(deposit, DepositStatusCriFunc.critical(repositoryConfig, statusProcessor).apply(deposit));

        verify(statusProcessor).process(eq(deposit), any());
    }

    /**
     * When there is an error resolving the DepositStatusProcessor, insure there is a proper error message
     * @throws IOException
     */
    @Test
    void depositCriFuncCriticalDepositStatusProcessorProducesNullStatus() {
        String repoKey = randomId();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        newRepositoryWithKey(repoKey);
        Repositories repos = newRepositoriesWithConfigFor(repoKey, statusProcessor);
        RepositoryConfig repositoryConfig = repos.getConfig(repoKey);
        when(statusProcessor.process(deposit, repos.getConfig(repoKey))).thenReturn(null);

        Exception e = assertThrows(DepositServiceRuntimeException.class, () -> {
            DepositStatusCriFunc.critical(repositoryConfig, statusProcessor).apply(deposit);
        });

        assertTrue(e.getMessage().contains("Failed to update deposit status"));

        verifyNoMoreInteractions(passClient);
    }

    private static Repository newRepositoryWithKey(String key) {
        Repository repo = new Repository();
        repo.setRepositoryKey(key);
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

    private static void testDepositCriFuncCriticalForStatus(DepositStatus statusProcessorResult,
                                                            Deposit deposit,
                                                            PassClient passClient) {
        String repoKey = randomId();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        newRepositoryWithKey(repoKey);
        Repositories repos = newRepositoriesWithConfigFor(repoKey, statusProcessor);
        RepositoryConfig repositoryConfig = repos.getConfig(repoKey);
        when(statusProcessor.process(eq(deposit), any())).thenReturn(statusProcessorResult);

        Deposit result = DepositStatusCriFunc.critical(repositoryConfig, statusProcessor).apply(deposit);

        assertEquals(statusProcessorResult, result.getDepositStatus());
        verify(statusProcessor).process(eq(deposit), any());
    }
}