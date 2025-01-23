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

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.pass.deposit.DepositApp;
import org.eclipse.pass.deposit.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.builder.DepositSubmissionModelBuilder;
import org.eclipse.pass.deposit.model.DepositFile;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.model.Packager;
import org.eclipse.pass.deposit.model.Registry;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource("/test-application.properties")
class FailedDepositRetryTest {

    @Autowired
    private DepositServiceErrorHandler depositServiceErrorHandler;

    @SuppressWarnings("unchecked")
    @Test
    void testRetryFailedDeposits_Success_WithMultipleFiles() throws IOException {
        // GIVEN
        final PassClient passClient = mock(PassClient.class);
        final DepositTaskHelper depositTaskHelper = mock(DepositTaskHelper.class);
        final Registry<Packager> packagerRegistry = mock(Registry.class);
        final Packager packager = mock(Packager.class);
        final DepositSubmissionModelBuilder depositSubmissionModelBuilder = mock(DepositSubmissionModelBuilder.class);
        final FailedDepositRetry failedDepositRetry = new FailedDepositRetry(passClient, depositTaskHelper,
            packagerRegistry, depositSubmissionModelBuilder, depositServiceErrorHandler);
        Deposit deposit1 = new Deposit();
        deposit1.setId("dp-1");
        deposit1.setDepositStatus(DepositStatus.RETRY);
        Repository repository = new Repository();
        deposit1.setRepository(repository);
        Submission submission = new Submission();
        submission.setId("sub-1");
        deposit1.setSubmission(submission);
        when(passClient.getObject(same(deposit1), any(String[].class))).thenReturn(deposit1);
        when(passClient.getObject(same(submission), any(String[].class))).thenReturn(submission);
        when(packagerRegistry.get(any())).thenReturn(packager);
        DepositSubmission depositSubmission = new DepositSubmission();
        depositSubmission.setId(deposit1.getId());
        DepositFile depositFile1 = new DepositFile();
        depositFile1.setName("file1");
        depositFile1.setLocation("filelocation1");
        DepositFile depositFile2 = new DepositFile();
        depositFile2.setName("file2");
        depositFile2.setLocation("filelocation2");
        depositSubmission.setFiles(List.of(depositFile1, depositFile2));
        when(depositSubmissionModelBuilder.build(any())).thenReturn(depositSubmission);

        // WHEN
        failedDepositRetry.retryFailedDeposit(deposit1);

        // THEN
        verify(depositTaskHelper).submitDeposit(same(submission), same(depositSubmission), same(repository),
            same(deposit1), same(packager));

        ArgumentCaptor<String[]> argument = ArgumentCaptor.forClass(String[].class);
        verify(passClient).getObject(same(deposit1), argument.capture());
        assertIterableEquals(List.of("submission", "repository"), Arrays.asList(argument.getAllValues().get(0)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRetryFailedDeposits_Fail_NoPackagerFound() throws IOException {
        // GIVEN
        final PassClient passClient = mock(PassClient.class);
        final DepositTaskHelper depositTaskHelper = mock(DepositTaskHelper.class);
        final Registry<Packager> packagerRegistry = mock(Registry.class);
        final DepositSubmissionModelBuilder depositSubmissionModelBuilder = mock(DepositSubmissionModelBuilder.class);
        final FailedDepositRetry failedDepositRetry = new FailedDepositRetry(passClient, depositTaskHelper,
            packagerRegistry, depositSubmissionModelBuilder, depositServiceErrorHandler);
        Deposit deposit1 = new Deposit();
        deposit1.setId("dp-1");
        deposit1.setDepositStatus(DepositStatus.RETRY);
        deposit1.setRepository(new Repository());
        Submission submission = new Submission();
        submission.setId("sub-1");
        deposit1.setSubmission(submission);
        when(passClient.getObject(same(deposit1), any(String[].class))).thenReturn(deposit1);
        when(passClient.getObject(same(submission), any(String[].class))).thenReturn(submission);
        when(packagerRegistry.get(any())).thenReturn(null);

        // WHEN
        failedDepositRetry.retryFailedDeposit(deposit1);

        // THEN
        verifyNoInteractions(depositTaskHelper);

        ArgumentCaptor<String[]> argument = ArgumentCaptor.forClass(String[].class);
        verify(passClient).getObject(same(deposit1), argument.capture());
        assertIterableEquals(List.of("submission", "repository"), Arrays.asList(argument.getAllValues().get(0)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRetryFailedDeposits_Fail_ErrorDepositSubmission() throws IOException {
        // GIVEN
        final PassClient passClient = mock(PassClient.class);
        final DepositTaskHelper depositTaskHelper = mock(DepositTaskHelper.class);
        final Registry<Packager> packagerRegistry = mock(Registry.class);
        final Packager packager = mock(Packager.class);
        final DepositSubmissionModelBuilder depositSubmissionModelBuilder = mock(DepositSubmissionModelBuilder.class);
        final FailedDepositRetry failedDepositRetry = new FailedDepositRetry(passClient, depositTaskHelper,
            packagerRegistry, depositSubmissionModelBuilder, depositServiceErrorHandler);
        Deposit deposit1 = new Deposit();
        deposit1.setId("dp-1");
        deposit1.setDepositStatus(DepositStatus.RETRY);
        deposit1.setRepository(new Repository());
        Submission submission = new Submission();
        submission.setId("sub-1");
        deposit1.setSubmission(submission);
        when(passClient.getObject(same(deposit1), any(String[].class))).thenReturn(deposit1);
        when(passClient.getObject(same(submission), any(String[].class))).thenReturn(submission);
        when(packagerRegistry.get(any())).thenReturn(packager);
        doThrow(new IOException("Testing depositsubmission error"))
            .when(depositSubmissionModelBuilder).build(any());

        // WHEN
        failedDepositRetry.retryFailedDeposit(deposit1);

        // THEN
        verifyNoInteractions(depositTaskHelper);

        ArgumentCaptor<String[]> argument = ArgumentCaptor.forClass(String[].class);
        verify(passClient).getObject(same(deposit1), argument.capture());
        assertIterableEquals(List.of("submission", "repository"), Arrays.asList(argument.getAllValues().get(0)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRetryFailedDeposits_Fail_InvalidFilesNoFiles() throws IOException {
        // GIVEN
        final PassClient passClient = mock(PassClient.class);
        final DepositTaskHelper depositTaskHelper = mock(DepositTaskHelper.class);
        final Registry<Packager> packagerRegistry = mock(Registry.class);
        final Packager packager = mock(Packager.class);
        final DepositSubmissionModelBuilder depositSubmissionModelBuilder = mock(DepositSubmissionModelBuilder.class);
        final FailedDepositRetry failedDepositRetry = new FailedDepositRetry(passClient, depositTaskHelper,
            packagerRegistry, depositSubmissionModelBuilder, depositServiceErrorHandler);
        Deposit deposit1 = new Deposit();
        deposit1.setId("dp-1");
        deposit1.setDepositStatus(DepositStatus.RETRY);
        deposit1.setRepository(new Repository());
        Submission submission = new Submission();
        submission.setId("sub-1");
        deposit1.setSubmission(submission);
        when(passClient.getObject(same(deposit1), any(String[].class))).thenReturn(deposit1);
        when(passClient.getObject(same(submission), any(String[].class))).thenReturn(submission);
        when(packagerRegistry.get(any())).thenReturn(packager);
        DepositSubmission depositSubmission = new DepositSubmission();
        depositSubmission.setId(deposit1.getId());
        when(depositSubmissionModelBuilder.build(any())).thenReturn(depositSubmission);

        // WHEN
        failedDepositRetry.retryFailedDeposit(deposit1);

        // THEN
        verifyNoInteractions(depositTaskHelper);

        ArgumentCaptor<String[]> argument = ArgumentCaptor.forClass(String[].class);
        verify(passClient).getObject(same(deposit1), argument.capture());
        assertIterableEquals(List.of("submission", "repository"), Arrays.asList(argument.getAllValues().get(0)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRetryFailedDeposits_Fail_InvalidFilesFilesNoLocation() throws IOException {
        // GIVEN
        final PassClient passClient = mock(PassClient.class);
        final DepositTaskHelper depositTaskHelper = mock(DepositTaskHelper.class);
        final Registry<Packager> packagerRegistry = mock(Registry.class);
        final Packager packager = mock(Packager.class);
        final DepositSubmissionModelBuilder depositSubmissionModelBuilder = mock(DepositSubmissionModelBuilder.class);
        final FailedDepositRetry failedDepositRetry = new FailedDepositRetry(passClient, depositTaskHelper,
            packagerRegistry, depositSubmissionModelBuilder, depositServiceErrorHandler);
        Deposit deposit1 = new Deposit();
        deposit1.setId("dp-1");
        deposit1.setDepositStatus(DepositStatus.RETRY);
        deposit1.setRepository(new Repository());
        Submission submission = new Submission();
        submission.setId("sub-1");
        deposit1.setSubmission(submission);
        when(passClient.getObject(same(deposit1), any(String[].class))).thenReturn(deposit1);
        when(passClient.getObject(same(submission), any(String[].class))).thenReturn(submission);
        when(packagerRegistry.get(any())).thenReturn(packager);
        DepositSubmission depositSubmission = new DepositSubmission();
        depositSubmission.setId(deposit1.getId());
        DepositFile depositFile1 = new DepositFile();
        depositFile1.setName("file1");
        DepositFile depositFile2 = new DepositFile();
        depositFile2.setName("file2");
        depositSubmission.setFiles(List.of(depositFile1, depositFile2));
        when(depositSubmissionModelBuilder.build(any())).thenReturn(depositSubmission);

        // WHEN
        failedDepositRetry.retryFailedDeposit(deposit1);

        // THEN
        verifyNoInteractions(depositTaskHelper);

        ArgumentCaptor<String[]> argument = ArgumentCaptor.forClass(String[].class);
        verify(passClient).getObject(same(deposit1), argument.capture());
        assertIterableEquals(List.of("submission", "repository"), Arrays.asList(argument.getAllValues().get(0)));
    }

}
