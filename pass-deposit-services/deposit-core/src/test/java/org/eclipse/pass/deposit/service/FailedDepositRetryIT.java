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
package org.eclipse.pass.deposit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Set;

import org.eclipse.deposit.util.async.Condition;
import org.eclipse.pass.deposit.transport.RepositoryConnectivityService;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.swordapp.client.SWORDCollection;
import org.swordapp.client.SWORDError;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
class FailedDepositRetryIT extends AbstractDepositIT {

    @Autowired private DepositUpdater depositUpdater;
    @Autowired private DepositTaskHelper depositTaskHelper;
    @MockBean private RepositoryConnectivityService repositoryConnectivityService;

    @Test
    void testFailedDepositRetry_SuccessOnRetry() throws Exception {
        // GIVEN
        Submission submission = initFailedSubmissionDeposit(DepositStatus.RETRY);
        mockSword();
        when(repositoryConnectivityService.verifyConnectByURL(anyString())).thenReturn(true);

        // WHEN
        try {
            depositUpdater.doUpdate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // THEN
        Condition<Set<Deposit>> actualDeposits = depositsForSubmission(submission.getId(), 1,
            (deposit, repo) -> true);
        assertTrue(actualDeposits.awaitAndVerify(deposits -> deposits.size() == 1 &&
            DepositStatus.SUBMITTED == deposits.iterator().next().getDepositStatus()));

        Deposit actualDeposit = actualDeposits.getResult().iterator().next();
        Deposit updatedDeposit = passClient.getObject(actualDeposit, "repositoryCopy");
        RepositoryCopy popRepoCopy = passClient.getObject(updatedDeposit.getRepositoryCopy(),
            "repository", "publication");
        updatedDeposit.setRepositoryCopy(popRepoCopy);
        verify(passClient, times(3)).updateObject(
            argThat(deposit -> deposit.getId().equals(actualDeposit.getId())));
        assertEquals(submission.getPublication().getId(), popRepoCopy.getPublication().getId());
        assertEquals(1, submission.getRepositories().size());
        assertEquals(submission.getRepositories().get(0).getId(), popRepoCopy.getRepository().getId());
    }

    @Test
    void testFailedDepositRetry_FailOnRetry() throws Exception {
        // GIVEN
        Submission submission = initFailedSubmissionDeposit(DepositStatus.RETRY);
        when(repositoryConnectivityService.verifyConnectByURL(anyString())).thenReturn(true);
        mockSword();
        doThrow(new SWORDError(400, "Testing deposit error"))
            .when(mockSwordClient).deposit(any(SWORDCollection.class), any(), any());

        // WHEN
        try {
            depositUpdater.doUpdate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // THEN
        Condition<Set<Deposit>> actualDeposits = depositsForSubmission(submission.getId(), 1,
            (deposit, repo) -> true);
        assertTrue(actualDeposits.awaitAndVerify(deposits -> deposits.size() == 1 &&
            DepositStatus.FAILED == deposits.iterator().next().getDepositStatus()));

        Deposit actualDeposit = actualDeposits.getResult().iterator().next();
        Deposit updatedDeposit = passClient.getObject(actualDeposit, "repositoryCopy");
        assertNull(updatedDeposit.getRepositoryCopy());
        verify(passClient, times(3)).updateObject(
            argThat(deposit -> deposit.getId().equals(actualDeposit.getId())));
    }

    @Test
    void testFailedDepositRetry_FailOnRetryConnectivity() throws Exception {
        // GIVEN
        Submission submission = initFailedSubmissionDeposit(DepositStatus.RETRY);

        // WHEN
        try {
            depositUpdater.doUpdate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // THEN
        Condition<Set<Deposit>> actualDeposits = depositsForSubmission(submission.getId(), 1,
            (deposit, repo) -> true);
        assertTrue(actualDeposits.awaitAndVerify(deposits -> deposits.size() == 1 &&
            DepositStatus.RETRY == deposits.iterator().next().getDepositStatus()));

        Deposit actualDeposit = actualDeposits.getResult().iterator().next();
        Deposit updatedDeposit = passClient.getObject(actualDeposit, "repositoryCopy");
        assertNull(updatedDeposit.getRepositoryCopy());
        verify(passClient, times(2)).updateObject(
            argThat(deposit -> deposit.getId().equals(actualDeposit.getId())));
    }

    @Test
    @DirtiesContext
    void testFailedDepositRetry_NoFailRetryIfDisabledAfterDeposit() throws Exception {
        // GIVEN
        Submission submission = initFailedSubmissionDeposit(DepositStatus.RETRY);
        mockSword();
        ReflectionTestUtils.setField(depositUpdater, "retryFailedDepositsEnabled", false);
        ReflectionTestUtils.setField(depositTaskHelper, "retryFailedDepositsEnabled", false);

        // WHEN
        try {
            depositUpdater.doUpdate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // THEN
        Condition<Set<Deposit>> actualDeposits = depositsForSubmission(submission.getId(), 1,
            (deposit, repo) -> true);
        assertTrue(actualDeposits.awaitAndVerify(deposits -> deposits.size() == 1 &&
            DepositStatus.RETRY == deposits.iterator().next().getDepositStatus()));

        Deposit actualDeposit = actualDeposits.getResult().iterator().next();
        Deposit updatedDeposit = passClient.getObject(actualDeposit, "repositoryCopy");
        assertNull(updatedDeposit.getRepositoryCopy());
        verify(passClient, times(1)).updateObject(
            argThat(deposit -> deposit.getId().equals(actualDeposit.getId())));
    }

    @Test
    @DirtiesContext
    void testFailedDepositRetry_NoFailRetryIfDisabledBeforeDeposit() throws Exception {
        // GIVEN
        ReflectionTestUtils.setField(depositUpdater, "retryFailedDepositsEnabled", false);
        ReflectionTestUtils.setField(depositTaskHelper, "retryFailedDepositsEnabled", false);
        mockSword();
        doThrow(new SWORDError(400, "Testing deposit error"))
            .when(mockSwordClient).deposit(any(SWORDCollection.class), any(), any());
        Submission submission = initFailedSubmissionDeposit(DepositStatus.FAILED);

        // WHEN
        try {
            depositUpdater.doUpdate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // THEN
        Condition<Set<Deposit>> actualDeposits = depositsForSubmission(submission.getId(), 1,
            (deposit, repo) -> true);
        assertTrue(actualDeposits.awaitAndVerify(deposits -> deposits.size() == 1 &&
            DepositStatus.FAILED == deposits.iterator().next().getDepositStatus()));

        Deposit actualDeposit = actualDeposits.getResult().iterator().next();
        Deposit updatedDeposit = passClient.getObject(actualDeposit, "repositoryCopy");
        assertNull(updatedDeposit.getRepositoryCopy());
        verify(passClient, times(2)).updateObject(
            argThat(deposit -> deposit.getId().equals(actualDeposit.getId())));
    }

    private Submission initFailedSubmissionDeposit(DepositStatus depositStatus) throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        submission.setSubmittedDate(ZonedDateTime.now());
        passClient.updateObject(submission);
        when(repositoryConnectivityService.verifyConnectByURL(anyString())).thenReturn(false);
        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());
        submissionProcessor.accept(actualSubmission);
        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
            depositStatus == deposits.iterator().next()
                .getDepositStatus()));
        return actualSubmission;
    }

}
