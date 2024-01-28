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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.ZonedDateTime;

import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@TestPropertySource(properties = {
    "pass.deposit.repository.configuration=classpath:/full-test-repositories.json"
})
public class DepositUpdaterIT extends AbstractDepositIT {

    @Autowired private DepositUpdater depositUpdater;
    @MockBean private DepositTaskHelper depositTaskHelper;

    @Test
    public void testDoUpdate_SkipNoDepositStatusProcessorRepos() throws Exception {
        // GIVEN
        Submission submission = initSubmissionAndDeposits();
        mockSword();

        // WHEN
        try {
            depositUpdater.doUpdate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // THEN
        PassClientSelector<Deposit> depositSelector = new PassClientSelector<>(Deposit.class);
        depositSelector.setFilter(RSQL.equals("submission.id", submission.getId()));
        depositSelector.setInclude("repository");
        PassClientResult<Deposit> actualDeposits = passClient.selectObjects(depositSelector);
        Deposit pmcDeposit = actualDeposits.getObjects().stream()
            .filter(deposit -> deposit.getRepository().getRepositoryKey().equals("PubMed Central"))
            .findFirst().get();
        Deposit j10pDeposit = actualDeposits.getObjects().stream()
            .filter(deposit -> deposit.getRepository().getRepositoryKey().equals("JScholarship"))
            .findFirst().get();
        verify(depositTaskHelper, times(0)).processDepositStatus(eq(pmcDeposit.getId()));
        verify(depositTaskHelper, times(1)).processDepositStatus(eq(j10pDeposit.getId()));
    }

    private Submission initSubmissionAndDeposits() throws Exception {
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
        Repository j10pRepo = submission.getRepositories().stream()
            .filter(repository -> repository.getRepositoryKey().equals("JScholarship"))
            .findFirst().get();
        Deposit j10pDeposit = new Deposit();
        j10pDeposit.setSubmission(submission);
        j10pDeposit.setRepository(j10pRepo);
        j10pDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        j10pDeposit.setDepositStatusRef("test-j10p-ref");
        passClient.createObject(j10pDeposit);
        return submission;
    }

}
