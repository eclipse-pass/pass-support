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
package org.eclipse.pass.deposit.service;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.eclipse.pass.support.client.ModelUtil;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DepositUpdater {
    private static final Logger LOG = LoggerFactory.getLogger(DepositUpdater.class);

    private final PassClient passClient;
    private final FailedDepositRetry failedDepositRetry;

    @Value("${pass.status.update.window.days}")
    private long updateWindowDays;

    @Value("${pass.deposit.retry.failed.enabled}")
    private Boolean retryFailedDepositsEnabled;

    @Autowired
    public DepositUpdater(PassClient passClient, FailedDepositRetry failedDepositRetry) {
        this.passClient = passClient;
        this.failedDepositRetry = failedDepositRetry;
    }

    public void doUpdate() throws IOException {
        ZonedDateTime submissionFromDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(updateWindowDays);
        retryFailedDeposits(submissionFromDate);
    }

    private void retryFailedDeposits(ZonedDateTime submissionFromDate) throws IOException {
        if (Boolean.FALSE.equals(retryFailedDepositsEnabled)) {
            LOG.warn("Failed Deposit Retry is Disabled.");
            return;
        }
        PassClientSelector<Deposit> failedDepositsSelector = new PassClientSelector<>(Deposit.class);
        failedDepositsSelector.setFilter(
            RSQL.and(
                RSQL.equals("depositStatus", DepositStatus.RETRY.getValue()),
                RSQL.gte("submission.submittedDate", ModelUtil.dateTimeFormatter().format(submissionFromDate))
            )
        );
        List<Deposit> failedDeposits = passClient.streamObjects(failedDepositsSelector).toList();
        LOG.warn("Failed Deposit Count for updating: {}", failedDeposits.size());
        failedDeposits.forEach(deposit -> {
            try {
                LOG.info("Retrying FAILED Deposit for {}", deposit.getId());
                failedDepositRetry.retryFailedDeposit(deposit);
            } catch (Exception e) {
                LOG.warn("Failed to retry Failed Deposit {}: {}", deposit.getId(), e.getMessage(), e);
            }
        });
    }
}
