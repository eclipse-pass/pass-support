package org.eclipse.pass.deposit.service;

import static org.mockito.Mockito.when;

import jakarta.annotation.PostConstruct;
import jakarta.mail.PasswordAuthentication;
import org.eclipse.pass.deposit.config.spring.email.Oauth2ExchangeMailAuthenticator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
@ConditionalOnProperty(name = "nihms.email.oauth.test.it", havingValue = "true")
public class OauthTestConfig {
    @MockBean
    private Oauth2ExchangeMailAuthenticator mockAuthenticator;

    @PostConstruct
    public void initMock() {
        PasswordAuthentication authentication =
            new PasswordAuthentication("testnihms@localhost", "testnihmspassword");
        when(mockAuthenticator.getPasswordAuthentication()).thenReturn(authentication);
    }
}
