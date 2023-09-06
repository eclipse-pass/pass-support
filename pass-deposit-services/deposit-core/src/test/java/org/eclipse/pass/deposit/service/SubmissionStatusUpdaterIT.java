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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.SubmissionStatusService;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class SubmissionStatusUpdaterIT extends AbstractSubmissionIT {

    @Autowired private SubmissionStatusUpdater submissionStatusUpdater;
    @MockBean private SubmissionStatusService statusService;

    private Submission submission;

    @BeforeEach
    void initSubmission() throws IOException {
        PassClientSelector<Submission> sel = new PassClientSelector<>(Submission.class);
        sel.setFilter(RSQL.equals("metadata", "substatusupdatetest"));
        List<Submission> submissions = passClient.streamObjects(sel).toList();
        if (submissions.isEmpty()) {
            Submission newSubmission = new Submission();
            newSubmission.setMetadata("substatusupdatetest");
            passClient.createObject(newSubmission);
            submission = newSubmission;
        } else {
            submission = submissions.get(0);
            submission.setSubmissionStatus(null);
            submission.setSubmitted(false);
            passClient.updateObject(submission);
        }
    }

    @Test
    void testDoUpdate_Success() throws IOException {
        // GIVEN
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmitted(true);
        passClient.updateObject(submission);
        when(statusService.calculateSubmissionStatus(any(Submission.class))).thenReturn(SubmissionStatus.COMPLETE);
        Mockito.clearInvocations(passClient);

        // WHEN
        submissionStatusUpdater.doUpdate();

        // THEN
        ArgumentCaptor<Submission> argument = ArgumentCaptor.forClass(Submission.class);
        verify(passClient, times(1)).updateObject(argument.capture());
        Submission updatedSubmission = argument.getValue();
        assertEquals(submission.getId(), updatedSubmission.getId());
        assertEquals(SubmissionStatus.COMPLETE, updatedSubmission.getSubmissionStatus());
        assertTrue(updatedSubmission.getSubmitted());
    }

    @Test
    void testDoUpdate_Success_NoUpdateStatusNotChanged() throws IOException {
        // GIVEN
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmitted(true);
        passClient.updateObject(submission);
        when(statusService.calculateSubmissionStatus(any(Submission.class))).thenReturn(SubmissionStatus.SUBMITTED);
        Mockito.clearInvocations(passClient);

        // WHEN
        submissionStatusUpdater.doUpdate();

        // THEN
        verify(passClient, times(0)).updateObject(any());
    }

    @Test
    void testDoUpdate_Success_NoUpdateStatusIsNull() throws IOException {
        // GIVEN
        submission.setSubmissionStatus(null);
        submission.setSubmitted(true);
        passClient.updateObject(submission);
        when(statusService.calculateSubmissionStatus(any(Submission.class))).thenReturn(SubmissionStatus.SUBMITTED);
        Mockito.clearInvocations(passClient);

        // WHEN
        submissionStatusUpdater.doUpdate();

        // THEN
        verify(passClient, times(0)).updateObject(any());
    }

    @Test
    void testDoUpdate_Success_NoUpdateNotSubmitted() throws IOException {
        // GIVEN
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmitted(false);
        passClient.updateObject(submission);
        Mockito.clearInvocations(passClient);

        // WHEN
        submissionStatusUpdater.doUpdate();

        // THEN
        verify(passClient, times(0)).updateObject(any());
    }
}
