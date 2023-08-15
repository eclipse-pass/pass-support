/*
 *
 *  * Copyright 2018 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package org.eclipse.pass.notification.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import lombok.SneakyThrows;
import org.eclipse.pass.notification.dispatch.DispatchService;
import org.eclipse.pass.notification.model.Notification;
import org.eclipse.pass.notification.model.SubmissionEventMessage;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NotificationServiceTest {

    private PassClient passClient;
    private DispatchService dispatchService;
    private Composer composer;
    private NotificationService notificationService;

    @BeforeEach
    public void setUp() throws Exception {
        passClient = mock(PassClient.class);
        dispatchService = mock(DispatchService.class);
        composer = mock(Composer.class);

        notificationService = new NotificationService(passClient, dispatchService, composer);
    }

    @Test
    public void testSuccess() throws IOException {
        // GIVEN
        SubmissionPreparer sp = new SubmissionPreparer(passClient);
        Notification n = mock(Notification.class);
        when(composer.apply(sp.event, sp.eventMessage)).thenReturn(n);

        // The preparers and the submitter must differ (i.e. must *not* be a self-submission) for the submissionevent to
        // be processed by defaultnotificationservice
        User submitter = new User("test-user-id");
        submitter.setEmail("test-user@test");
        when(sp.submission.getSubmitter()).thenReturn(submitter);
        User preparer = new User("test-user-id");
        preparer.setEmail("test-user-prep@test");
        when(sp.submission.getPreparers()).thenReturn(List.of(preparer));

        // WHEN
        notificationService.notify(sp.eventMessage);

        // THEN
        verify(passClient, times(1))
            .getObject(SubmissionEvent.class, "test-event-id", "submission", "performedBy");
        verify(composer).apply(sp.event, sp.eventMessage);
        verify(dispatchService).dispatch(n);
    }

    /**
     * A self-submission is where the authorized submitter prepares and submits their own submission (i.e.
     * self-submission).  Notification services should not respond to self-submission SubmissionEvents
     */
    @Test
    public void testSelfSubmissionPreparerIsNull() throws IOException {
        // GIVEN
        // mock a self submission where Submission.preparer is null
        SubmissionPreparer sp = new SubmissionPreparer(passClient);
        when(sp.submission.getPreparers()).thenReturn(null);

        // WHEN
        notificationService.notify(sp.eventMessage);

        // THEN
        verify(passClient, times(1))
            .getObject(SubmissionEvent.class, "test-event-id", "submission", "performedBy");
        verifyNoInteractions(composer);
        verifyNoInteractions(dispatchService);
    }

    /**
     * A self-submission is where the authorized submitter prepares and submits their own submission (i.e.
     * self-submission).  Notification services should not respond to self-submission SubmissionEvents
     */
    @Test
    public void testSelfSubmissionPreparerIsEmpty() throws IOException {
        // GIVEN
        // mock a self submission where Submission.preparer is empty
        SubmissionPreparer sp = new SubmissionPreparer(passClient);
        when(sp.submission.getPreparers()).thenReturn(Collections.emptyList());

        // WHEN
        notificationService.notify(sp.eventMessage);

        // THEN
        verify(passClient, times(1))
            .getObject(SubmissionEvent.class, "test-event-id", "submission", "performedBy");
        verifyNoInteractions(composer);
        verifyNoInteractions(dispatchService);
    }

    /**
     * A self-submission is where the authorized submitter prepares and submits their own submission (i.e.
     * self-submission).  Notification services should not respond to self-submission SubmissionEvents
     */
    @Test
    public void testSelfSubmissionPreparerIsSubmitter() throws IOException {
        // GIVEN
        // mock a self submission where Submission.preparer contains exactly one URI, the URI of the submitter
        SubmissionPreparer sp = new SubmissionPreparer(passClient);
        User submitter = new User("test-user-id");
        submitter.setEmail("test-user@test");
        when(sp.submission.getPreparers()).thenReturn(List.of(submitter));
        when(sp.submission.getSubmitter()).thenReturn(submitter);

        // WHEN
        notificationService.notify(sp.eventMessage);

        // THEN
        verify(passClient, times(1))
            .getObject(SubmissionEvent.class, "test-event-id", "submission", "performedBy");
        verifyNoInteractions(composer);
        verifyNoInteractions(dispatchService);
    }

    /**
     * A self-submission is where the authorized submitter prepares and submits their own submission (i.e.
     * self-submission).  Notification services should not respond to self-submission SubmissionEvents.
     *
     * In this case there are multiple preparers, and the submitter is one of them.  in this case, process the
     * submissionevent.
     */
    @Test
    public void testSelfSubmissionPreparerContainsSubmitter() throws IOException {
        // GIVEN
        // mock a self submission where Submission.preparer contains multiple URIs, one of them is the URI of the
        // submitter
        SubmissionPreparer sp = new SubmissionPreparer(passClient);
        User submitter = new User("test-user-id-1");
        submitter.setEmail("test-user@test");
        User anotherPreparer = new User("test-user-id-2");
        anotherPreparer.setEmail("test-user-prep@test");
        when(sp.submission.getPreparers()).thenReturn(List.of(submitter, anotherPreparer));
        when(sp.submission.getSubmitter()).thenReturn(submitter);

        Notification n = mock(Notification.class);
        when(composer.apply(sp.event, sp.eventMessage)).thenReturn(n);

        // WHEN
        notificationService.notify(sp.eventMessage);

        // THEN
        verify(passClient, times(1))
            .getObject(SubmissionEvent.class, "test-event-id", "submission", "performedBy");
        verify(composer).apply(sp.event, sp.eventMessage);
        verify(dispatchService).dispatch(n);
    }

    private static class SubmissionPreparer {
        private final SubmissionEventMessage eventMessage;
        private final SubmissionEvent event;
        private final Submission submission;

        @SneakyThrows
        private SubmissionPreparer(PassClient passClient) {
            String eventId = "test-event-id";
            String submissionId = "test-submission-id";

            eventMessage = new SubmissionEventMessage();
            eventMessage.setSubmissionEventId(eventId)
            ;
            event = mock(SubmissionEvent.class);
            when(event.getId()).thenReturn(eventId);

            submission = mock(Submission.class);
            when(submission.getId()).thenReturn(submissionId);
            when(event.getSubmission()).thenReturn(submission);

            when(passClient.getObject(SubmissionEvent.class, "test-event-id", "submission", "performedBy"))
                .thenReturn(event);
        }
    }
}