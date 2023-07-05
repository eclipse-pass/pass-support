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
package org.eclipse.pass.deposit.messaging.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.eclipse.pass.deposit.messaging.service.AwsSqsTestConfig.QUEUE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import javax.json.Json;

import jakarta.jms.TextMessage;
import org.eclipse.pass.support.client.model.Deposit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@TestPropertySource(properties = {
    "pass.deposit.queue.deposit.name=" + QUEUE_NAME
})
public class DepositListenerIT extends AbstractListenerIT {

    @MockBean private DepositProcessor depositProcessor;
    @Captor ArgumentCaptor<Deposit> messageCaptor;

    @Test
    void testProcessDepositMessage() throws IOException {
        Deposit deposit = new Deposit();
        deposit.setId("111");
        when(passClient.getObject(Deposit.class, "111")).thenReturn(deposit);
        String message = Json.createObjectBuilder()
            .add("deposit", "111")
            .build().toString();

        jmsTemplate.send(QUEUE_NAME, ses -> {
            TextMessage textMessage = ses.createTextMessage(message);
            textMessage.setStringProperty("type", "DepositStatus");
            return textMessage;
        });

        await().atMost(3, SECONDS).untilAsserted(() -> {
            verify(depositProcessor).accept(messageCaptor.capture());
            Deposit actualDeposit = messageCaptor.getValue();
            assertEquals("111", actualDeposit.getId());
        });
    }

}
