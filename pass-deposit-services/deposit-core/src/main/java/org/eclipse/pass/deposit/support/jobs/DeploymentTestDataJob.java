/*
 * Copyright 2024 Johns Hopkins University
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

import org.eclipse.pass.deposit.support.deploymenttest.DeploymentTestDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ConditionalOnExpression(
    "'${pass.test.data.job.enabled}'=='true' and '${pass.deposit.jobs.disabled}'=='false'"
)
@Component
public class DeploymentTestDataJob {

    private static final Logger LOG = LoggerFactory.getLogger(DeploymentTestDataJob.class);

    private final DeploymentTestDataService deploymentTestDataService;

    public DeploymentTestDataJob(DeploymentTestDataService deploymentTestDataService) {
        this.deploymentTestDataService = deploymentTestDataService;
    }

    @Scheduled(
        fixedDelayString = "${pass.test.data.job.interval-ms}",
        initialDelayString = "${pass.deposit.jobs.3.init.delay}"
    )
    public void processDeploymentTestData() {
        LOG.warn("Starting {}", this.getClass().getSimpleName());
        try {
            deploymentTestDataService.processTestData();
        } catch (Exception e) {
            LOG.error("DeploymentTestDataJob execution failed: {}", e.getMessage(), e);
        }
        LOG.warn("Finished {}", this.getClass().getSimpleName());
    }

}
