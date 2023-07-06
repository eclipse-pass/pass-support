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
package org.eclipse.pass.deposit.messaging.support.jobs;

import org.eclipse.pass.deposit.messaging.service.DepositUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DepositUpdaterJob {

    private static final Logger LOG = LoggerFactory.getLogger(DepositUpdaterJob.class);

    private final DepositUpdater depositUpdater;

    public DepositUpdaterJob(DepositUpdater depositUpdater) {
        this.depositUpdater = depositUpdater;
    }

    @Scheduled(fixedDelayString = "${pass.deposit.jobs.default-interval-ms}")
    public void updateDeposits() {
        try {
            depositUpdater.doUpdate();
        } catch (Exception e) {
            LOG.error("DepositUpdaterJob execution failed: {}", e.getMessage(), e);
        }
    }

}
