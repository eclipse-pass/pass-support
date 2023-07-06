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

import static org.eclipse.pass.deposit.messaging.service.AwsSqsTestConfig.QUEUE_NAME;

import org.eclipse.pass.deposit.DepositApp;
import org.eclipse.pass.support.client.PassClient;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource(properties = {
    "aws.region=us-east-1",
    "spring.jms.listener.auto-startup=true",
    "pass.deposit.queue.submission.name=" + QUEUE_NAME,
    "pass.deposit.jobs.disabled=true"
})
@Testcontainers
@DirtiesContext
public abstract class AbstractListenerIT {

    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:2.1.0");

    @Container
    static final LocalStackContainer LOCALSTACK_CONTAINER =
        new LocalStackContainer(LOCALSTACK_IMG)
            .withServices(LocalStackContainer.Service.SQS);

    @Autowired protected JmsTemplate jmsTemplate;
    @MockBean protected PassClient passClient;

}
