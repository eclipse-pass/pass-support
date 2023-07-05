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
package org.eclipse.pass.deposit.messaging.service;

import org.eclipse.pass.deposit.messaging.model.SubmissionMessage;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Component
public class SubmissionListener {
    private static final Logger LOG = LoggerFactory.getLogger(SubmissionListener.class);

    private final PassClient passClient;
    private final SubmissionProcessor submissionProcessor;

    public SubmissionListener(SubmissionProcessor submissionProcessor, PassClient passClient) {
        this.submissionProcessor = submissionProcessor;
        this.passClient = passClient;
    }

    @JmsListener(destination = "${pass.deposit.queue.submission.name}")
    public void processSubmissionMessage(SubmissionMessage submissionMessage) {
        try {
            Submission submission = passClient.getObject(Submission.class, submissionMessage.getSubmissionId());
            submissionProcessor.accept(submission);
        } catch (Exception e) {
            LOG.error("Failed to process submission JMS message.\nSubmission ID: '{}'",
                submissionMessage.getSubmissionId(), e);
        }
    }
}
