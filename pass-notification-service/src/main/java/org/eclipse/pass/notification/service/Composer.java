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
package org.eclipse.pass.notification.service;

import static java.lang.String.join;
import static org.eclipse.pass.notification.service.LinksUtil.concat;
import static org.eclipse.pass.notification.service.LinksUtil.serialized;

import java.util.Collection;
import java.util.HashMap;
import java.util.function.BiFunction;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.pass.notification.config.RecipientConfig;
import org.eclipse.pass.notification.model.Notification;
import org.eclipse.pass.notification.model.NotificationParam;
import org.eclipse.pass.notification.model.NotificationType;
import org.eclipse.pass.notification.model.SubmissionEventMessage;
import org.eclipse.pass.support.client.model.EventType;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Composes a {@link Notification} from a {@link SubmissionEvent} and its corresponding {@link Submission}, according
 * to a {@link RecipientConfig}.  Responsible for determining the type of notification, and who the recipients and
 * sender of the notification are.  It is also responsible for populating other parameters of the notification such as
 * resource metadata, link metadata, or event metadata.
 * <p>
 * The implementation applies a whitelist to the recipients of the notification according to the recipient
 * configuration.  If the recipient configuration has a null or empty whitelist, that means that *all* recipients are
 * whitelisted (each recipient will receive the notification).  If the recipient configuration has a non-empty
 * whitelist, then only those users specified in the whitelist will receive a notification.  (N.B. the global CC field
 * of the recipient configuration is not run through the whitelist).
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see RecipientConfig
 * @see NotificationType
 * @see NotificationParam
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class Composer implements BiFunction<SubmissionEvent, SubmissionEventMessage, Notification> {

    private final RecipientConfig recipientConfig;
    private final RecipientAnalyzer recipientAnalyzer;
    private final SubmissionLinkAnalyzer submissionLinkAnalyzer;
    private final LinkValidator linkValidator;
    private final ObjectMapper mapper;

    @Value("${pass.app.domain}")
    private String passAppDomain;

    /**
     * Composes a {@code Notification} from a {@code Submission} and {@code SubmissionEvent}.
     *
     * @param event the submission event
     * @param submissionEventMessage the submission event message
     * @return the composed Notification
     */
    @Override
    public Notification apply(SubmissionEvent event, SubmissionEventMessage submissionEventMessage) {
        Submission submission = event.getSubmission();
        if (!event.getSubmission().getId().equals(submission.getId())) {
            // todo: exception?
            log.warn("Composing a Notification for tuple [{},{}] but {} references a different Submission: {}.",
                    submission.getId(), event.getId(), event.getId(), event.getSubmission());
        }

        Notification notification = new Notification();
        HashMap<NotificationParam, String> params = new HashMap<>();
        notification.setParameters(params);

        notification.setEventId(event.getId());
        params.put(NotificationParam.EVENT_METADATA, JsonMetadataBuilder.eventMetadata(event, mapper));

        Collection<String> cc = recipientConfig.getGlobalCc();
        if (cc != null && !cc.isEmpty()) {
            notification.setCc(cc);
            params.put(NotificationParam.CC, join(",", cc));
        }

        Collection<String> bcc = recipientConfig.getGlobalBcc();
        if (bcc != null && !bcc.isEmpty()) {
            notification.setBcc(bcc);
            params.put(NotificationParam.BCC, join(",", bcc));
        }

        notification.setResourceId(submission.getId());
        params.put(NotificationParam.RESOURCE_METADATA, JsonMetadataBuilder.resourceMetadata(submission, mapper));

        String from = recipientConfig.getFromAddress();
        notification.setSender(from);
        params.put(NotificationParam.FROM, from);

        Collection<String> recipients = recipientAnalyzer.apply(submission, event);
        notification.setRecipients(recipients);
        params.put(NotificationParam.TO, join(",", recipients));

        params.put(NotificationParam.LINKS, concat(submissionLinkAnalyzer.apply(event, submissionEventMessage))
                .filter(linkValidator)
                .collect(serialized()));

        params.put(NotificationParam.APP_DOMAIN, passAppDomain);

        EventType eventType = event.getEventType();
        NotificationType notificationType = NotificationType.findForEventType(eventType);
        notification.setType(notificationType);

        return notification;
    }

}
