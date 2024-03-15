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

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Set;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IClientCredential;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pass.deposit.nihms.email.auth", havingValue = "MS_EXCHANGE_OAUTH2")
public class Oauth2ExchangeMailAuthenticator extends Authenticator {
    private static final Set<String> SCOPE = Collections.singleton("https://outlook.office365.com/.default");
    private static final String AUTHORITY_BASE_URL = "https://login.microsoftonline.com/";

    @Value("${nihms.mail.username}")
    private String nihmsMailUsername;

    @Value("${nihms.mail.tenant.id}")
    private String nihmsTenantId;

    @Value("${nihms.mail.client.id}")
    private String nihmsClientId;

    @Value("${nihms.mail.client.secret}")
    private String nihmsClientSecret;

    @Override
    public PasswordAuthentication getPasswordAuthentication() {
        String accessToken = acquireToken();
        return new PasswordAuthentication(nihmsMailUsername, accessToken);
    }

    private String acquireToken() {
        ConfidentialClientApplication clientApplication = getClientApplication();
        ClientCredentialParameters parameters = ClientCredentialParameters.builder(SCOPE).build();
        // Cached token will be used and refreshed automatically if needed.
        return clientApplication.acquireToken(parameters).join().accessToken();
    }

    private ConfidentialClientApplication getClientApplication() {
        try {
            IClientCredential clientCredential = ClientCredentialFactory.createFromSecret(nihmsClientSecret);
            return ConfidentialClientApplication
                .builder(nihmsClientId, clientCredential)
                .authority(AUTHORITY_BASE_URL + nihmsTenantId)
                .build();
        } catch (MalformedURLException e) {
            throw new DepositServiceRuntimeException("Error building exchange client application", e);
        }
    }
}
