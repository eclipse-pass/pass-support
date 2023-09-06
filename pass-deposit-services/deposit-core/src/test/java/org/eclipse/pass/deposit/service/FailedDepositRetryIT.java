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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Set;

import org.eclipse.deposit.util.async.Condition;
import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.swordapp.client.SWORDCollection;
import org.swordapp.client.SWORDError;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class FailedDepositRetryIT extends AbstractDepositIT {

    @Autowired private DepositUpdater depositUpdater;

    @Test
    public void testFailedDepositRetry() throws Exception {
        // GIVEN
        Submission submission = initFailedSubmissionDeposit();
        mockSword();

        // WHEN
        try {
            depositUpdater.doUpdate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // THEN
        Condition<Set<Deposit>> actualDeposits = depositsForSubmission(submission.getId(), 1,
            (deposit, repo) -> true);
        assertTrue(actualDeposits.awaitAndVerify(deposits -> deposits.size() == 1 &&
            DepositStatus.SUBMITTED == deposits.iterator().next().getDepositStatus()));
    }

    private Submission initFailedSubmissionDeposit() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        submission.setSubmittedDate(ZonedDateTime.now());
        passClient.updateObject(submission);
        mockSword();
        doThrow(new SWORDError(400, "Testing deposit error"))
            .when(mockSwordClient).deposit(any(SWORDCollection.class), any(), any());
        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());
        assertThrows(DepositServiceRuntimeException.class, () -> submissionProcessor.accept(actualSubmission));
        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
            DepositStatus.FAILED == deposits.iterator().next()
                .getDepositStatus()));
        return actualSubmission;
    }

}
