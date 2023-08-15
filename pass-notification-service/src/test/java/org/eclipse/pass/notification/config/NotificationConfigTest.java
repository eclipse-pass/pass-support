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
package org.eclipse.pass.notification.config;

import static org.eclipse.pass.notification.config.Mode.DEMO;
import static org.eclipse.pass.notification.config.Mode.PRODUCTION;
import static org.eclipse.pass.notification.config.NotificationTemplateName.BODY;
import static org.eclipse.pass.notification.config.NotificationTemplateName.FOOTER;
import static org.eclipse.pass.notification.config.NotificationTemplateName.SUBJECT;
import static org.eclipse.pass.notification.model.NotificationType.SUBMISSION_APPROVAL_INVITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.pass.notification.AbstractNotificationSpringTest;
import org.eclipse.pass.notification.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NotificationConfigTest extends AbstractNotificationSpringTest {

    @Autowired private NotificationConfig notificationConfig;
    @Autowired private RecipientConfig recipientConfig;

    @Test
    void testLoadNotificationConfig() {
        assertEquals(DEMO, notificationConfig.getMode());
        assertEquals(DEMO, recipientConfig.getMode());
        assertEquals(2, notificationConfig.getRecipientConfigs().size());
        notificationConfig.getRecipientConfigs()
                .stream().filter(rc -> rc.getMode() == PRODUCTION).findAny()
                .orElseThrow(() -> new RuntimeException("Missing RecipientConfig for mode PRODUCTION."));
        notificationConfig.getRecipientConfigs()
                .stream().filter(rc -> rc.getMode() == DEMO).findAny()
                .orElseThrow(() -> new RuntimeException("Missing RecipientConfig for mode DEMO."));
        assertEquals(1, notificationConfig.getTemplates().size());
        notificationConfig.getTemplates()
                .stream().filter(tc -> tc.getNotificationType() == SUBMISSION_APPROVAL_INVITE).findAny()
                .orElseThrow(() -> new RuntimeException(
                    "Missing NotificationTemplate for type SUBMISSION_APPROVAL_INVITE"));

        NotificationTemplate template = new ArrayList<>(notificationConfig.getTemplates()).get(0);
        assertEquals(NotificationType.SUBMISSION_APPROVAL_INVITE, template.getNotificationType());
        assertEquals(3, template.getTemplates().size());
        assertEquals("PASS Submission Approval: {{abbreviate resource_metadata.title 50}}",
            template.getTemplates().get(SUBJECT));
        assertEquals("classpath:/templates/pass-body-submission-approval-invite-template.hbr",
            template.getTemplates().get(BODY));
        assertEquals("A test inline footer", template.getTemplates().get(FOOTER));

        RecipientConfig recipientConfigDemo = notificationConfig.getRecipientConfigs().stream()
            .filter(recipientConfig -> DEMO == recipientConfig.getMode())
                .findFirst().get();

        assertEquals(1, recipientConfigDemo.getGlobalCc().size());
        assertEquals(4, recipientConfigDemo.getWhitelist().size());
        assertNull(recipientConfigDemo.getGlobalBcc());
        assertTrue(recipientConfigDemo.getGlobalCc().contains("demo@pass.jhu.edu"));
        assertTrue(recipientConfigDemo.getWhitelist().contains("emetsger@jhu.edu"));
        assertTrue(recipientConfigDemo.getWhitelist().contains("hvu@jhu.edu"));
        assertTrue(recipientConfigDemo.getWhitelist().contains("apb@jhu.edu"));
        assertTrue(recipientConfigDemo.getWhitelist().contains("khanson@jhu.edu"));

        Collection<LinkValidationRule> linkValidationRules = notificationConfig.getLinkValidatorConfig();
        assertEquals(1, linkValidationRules.size());
        LinkValidationRule linkValidationRule = linkValidationRules.iterator().next();
        assertEquals("https://testing-host", linkValidationRule.getRequiredBaseURI());
    }

}