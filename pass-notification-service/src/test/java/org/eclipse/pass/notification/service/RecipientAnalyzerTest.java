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

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.pass.support.client.model.EventType;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RecipientAnalyzerTest {

    private final static String PREPARER_1 = "preparer_one@example.org";
    private final static String PREPARER_2 = "preparer_two@example.org";
    private final static String SUBMITTER = "submitter@example.org";

    private Collection<String> preparers;
    private Submission submission;
    private SubmissionEvent event;
    private RecipientAnalyzer recipientAnalyzer;

    @BeforeEach
    public void setUp() throws Exception {
        submission = mock(Submission.class);
        event = mock(SubmissionEvent.class);
        recipientAnalyzer = new RecipientAnalyzer();
        preparers = Arrays.asList(PREPARER_1, PREPARER_2);
        User submitterUser = new User("test-submitter");
        submitterUser.setEmail(SUBMITTER);
        when(submission.getSubmitter()).thenReturn(submitterUser);
        when(submission.getPreparers()).thenReturn(preparers.stream().map(email -> {
            User preparer = new User("test-" + email);
            preparer.setEmail(email);
            return preparer;
        }).collect(toList()));
    }

    @Test
    public void analyzeApprovalRequested() {
        perform(singleton(SUBMITTER), EventType.APPROVAL_REQUESTED);
    }

    @Test
    public void analyzeApprovalRequestedNewUser() {
        perform(singleton(SUBMITTER), EventType.APPROVAL_REQUESTED_NEWUSER);
    }

    @Test
    public void analyzeChangesRequested() {
        perform(preparers, EventType.CHANGES_REQUESTED);
    }

    @Test
    public void analyzeCancelledBySubmitter() {
        User submitterUser = new User("test-submitter");
        submitterUser.setEmail(SUBMITTER);
        when(event.getPerformedBy()).thenReturn(submitterUser);
        perform(preparers, EventType.CANCELLED);
    }

    @Test
    public void analyzeCancelledByPreparer() {
        User preparerUser = new User("test-preparer-1");
        preparerUser.setEmail(PREPARER_1);
        when(event.getPerformedBy()).thenReturn(preparerUser);
        // fixme: other preparers should get a notification to
        perform(singleton(SUBMITTER), EventType.CANCELLED);
    }

    @Test
    public void analyzeSubmitted() {
        perform(preparers, EventType.SUBMITTED);
    }

    /**
     * Insure a null submitter and null submitter email is reported as a thrown runtime exception
     * (model version 3.2 allows the submitter to be null)
     */
    @Test
    public void testNullSubmitterAndNullEmail() {
        when(submission.getId()).thenReturn("1");
        when(submission.getSubmitter()).thenReturn(null);
        when(submission.getSubmitterEmail()).thenReturn(null);

        assertThrows(RuntimeException.class, () -> {
            perform(Collections.emptyList(), EventType.APPROVAL_REQUESTED_NEWUSER);
        });

        verify(submission).getSubmitter();
        verify(submission).getSubmitterEmail();
    }

    /**
     * Insure a null submitter results in the submitter email address being used
     * (model version 3.2 allows the submitter to be null)
     */
    @Test
    public void testNullSubmitter() {
        when(submission.getId()).thenReturn("1");
        when(submission.getSubmitter()).thenReturn(null);
        URI expectedRecipient = URI.create("mailto:ex@ample.org");
        when(submission.getSubmitterEmail()).thenReturn(expectedRecipient);

        perform(singleton(expectedRecipient.getSchemeSpecificPart()), EventType.APPROVAL_REQUESTED_NEWUSER);

        verify(submission).getSubmitter();
        verify(submission).getSubmitterEmail();
    }

    /**
     * insure a null submitter email results in the submitter user uri being used
     */
    @Test
    public void testNullSubmitterEmail() {
        when(submission.getId()).thenReturn("1");
        User expectedRecipient = new User("2");
        expectedRecipient.setEmail("ex@ample.org");
        when(submission.getSubmitter()).thenReturn(expectedRecipient);

        perform(singleton(expectedRecipient.getEmail()), EventType.APPROVAL_REQUESTED_NEWUSER);

        verify(submission).getSubmitter();
        verify(submission, times(0)).getSubmitterEmail();
    }

    /**
     * insure the User URI has precedence over the submission submitter email
     */
    @Test
    public void testNonNullSubmitterUserUriAndNonNullSubmitterEmail() {
        when(submission.getId()).thenReturn("1");
        User expectedRecipient = new User("2");
        expectedRecipient.setEmail("ex@ample.org");
        when(submission.getSubmitter()).thenReturn(expectedRecipient);
        when(submission.getSubmitterEmail()).thenReturn(URI.create("mailto:ex@ample.org"));

        perform(singleton(expectedRecipient.getEmail()), EventType.APPROVAL_REQUESTED_NEWUSER);

        verify(submission).getSubmitter();
        verify(submission, times(0)).getSubmitterEmail();
    }

    private void perform(Collection<String> expectedRecipients, EventType type) {
        when(event.getEventType()).thenReturn(type);

        Collection<String> actualRecipients = recipientAnalyzer.apply(submission, event);

        actualRecipients.forEach(actualRecipient -> assertTrue(expectedRecipients.contains(actualRecipient)));
        expectedRecipients.forEach(expectedRecipient -> assertTrue(actualRecipients.contains(expectedRecipient)));

        verify(event, atLeastOnce()).getEventType();
    }
}