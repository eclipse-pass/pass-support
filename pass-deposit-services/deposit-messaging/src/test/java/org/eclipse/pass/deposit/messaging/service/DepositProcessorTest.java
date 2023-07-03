/*
 * Copyright 2019 Johns Hopkins University
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

import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomIntermediateAggregatedDepositStatus;
import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomTerminalAggregatedDepositStatus;
import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomId;
import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomIntermediateDepositStatus;
import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomTerminalDepositStatus;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.pass.deposit.messaging.policy.Policy;
import org.eclipse.pass.deposit.messaging.service.DepositProcessor.DepositProcessorCriFunc;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositProcessorTest {

    @SuppressWarnings("unchecked")
    private final Policy<AggregatedDepositStatus> intermediateStatusPolicy = mock(Policy.class);

    @SuppressWarnings("unchecked")
    private final Policy<DepositStatus> terminalStatusPolicy = mock(Policy.class);

    private final PassClient passClient = mock(PassClient.class);
    private final Submission submission = mock(Submission.class);
    private final CriticalRepositoryInteraction cri = mock(CriticalRepositoryInteraction.class);
    private final DepositTaskHelper depositTaskHelper = mock(DepositTaskHelper.class);
    private final DepositProcessor depositProcessor =
        new DepositProcessor(terminalStatusPolicy, intermediateStatusPolicy, cri, passClient, depositTaskHelper);

    @Test
    public void criFuncPreconditionSuccess() {
        when(intermediateStatusPolicy.test(any())).thenReturn(true);
        when(submission.getAggregatedDepositStatus()).thenReturn(randomIntermediateAggregatedDepositStatus.get());

        assertTrue(DepositProcessorCriFunc.precondition(intermediateStatusPolicy).test(submission));

        verify(submission).getAggregatedDepositStatus();
        verify(intermediateStatusPolicy).test(any());
    }

    @Test
    public void criFuncPreconditionFailStatusPolicy() {
        when(intermediateStatusPolicy.test(any())).thenReturn(false);
        when(submission.getAggregatedDepositStatus()).thenReturn(randomTerminalAggregatedDepositStatus.get());

        assertFalse(DepositProcessorCriFunc.precondition(intermediateStatusPolicy).test(submission));

        verify(submission).getAggregatedDepositStatus();
        verify(intermediateStatusPolicy).test(any());
    }

    @Test
    public void criFuncPostconditionSuccess() {
        assertTrue(DepositProcessorCriFunc.postcondition().test(submission));
        verifyNoInteractions(submission);
    }

    @Test
    public void criFuncCriticalSuccessDepositsAreAllAccepted() throws IOException {
        DepositStatus depositStatus = DepositStatus.ACCEPTED;
        AggregatedDepositStatus expectedAggregatedDepositStatus = AggregatedDepositStatus.ACCEPTED;
        prepareCriFuncCriticalSuccess(depositStatus);

        assertSame(submission, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(submission));

        verify(terminalStatusPolicy, times(2)).test(depositStatus);
        verify(submission).setAggregatedDepositStatus(expectedAggregatedDepositStatus);
    }

    @Test
    public void criFuncCriticalSuccessDepositsAreAllRejected() throws IOException {
        DepositStatus depositStatus = DepositStatus.REJECTED;
        AggregatedDepositStatus expectedAggregatedDepositStatus = AggregatedDepositStatus.REJECTED;
        prepareCriFuncCriticalSuccess(depositStatus);

        assertSame(submission, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(submission));

        verify(terminalStatusPolicy, times(2)).test(depositStatus);
        verify(submission).setAggregatedDepositStatus(expectedAggregatedDepositStatus);
    }

    @Test
    public void criFuncCriticalNoopNoDeposits() throws IOException {
        String submissionId = randomId();

        when(submission.getId()).thenReturn(submissionId);
        when(passClient.streamObjects(any())).thenReturn(Stream.empty());

        assertSame(submission, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(submission));

        verify(submission).getId();
        verifyNoMoreInteractions(submission);
        verify(passClient).streamObjects(any());
        verifyNoInteractions(terminalStatusPolicy);
    }

    @Test
    public void criFuncCriticalNoopAtLeastOneDepositIsIntermediate() throws IOException {
        prepareCriFuncCriticalNoop(randomIntermediateDepositStatus, randomTerminalDepositStatus);

        assertSame(submission, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(submission));

        verify(terminalStatusPolicy, atLeastOnce()).test(any());
        verify(submission).getId();
        verifyNoMoreInteractions(submission);
    }

    @Test
    public void criFuncCriticalJsonTypeCoercionException() throws IOException {
        String submissionId = randomId();
        String depositId1 = randomId();
        Deposit deposit1 = mock(Deposit.class);
        Deposit deposit2 = mock(Deposit.class);
        String depositId2 = randomId();

        DepositStatus depositStatus = DepositStatus.ACCEPTED;
        AggregatedDepositStatus expectedStatus = AggregatedDepositStatus.ACCEPTED;
        InvalidTypeIdException invalidTypeIdException = mock(InvalidTypeIdException.class);
        RuntimeException e = new RuntimeException(invalidTypeIdException);

        when(submission.getId()).thenReturn(submissionId);
        when(passClient.streamObjects(any())).thenReturn(Stream.of(deposit1, deposit2));
        when(passClient.getObject(Deposit.class, depositId1)).thenReturn(deposit1);
        when(passClient.getObject(Deposit.class, depositId2)).thenThrow(e);
        when(deposit1.getDepositStatus()).thenReturn(depositStatus);
        when(deposit2.getDepositStatus()).thenReturn(depositStatus);
        when(terminalStatusPolicy.test(depositStatus)).thenReturn(true);

        assertSame(submission, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(submission));

        verify(terminalStatusPolicy, times(2)).test(depositStatus);
        verify(submission).setAggregatedDepositStatus(expectedStatus);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void acceptDepositWithTerminalStatus() {
        String submissionId = randomId();
        when(submission.getId()).thenReturn(submissionId);
        Deposit terminalDeposit = mock(Deposit.class);
        DepositStatus terminalStatus = randomTerminalDepositStatus.get();

        when(terminalDeposit.getSubmission()).thenReturn(submission);
        when(terminalDeposit.getDepositStatus()).thenReturn(terminalStatus);
        when(terminalStatusPolicy.test(terminalStatus)).thenReturn(true);

        depositProcessor.accept(terminalDeposit);

        verify(terminalDeposit).getDepositStatus();
        verify(terminalStatusPolicy).test(terminalStatus);
        verify(cri).performCritical(any(), any(), any(), any(Predicate.class), any());
        verifyNoInteractions(depositTaskHelper);
    }

    @Test
    public void acceptDepositWithIntermediateStatus() {
        String depositId = randomId();
        Deposit intermediateDeposit = mock(Deposit.class);
        DepositStatus intermediateStatus = randomIntermediateDepositStatus.get();

        when(intermediateDeposit.getId()).thenReturn(depositId);
        when(intermediateDeposit.getDepositStatus()).thenReturn(intermediateStatus);
        when(terminalStatusPolicy.test(intermediateStatus)).thenReturn(false);

        depositProcessor.accept(intermediateDeposit);

        verify(intermediateDeposit).getDepositStatus();
        verify(terminalStatusPolicy).test(intermediateStatus);
        verifyNoInteractions(cri);
        verify(depositTaskHelper).processDepositStatus(depositId);
    }

    private void prepareCriFuncCriticalSuccess(DepositStatus depositStatus) throws IOException {
        String submissionId = randomId();
        String depositId1 = randomId();
        Deposit deposit1 = mock(Deposit.class);
        String depositId2 = randomId();
        Deposit deposit2 = mock(Deposit.class);

        when(submission.getId()).thenReturn(submissionId);
        when(passClient.streamObjects(any())).thenReturn(Stream.of(deposit1, deposit2));
        when(passClient.getObject(Deposit.class, depositId1)).thenReturn(deposit1);
        when(passClient.getObject(Deposit.class, depositId2)).thenReturn(deposit2);

        when(deposit1.getDepositStatus()).thenReturn(depositStatus);
        when(deposit2.getDepositStatus()).thenReturn(depositStatus);
        when(terminalStatusPolicy.test(depositStatus)).thenReturn(true);
    }

    private void prepareCriFuncCriticalNoop(Supplier<DepositStatus> intermediateSupplier,
                                            Supplier<DepositStatus> terminalSupplier) throws IOException {
        String submissionId = randomId();
        String depositId1 = randomId();
        Deposit deposit1 = mock(Deposit.class);
        String depositId2 = randomId();
        Deposit deposit2 = mock(Deposit.class);
        DepositStatus intermediateStatus = intermediateSupplier.get();
        DepositStatus terminalStatus = terminalSupplier.get();

        when(submission.getId()).thenReturn(submissionId);
        when(passClient.streamObjects(any())).thenReturn(Stream.of(deposit1, deposit2));
        when(passClient.getObject(Deposit.class, depositId1)).thenReturn(deposit1);
        when(passClient.getObject(Deposit.class, depositId2)).thenReturn(deposit2);

        when(deposit1.getDepositStatus()).thenReturn(intermediateStatus);
        when(deposit2.getDepositStatus()).thenReturn(terminalStatus);
        when(terminalStatusPolicy.test(intermediateStatus)).thenReturn(false);
        when(terminalStatusPolicy.test(terminalStatus)).thenReturn(true);
    }

}