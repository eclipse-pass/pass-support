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
package org.dataconservancy.pass.deposit.messaging.config.spring;

import java.util.function.Consumer;
import javax.jms.ConnectionFactory;
import javax.jms.Session;

import org.dataconservancy.pass.deposit.messaging.DepositServiceErrorHandler;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@EnableJms
public class JmsConfig {
    private static final Logger LOG = LoggerFactory.getLogger(JmsConfig.class);

    @Autowired
    private PassClient passClient;

   @Autowired
    private Consumer<Submission> submissionConsumer;

    @Autowired
    private Consumer<Deposit> depositConsumer;

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(DepositServiceErrorHandler errorHandler,
                                                                          @Value("${spring.jms.listener.concurrency}")
                                                                              String concurrency,
                                                                          @Value("${spring.jms.listener.auto-startup}")
                                                                              boolean autoStart,
                                                                          ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setErrorHandler(errorHandler);
        factory.setConcurrency(concurrency);
        factory.setConnectionFactory(connectionFactory);
        factory.setAutoStartup(autoStart);
        return factory;
    }

    @JmsListener(destination = "${pass.deposit.queue.submission.name}", containerFactory = "jmsListenerContainerFactory")
    public void processSubmissionMessage(String message) {
        try {
            JsonObject json = new JsonParser().parse(message).getAsJsonObject();
            String submissionId = json.get("submission-id").getAsString();
            submissionConsumer.accept(passClient.getObject(Submission.class, submissionId));
        } catch (Exception e) {
            LOG.error("Failed to process submission JMS message.\nPayload: '{}'", message, e);
        }
    }

    @JmsListener(destination = "${pass.deposit.queue.deposit.name}", containerFactory = "jmsListenerContainerFactory")
    public void processDepositMessage(String message) {
        try {
            JsonObject json = new JsonParser().parse(message).getAsJsonObject();
            String depositId = json.get("deposit-id").getAsString();
            depositConsumer.accept(passClient.getObject(Deposit.class, depositId));
        } catch (Exception e) {
            LOG.error("Failed to process deposit JMS message.\nPayload: '{}'", message, e);
        }
    }
}
