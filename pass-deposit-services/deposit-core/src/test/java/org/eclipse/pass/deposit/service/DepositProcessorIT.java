/*
 * Copyright 2024 Johns Hopkins University
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;

import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.status.DefaultDepositStatusProcessor;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@TestPropertySource(properties = {
    "pass.deposit.repository.configuration=classpath:/full-test-repositories.json"
})
public class DepositProcessorIT extends AbstractDepositIT {

    @Autowired private DepositProcessor depositProcessor;
    @SpyBean private DefaultDepositStatusProcessor defaultDepositStatusProcessor;

    @Test
    public void testDepositProcessor_WithDepositProcessor() throws Exception {
        // GIVEN
        Deposit j10pDeposit = initJScholarshipDeposit();
        mockSword();

        // WHEN
        depositProcessor.accept(j10pDeposit);

        // THEN
        Deposit actualDeposit = passClient.getObject(j10pDeposit);
        assertEquals(DepositStatus.ACCEPTED, actualDeposit.getDepositStatus());
        assertEquals("test-j10p-ref", actualDeposit.getDepositStatusRef());
        verify(passClient).updateObject(eq(actualDeposit));
    }

    @Test
    public void testDepositProcessor_WithDepositProcessorThrowsException() throws Exception {
        // GIVEN
        Deposit j10pDeposit = initJScholarshipDeposit();
        doThrow(new RuntimeException("Testing deposit status error"))
            .when(defaultDepositStatusProcessor).process(any(Deposit.class), any());

        // WHEN
        DepositServiceRuntimeException exception =
            assertThrows(DepositServiceRuntimeException.class, () -> depositProcessor.accept(j10pDeposit));

        // THEN
        assertEquals("Failed to update deposit status for [" + j10pDeposit.getId() +
            "], parsing the status document referenced by test-j10p-ref failed: Testing deposit status error",
            exception.getMessage());
        Deposit actualDeposit = passClient.getObject(j10pDeposit);
        assertEquals(DepositStatus.SUBMITTED, actualDeposit.getDepositStatus());
        assertEquals("test-j10p-ref", actualDeposit.getDepositStatusRef());
        verify(passClient, times(0)).updateObject(eq(actualDeposit));
    }

    @Test
    public void testDepositProcessor_NoUpdateOnDepositNoDepositProcessor() throws Exception {
        // GIVEN
        Deposit pmcDeposit = initPmcDeposit();

        // WHEN
        depositProcessor.accept(pmcDeposit);

        // THEN
        Deposit actualDeposit = passClient.getObject(pmcDeposit);
        assertEquals(DepositStatus.SUBMITTED, actualDeposit.getDepositStatus());
        assertEquals("test-pmc-package", actualDeposit.getDepositStatusRef());
        verify(passClient, times(0)).updateObject(eq(actualDeposit));
    }

    private Deposit initPmcDeposit() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample1")));
        submission.setSubmittedDate(ZonedDateTime.now());
        passClient.updateObject(submission);
        Repository pmcRepo = submission.getRepositories().stream()
            .filter(repository -> repository.getRepositoryKey().equals("PubMed Central"))
            .findFirst().get();
        Deposit pmcDeposit = new Deposit();
        pmcDeposit.setSubmission(submission);
        pmcDeposit.setRepository(pmcRepo);
        pmcDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        pmcDeposit.setDepositStatusRef("test-pmc-package");
        passClient.createObject(pmcDeposit);
        return pmcDeposit;
    }

    private Deposit initJScholarshipDeposit() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample1")));
        submission.setSubmittedDate(ZonedDateTime.now());
        passClient.updateObject(submission);
        Repository j10pRepo = submission.getRepositories().stream()
            .filter(repository -> repository.getRepositoryKey().equals("JScholarship"))
            .findFirst().get();
        RepositoryCopy repositoryCopy = new RepositoryCopy();
        repositoryCopy.setRepository(j10pRepo);
        repositoryCopy.setCopyStatus(CopyStatus.IN_PROGRESS);
        passClient.createObject(repositoryCopy);
        Deposit j10pDeposit = new Deposit();
        j10pDeposit.setSubmission(submission);
        j10pDeposit.setRepository(j10pRepo);
        j10pDeposit.setRepositoryCopy(repositoryCopy);
        j10pDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        j10pDeposit.setDepositStatusRef("test-j10p-ref");
        passClient.createObject(j10pDeposit);
        return j10pDeposit;
    }

}
