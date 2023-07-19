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
package org.eclipse.pass.notification.service;

import static org.apache.commons.io.IOUtils.resourceToString;
import static org.eclipse.pass.notification.model.Link.SUBMISSION_REVIEW_INVITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.pass.notification.AbstractNotificationSpringIntegrationTest;
import org.eclipse.pass.notification.model.Link;
import org.eclipse.pass.notification.model.SubmissionEventMessage;
import org.eclipse.pass.notification.util.PathUtil;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.EventType;
import org.eclipse.pass.support.client.model.PerformerRole;
import org.eclipse.pass.support.client.model.Source;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.client.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@TestPropertySource(properties = {
    "pass.client.url=http://localhost:8080",
    "pass.client.user=backend",
    "pass.client.password=backend"
})
@Testcontainers
@DirtiesContext
public class NotificationServiceIT extends AbstractNotificationSpringIntegrationTest {
    private static final String SENDER = "demo-pass@mail.local.domain";
    private static final String RECIPIENT = "staffWithNoGrants@jhu.edu";
    private static final String CC = "notification-demo-cc@jhu.edu";

    static {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader("pom.xml"));
            String version = model.getParent().getVersion();
            PASS_CORE_IMG = DockerImageName.parse("ghcr.io/eclipse-pass/pass-core-main:" + version);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final DockerImageName PASS_CORE_IMG;

    @Container
    static final GenericContainer<?> PASS_CORE_CONTAINER = new GenericContainer<>(PASS_CORE_IMG)
        .withEnv("PASS_CORE_BASE_URL", "http://localhost:8080")
        .withEnv("PASS_CORE_BACKEND_USER", "backend")
        .withEnv("PASS_CORE_BACKEND_PASSWORD", "backend")
        .waitingFor(Wait.forHttp("/data/grant").forStatusCode(401))
        .withExposedPorts(8080);

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

    @Autowired private PassClient passClient;
    @Autowired private NotificationService notificationService;

    @DynamicPropertySource
    static void updateProperties(DynamicPropertyRegistry registry) {
        registry.add("pass.client.url",
            () -> "http://localhost:" + PASS_CORE_CONTAINER.getMappedPort(8080));
    }

    @Test
    void testNotify() throws Exception {
        // GIVEN
        final String expectedBody = "Dear staffWithNoGrants@jhu.edu\r\n\r\nA submission titled \"Specific protein " +
            "supplementation using soya, casein or whey differentially affects regional gut growth and luminal " +
            "growth factor bioactivity in rats; implications for the treatment of gut injury and stimulating " +
            "repair\" has been prepared on your behalf by demo-pass@mail.local.domain with comment \"How " +
            "does this submission look?\"\r\n\r\n\r\nPlease review the submission at the following URL: " +
            "http://example.org/user-token-test\r\n\r\nA test inline footer";

        SubmissionEvent submissionEvent = stagePassData();

        SubmissionEventMessage submissionEventMessage = new SubmissionEventMessage();
        submissionEventMessage.setSubmissionEventId(submissionEvent.getId());
        submissionEventMessage.setUserApprovalLink(URI.create("http://example.org/user-token-test"));

        notificationService.notify(submissionEventMessage);

        List<MimeMessage> receivedMessages = Arrays.asList(greenMail.getReceivedMessages());
        // 3 = 1 To + 1 CC + 1 BCC
        assertEquals(3, receivedMessages.size());

        MimeMessage message = receivedMessages.get(0);
        assertTrue(message.getSubject().contains("PASS Submission Approval: Specific protein"));
        assertEquals(SENDER, message.getFrom()[0].toString());
        assertEquals(CC, message.getRecipients(MimeMessage.RecipientType.CC)[0].toString());
        assertEquals(RECIPIENT, message.getRecipients(MimeMessage.RecipientType.TO)[0].toString());
        assertEquals(expectedBody,  message.getContent().toString());
    }

    private SubmissionEvent stagePassData() throws IOException {
        // This User prepares the submission on behalf of the Submission.submitter
        // Confusingly, this User has the ability to submit to PASS.  The authorization-related role of
        // User.Role.SUBMITTER should not be confused with the the logical role as a preparer of a submission.
        User preparer = new User();
        preparer.setEmail("emetsger@gmail.com");
        preparer.setDisplayName("Submission Preparer");
        preparer.setFirstName("Pre");
        preparer.setLastName("Parer");
        preparer.setRoles(List.of(UserRole.SUBMITTER));

        passClient.createObject(preparer);

        // The Submission as prepared by the preparer.
        // The preparer did not find the authorized submitter in PASS, so they filled in the email address of the
        // authorized submitter. Therefore, the Submission.submitter field will be null (because that *must* be a URI
        // to a User resource, and the User does not exist). The Submission.submitterEmail will be set to the email
        // address of the authorized submitter
        Submission submission = new Submission();
        submission.setMetadata(resourceToString("/" + PathUtil.packageAsPath(this.getClass()) +
            "/submission-metadata.json", StandardCharsets.UTF_8));
        submission.setPreparers(List.of(preparer));
        submission.setSource(Source.PASS);
        submission.setSubmitter(null);
        submission.setSubmitterEmail(URI.create("mailto:" + RECIPIENT));

        passClient.createObject(submission);

        // When this event is processed, the authorized submitter will recieve an email notification with a link that
        // will invite them to use PASS, and link the Submission to their newly created User (created when they login
        // to PASS for the first time)
        SubmissionEvent event = new SubmissionEvent();
        event.setSubmission(submission);
        event.setPerformerRole(PerformerRole.PREPARER);
        event.setPerformedBy(preparer);
        String comment = "How does this submission look?";
        event.setComment(comment);
        event.setEventType(EventType.APPROVAL_REQUESTED_NEWUSER);
        event.setPerformedDate(ZonedDateTime.now());

        String submissionId = submission.getId();
        Link link = new Link(URI.create(submissionId
            .replace("http://localhost", "https://pass.local")), SUBMISSION_REVIEW_INVITE);
        event.setLink(link.getHref());

        passClient.createObject(event);

        return event;
    }
}
