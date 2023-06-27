/*
 * Copyright 2018 Johns Hopkins University
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.eclipse.deposit.util.async.Condition;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionProcessorIT extends AbstractSubmissionIT {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionProcessorIT.class);

    @Autowired private SubmissionStatusUpdater submissionStatusUpdater;
    @Autowired private DepositProcessor depositProcessor;

    @Test
    public void testSubmissionProcessingFull() throws Exception {
        // GIVEN
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample1-unsubmitted")));
        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());

        // WHEN
        submissionProcessor.accept(actualSubmission);

        // After the SubmissionProcessor successfully processing a submission we should observe:
        // 1. Deposit resources created for each Repository associated with the Submission
        // 2. The Deposit resources should be in a ACCEPTED state
        // These statuses are dependant on the transport being used - because the TransportResponse.onSuccess(...)
        // method may modify the repository resources associated with the Submission.  Because the FilesystemTransport
        // is used, the Deposit resources will be in the ACCEPTED state, and RepositoryCopy resources in the ACCEPTED
        // state.
        // 3. The Submission's AggregateDepositStatus should be set to ACCEPTED
        // 4. The Submission's SubmissionStatus should be changed to COMPLETE

        // THEN
        // TODO replace with awaitility
        Condition<Set<Deposit>> deposits = depositsForSubmission(
            actualSubmission.getId(),
            actualSubmission.getRepositories().size(),
                (deposit, repo) -> {
                    LOG.debug("Polling Submission {} for deposit-related resources", actualSubmission.getId());
                    LOG.debug("  Deposit: {} {}", deposit.getDepositStatus(), deposit.getId());
                    LOG.debug("  Repository: {} {}", repo.getName(), repo.getId());

                    // Transport-dependent part: FilesystemTransport
                    // .onSuccess(...) sets the correct statuses

                    if (deposit.getRepositoryCopy() == null) {
                        return false;
                    }

                    RepositoryCopy repoCopy = null;
                    try {
                        repoCopy = passClient.getObject(RepositoryCopy.class,
                            deposit.getRepositoryCopy().getId());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    LOG.debug("  RepositoryCopy: {} {} {} {}", repoCopy.getCopyStatus(), repoCopy.getAccessUrl(),
                        String.join(",", repoCopy.getExternalIds()), repoCopy.getId());

                    return DepositStatus.ACCEPTED == deposit.getDepositStatus() &&
                        CopyStatus.COMPLETE == repoCopy.getCopyStatus() &&
                        repoCopy.getAccessUrl() != null &&
                        repoCopy.getExternalIds().size() > 0;
                });

        deposits.await();

        Set<Deposit> resultDeposits = deposits.getResult();
        assertEquals(actualSubmission.getRepositories().size(), resultDeposits.size());
        long actualSubmissionDepositCount = resultDeposits.stream()
            .filter(deposit -> deposit.getSubmission().getId().equals(actualSubmission.getId()))
            .count();
        assertEquals(actualSubmissionDepositCount, submission.getRepositories().size());
        assertTrue(resultDeposits.stream().allMatch(deposit -> deposit.getDepositStatus() == DepositStatus.ACCEPTED));

        // WHEN
        submissionStatusUpdater.doUpdate(List.of(actualSubmission.getId()));

        // THEN
        final Submission statusSubmission = passClient.getObject(Submission.class, submission.getId());
        assertEquals(SubmissionStatus.COMPLETE, statusSubmission.getSubmissionStatus());

        // WHEN
        Deposit deposit = resultDeposits.iterator().next();
        depositProcessor.accept(deposit);

        // THEN
        final Submission aggrStatusSubmission = passClient.getObject(Submission.class, submission.getId());
        assertEquals(AggregatedDepositStatus.ACCEPTED, aggrStatusSubmission.getAggregatedDepositStatus());
    }

}
