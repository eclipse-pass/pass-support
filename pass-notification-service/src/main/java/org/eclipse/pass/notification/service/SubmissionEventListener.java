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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.pass.notification.config.Mode;
import org.eclipse.pass.notification.config.NotificationConfig;
import org.eclipse.pass.notification.model.SubmissionEventMessage;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@AllArgsConstructor
@Slf4j
@Component
public class SubmissionEventListener {

    private final NotificationService notificationService;
    private final NotificationConfig notificationConfig;

    /**
     * Listen for submission even messages and process them.
     * @param submissionEventMessage the message
     */
    @JmsListener(destination = "${pass.jms.queue.submission.event.name}")
    public void processMessage(SubmissionEventMessage submissionEventMessage) {
        try {
            log.trace("Receiving SubmissionEvent: {}", submissionEventMessage.getSubmissionEventId());
            if (Mode.DISABLED == notificationConfig.getMode()) {
                log.trace("Discarding message {}, mode is {}",
                    submissionEventMessage.getSubmissionEventId(),
                    notificationConfig.getMode());
                return;
            }
            notificationService.notify(submissionEventMessage);
        } catch (Throwable throwable) {
            log.error("Error processing SubmissionEvent: " + submissionEventMessage.getSubmissionEventId(), throwable);
        }
    }
}
