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
import static org.eclipse.pass.deposit.util.ResourceTestUtil.findByNameAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.eclipse.pass.deposit.DepositApp;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
    "pass.deposit.nihms.email.from=test-from@localhost",
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

    @MockBean private PassClient passClient;
    @SpyBean private NihmsReceiveMailService nihmsReceiveMailService;
    @Captor ArgumentCaptor<MimeMessage> messageCaptor;
    @Captor ArgumentCaptor<Deposit> depositCaptor;

    @Test
    void testHandleReceivedMail() {
        // GIVEN
        final String subject1 = GreenMailUtil.random();
        final String body1 = GreenMailUtil.random();
        MimeMessage message1 = GreenMailUtil.createTextEmail("testnihms@localhost", "test-from@localhost",
            subject1, body1, greenMail.getImaps().getServerSetup());
        GreenMailUser user = greenMail.setUser("testnihms@localhost", "testnihmspassword");

        // WHEN
        user.deliver(message1);

        // THEN
        // wait for email poller to run, every 2 seconds
        await().atMost(4, SECONDS).untilAsserted(() ->
            verify(nihmsReceiveMailService, times(1)).handleReceivedMail(any())
        );

        // GIVEN
        final String subject2 = GreenMailUtil.random();
        final String body2 = GreenMailUtil.random();
        MimeMessage message2 = GreenMailUtil.createTextEmail("testnihms@localhost", "test-from@localhost",
            subject2, body2, greenMail.getImaps().getServerSetup());

        // WHEN
        user.deliver(message2);

        // THEN
        // wait for email poller to run, every 2 seconds
        await().atMost(4, SECONDS).untilAsserted(() -> {
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
        });
    }

    @Test
    void testHandleReceivedMail_MessageParsing() throws MessagingException, IOException {
        // GIVEN
        final String subject = "Bulk submission (errors encountered)";
        final String body = findByNameAsString("nihmsemail.html", this.getClass());
        Session smtpSession = GreenMailUtil.getSession(greenMail.getImaps().getServerSetup());
        MimeMessage mimeMessage = new MimeMessage(smtpSession);
        mimeMessage.setRecipients(Message.RecipientType.TO, "testnihms@localhost");
        mimeMessage.setFrom("test-from@localhost");
        mimeMessage.setSubject(subject);
        mimeMessage.setContent(body, "text/html; charset=\"utf-8\"");
        mimeMessage.saveChanges();
        mockPassClientStreams();

        // WHEN
        nihmsReceiveMailService.handleReceivedMail(mimeMessage);

        // THEN
        verifyDepositUpdates();
    }

    @Test
    void testHandleReceivedMail_MessageParsing_Multipart() throws MessagingException, IOException {
        // GIVEN
        final String subject = "Fwd: Bulk submission (errors encountered)";
        final String body = findByNameAsString("nihmsemail.html", this.getClass());
        Session smtpSession = GreenMailUtil.getSession(greenMail.getImaps().getServerSetup());
        MimeMessage mimeMessage = new MimeMessage(smtpSession);
        mimeMessage.setRecipients(Message.RecipientType.TO, "testnihms@localhost");
        mimeMessage.setFrom("test-from@localhost");
        mimeMessage.setSubject(subject);

        BodyPart textBodyPart = new MimeBodyPart();
        textBodyPart.setContent("Test text content", "text/plain");
        BodyPart htmlBodyPart = new MimeBodyPart();
        htmlBodyPart.setContent(body, "text/html; charset=\"utf-8\"");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(textBodyPart);
        multipart.addBodyPart(htmlBodyPart);

        mimeMessage.setContent(multipart);
        mimeMessage.saveChanges();
        mockPassClientStreams();

        // WHEN
        nihmsReceiveMailService.handleReceivedMail(mimeMessage);

        // THEN
        verifyDepositUpdates();
    }

    @Test
    void testHandleReceivedMail_MessageParsing_HtmlNoClass() throws MessagingException, IOException {
        // GIVEN
        final String subject = "Bulk submission (errors encountered)";
        final String body = findByNameAsString("nihmsemail-noclass.html", this.getClass());
        Session smtpSession = GreenMailUtil.getSession(greenMail.getImaps().getServerSetup());
        MimeMessage mimeMessage = new MimeMessage(smtpSession);
        mimeMessage.setRecipients(Message.RecipientType.TO, "testnihms@localhost");
        mimeMessage.setFrom("test-from@localhost");
        mimeMessage.setSubject(subject);
        mimeMessage.setContent(body, "text/html; charset=\"utf-8\"");
        mimeMessage.saveChanges();
        mockPassClientStreams();

        // WHEN
        nihmsReceiveMailService.handleReceivedMail(mimeMessage);

        // THEN
        verifyDepositUpdates();
    }

    private void verifyDepositUpdates() throws IOException {
        verify(passClient, times(3)).updateObject(depositCaptor.capture());
        Deposit updatedDeposit1 = depositCaptor.getAllValues().stream().filter(deposit -> deposit.getId().equals("1"))
            .findFirst().get();
        assertEquals(DepositStatus.REJECTED, updatedDeposit1.getDepositStatus());
        assertEquals(CopyStatus.REJECTED, updatedDeposit1.getRepositoryCopy().getCopyStatus());
        assertEquals("Package ID=nihms-native-2017-07_2023-10-23_13-10-12_229935 failed because all " +
            "manuscripts from this journal should be submitted directly to PMC.", updatedDeposit1.getStatusMessage());
        assertNull(updatedDeposit1.getDepositStatusRef());
        Deposit updatedDeposit2 = depositCaptor.getAllValues().stream().filter(deposit -> deposit.getId().equals("2"))
            .findFirst().get();
        assertEquals(DepositStatus.REJECTED, updatedDeposit2.getDepositStatus());
        assertEquals(CopyStatus.REJECTED, updatedDeposit2.getRepositoryCopy().getCopyStatus());
        assertEquals("Package ID=nihms-native-2017-07_2023-10-23_13-10-38_229941 failed because all " +
            "manuscripts from this journal should be submitted directly to PMC.", updatedDeposit2.getStatusMessage());
        assertNull(updatedDeposit2.getDepositStatusRef());
        Deposit updatedDeposit3 = depositCaptor.getAllValues().stream().filter(deposit -> deposit.getId().equals("3"))
            .findFirst().get();
        assertEquals(DepositStatus.ACCEPTED, updatedDeposit3.getDepositStatus());
        assertEquals(CopyStatus.ACCEPTED, updatedDeposit3.getRepositoryCopy().getCopyStatus());
        assertNull(updatedDeposit3.getStatusMessage());
        assertEquals(NihmsReceiveMailService.NIHMS_DEP_STATUS_REF_PREFIX + "1502302",
            updatedDeposit3.getDepositStatusRef());
    }

    @Test
    void testHandleReceivedMail_UnknownMessagePattern() throws MessagingException, IOException {
        // GIVEN
        final String subject = "Bulk submission (errors encountered)";
        final String body = findByNameAsString("nihmsemail-unknown-message.html", this.getClass());
        Session smtpSession = GreenMailUtil.getSession(greenMail.getImaps().getServerSetup());
        MimeMessage mimeMessage = new MimeMessage(smtpSession);
        mimeMessage.setRecipients(Message.RecipientType.TO, "testnihms@localhost");
        mimeMessage.setFrom("test-from@localhost");
        mimeMessage.setSubject(subject);
        mimeMessage.setContent(body, "text/html; charset=\"utf-8\"");
        mimeMessage.saveChanges();
        mockPassClientStreams();

        // WHEN
        nihmsReceiveMailService.handleReceivedMail(mimeMessage);

        // THEN
        verifyNoInteractions(passClient);
    }

    @Test
    void testHandleReceivedMail_NoMessage() throws MessagingException, IOException {
        // GIVEN
        final String subject = "Bulk submission (errors encountered)";
        final String body = findByNameAsString("nihmsemail-no-messages.html", this.getClass());
        Session smtpSession = GreenMailUtil.getSession(greenMail.getImaps().getServerSetup());
        MimeMessage mimeMessage = new MimeMessage(smtpSession);
        mimeMessage.setRecipients(Message.RecipientType.TO, "testnihms@localhost");
        mimeMessage.setFrom("test-from@localhost");
        mimeMessage.setSubject(subject);
        mimeMessage.setContent(body, "text/html; charset=\"utf-8\"");
        mimeMessage.saveChanges();
        mockPassClientStreams();

        // WHEN
        nihmsReceiveMailService.handleReceivedMail(mimeMessage);

        // THEN
        verifyNoInteractions(passClient);
    }

    @Test
    void testHandleReceivedMail_SkipNonNihmsEmail() throws MessagingException, IOException {
        // GIVEN
        final String subject = "Bulk submission";
        Session smtpSession = GreenMailUtil.getSession(greenMail.getImaps().getServerSetup());
        MimeMessage mimeMessage = new MimeMessage(smtpSession);
        mimeMessage.setRecipients(Message.RecipientType.TO, "testnihms@localhost");
        mimeMessage.setFrom("someotherfrom@localhost");
        mimeMessage.setSubject(subject);
        mimeMessage.setContent("testing content", "text/html; charset=\"utf-8\"");
        mimeMessage.saveChanges();
        mockPassClientStreams();

        // WHEN
        nihmsReceiveMailService.handleReceivedMail(mimeMessage);

        // THEN
        verifyNoInteractions(passClient);
    }

    @Test
    void testHandleReceivedMail_SkipNonNihmsSubject() throws MessagingException, IOException {
        // GIVEN
        final String subject = "Nihms Update";
        Session smtpSession = GreenMailUtil.getSession(greenMail.getImaps().getServerSetup());
        MimeMessage mimeMessage = new MimeMessage(smtpSession);
        mimeMessage.setRecipients(Message.RecipientType.TO, "testnihms@localhost");
        mimeMessage.setFrom("test-from@localhost");
        mimeMessage.setSubject(subject);
        mimeMessage.setContent("testing content", "text/html; charset=\"utf-8\"");
        mimeMessage.saveChanges();
        mockPassClientStreams();

        // WHEN
        nihmsReceiveMailService.handleReceivedMail(mimeMessage);

        // THEN
        verifyNoInteractions(passClient);
    }

    @Test
    void testHandleReceivedMail_SkipNonNihmsSubjectAndEmail() throws MessagingException, IOException {
        // GIVEN
        final String subject = "Nihms Update";
        Session smtpSession = GreenMailUtil.getSession(greenMail.getImaps().getServerSetup());
        MimeMessage mimeMessage = new MimeMessage(smtpSession);
        mimeMessage.setRecipients(Message.RecipientType.TO, "testnihms@localhost");
        mimeMessage.setFrom("someotherfrom@localhost");
        mimeMessage.setSubject(subject);
        mimeMessage.setContent("testing content", "text/html; charset=\"utf-8\"");
        mimeMessage.saveChanges();
        mockPassClientStreams();

        // WHEN
        nihmsReceiveMailService.handleReceivedMail(mimeMessage);

        // THEN
        verifyNoInteractions(passClient);
    }

    private void mockPassClientStreams() throws IOException {
        Deposit deposit1 = new Deposit();
        deposit1.setId("1");
        deposit1.setDepositStatus(DepositStatus.SUBMITTED);
        deposit1.setStatusMessage("init-submitted");
        deposit1.setRepositoryCopy(new RepositoryCopy());
        Deposit deposit2 = new Deposit();
        deposit2.setId("2");
        deposit2.setDepositStatus(DepositStatus.SUBMITTED);
        deposit2.setStatusMessage("init-submitted");
        deposit2.setRepositoryCopy(new RepositoryCopy());
        Deposit deposit3 = new Deposit();
        deposit3.setId("3");
        deposit3.setDepositStatus(DepositStatus.SUBMITTED);
        deposit3.setStatusMessage("init-submitted");
        deposit3.setRepositoryCopy(new RepositoryCopy());
        when(passClient.streamObjects(any())).thenAnswer(input -> {
            PassClientSelector<Deposit> selector = input.getArgument(0);
            if (selector.getFilter().contains("submission.id=='229935'")) {
                return Stream.of(deposit1);
            }
            if (selector.getFilter().contains("submission.id=='229941'")) {
                return Stream.of(deposit2);
            }
            if (selector.getFilter().contains("submission.id=='229947'")) {
                return Stream.of(deposit3);
            }
            throw new RuntimeException("Fail test, should not happen");
        });
    }
}
