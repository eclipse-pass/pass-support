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

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazon.sqs.javamessaging.SQSMessageConsumerPrefetch;
import jakarta.jms.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ConditionalOnProperty(
    name = "spring.jms.listener.auto-startup",
    havingValue = "true"
)
@TestConfiguration
public class AwsSqsTestConfig {

    static final String QUEUE_NAME = "test-deposit-queue";

    @Bean
    @Primary
    public ConnectionFactory testJmsConnectionFactory() {
        SqsClient sqsClient = testSqsClient();
        createTestEventQueue(sqsClient);
        return new SQSConnectionFactory(
            new ProviderConfiguration(),
            sqsClient
        );
    }

    private SqsClient testSqsClient() {
        /*
         * The strange code below to set WAIT_TIME_SECONDS to 2 seconds is so the Sqs message consumer
         * shuts down faster after the tests have finished.
         */
        ReflectionTestUtils.setField(SQSMessageConsumerPrefetch.class, "WAIT_TIME_SECONDS", 1);
        return SqsClient.builder()
            .endpointOverride(AbstractListenerIT.LOCALSTACK_CONTAINER.getEndpoint())
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("localstackkeyid", "dummykey")
                )
            )
            .region(Region.of("us-east-1"))
            .build();
    }

    private void createTestEventQueue(SqsClient sqsClient) {
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
            .queueName(QUEUE_NAME)
            .build();
        sqsClient.createQueue(createQueueRequest);
    }

}
