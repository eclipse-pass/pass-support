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
@Component
public class MailIntegration {

    @Value("${pass.deposit.nihms.email.delay}")
    private Integer nihmsEmailDelay;

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

}
