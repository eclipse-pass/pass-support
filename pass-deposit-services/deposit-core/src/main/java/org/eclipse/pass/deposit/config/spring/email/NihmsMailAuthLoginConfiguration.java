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

import java.util.Properties;

import jakarta.mail.URLName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Configuration
@EnableIntegration
@ConditionalOnExpression(
    "'${pass.deposit.nihms.email.enabled}'=='true' and '${pass.deposit.nihms.email.auth}'=='LOGIN'"
)
public class NihmsMailAuthLoginConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(NihmsMailAuthLoginConfiguration.class);

    @Value("${nihms.mail.username}")
    private String nihmsMailUsername;

    @Value("${nihms.mail.password}")
    private String nihmsMailPassword;

    @Value("${nihms.mail.host}")
    private String nihmsImapHost;

    @Value("${nihms.mail.port}")
    private Integer nihmsImapPort;

    private final MailIntegration mailIntegration;

    public NihmsMailAuthLoginConfiguration(MailIntegration mailIntegration) {
        this.mailIntegration = mailIntegration;
    }

    @Bean
    public IntegrationFlow imapMailFlowLogin() {
        LOG.warn("Nihms Email Service is enabled (LOGIN AUTH), configuration is being executed");
        String storeUrl = new URLName("imaps", nihmsImapHost, nihmsImapPort, "inbox",
            nihmsMailUsername, nihmsMailPassword).toString();
        Properties properties = getImapLoginProperties();
        return mailIntegration.imapMailFlow(storeUrl, properties, null);
    }

    private Properties getImapLoginProperties() {
        Properties javaMailProperties = new Properties();
        javaMailProperties.setProperty("mail.imaps.ssl.enable", "true");
        javaMailProperties.setProperty("mail.imaps.ssl.trust", nihmsImapHost);
        javaMailProperties.setProperty("mail.imaps.starttls.enable", "true");
        javaMailProperties.setProperty("mail.imaps.auth.plain.disable", "true");
        return javaMailProperties;
    }

}