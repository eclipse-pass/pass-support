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
package org.eclipse.pass.deposit.service;

import static org.eclipse.pass.deposit.service.DepositTaskHelper.MISSING_PACKAGER;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.deposit.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.builder.DepositSubmissionModelBuilder;
import org.eclipse.pass.deposit.model.DepositFile;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.model.Packager;
import org.eclipse.pass.deposit.model.Registry;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Component
public class FailedDepositRetry {

    private static final Logger LOG = LoggerFactory.getLogger(FailedDepositRetry.class);
    private static final String FAILED_TO_PROCESS = "Failed to process {}: {}";

    private final PassClient passClient;
    private final DepositSubmissionModelBuilder depositSubmissionModelBuilder;
    private final DepositTaskHelper depositTaskHelper;
    private final Registry<Packager> packagerRegistry;
    private final DepositServiceErrorHandler depositServiceErrorHandler;

    public FailedDepositRetry(PassClient passClient, DepositTaskHelper depositTaskHelper,
                              Registry<Packager> packagerRegistry,
                              DepositSubmissionModelBuilder depositSubmissionModelBuilder,
                              DepositServiceErrorHandler depositServiceErrorHandler) {
        this.passClient = passClient;
        this.depositTaskHelper = depositTaskHelper;
        this.packagerRegistry = packagerRegistry;
        this.depositSubmissionModelBuilder = depositSubmissionModelBuilder;
        this.depositServiceErrorHandler = depositServiceErrorHandler;
    }

    public void retryFailedDeposit(Deposit failedDeposit) {
        try {
            Deposit deposit = passClient.getObject(failedDeposit, "submission", "repository");
            final Submission submission = passClient.getObject(deposit.getSubmission(),
                "publication", "submitter");
            final Repository repository = deposit.getRepository();

            final Packager packager = packagerRegistry.get(repository.getRepositoryKey());
            if (Objects.isNull(packager)) {
                LOG.warn(MISSING_PACKAGER, submission.getId(), repository.getId(), deposit.getId(),
                    repository.getName());
                return;
            }

            final DepositSubmission depositSubmission;
            try {
                depositSubmission = depositSubmissionModelBuilder.build(submission.getId());
            } catch (IOException e) {
                LOG.warn(FAILED_TO_PROCESS, deposit.getId(),
                    "Failed to build the DepositSubmission model: " + e.getMessage());
                return;
            }

            if (hasInvalidFiles(depositSubmission)) {
                return;
            }

            depositTaskHelper.submitDeposit(
                submission,
                depositSubmission,
                repository,
                deposit,
                packager);

        } catch (Exception e) {
            LOG.warn(FAILED_TO_PROCESS, failedDeposit.getId(), e.getMessage(), e);
            depositServiceErrorHandler.handleError(e);
        }
    }

    private boolean hasInvalidFiles(DepositSubmission depositSubmission) {
        if (depositSubmission.getFiles() == null || depositSubmission.getFiles().isEmpty()) {
            LOG.warn(FAILED_TO_PROCESS, depositSubmission.getId(),
                "There are no files attached for deposit");
            return true;
        }

        String filesMissingLocations = depositSubmission.getFiles().stream()
            .filter(df -> StringUtils.isBlank(df.getLocation()))
            .map(DepositFile::getName)
            .collect(Collectors.joining(", "));

        if (StringUtils.isNotEmpty(filesMissingLocations)) {
            LOG.warn(FAILED_TO_PROCESS, depositSubmission.getId(),
                "The following DepositFiles are missing locations: " + filesMissingLocations);
            return true;
        }

        return false;
    }

}
