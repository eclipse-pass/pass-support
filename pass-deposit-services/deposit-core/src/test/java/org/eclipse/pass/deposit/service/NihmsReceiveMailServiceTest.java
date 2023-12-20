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
package org.eclipse.pass.deposit.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.pass.deposit.DepositApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource("classpath:test-application.properties")
@TestPropertySource(properties = {
    "pass.deposit.nihms.email.enabled=true",
    "pass.deposit.nihms.email.delay=2000",
    "nihms.mail.host=localhost",
    "nihms.mail.port=3993",
    "nihms.mail.username=testnihms@localhost",
    "nihms.mail.password=testnihmspassword"

})
public class NihmsReceiveMailServiceTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAPS)
        .withConfiguration(new GreenMailConfiguration().withUser("testnihms@localhost", "testnihmspassword"))
        .withPerMethodLifecycle(false);

    @SpyBean private NihmsReceiveMailService nihmsReceiveMailService;
    @Captor ArgumentCaptor<MimeMessage> messageCaptor;

    @Test
    void testHandleReceivedMail() throws MessagingException, IOException {
        // GIVEN
        final String subject1 = GreenMailUtil.random();
        final String body1 = GreenMailUtil.random();
        MimeMessage message1 = GreenMailUtil.createTextEmail("testnihms@localhost", "from@localhost",
            subject1, body1, greenMail.getImaps().getServerSetup());
        GreenMailUser user = greenMail.setUser("testnihms@localhost", "testnihmspassword");

        // WHEN
        user.deliver(message1);

        // THEN
        // wait for email poller to run, every 2 seconds
        await().pollDelay(4, SECONDS).until(() -> true);
        verify(nihmsReceiveMailService, times(1))
            .handleReceivedMail(any());

        // GIVEN
        final String subject2 = GreenMailUtil.random();
        final String body2 = GreenMailUtil.random();
        MimeMessage message2 = GreenMailUtil.createTextEmail("testnihms@localhost", "from@localhost",
            subject2, body2, greenMail.getImaps().getServerSetup());

        // WHEN
        user.deliver(message2);

        // THEN
        // wait for email poller to run, every 2 seconds
        await().pollDelay(4, SECONDS).until(() -> true);
        verify(nihmsReceiveMailService, times(2))
            .handleReceivedMail(messageCaptor.capture());
        List<MimeMessage> mimeMessages = messageCaptor.getAllValues();
        assertEquals(2, mimeMessages.size());
        MimeMessage mimeMessage1 = mimeMessages.get(0);
        assertEquals(subject1, mimeMessage1.getSubject());
        assertEquals(body1, mimeMessage1.getContent());
        MimeMessage mimeMessage2 = mimeMessages.get(1);
        assertEquals(subject2, mimeMessage2.getSubject());
        assertEquals(body2, mimeMessage2.getContent());

    }
}
