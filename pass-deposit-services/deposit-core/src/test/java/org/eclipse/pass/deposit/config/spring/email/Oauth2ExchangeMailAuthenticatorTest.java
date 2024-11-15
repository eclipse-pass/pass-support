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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;

import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import jakarta.mail.PasswordAuthentication;
import org.eclipse.pass.deposit.DepositApp;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource(
    locations = "/test-application.properties",
    properties = {
        "pass.deposit.nihms.email.auth=MS_EXCHANGE_OAUTH2",
        "nihms.mail.username=testnihms-oauth@localhost",
        "nihms.mail.tenant.id=test-tenant",
        "nihms.mail.client.id=test-client-id",
        "nihms.mail.client.secret=test-client-id"
    })
public class Oauth2ExchangeMailAuthenticatorTest {

    @Autowired private Oauth2ExchangeMailAuthenticator oauth2ExchangeMailAuthenticator;

    @SuppressWarnings("unchecked")
    @Test
    void testGetPasswordAuthentication() throws MalformedURLException {
        // GIVEN
        IAuthenticationResult authenticationResultMock =  mock(IAuthenticationResult.class);
        when(authenticationResultMock.accessToken()).thenReturn("test-access-token");
        CompletableFuture<IAuthenticationResult> completableFutureMock = mock(CompletableFuture.class);
        when(completableFutureMock.join()).thenReturn(authenticationResultMock);
        ConfidentialClientApplication clientAppMock = mock(ConfidentialClientApplication.class);
        when(clientAppMock.acquireToken(any(ClientCredentialParameters.class))).thenReturn(completableFutureMock);
        ConfidentialClientApplication.Builder mockBuilder = mock(ConfidentialClientApplication.Builder.class);
        when(mockBuilder.authority(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(clientAppMock);
        try (MockedStatic<ConfidentialClientApplication> clientAppStaticMock =
                 mockStatic(ConfidentialClientApplication.class)) {
            clientAppStaticMock.when(() -> ConfidentialClientApplication.builder(any(), any())).thenReturn(mockBuilder);

            // WHEN
            PasswordAuthentication authentication = oauth2ExchangeMailAuthenticator.getPasswordAuthentication();

            // THEN
            assertEquals(authentication.getUserName(), "testnihms-oauth@localhost");
            assertEquals(authentication.getPassword(), "test-access-token");
        }
    }

}
