/*
 * Copyright 2018 Johns Hopkins University
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
package org.eclipse.pass.notification.dispatch.email;

import static org.apache.commons.io.IOUtils.resourceToString;
import static org.eclipse.pass.notification.model.Link.SUBMISSION_REVIEW_INVITE;
import static org.eclipse.pass.notification.model.NotificationParam.EVENT_METADATA;
import static org.eclipse.pass.notification.model.NotificationParam.FROM;
import static org.eclipse.pass.notification.model.NotificationParam.LINKS;
import static org.eclipse.pass.notification.model.NotificationParam.RESOURCE_METADATA;
import static org.eclipse.pass.notification.model.NotificationParam.TO;
import static org.eclipse.pass.notification.model.NotificationType.SUBMISSION_APPROVAL_INVITE;
import static org.eclipse.pass.notification.service.LinksUtil.serialized;
import static org.eclipse.pass.notification.util.PathUtil.packageAsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.pass.notification.AbstractNotificationSpringTest;
import org.eclipse.pass.notification.config.NotificationConfig;
import org.eclipse.pass.notification.config.NotificationTemplate;
import org.eclipse.pass.notification.config.NotificationTemplateName;
import org.eclipse.pass.notification.config.RecipientConfig;
import org.eclipse.pass.notification.dispatch.DispatchException;
import org.eclipse.pass.notification.model.Link;
import org.eclipse.pass.notification.model.Notification;
import org.eclipse.pass.notification.service.ComposerMockTest;
import org.eclipse.pass.notification.service.JsonMetadataBuilder;
import org.eclipse.pass.support.client.model.EventType;
import org.eclipse.pass.support.client.model.PerformerRole;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@TestPropertySource(properties = {
    "pass.notification.mode=DEMO",
    "pass.notification.configuration=classpath:notification.json"
})
public class EmailDispatchImplTest extends AbstractNotificationSpringTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

    private static final String SENDER = "staffWithGrants@jhu.edu";
    private static final String RECIPIENT = "staffWithNoGrants@jhu.edu";
    private static final String CC = "facultyWithGrants@jhu.edu";
    private static final String BCC = "notification-demo-bcc@jhu.edu";
    private static final String GLOBAL_DEMO_CC_ADDRESS = "notification-demo-cc@jhu.edu";
    private static final String SUBMISSION_RESOURCE_ID = "test-submission-id";
    private static final String EVENT_RESOURCE_ID = "test-event-id";

    @Autowired private EmailDispatchImpl emailDispatch;
    @Autowired private NotificationConfig config;
    @Autowired private RecipientConfig recipientConfig;
    @Autowired private ObjectMapper objectMapper;

    /**
     * Simple test insuring the basic parts of the dispatch email are where they belong.
     */
    @Test
    public void testSimpleSuccess() throws Exception {
        // GIVEN
        String expectedBody = "Approval Invite Body\r\n\r\nApproval Invite Footer";
        Notification n = new Notification();
        n.setType(SUBMISSION_APPROVAL_INVITE);
        n.setSender(SENDER);
        n.setCc(List.of(CC));
        n.setRecipients(List.of(RECIPIENT));
        n.setResourceId(SUBMISSION_RESOURCE_ID);
        n.setEventId(EVENT_RESOURCE_ID);

        // WHEN
        emailDispatch.dispatch(n);

        // THEN
        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];
        assertNotNull(receivedMessage);

        assertEquals("Approval Invite Subject", receivedMessage.getSubject());
        assertEquals(expectedBody, receivedMessage.getContent().toString());
        assertEquals(SENDER, receivedMessage.getFrom()[0].toString());
        assertEquals(CC, receivedMessage.getRecipients(MimeMessage.RecipientType.CC)[0].toString());
        assertEquals(RECIPIENT, receivedMessage.getRecipients(MimeMessage.RecipientType.TO)[0].toString());
    }

    /**
     * Simple test insuring that BCC users receive their notification
     */
    @Test
    public void testSimpleBccSuccess() throws Exception {
        // GIVEN
        String expectedBody = "Approval Invite Body\r\n\r\nApproval Invite Footer";
        Notification n = new Notification();
        n.setType(SUBMISSION_APPROVAL_INVITE);
        n.setSender(SENDER);
        n.setBcc(List.of(BCC));
        n.setRecipients(List.of(RECIPIENT));
        n.setResourceId(SUBMISSION_RESOURCE_ID);
        n.setEventId(EVENT_RESOURCE_ID);

        // WHEN
        emailDispatch.dispatch(n);

        // THEN
        greenMail.setUser(RECIPIENT, RECIPIENT, "secret-pwd");
        greenMail.setUser(BCC, BCC, "secret-pwd");

        // Get the message from TO staffWithNoGrants@jhu.edu inbox
        IMAPStore imapStore = greenMail.getImap().createStore();
        imapStore.connect(RECIPIENT, "secret-pwd");
        Folder inbox = imapStore.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
        Message receivedMessage = inbox.getMessage(1);
        assertNotNull(receivedMessage);

        assertEquals("Approval Invite Subject", receivedMessage.getSubject());
        assertEquals(expectedBody, receivedMessage.getContent());
        assertEquals(SENDER, receivedMessage.getFrom()[0].toString());
        assertEquals(RECIPIENT, receivedMessage.getRecipients(Message.RecipientType.TO)[0].toString());
        assertNull(receivedMessage.getRecipients(Message.RecipientType.CC));
        assertNull(receivedMessage.getRecipients(Message.RecipientType.BCC));

        // Get the message from BCC notification-demo-bcc@jhu.edu inbox
        IMAPStore imapStoreBcc = greenMail.getImap().createStore();
        imapStoreBcc.connect(BCC, "secret-pwd");
        Folder inboxBcc = imapStoreBcc.getFolder("INBOX");
        inboxBcc.open(Folder.READ_ONLY);
        Message receivedMessageBcc = inboxBcc.getMessage(1);
        assertNotNull(receivedMessageBcc);
        assertEquals("Approval Invite Subject", receivedMessageBcc.getSubject());
        assertEquals(expectedBody, receivedMessageBcc.getContent());
        assertEquals(SENDER, receivedMessageBcc.getFrom()[0].toString());
        assertEquals(RECIPIENT, receivedMessageBcc.getRecipients(Message.RecipientType.TO)[0].toString());
    }

    /**
     * References to subject/body/footer templates should be resolved
     */
    @Test
    @DirtiesContext
    public void testNotificationConfigWithTemplateRefs() throws Exception {
        // GIVEN
        // Override the NotificationTemplate for approval invites, subbing in Spring URIs as references
        // to template bodies
        NotificationTemplate template = config.getTemplates().stream()
                .filter(templatePrototype ->
                        templatePrototype.getNotificationType() == SUBMISSION_APPROVAL_INVITE)
                .findAny()
                .orElseThrow(() -> new RuntimeException("Missing expected template for SUBMISSION_APPROVAL_INVITE"));

        template.setTemplates(new HashMap<>() {
            {
                put(NotificationTemplateName.SUBJECT, "classpath:" + packageAsPath(EmailDispatchImplTest.class) +
                    "/subject.hbr");
                put(NotificationTemplateName.BODY, "classpath:" + packageAsPath(EmailDispatchImplTest.class) +
                    "/body.hbr");
                put(NotificationTemplateName.FOOTER, "classpath:" + packageAsPath(EmailDispatchImplTest.class) +
                    "/footer.hbr");
            }
        });
        config.setTemplates(List.of(template));

        Notification n = new Notification();
        n.setType(SUBMISSION_APPROVAL_INVITE);
        n.setSender(SENDER);
        n.setResourceId(SUBMISSION_RESOURCE_ID);
        n.setEventId(EVENT_RESOURCE_ID);
        n.setRecipients(List.of(RECIPIENT));

        // WHEN
        emailDispatch.dispatch(n);

        // THEN
        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];
        assertNotNull(receivedMessage);

        assertEquals("Handlebars Subject", receivedMessage.getSubject());
        assertEquals("Handlebars Body\r\n\r\nHandlebars Footer", receivedMessage.getContent());
    }

    @Test
    @DirtiesContext
    public void testSubjectTemplateParameterization() throws Exception {
        // GIVEN
        Submission submission = new Submission();
        submission.setMetadata(resourceToString("/" + packageAsPath(ComposerMockTest.class) +
                                                "/submission-metadata.json", StandardCharsets.UTF_8));
        submission.setId(SUBMISSION_RESOURCE_ID);

        SubmissionEvent event = new SubmissionEvent();
        event.setId(EVENT_RESOURCE_ID);
        event.setPerformerRole(PerformerRole.PREPARER);
        User preparer = new User("test-user-id");
        preparer.setEmail("test-user-prep@test");
        event.setPerformedBy(preparer);
        event.setComment("How does this submission look?");
        event.setEventType(EventType.APPROVAL_REQUESTED_NEWUSER);
        event.setPerformedDate(ZonedDateTime.now());
        event.setSubmission(submission);

        // Override the NotificationTemplate for approval invites, including a template that
        // requires parameterization
        NotificationTemplate template = config.getTemplates().stream()
                .filter(templatePrototype ->
                        templatePrototype.getNotificationType() == SUBMISSION_APPROVAL_INVITE)
                .findAny()
                .orElseThrow(() -> new RuntimeException("Missing expected template for SUBMISSION_APPROVAL_INVITE"));

        template.setTemplates(new HashMap<>() {
            {
                put(NotificationTemplateName.SUBJECT, "classpath:" + packageAsPath(EmailDispatchImplTest.class) +
                    "/subject-parameterize.hbr");
                put(NotificationTemplateName.BODY, "classpath:" + packageAsPath(EmailDispatchImplTest.class) +
                    "/body-parameterize.hbr");
                put(NotificationTemplateName.FOOTER, "Footer");
            }
        });

        config.setTemplates(List.of(template));
        Link link = new Link(URI.create("http://example.org/email/dispatch/myLink"), SUBMISSION_REVIEW_INVITE);

        Notification n = new Notification();
        n.setType(SUBMISSION_APPROVAL_INVITE);
        n.setSender(SENDER);
        n.setRecipients(List.of(RECIPIENT));
        n.setEventId(event.getId());
        n.setResourceId(submission.getId());
        n.setParameters(new HashMap<>() {
            {
                put(RESOURCE_METADATA, JsonMetadataBuilder.resourceMetadata(submission, objectMapper));
                put(EVENT_METADATA, JsonMetadataBuilder.eventMetadata(event, objectMapper));
                put(FROM, SENDER);
                put(TO, RECIPIENT);
                put(LINKS, Stream.of(link).collect(serialized()));
            }
        });

        // WHEN
        emailDispatch.dispatch(n);

        // THEN
        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];
        assertNotNull(receivedMessage);

        String expectedTitle = objectMapper.readTree(submission.getMetadata()).findValue("title").asText();
        String expectedSubject = "PASS Submission titled \"" + expectedTitle + "\" awaiting your approval";
        assertEquals(expectedSubject, receivedMessage.getSubject());

        String body = receivedMessage.getContent().toString();

        assertTrue(body.contains("Dear " + n.getParameters().get(TO)));
        // todo: FROM will be the global FROM, must insure the preparer User is represented in metadata.
        assertTrue(body.contains("prepared on your behalf by " + n.getParameters().get(FROM)));
        assertTrue(body.contains(event.getComment()));
        assertTrue(body.contains(expectedTitle));
        assertTrue(body.contains("Please review the submission at the following URL: " + link.getHref()));
    }

    /**
     * mailing a non-existent email address should result in the appropriate exception
     * (in coordination with a like-minded email relay)
     */
    @Test
    @DirtiesContext
    public void testInvalidEmailAddress() {
        // GIVEN
        recipientConfig.setWhitelist(Collections.emptyList());
        String nonExistentRecipientAddress = "./moo-thru@bar.edu";
        Notification n = new Notification();
        n.setType(SUBMISSION_APPROVAL_INVITE);
        n.setSender(SENDER);
        n.setResourceId(SUBMISSION_RESOURCE_ID);
        n.setEventId(EVENT_RESOURCE_ID);
        n.setRecipients(List.of(nonExistentRecipientAddress));

        // WHEN/THEN
        DispatchException ex = assertThrows(DispatchException.class, () -> {
            emailDispatch.dispatch(n);
        });

        assertTrue(ex.getCause().getMessage().contains("Local address starts with dot"));
    }

    /**
     * When dispatching a Notification with a non-empty whitelist, only those whitelisted recipients should be present
     * on the email that is sent
     */
    @Test
    public void testWhitelistFilter() throws Exception {
        // GIVEN
        String unlistedRecipient = "facultyWithNoGrants@jhu.edu";
        Notification n = new Notification();
        n.setType(SUBMISSION_APPROVAL_INVITE);
        n.setSender(SENDER);
        n.setResourceId(SUBMISSION_RESOURCE_ID);
        n.setEventId(EVENT_RESOURCE_ID);
        n.setCc(List.of(CC));
        n.setRecipients(List.of(RECIPIENT, unlistedRecipient));

        // WHEN
        emailDispatch.dispatch(n);

        // THEN
        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];
        assertNotNull(receivedMessage);

        // Only the whitelisted recipient should be present
        assertEquals(1, receivedMessage.getRecipients(Message.RecipientType.TO).length);
        assertEquals(RECIPIENT, receivedMessage.getRecipients(Message.RecipientType.TO)[0].toString());
    }

    /**
     * When composing a Notification, the global CC addresses should not be filtered by the whitelist, while the direct
     * recipients are.
     */
    @Test
    @DirtiesContext
    public void testGlobalCCUnaffectedByWhitelist() throws Exception {
        // GIVEN
        // Configure the whitelist such that the submitter's address will
        // *not* be whitelisted
        String whitelistEmail = RECIPIENT;
        recipientConfig.setWhitelist(List.of(whitelistEmail));

        Notification n = new Notification();
        n.setType(SUBMISSION_APPROVAL_INVITE);
        n.setSender(SENDER);
        n.setResourceId(SUBMISSION_RESOURCE_ID);
        n.setEventId(EVENT_RESOURCE_ID);
        n.setRecipients(List.of("facultyWithNoGrants@jhu.edu", whitelistEmail));
        n.setCc(List.of(GLOBAL_DEMO_CC_ADDRESS));

        // WHEN
        emailDispatch.dispatch(n);

        // THEN
        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];
        assertNotNull(receivedMessage);

        // The recipient list doesn't contain facultyWithNoGrants@jhu.edu because it isn't whitelisted
        assertEquals(1, receivedMessage.getRecipients(Message.RecipientType.TO).length);
        assertEquals(whitelistEmail, receivedMessage.getRecipients(Message.RecipientType.TO)[0].toString());

        // The cc list does contain the expected address, because the global cc is not filtered through the whitelist
        // at all
        assertEquals(1, receivedMessage.getRecipients(Message.RecipientType.CC).length);
        assertEquals(GLOBAL_DEMO_CC_ADDRESS, receivedMessage.getRecipients(Message.RecipientType.CC)[0].toString());
    }

    /**
     * When composing a Notification with an empty whitelist, every recipient should be present.
     */
    @Test
    @DirtiesContext
    public void testEmptyWhitelist() throws Exception {
        // GIVEN
        recipientConfig.setWhitelist(Collections.emptyList());
        String secondRecipient = "facultyWithNoGrants@jhu.edu";
        Notification n = new Notification();
        n.setType(SUBMISSION_APPROVAL_INVITE);
        n.setSender(SENDER);
        n.setResourceId(SUBMISSION_RESOURCE_ID);
        n.setEventId(EVENT_RESOURCE_ID);
        n.setCc(List.of(CC, GLOBAL_DEMO_CC_ADDRESS));
        n.setRecipients(List.of(RECIPIENT, secondRecipient));

        // WHEN
        emailDispatch.dispatch(n);

        // THEN
        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];
        assertNotNull(receivedMessage);

        Set<String> actualRecipients = Arrays.stream(receivedMessage.getAllRecipients())
                                                    .map(Object::toString)
                                                    .collect(Collectors.toSet());
        assertTrue(actualRecipients.contains(RECIPIENT));
        assertTrue(actualRecipients.contains(secondRecipient));
        assertTrue(actualRecipients.contains(CC));
        assertTrue(actualRecipients.contains(GLOBAL_DEMO_CC_ADDRESS));
        assertEquals(4, actualRecipients.size());
    }

}
