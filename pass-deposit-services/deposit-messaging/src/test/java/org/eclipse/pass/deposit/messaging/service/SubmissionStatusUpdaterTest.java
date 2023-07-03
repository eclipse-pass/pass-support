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

import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomSubmissionStatus;
import static org.eclipse.pass.deposit.messaging.DepositMessagingTestUtil.randomSubmissionStatusExcept;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.deposit.messaging.service.SubmissionStatusUpdater.CriFunc;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.SubmissionStatusService;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionStatusUpdaterTest {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionStatusUpdater.class);

    private SubmissionStatusUpdater submissionStatusUpdater;

    private SubmissionStatusService statusService;

    private PassClient passClient;

    private CriticalRepositoryInteraction cri;

    @BeforeEach
    public void setUp() throws Exception {
        statusService = mock(SubmissionStatusService.class);
        passClient = mock(PassClient.class);
        cri = mock(CriticalRepositoryInteraction.class);

        submissionStatusUpdater = new SubmissionStatusUpdater(statusService, passClient, cri);
    }

    /**
     * A submitted flag of TRUE and a status of anything other than CANCELLED or COMPLETE should meet the precondition
     */
    @Test
    public void criPreconditionSuccess() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(
            randomSubmissionStatusExcept(SubmissionStatus.COMPLETE, SubmissionStatus.CANCELLED));
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertTrue(CriFunc.preCondition.test(s));
    }

    /**
     * A null status should fail the precondition
     */
    @Test
    public void criPreconditionFailsNullStatus() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(null);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * A null submitted flag should fail the precondition
     */
    @Test
    public void criPreconditionFailsNullSubmit() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(SubmissionStatus.SUBMITTED);
        when(s.getSubmitted()).thenReturn(null);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * A submitted flag of FALSE will fail the precondition
     */
    @Test
    public void criPreconditionFailsSubmitted() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(SubmissionStatus.SUBMITTED);
        when(s.getSubmitted()).thenReturn(Boolean.FALSE);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * A status of COMPLETE will fail the precondition
     */
    @Test
    public void criPreconditionFailsStatusComplete() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(SubmissionStatus.COMPLETE);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * A status of CANCELLED will fail the precondition
     */
    @Test
    public void criPreconditionFailsStatusCancelled() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(SubmissionStatus.CANCELLED);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * Every status except CANCELLED and COMPLETE should meet the precondition
     */
    @Test
    public void criPreconditionSuccessAllStatus() {
        Submission s = mock(Submission.class);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        Stream.of(SubmissionStatus.values())
              .filter(status -> status != SubmissionStatus.CANCELLED && status != SubmissionStatus.COMPLETE)
              .peek(status -> when(s.getSubmissionStatus()).thenReturn(status))
              .peek(status -> LOG.trace("Testing status {}", status))
              .forEach(status -> assertTrue(CriFunc.preCondition.test(s)));
    }

    /**
     * Any non-null status and a submission flag set to true should result in a successful post-condition
     */
    @Test
    public void criPostconditionSuccess() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(randomSubmissionStatus());
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertTrue(CriFunc.postCondition.test(s));
    }

    /**
     * A null or false submission flag should fail the post condition
     */
    @Test
    public void criPostconditionFailSubmissionFlag() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(randomSubmissionStatus());
        when(s.getSubmitted()).thenReturn(Boolean.FALSE);

        assertFalse(CriFunc.postCondition.test(s));
    }

    /**
     * A null or false submission flag should fail the post condition
     */
    @Test
    public void criPostconditionFailNullSubmissionFlag() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(randomSubmissionStatus());
        when(s.getSubmitted()).thenReturn(null);

        assertFalse(CriFunc.postCondition.test(s));
    }

    /**
     * A null status should fail the post condition
     */
    @Test
    public void criPostconditionFailNullStatus() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(null);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertFalse(CriFunc.postCondition.test(s));
    }

    /**
     * The critical function ought to invoke the status service and modify the status
     */
    @Test
    public void criCriticalInvokesSubmissionStatus() {
        Submission s = mock(Submission.class);
        when(statusService.calculateSubmissionStatus(s)).thenReturn(randomSubmissionStatus());
        assertNotNull(CriFunc.critical(statusService).apply(s));
        verify(statusService).calculateSubmissionStatus(s);
    }

    /**
     * the toUpdate method should not try to find Submissions with a status of COMPLETE or CANCELLED.
     * the toUpdate method should try to find Submissions with all other statuses.
     * @throws IOException
     */
    @Test
    public void toUpdateCollectsAllButCompleteAndCancelled() throws IOException {
        when(passClient.streamObjects(any()))
            .then(inv -> {
                PassClientSelector<?> sel = inv.getArgument(0);

                assertFalse(sel.getFilter().contains(SubmissionStatus.COMPLETE.getValue()));
                assertFalse(sel.getFilter().contains(SubmissionStatus.CANCELLED.getValue()));

                return Stream.empty();
            });

        SubmissionStatusUpdater.toUpdate(passClient);

        verify(passClient, times(1)).streamObjects(any());
    }

    /**
     * invoking doUpdate(Collection) with a non-empty collection should invoke the CRI for every URI.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void doUpdateInvokesCri() {
        String submissionId = UUID.randomUUID().toString();
        submissionStatusUpdater.doUpdate(Collections.singleton(submissionId));

        verify(cri, times(1)).performCritical(eq(submissionId), eq(Submission.class), any(),
            any(Predicate.class), any());
    }

    /**
     * invoking doUpdate() should invoke the pass client to find all submissions that are not CANCELLED or COMPLETE,
     * and then invoke the CRI for every discovered URI
     * @throws IOException
     */
    @Test
    @SuppressWarnings("unchecked")
    public void doUpdateInvokesPassClientAndCri() throws IOException {
        String submissionId = UUID.randomUUID().toString();

        when(passClient.streamObjects(any())).thenReturn(Stream.of(new Submission(submissionId)));

        submissionStatusUpdater.doUpdate();

        verify(passClient, times(1)).streamObjects(any());
        verify(cri, times(1)).performCritical(eq(submissionId), eq(Submission.class), any(),
            any(Predicate.class), any());
    }

}