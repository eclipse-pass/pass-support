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
package org.eclipse.pass.notification.config;

import java.util.Map;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import org.eclipse.pass.notification.model.SubmissionEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ConditionalOnProperty(
    name = "spring.jms.listener.auto-startup",
    havingValue = "true"
)
@Configuration
@EnableJms
public class JmsConfig {

    @Value("${aws.region:AWS_REGION}")
    private String awsRegion;

    /**
     * Configure a JMS connection factory for Amazon SQS.
     * <p>
     * Note that if a different JMS provider is required, this method should be changed to return the
     * ConnectionFactory of the different JMS provider.
     *
     * @return ConnectionFactory
     */
    @Bean
    public ConnectionFactory jmsConnectionFactory() {
        SqsClient sqsClient = SqsClient.builder()
            .region(Region.of(awsRegion))
            .build();
        return new SQSConnectionFactory(
            new ProviderConfiguration(),
            sqsClient
        );
    }

    /**
     * Configure the default message listener factory.
     * @param jmsConnectionFactory the jms conn factory
     * @param messageConverter the message converter
     * @return the factory
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
        ConnectionFactory jmsConnectionFactory,
        MessageConverter messageConverter
    ) {
        DefaultJmsListenerContainerFactory factory =
            new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(jmsConnectionFactory);
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setMessageConverter(messageConverter);
        return factory;
    }

    /**
     * Configure the message converter
     * @param jsonMapper the Jackson json mapper
     * @return the message converter
     */
    @Bean
    public MessageConverter messageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setTypeIdPropertyName("type");
        converter.setTypeIdMappings(Map.of("SubmissionEvent", SubmissionEventMessage.class));
        return converter;
    }

}
