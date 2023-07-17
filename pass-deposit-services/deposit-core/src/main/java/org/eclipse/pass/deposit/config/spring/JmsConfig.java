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
package org.eclipse.pass.deposit.config.spring;

import java.net.URI;
import java.util.Map;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import org.apache.commons.lang.StringUtils;
import org.eclipse.pass.deposit.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.model.DepositMessage;
import org.eclipse.pass.deposit.model.SubmissionMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
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

    @Value("${aws.sqs.endpoint.override:AWS_SQS_ENDPOINT_OVERRIDE}")
    private String awsSqsEndpointOverride;

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
        SqsClientBuilder sqsClientBuilder = SqsClient.builder()
            .region(Region.of(awsRegion));
        SqsClient sqsClient = createSqsClient(sqsClientBuilder);

        return new SQSConnectionFactory(
            new ProviderConfiguration(),
            sqsClient
        );
    }

    private SqsClient createSqsClient(SqsClientBuilder sqsClientBuilder) {
        if (StringUtils.isNotEmpty(awsSqsEndpointOverride)) {
            return sqsClientBuilder
                .endpointOverride(URI.create(awsSqsEndpointOverride))
                .build();
        }
        return sqsClientBuilder.build();
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(DepositServiceErrorHandler errorHandler,
                                                                          ConnectionFactory connectionFactory,
                                                                          MessageConverter messageConverter) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setErrorHandler(errorHandler);
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }

    /**
     * Configure the message converter
     * @param objectMapper the object mapper
     * @return the message converter
     */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTypeIdPropertyName("type");
        converter.setTypeIdMappings(
            Map.of(
                "SubmissionReady", SubmissionMessage.class,
                "DepositStatus", DepositMessage.class
            )
        );
        converter.setObjectMapper(objectMapper);
        return converter;
    }

}
