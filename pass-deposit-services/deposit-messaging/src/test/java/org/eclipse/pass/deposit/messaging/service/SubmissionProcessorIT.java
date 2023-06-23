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

import static org.eclipse.pass.deposit.util.SubmissionTestUtil.getFileUris;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import org.eclipse.deposit.util.async.Condition;
import org.eclipse.pass.deposit.messaging.config.spring.DepositConfig;
import org.eclipse.pass.deposit.messaging.config.spring.JmsConfig;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"pass.deposit.jobs.default-interval-ms=5000"})
@Import({DepositConfig.class, JmsConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SubmissionProcessorIT extends AbstractSubmissionIT {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionProcessorIT.class);

    private Submission submission;

    @Before
    public void submit() throws IOException {
        submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample1-unsubmitted")));
    }

    @Test
    public void smokeSubmission() throws Exception {

        assertEquals(Boolean.FALSE, submission.getSubmitted());
        assertTrue(getFileUris(submission, passClient).size() > 0);

        triggerSubmission(submission.getId());

        submission = passClient.getObject(Submission.class, submission.getId());
        assertEquals(SubmissionStatus.SUBMITTED, submission.getSubmissionStatus());

        // After the SubmissionProcessor successfully processing a submission we should observe:

        // 1. Deposit resources created for each Repository associated with the Submission

        // 2. The Deposit resources should be in a ACCEPTED state

        // These statuses are dependant on the transport being used - because the TransportResponse.onSuccess(...)
        // method may modify the repository resources associated with the Submission.  Because the FilesystemTransport
        // is used, the Deposit resources will be in the ACCEPTED state, and RepositoryCopy resources in the ACCEPTED
        // state.

        // 3. The Submission's AggregateDepositStatus should be set to ACCEPTED

        // 4. The Submission's SubmissionStatus should be changed to COMPLETE

        Condition<Set<Deposit>> deposits = depositsForSubmission(
                submission.getId(),
                submission.getRepositories().size(),
                (deposit, repo) -> {
                    LOG.debug("Polling Submission {} for deposit-related resources", submission.getId());
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

        // Verification

        Set<Deposit> result = deposits.getResult();
        assertEquals(submission.getRepositories().size(), result.size());
        assertEquals(result.stream().filter(deposit -> deposit.getSubmission().equals(submission.getId())).count(),
                     submission.getRepositories().size());
        assertTrue(result.stream().allMatch(deposit -> deposit.getDepositStatus() == DepositStatus.ACCEPTED));

        Condition<Submission> statusVerification =
            new Condition<>(() -> passClient.getObject(Submission.class, submission.getId()),
                            "Get updated Submission");

        statusVerification.awaitAndVerify(
            sub -> AggregatedDepositStatus.ACCEPTED == sub.getAggregatedDepositStatus());
        statusVerification.awaitAndVerify(
            sub -> SubmissionStatus.COMPLETE == sub.getSubmissionStatus());

        assertEquals(AggregatedDepositStatus.ACCEPTED, statusVerification.getResult().getAggregatedDepositStatus());
        assertEquals(SubmissionStatus.COMPLETE, statusVerification.getResult().getSubmissionStatus());
    }

}
