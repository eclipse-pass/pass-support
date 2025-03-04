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
package org.eclipse.pass.deposit.service;

import static org.mockito.Mockito.when;

import jakarta.annotation.PostConstruct;
import jakarta.mail.PasswordAuthentication;
import org.eclipse.pass.deposit.config.spring.email.Oauth2ExchangeMailAuthenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@ConditionalOnProperty(name = "nihms.email.oauth.test.it", havingValue = "true")
public class OauthTestConfig {
    @Autowired Oauth2ExchangeMailAuthenticator mockAuthenticator;

    @PostConstruct
    public void initMock() {
        PasswordAuthentication authentication =
            new PasswordAuthentication("testnihms@localhost", "testnihmspassword");
        when(mockAuthenticator.getPasswordAuthentication()).thenReturn(authentication);
    }
}
