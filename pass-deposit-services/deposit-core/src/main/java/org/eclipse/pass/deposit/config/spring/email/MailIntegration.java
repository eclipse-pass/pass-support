/*
 * Copyright 2024 Johns Hopkins University
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
package org.eclipse.pass.deposit.config.spring.email;

import java.util.Objects;
import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.pass.deposit.service.NihmsReceiveMailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.integration.mail.dsl.ImapMailInboundChannelAdapterSpec;
import org.springframework.integration.mail.dsl.Mail;
import org.springframework.integration.mail.dsl.MailInboundChannelAdapterSpec;
import org.springframework.stereotype.Component;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ConditionalOnProperty(name = "pass.deposit.nihms.email.enabled")
@Component
public class MailIntegration {

    @Value("${pass.deposit.nihms.email.delay}")
    private Integer nihmsEmailDelay;

    @Value("${nihms.mail.host}")
    private String nihmsImapHost;

    @Value("${pass.deposit.nihms.email.ssl.checkserveridentity}")
    private String sslCheckServerIdentity;

    private final NihmsReceiveMailService nihmsReceiveMailService;

    public MailIntegration(NihmsReceiveMailService nihmsReceiveMailService) {
        this.nihmsReceiveMailService = nihmsReceiveMailService;
    }

    public IntegrationFlow imapMailFlow(String storeUrl, Properties mailProperties, Authenticator authenticator) {
        MailInboundChannelAdapterSpec<ImapMailInboundChannelAdapterSpec, ImapMailReceiver> inboundSpec =
            Mail.imapInboundAdapter(storeUrl)
                .shouldMarkMessagesAsRead(true)
                .shouldDeleteMessages(false)
                .simpleContent(true)
                .maxFetchSize(10)
                .javaMailProperties(mailProperties);
        return IntegrationFlow
            .from(
                Objects.nonNull(authenticator)
                    ? inboundSpec.javaMailAuthenticator(authenticator)
                    : inboundSpec,
                e -> e.poller(p -> p.fixedDelay(nihmsEmailDelay)))
            .channel(MessageChannels.direct().datatype(MimeMessage.class))
            .handle(message -> nihmsReceiveMailService.handleReceivedMail((MimeMessage) message.getPayload()))
            .get();
    }

    Properties getImapDefaultProperties() {
        Properties javaMailProperties = new Properties();
        javaMailProperties.setProperty("mail.imaps.ssl.enable", "true");
        javaMailProperties.setProperty("mail.imaps.ssl.trust", nihmsImapHost);
        javaMailProperties.setProperty("mail.imaps.ssl.checkserveridentity", sslCheckServerIdentity);
        javaMailProperties.setProperty("mail.imaps.starttls.enable", "true");
        javaMailProperties.setProperty("mail.imaps.auth.login.disable", "true");
        javaMailProperties.setProperty("mail.imaps.auth.plain.disable", "true");
        javaMailProperties.setProperty("mail.imaps.connectiontimeout", "60000");
        javaMailProperties.setProperty("mail.imaps.timeout", "90000");
        javaMailProperties.setProperty("mail.imaps.writetimeout", "90000");
        return javaMailProperties;
    }

}
