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
    "'${pass.deposit.nihms.email.enabled}'=='true' and '${pass.deposit.nihms.email.auth}'=='MS_EXCHANGE_OAUTH2'"
)
public class NihmsMailAuthOauthConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(NihmsMailAuthOauthConfiguration.class);

    @Value("${nihms.mail.host}")
    private String nihmsImapHost;

    @Value("${nihms.mail.port}")
    private Integer nihmsImapPort;

    private final MailIntegration mailIntegration;
    private final Oauth2ExchangeMailAuthenticator oauth2ExchangeMailAuthenticator;

    public NihmsMailAuthOauthConfiguration(MailIntegration mailIntegration,
                                           Oauth2ExchangeMailAuthenticator oauth2ExchangeMailAuthenticator) {
        this.mailIntegration = mailIntegration;
        this.oauth2ExchangeMailAuthenticator = oauth2ExchangeMailAuthenticator;
    }

    @Bean
    public IntegrationFlow imapMailFlowOauth() {
        LOG.warn("Nihms Email Service is enabled (MS_EXCHANGE_OAUTH2 AUTH), configuration is being executed");
        String storeUrl = "imaps://" + nihmsImapHost + ":" + nihmsImapPort + "/inbox";
        Properties properties = getImapOauthProperties();
        return mailIntegration.imapMailFlow(storeUrl, properties, oauth2ExchangeMailAuthenticator);
    }

    private Properties getImapOauthProperties() {
        Properties javaMailProperties = mailIntegration.getImapDefaultProperties();
        javaMailProperties.setProperty("mail.imaps.sasl.mechanisms", "XOAUTH2");
        return javaMailProperties;
    }

}
