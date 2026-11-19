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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.notification.config.NotificationConfig;
import org.eclipse.pass.notification.config.NotificationTemplate;
import org.eclipse.pass.notification.config.NotificationTemplateName;
import org.eclipse.pass.notification.dispatch.DispatchException;
import org.eclipse.pass.notification.model.Notification;
import org.eclipse.pass.notification.model.NotificationParam;
import org.eclipse.pass.notification.model.NotificationType;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.json.JsonMapper;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class EmailDispatchImplMockTest {
    private final static Collection<String> CC = List.of("pass-prod-cc@jhu.edu", "pass-admin@jhu.edu");
    private final static String CC_JOINED = "pass-prod-cc@jhu.edu,pass-admin@jhu.edu";
    private final static String USER_EMAIL = "user@bar.com";
    private final static String FROM = "pass-noreply@jhu.edu";
    private final static String METADATA =
        "{\n" +
        "  \"a sample metadata\": \"blob\",\n" +
        "  \"for\": \"a Submission\"\n" +
        "}\n";

    private Notification notification;
    private NotificationTemplate templateProto;
    private CompositeResolver templateResolver;
    private JavaMailSender mailer;

    private EmailDispatchImpl emailDispatch;

    @BeforeEach
    public void setUp() throws Exception {
        notification = mock(Notification.class);
        NotificationConfig config = mock(NotificationConfig.class);
        templateProto = mock(NotificationTemplate.class);
        templateResolver = mock(CompositeResolver.class);
        User user = mock(User.class);
        mailer = mock(JavaMailSender.class);

        when(config.getTemplates()).thenReturn(Collections.singletonList(templateProto));
        when(templateProto.getTemplates()).thenReturn(new HashMap<>() {
            {
                put(NotificationTemplateName.SUBJECT, "A Subject");
                put(NotificationTemplateName.BODY, "A Body");
                put(NotificationTemplateName.FOOTER, "A Footer");
            }
        });
        when(user.getId()).thenReturn("test-user");
        when(user.getEmail()).thenReturn(USER_EMAIL);

        Parameterizer parameterizer = new Parameterizer(config, templateResolver,
            new HandlebarsParameterizer(JsonMapper.builder().build()));

        // mock a whitelist that accepts all recipients by simply returning the collection of recipients it was provided
        SimpleWhitelist whitelist = mock(SimpleWhitelist.class);
        when(whitelist.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        EmailComposer composer = new EmailComposer(whitelist, mailer);

        emailDispatch = new EmailDispatchImpl(parameterizer, composer, mailer);
    }

    @Test
    public void testSimpleSuccess() throws IOException, MessagingException {
        // GIVEN
        when(notification.getType()).thenReturn(NotificationType.SUBMISSION_APPROVAL_INVITE);
        when(notification.getParameters()).thenReturn(
                new HashMap<>() {
                    {
                        put(NotificationParam.FROM, FROM);
                        put(NotificationParam.TO, USER_EMAIL);
                        put(NotificationParam.CC, CC_JOINED);
                        put(NotificationParam.RESOURCE_METADATA, METADATA);
                    }
                });
        when(notification.getRecipients()).thenReturn(Collections.singleton(USER_EMAIL));
        when(notification.getSender()).thenReturn(FROM);
        when(notification.getCc()).thenReturn(CC);

        when(templateProto.getNotificationType()).thenReturn(NotificationType.SUBMISSION_APPROVAL_INVITE);
        when(templateResolver.resolve(any(), any())).thenAnswer(inv ->
                IOUtils.toInputStream(inv.getArgument(1), "UTF-8"));

        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailer.createMimeMessage()).thenReturn(mimeMessage);

        // WHEN
        emailDispatch.dispatch(notification);

        // THEN
        ArgumentCaptor<MimeMessage> emailCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailer).send(emailCaptor.capture());
        MimeMessage email = emailCaptor.getValue();

        // Verify the email was composed properly
        assertEquals(FROM, email.getFrom()[0].toString());

        assertTrue(Arrays.stream(email.getRecipients(MimeMessage.RecipientType.TO))
            .anyMatch(recipient -> recipient.toString().equals(USER_EMAIL)));

        Arrays.stream(email.getRecipients(MimeMessage.RecipientType.CC))
            .forEach(address ->
                    assertTrue(CC.contains(address.toString())));

        assertEquals("A Subject", email.getSubject());
        assertEquals(String.join("\n\n", "A Body", "A Footer"), email.getContent());
    }

    /**
     * A nice DispatchException should be thrown if the To field of the email is missing or empty
     */
    @Test
    public void testEmptyToAddress() throws MessagingException {
        // GIVEN
        Notification notification = mock(Notification.class);
        Parameterizer parameterizer = mock(Parameterizer.class);
        EmailComposer composer = mock(EmailComposer.class);
        MimeMessage emailMessage = mock(MimeMessage.class);

        when(notification.getResourceId()).thenReturn(UUID.randomUUID().toString());
        when(notification.getEventId()).thenReturn(UUID.randomUUID().toString());

        when(parameterizer.resolveAndParameterize(any(), any())).thenReturn(Collections.emptyMap());
        when(composer.compose(any(), any())).thenReturn(emailMessage);
        when(emailMessage.getRecipients(any())).thenReturn(new Address[0]);

        // WHEN/THEN
        DispatchException ex = assertThrows(DispatchException.class, () -> {
            emailDispatch = new EmailDispatchImpl(parameterizer, composer, mailer);
            emailDispatch.dispatch(notification);
        });

        assertTrue(ex.getMessage().contains("dispatch email with an empty To: address"));
        verifyNoInteractions(mailer);
    }

}