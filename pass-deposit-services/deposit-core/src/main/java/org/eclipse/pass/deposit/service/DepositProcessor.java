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
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DepositProcessor implements Consumer<Deposit> {
    static final String SUBMISSION_REL = "submission.id";
    private static final Logger LOG = LoggerFactory.getLogger(DepositProcessor.class);

    private final CriticalRepositoryInteraction cri;
    private final PassClient passClient;
    private final DepositTaskHelper depositHelper;

    public DepositProcessor(CriticalRepositoryInteraction cri,
                            PassClient passClient,
                            DepositTaskHelper depositHelper) {
        this.cri = cri;
        this.passClient = passClient;
        this.depositHelper = depositHelper;
    }

    @Override
    public void accept(Deposit deposit) {

        if (DepositStatus.isTerminalStatus(deposit.getDepositStatus())) {
            // terminal Deposit status, so update its Submission aggregate deposit status.
            cri.performCritical(deposit.getSubmission().getId(), Submission.class,
                                DepositProcessorCriFunc.precondition(),
                                DepositProcessorCriFunc.postcondition(),
                                DepositProcessorCriFunc.critical(passClient));
        } else {
            // intermediate status, process the Deposit depositStatusRef

            // determine the RepositoryConfig for the Deposit
            // retrieve and invoke the DepositStatusProcessor from the RepositoryConfig
            //   - requires Collection<AuthRealm> and StatusMapping

            // if result is still intermediate, add Deposit to queue for processing?

            // Determine the logical success or failure of the Deposit, and persist the Deposit and RepositoryCopy
            depositHelper.processDepositStatus(deposit.getId());
        }
    }

    static class DepositProcessorCriFunc {

        /**
         * Answers a Predicate that accepts the Submission for processing.  It must accept Submissions with an
         * intermediate deposit status, and reject those with a terminal status.
         *
         * @return the Predicate that applies the precondition
         */
        static Predicate<Submission> precondition() {
            return (criSubmission) ->
                !AggregatedDepositStatus.isTerminalStatus(criSubmission.getAggregatedDepositStatus());
        }

        /**
         * The critical function may or may not modify the state of the Submission, so this answers a Predicate that
         * always returns {@code true}.
         *
         * @return a Predicate that always returns {@code true}
         */
        static Predicate<Submission> postcondition() {
            return (criSubmission) -> true;
        }

        /**
         * Answers a Function that updates the {@link AggregatedDepositStatus} of the {@code Submission} if
         * all of the Deposits attached to the Submission are in a terminal state.
         * <p>
         * If any Deposit attached to the Submission is in an intermediate state, no modifications are made to the
         * Submission.AggregatedDepositStatus.
         * </p>
         * <p>
         * If all Deposits attached to the Submission have a terminal DepositStatus.ACCEPTED state, then the Submission
         * AggregatedDepositStatus is updated to ACCEPTED.
         * </p>
         * <p>
         * If all Deposits attached to the Submission have a terminal DepositStatus.REJECTED state, then the Submission
         * AggregatedDepositStatus is updated to REJECTED.
         * </p>
         *
         * @param passClient           used to query the PASS repository for resource
         * @return the critical function that may modify the Submission.AggregatedDepositStatus based on its Deposits
         */
        static Function<Submission, Submission> critical(PassClient passClient) {
            return (criSubmission) -> {
                PassClientSelector<Deposit> sel = new PassClientSelector<>(Deposit.class);
                sel.setFilter(RSQL.equals(SUBMISSION_REL,  criSubmission.getId()));

                Collection<Deposit> deposits;
                try {
                    deposits = passClient.streamObjects(sel).toList();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to retrieve deposits for submission " + criSubmission.getId(),
                        e);
                }

                if (deposits.isEmpty()) {
                    return criSubmission;
                }

                // If all the statuses are terminal, then we can update the aggregated deposit status of
                // the submission
                if (deposits.stream()
                            .allMatch((criDeposit) ->
                                DepositStatus.isTerminalStatus(criDeposit.getDepositStatus()))) {

                    if (deposits.stream()
                                .allMatch((criDeposit) -> DepositStatus.ACCEPTED == criDeposit.getDepositStatus())) {
                        criSubmission.setAggregatedDepositStatus(AggregatedDepositStatus.ACCEPTED);
                        LOG.debug("Updating {} aggregated deposit status to {}", criSubmission.getId(),
                                  DepositStatus.ACCEPTED);
                    } else {
                        criSubmission.setAggregatedDepositStatus(AggregatedDepositStatus.REJECTED);
                        LOG.debug("Updating {} aggregated deposit status to {}", criSubmission.getId(),
                                  AggregatedDepositStatus.REJECTED);
                    }
                }

                return criSubmission;
            };
        }
    }
}
