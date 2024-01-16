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
package org.eclipse.pass.deposit.config.spring;

import java.util.Properties;

import jakarta.mail.URLName;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.pass.deposit.service.NihmsReceiveMailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.integration.mail.MailReceiver;
import org.springframework.integration.mail.MailReceivingMessageSource;
import org.springframework.messaging.Message;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Configuration
@EnableIntegration
@ConditionalOnProperty(name = "pass.deposit.nihms.email.enabled", havingValue = "true")
public class NihmsMailReceiverConfiguration {

    @Value("${nihms.mail.username}")
    private String nihmsMailUsername;

    @Value("${nihms.mail.password}")
    private String nihmsMailPassword;

    @Value("${nihms.mail.host}")
    private String nihmsImapHost;

    @Value("${nihms.mail.port}")
    private Integer nihmsImapPort;

    private final NihmsReceiveMailService nihmsReceiveMailService;

    public NihmsMailReceiverConfiguration(NihmsReceiveMailService nihmsReceiveMailService) {
        this.nihmsReceiveMailService = nihmsReceiveMailService;
    }

    @ServiceActivator(inputChannel = "receiveEmailChannel")
    public void receive(Message<?> message) {
        nihmsReceiveMailService.handleReceivedMail((MimeMessage) message.getPayload());
    }

    @Bean("receiveEmailChannel")
    public DirectChannel defaultChannel() {
        DirectChannel directChannel = new DirectChannel();
        directChannel.setDatatypes(jakarta.mail.internet.MimeMessage.class);
        return directChannel;
    }

    @Bean()
    @InboundChannelAdapter(
        channel = "receiveEmailChannel",
        poller = @Poller(fixedDelay = "${pass.deposit.nihms.email.delay}")
    )
    public MailReceivingMessageSource mailMessageSource(MailReceiver mailReceiver) {
        return new MailReceivingMessageSource(mailReceiver);
    }

    @Bean
    public MailReceiver imapMailReceiver() {
        String storeUrl = new URLName("imaps", nihmsImapHost, nihmsImapPort, "inbox",
            nihmsMailUsername, nihmsMailPassword).toString();
        ImapMailReceiver imapMailReceiver = new ImapMailReceiver(storeUrl);
        imapMailReceiver.setShouldMarkMessagesAsRead(true);
        imapMailReceiver.setShouldDeleteMessages(false);
        imapMailReceiver.setSimpleContent(true);
        imapMailReceiver.setMaxFetchSize(10);
        Properties javaMailProperties = new Properties();
        javaMailProperties.setProperty("mail.imaps.ssl.enable", "true");
        javaMailProperties.setProperty("mail.imaps.ssl.trust", nihmsImapHost);
        javaMailProperties.setProperty("mail.imaps.starttls.enable", "true");
        javaMailProperties.setProperty("mail.imaps.auth.plain.disable", "true");
        imapMailReceiver.setJavaMailProperties(javaMailProperties);
        return imapMailReceiver;
    }

}
