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
package org.eclipse.pass.loader.nihms;

import java.io.IOException;

import org.eclipse.pass.client.nihms.NihmsPassClientService;
import org.eclipse.pass.support.client.SubmissionStatusService;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates / updates the Submission, RepositoryCopy, Publication, and Deposit in the database as needed
 *
 * @author Karen Hanson
 */
public class SubmissionLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionLoader.class);

    private NihmsPassClientService clientService;

    private SubmissionStatusService statusService;

    /**
     * Initiates with default client service
     */
    public SubmissionLoader() {
        this.clientService = new NihmsPassClientService();
        this.statusService = new SubmissionStatusService();
    }

    /**
     * Supports initiation with specific client service
     *
     * @param clientService PASS client service
     * @param statusService Submission status service
     */
    public SubmissionLoader(NihmsPassClientService clientService, SubmissionStatusService statusService) {
        this.clientService = clientService;
        this.statusService = statusService;
    }

    /**
     * Load the data in the NihmsSubmissionDTO to the database. Deal with any conflicts that occur during the updates
     * by implementing retries, failing gracefully etc.
     *
     * @param dto the DTO
     * @throws IOException if there is an error updating/creating the publication, repository copy, or submission
     */
    public void load(SubmissionDTO dto) throws IOException {
        if (dto == null || dto.getSubmission() == null) {
            throw new RuntimeException("A null Submission object was passed to the loader.");
        }

        LOG.info("Loading information for Submission with PMID {}", dto.getPublication().getPmid());

        Publication publication = dto.getPublication();
        if (publication.getId() == null) {
            clientService.createPublication(publication);
        } else if (dto.doUpdatePublication()) {
            clientService.updatePublication(publication);
        }

        Submission submission = dto.getSubmission();
        if (submission.getId() == null) {
            submission.setPublication(publication);
            clientService.createSubmission(submission);
        } else if (dto.doUpdateSubmission()) {
            clientService.updateSubmission(submission);
        }

        RepositoryCopy repositoryCopy = dto.getRepositoryCopy();
        if (repositoryCopy != null) {
            if (repositoryCopy.getId() == null) {
                repositoryCopy.setPublication(publication);
                clientService.createRepositoryCopy(repositoryCopy);
            } else if (dto.doUpdateRepositoryCopy()) {
                clientService.updateRepositoryCopy(repositoryCopy);
            }

            // If repository copy is changing, check Deposit to make sure the RepositoryCopyId is present
            Deposit deposit = clientService.findNihmsDepositForSubmission(submission.getId());
            if (deposit != null && deposit.getRepositoryCopy() == null) {
                deposit.setRepositoryCopy(repositoryCopy);
                deposit.setDepositStatus(DepositStatus.ACCEPTED);
                clientService.updateDeposit(deposit);
            } else if (deposit != null && !deposit.getRepositoryCopy().equals(repositoryCopy.getId())) {
                //this shouldn't happen in principle, but if it does it should be checked.
                LOG.warn(
                    "A NIHMS Deposit with id {} was found for the Submission but it is associated with a different " +
                    "RepositoryCopy ({}) from the one processed ({}). "
                    + "This may indicate a data error, please verify the RepositoryCopy association for this Deposit",
                    deposit.getId(), deposit.getRepositoryCopy(), repositoryCopy.getId());
            }
        }

        //before moving on do one last check to see if SubmissionStatus has been affected by the changes
        //if so, update status.
        if (dto.doUpdate()) {
            statusService.calculateAndUpdateSubmissionStatus(submission.getId());
        }
    }

}
