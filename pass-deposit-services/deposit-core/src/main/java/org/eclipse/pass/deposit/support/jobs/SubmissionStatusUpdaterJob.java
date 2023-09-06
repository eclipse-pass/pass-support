/*
 * Copyright 2019 Johns Hopkins University
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
package org.eclipse.pass.deposit.support.jobs;

import java.io.IOException;

import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.service.SubmissionStatusUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class SubmissionStatusUpdaterJob {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionStatusUpdaterJob.class);

    private final SubmissionStatusUpdater updater;

    public SubmissionStatusUpdaterJob(SubmissionStatusUpdater updater) {
        this.updater = updater;
    }

    @Scheduled(
        fixedDelayString = "${pass.deposit.jobs.default-interval-ms}",
        initialDelayString = "${pass.deposit.jobs.1.init.delay}"
    )
    public void updateSubmissions() {
        LOG.warn("Starting {}", this.getClass().getSimpleName());
        try {
            updater.doUpdate();
        } catch (IOException e) {
            throw new DepositServiceRuntimeException("Submission status updater failed", e);
        }
        LOG.warn("Finished {}", this.getClass().getSimpleName());
    }

}
