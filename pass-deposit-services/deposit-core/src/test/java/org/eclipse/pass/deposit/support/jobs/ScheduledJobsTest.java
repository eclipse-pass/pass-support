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
package org.eclipse.pass.deposit.support.jobs;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

import org.eclipse.pass.deposit.DepositApp;
import org.eclipse.pass.deposit.service.DepositUpdater;
import org.eclipse.pass.deposit.service.FailedDepositRetry;
import org.eclipse.pass.deposit.service.SubmissionStatusUpdater;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource("classpath:test-application.properties")
@TestPropertySource(properties = {
    "pass.deposit.jobs.disabled=false",
    "pass.deposit.jobs.default-interval-ms=1500",
    "pass.deposit.jobs.1.init.delay=50",
    "pass.deposit.jobs.2.init.delay=100",
    "pass.deposit.jobs.3.init.delay=150"
})
public class ScheduledJobsTest {

    @MockBean private SubmissionStatusUpdater submissionStatusUpdater;
    @MockBean private DepositUpdater depositUpdater;
    @MockBean private FailedDepositRetry failedDepositRetry;

    @Test
    void testDepositUpdaterJob() {
        // GIVEN/WHEN
        // depositUpdater.doUpdate() will be called from Scheduled method in job
        await().atMost(3, SECONDS).untilAsserted(() -> {
            verify(depositUpdater).doUpdate();
        });
    }

    @Test
    void testSubmissionStatusUpdaterJob() {
        // GIVEN/WHEN
        // submissionStatusUpdater.doUpdate() will be called from Scheduled method in job
        await().atMost(3, SECONDS).untilAsserted(() -> {
            verify(submissionStatusUpdater).doUpdate();
        });
    }

    @Test
    void testFailedDepositRetryJob() {
        // GIVEN/WHEN
        // failedDepositRetry.retryFailedDeposits() will be called from Scheduled method in job
        await().atMost(3, SECONDS).untilAsserted(() -> {
            verify(failedDepositRetry).retryFailedDeposits();
        });
    }
}
