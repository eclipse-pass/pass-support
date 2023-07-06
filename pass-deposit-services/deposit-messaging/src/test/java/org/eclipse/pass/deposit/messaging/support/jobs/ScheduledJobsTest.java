package org.eclipse.pass.deposit.messaging.support.jobs;

import org.eclipse.pass.deposit.DepositApp;
import org.eclipse.pass.deposit.messaging.service.DepositUpdater;
import org.eclipse.pass.deposit.messaging.service.SubmissionStatusUpdater;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = DepositApp.class)
@TestPropertySource("classpath:test-application.properties")
@TestPropertySource(properties = {
    "pass.deposit.jobs.disabled=false",
    "pass.deposit.jobs.default-interval-ms=1500"
})
public class ScheduledJobsTest {

    @MockBean private SubmissionStatusUpdater submissionStatusUpdater;
    @MockBean private DepositUpdater depositUpdater;

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
}
