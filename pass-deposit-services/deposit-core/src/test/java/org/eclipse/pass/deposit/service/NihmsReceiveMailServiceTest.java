package org.eclipse.pass.deposit.service;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.pass.deposit.DepositApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource("classpath:test-application.properties")
@TestPropertySource(properties = {
    "pass.deposit.nihms.email.enabled=true",
    "pass.deposit.nihms.email.delay=2000",
    "nihms.mail.host=localhost",
    "nihms.mail.port=3143",
    "nihms.mail.username=testnihms%40localhost",
    "nihms.mail.password=testnihmspassword"

})
public class NihmsReceiveMailServiceTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAP)
        .withConfiguration(new GreenMailConfiguration().withUser("testnihms@localhost", "testnihmspassword"))
        .withPerMethodLifecycle(false);

    @SpyBean private NihmsReceiveMailService nihmsReceiveMailService;

    @Test
    void testReceiveMail() {
        final String subject = GreenMailUtil.random();
        final String body = GreenMailUtil.random();
        MimeMessage message = GreenMailUtil.createTextEmail("testnihms@localhost", "from@localhost",
            subject, body, greenMail.getImap().getServerSetup());
        GreenMailUser user = greenMail.setUser("testnihms@localhost", "testnihmspassword");
        user.deliver(message);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            assertEquals(1, greenMail.getReceivedMessages().length);
            verify(nihmsReceiveMailService, times(1)).handleReceivedMail(any());
        });

    }
}
