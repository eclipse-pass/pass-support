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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.eclipse.pass.deposit.messaging.policy.Policy;
import org.eclipse.pass.deposit.messaging.service.DepositProcessor.DepositProcessorCriFunc;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositProcessorTest {

    @SuppressWarnings("unchecked")
    private Policy<AggregatedDepositStatus> intermediateStatusPolicy = mock(Policy.class);

    @SuppressWarnings("unchecked")
    private Policy<DepositStatus> terminalStatusPolicy = mock(Policy.class);

    private PassClient passClient = mock(PassClient.class);

    private Submission s = mock(Submission.class);

    private CriticalRepositoryInteraction cri = mock(CriticalRepositoryInteraction.class);

    private DepositTaskHelper depositHelper = mock(DepositTaskHelper.class);

    private DepositProcessor underTest = new DepositProcessor(terminalStatusPolicy, intermediateStatusPolicy, cri,
                                                              passClient, depositHelper);

    @Test
    public void criFuncPreconditionSuccess() {
        when(intermediateStatusPolicy.test(any())).thenReturn(true);
        when(s.getAggregatedDepositStatus()).thenReturn(randomIntermediateAggregatedDepositStatus.get());

        assertTrue(DepositProcessorCriFunc.precondition(intermediateStatusPolicy).test(s));

        verify(s).getAggregatedDepositStatus();
        verify(intermediateStatusPolicy).test(any());
    }

    @Test
    public void criFuncPreconditionFailStatusPolicy() {
        when(intermediateStatusPolicy.test(any())).thenReturn(false);
        when(s.getAggregatedDepositStatus()).thenReturn(randomTerminalAggregatedDepositStatus.get());

        assertFalse(DepositProcessorCriFunc.precondition(intermediateStatusPolicy).test(s));

        verify(s).getAggregatedDepositStatus();
        verify(intermediateStatusPolicy).test(any());
    }

    @Test
    public void criFuncPostconditionSuccess() {
        assertTrue(DepositProcessorCriFunc.postcondition().test(s));
        verifyZeroInteractions(s);
    }

    // TODO Deposit service port pending
//    @Test
//    public void criFuncCriticalSuccessDepositsAreAllAccepted() {
//        DepositStatus depositStatus = DepositStatus.ACCEPTED;
//        AggregatedDepositStatus expectedAggregatedDepositStatus = AggregatedDepositStatus.ACCEPTED;
//        prepareCriFuncCriticalSuccess(depositStatus);
//
//        assertSame(s, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(s));
//
//        verify(terminalStatusPolicy, times(2)).test(depositStatus);
//        verify(s).setAggregatedDepositStatus(expectedAggregatedDepositStatus);
//    }
//
//    @Test
//    public void criFuncCriticalSuccessDepositsAreAllRejected() {
//        DepositStatus depositStatus = DepositStatus.REJECTED;
//        AggregatedDepositStatus expectedAggregatedDepositStatus = AggregatedDepositStatus.REJECTED;
//        prepareCriFuncCriticalSuccess(depositStatus);
//
//        assertSame(s, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(s));
//
//        verify(terminalStatusPolicy, times(2)).test(depositStatus);
//        verify(s).setAggregatedDepositStatus(expectedAggregatedDepositStatus);
//    }

//    @Test
//    public void criFuncCriticalNoopNoDeposits() {
//        URI submissionUri = randomId();
//
//        when(s.getId()).thenReturn(submissionUri);
//        when(passClient.getIncoming(submissionUri)).thenReturn(Collections.emptyMap());
//
//        assertSame(s, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(s));
//
//        verify(s).getId();
//        verifyNoMoreInteractions(s);
//        verify(passClient).getIncoming(submissionUri);
//        verifyZeroInteractions(terminalStatusPolicy);
//    }
//
//    @Test
//    public void criFuncCriticalNoopAtLeastOneDepositIsIntermediate() {
//        prepareCriFuncCriticalNoop(randomIntermediateDepositStatus, randomTerminalDepositStatus);
//
//        assertSame(s, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(s));
//
//        verify(terminalStatusPolicy, atLeastOnce()).test(any());
//        verify(s).getId();
//        verifyNoMoreInteractions(s);
//    }
//
//    @Test
//    public void criFuncCriticalJsonTypeCoercionException() {
//        URI submissionUri = randomId();
//        URI depositUri1 = randomId();
//        Deposit deposit1 = mock(Deposit.class);
//        URI depositUri2 = randomId();
//        Map<String, Collection<URI>> incoming = new HashMap<>();
//        incoming.put(DepositProcessor.SUBMISSION_REL, Arrays.asList(depositUri1, depositUri2));
//        DepositStatus depositStatus = DepositStatus.ACCEPTED;
//        AggregatedDepositStatus expectedStatus = AggregatedDepositStatus.ACCEPTED;
//        InvalidTypeIdException invalidTypeIdException = mock(InvalidTypeIdException.class);
//        RuntimeException e = new RuntimeException(invalidTypeIdException);
//
//        when(s.getId()).thenReturn(submissionUri);
//        when(passClient.getIncoming(submissionUri)).thenReturn(incoming);
//        when(passClient.readResource(depositUri1, Deposit.class)).thenReturn(deposit1);
//        when(passClient.readResource(depositUri2, Deposit.class)).thenThrow(e);
//        when(deposit1.getDepositStatus()).thenReturn(depositStatus);
//        when(terminalStatusPolicy.test(depositStatus)).thenReturn(true);
//
//        assertSame(s, DepositProcessorCriFunc.critical(passClient, terminalStatusPolicy).apply(s));
//
//        verify(terminalStatusPolicy).test(depositStatus);
//        verify(s).setAggregatedDepositStatus(expectedStatus);
//    }
//
//    @Test
//    @SuppressWarnings("unchecked")
//    public void acceptDepositWithTerminalStatus() {
//        Deposit terminalDeposit = mock(Deposit.class);
//        DepositStatus terminalStatus = randomTerminalDepositStatus.get();
//
//        when(terminalDeposit.getDepositStatus()).thenReturn(terminalStatus);
//        when(terminalStatusPolicy.test(terminalStatus)).thenReturn(true);
//
//        underTest.accept(terminalDeposit);
//
//        verify(terminalDeposit).getDepositStatus();
//        verify(terminalStatusPolicy).test(terminalStatus);
//        verify(cri).performCritical(any(), any(), any(), any(Predicate.class), any());
//        verifyZeroInteractions(depositHelper);
//    }
//
//    @Test
//    public void acceptDepositWithIntermediateStatus() {
//        URI depositUri = randomId();
//        Deposit intermediateDeposit = mock(Deposit.class);
//        DepositStatus intermediateStatus = randomIntermediateDepositStatus.get();
//
//        when(intermediateDeposit.getId()).thenReturn(depositUri);
//        when(intermediateDeposit.getDepositStatus()).thenReturn(intermediateStatus);
//        when(terminalStatusPolicy.test(intermediateStatus)).thenReturn(false);
//
//        underTest.accept(intermediateDeposit);
//
//        verify(intermediateDeposit).getDepositStatus();
//        verify(terminalStatusPolicy).test(intermediateStatus);
//        verifyZeroInteractions(cri);
//        verify(depositHelper).processDepositStatus(depositUri);
//    }
//
//    private void prepareCriFuncCriticalSuccess(DepositStatus depositStatus) {
//        URI submissionUri = randomId();
//        URI depositUri1 = randomId();
//        Deposit deposit1 = mock(Deposit.class);
//        URI depositUri2 = randomId();
//        Deposit deposit2 = mock(Deposit.class);
//        Map<String, Collection<URI>> incoming = new HashMap<>();
//        incoming.put(DepositProcessor.SUBMISSION_REL, Arrays.asList(depositUri1, depositUri2));
//
//        when(s.getId()).thenReturn(submissionUri);
//        when(passClient.getIncoming(submissionUri)).thenReturn(incoming);
//        when(passClient.readResource(depositUri1, Deposit.class)).thenReturn(deposit1);
//        when(passClient.readResource(depositUri2, Deposit.class)).thenReturn(deposit2);
//        when(deposit1.getDepositStatus()).thenReturn(depositStatus);
//        when(deposit2.getDepositStatus()).thenReturn(depositStatus);
//        when(terminalStatusPolicy.test(depositStatus)).thenReturn(true);
//    }
//
//    private void prepareCriFuncCriticalNoop(Supplier<DepositStatus> intermediateSupplier,
//                                            Supplier<DepositStatus> terminalSupplier) {
//        URI submissionUri = randomId();
//        URI depositUri1 = randomId();
//        Deposit deposit1 = mock(Deposit.class);
//        URI depositUri2 = randomId();
//        Deposit deposit2 = mock(Deposit.class);
//        DepositStatus intermediateStatus = intermediateSupplier.get();
//        DepositStatus terminalStatus = terminalSupplier.get();
//
//        Map<String, Collection<URI>> incoming = new HashMap<>();
//        incoming.put(DepositProcessor.SUBMISSION_REL, Arrays.asList(depositUri1, depositUri2));
//
//        when(s.getId()).thenReturn(submissionUri);
//        when(passClient.getIncoming(submissionUri)).thenReturn(incoming);
//        when(passClient.readResource(depositUri1, Deposit.class)).thenReturn(deposit1);
//        when(passClient.readResource(depositUri2, Deposit.class)).thenReturn(deposit2);
//
//        when(deposit1.getDepositStatus()).thenReturn(intermediateStatus);
//        when(deposit2.getDepositStatus()).thenReturn(terminalStatus);
//        when(terminalStatusPolicy.test(intermediateStatus)).thenReturn(false);
//        when(terminalStatusPolicy.test(terminalStatus)).thenReturn(true);
//    }

}