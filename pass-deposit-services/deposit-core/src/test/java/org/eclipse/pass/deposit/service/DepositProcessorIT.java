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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;

import org.eclipse.pass.deposit.AbstractDepositSubmissionIT;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class DepositProcessorIT extends AbstractDepositSubmissionIT {

    @Autowired private DepositProcessor depositProcessor;

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

}
