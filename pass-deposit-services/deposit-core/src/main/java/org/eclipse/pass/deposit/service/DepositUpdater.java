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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import org.eclipse.pass.deposit.config.repository.Repositories;
import org.eclipse.pass.deposit.config.repository.RepositoryConfig;
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
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

    private final PassClient passClient;
    private final DepositTaskHelper depositHelper;
    private final FailedDepositRetry failedDepositRetry;
    private final Repositories repositories;

    private final List<String> repoKeysWithDepositProcessors;

    @Value("${pass.deposit.update.window.days}")
    private long updateWindowDays;

    @Autowired
    public DepositUpdater(PassClient passClient, DepositTaskHelper depositHelper,
                          FailedDepositRetry failedDepositRetry, Repositories repositories) {
        this.passClient = passClient;
        this.depositHelper = depositHelper;
        this.failedDepositRetry = failedDepositRetry;
        this.repositories = repositories;
        this.repoKeysWithDepositProcessors = getReposWithDepositProcessors();
    }

    public void doUpdate() throws IOException {
        ZonedDateTime submissionFromDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(updateWindowDays);
        retryFailedDeposits(submissionFromDate);
        updateSubmittedDeposits(submissionFromDate);
    }

    private void retryFailedDeposits(ZonedDateTime submissionFromDate) throws IOException {
        PassClientSelector<Deposit> failedDepositsSelector = new PassClientSelector<>(Deposit.class);
        failedDepositsSelector.setFilter(
            RSQL.and(
                RSQL.equals("depositStatus", DepositStatus.FAILED.getValue()),
                RSQL.gte("submission.submittedDate", DATE_TIME_FORMATTER.format(submissionFromDate))
            )
        );
        List<Deposit> failedDeposits = passClient.streamObjects(failedDepositsSelector).toList();
        LOG.warn("Failed Deposit Count for updating: " + failedDeposits.size());
        failedDeposits.forEach(deposit -> {
            try {
                LOG.info("Retrying FAILED Deposit for {}", deposit.getId());
                failedDepositRetry.retryFailedDeposit(deposit);
            } catch (Exception e) {
                LOG.warn("Failed to retry Failed Deposit {}: {}", deposit.getId(), e.getMessage(), e);
            }
        });
    }

    private void updateSubmittedDeposits(ZonedDateTime submissionFromDate) throws IOException {
        if (repoKeysWithDepositProcessors.isEmpty()) {
            return;
        }
        PassClientSelector<Deposit> submittedDepositsSelector = new PassClientSelector<>(Deposit.class);
        submittedDepositsSelector.setFilter(
            RSQL.and(
                RSQL.equals("depositStatus", DepositStatus.SUBMITTED.getValue()),
                RSQL.gte("submission.submittedDate", DATE_TIME_FORMATTER.format(submissionFromDate)),
                RSQL.in("repository.repositoryKey", repoKeysWithDepositProcessors.toArray(new String[0]))
            )
        );
        List<Deposit> submittedDeposits = passClient.streamObjects(submittedDepositsSelector).toList();
        LOG.warn("Deposit Count for updating: " + submittedDeposits.size());
        submittedDeposits.forEach(deposit -> {
            try {
                LOG.info("Updating Deposit.depositStatus for {}", deposit.getId());
                depositHelper.processDepositStatus(deposit.getId());
            } catch (Exception e) {
                LOG.warn("Failed to update Deposit {}: {}", deposit.getId(), e.getMessage(), e);
            }
        });
    }

    private List<String> getReposWithDepositProcessors() {
        return repositories.getAllConfigs().stream()
            .filter(repositoryConfig ->
                Objects.nonNull(repositoryConfig.getRepositoryDepositConfig())
                    && Objects.nonNull(repositoryConfig.getRepositoryDepositConfig().getDepositProcessing())
                    && Objects.nonNull(repositoryConfig.getRepositoryDepositConfig().getDepositProcessing()
                        .getProcessor())
            ).map(RepositoryConfig::getRepositoryKey)
            .toList();
    }
}
