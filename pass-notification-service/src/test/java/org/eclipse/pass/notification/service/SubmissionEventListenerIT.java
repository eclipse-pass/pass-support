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
package org.eclipse.pass.notification.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.eclipse.pass.notification.service.AwsSqsTestConfig.QUEUE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.net.URI;
import javax.json.Json;

import jakarta.jms.TextMessage;
import org.eclipse.pass.notification.AbstractNotificationSpringIntegrationTest;
import org.eclipse.pass.notification.model.SubmissionEventMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Import(AwsSqsTestConfig.class)
@TestPropertySource(properties = {
    "aws.region=us-east-1",
    "spring.jms.listener.auto-startup=true",
    "pass.jms.queue.submission.event.name=" + QUEUE_NAME
})
@Testcontainers
@DirtiesContext
public class SubmissionEventListenerIT extends AbstractNotificationSpringIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:2.1.0");

    @Container
    static final LocalStackContainer LOCALSTACK_CONTAINER =
        new LocalStackContainer(LOCALSTACK_IMG)
            .withServices(LocalStackContainer.Service.SQS);

    @Autowired private JmsTemplate jmsTemplate;
    @MockBean private NotificationService notificationService;
    @Captor ArgumentCaptor<SubmissionEventMessage> messageCaptor;

    @Test
    void testProcessMessage() {
        String message = Json.createObjectBuilder()
            .add("submission-event", "222")
            .add("approval-link", "http://example.org/user-token-test")
            .add("type", "SubmissionEvent")
            .build().toString();

        jmsTemplate.send(QUEUE_NAME, ses -> {
            TextMessage textMessage = ses.createTextMessage(message);
            textMessage.setStringProperty("type", "SubmissionEvent");
            return textMessage;
        });

        await().atMost(3, SECONDS).untilAsserted(() -> {
            verify(notificationService).notify(messageCaptor.capture());
            SubmissionEventMessage actualMessage = messageCaptor.getValue();
            assertEquals("222", actualMessage.getSubmissionEventId());
            assertEquals(URI.create("http://example.org/user-token-test"), actualMessage.getUserApprovalLink());
        });
    }
}
