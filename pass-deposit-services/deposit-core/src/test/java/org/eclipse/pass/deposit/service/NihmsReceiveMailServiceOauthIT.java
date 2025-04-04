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

import org.eclipse.pass.deposit.DepositApp;
import org.eclipse.pass.deposit.config.spring.email.Oauth2ExchangeMailAuthenticator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource("classpath:test-application.properties")
@TestPropertySource(properties = {
    "nihms.email.oauth.test.it=true",
    "pass.deposit.nihms.email.enabled=true",
    "pass.deposit.nihms.email.delay=2000",
    "pass.deposit.nihms.email.from=test-from-2@localhost,test-from@localhost",
    "pass.deposit.nihms.email.ssl.checkserveridentity=false",
    "pass.deposit.nihms.email.auth=MS_EXCHANGE_OAUTH2",
    "nihms.mail.host=localhost",
    "nihms.mail.port=3993",
    "nihms.mail.username=testnihms@localhost",
    "nihms.mail.tenant.id=test-tenant",
    "nihms.mail.client.id=test-client-id",
    "nihms.mail.client.secret=test-client-id"
})
public class NihmsReceiveMailServiceOauthIT extends AbstractNihmsReceiveMailServiceIT {
    // Test is in abstract class; important config params for imap oauth auth in test annotations in this class.
    @MockitoBean private Oauth2ExchangeMailAuthenticator mockAuthenticator;
}
