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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.pass.deposit.config.repository.Repositories;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Repository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class DepositUpdaterTest {

    @SuppressWarnings("unchecked")
    @Test
    void testDoUpdate() throws IOException {
        // GIVEN
        final PassClient passClient = mock(PassClient.class);
        final DepositTaskHelper depositTaskHelper = mock(DepositTaskHelper.class);
        final FailedDepositRetry failedDepositRetry = mock(FailedDepositRetry.class);
        final Repositories repositories = mock(Repositories.class);
        final DepositUpdater depositUpdater = new DepositUpdater(passClient, depositTaskHelper, failedDepositRetry,
            repositories);
        ReflectionTestUtils.setField(depositUpdater, "repoKeysWithDepositProcessors", List.of("repo-1"));
        Repository repository = new Repository();
        repository.setRepositoryKey("repo-1");
        Deposit deposit1 = new Deposit();
        deposit1.setId("dp-1");
        deposit1.setDepositStatus(DepositStatus.SUBMITTED);
        deposit1.setRepository(repository);
        Deposit deposit2 = new Deposit();
        deposit2.setDepositStatus(DepositStatus.SUBMITTED);
        deposit2.setId("dp-2");
        deposit2.setRepository(repository);
        when(passClient.streamObjects(any())).thenAnswer(input -> {
            PassClientSelector<Deposit> selector = input.getArgument(0);
            if (selector.getFilter().contains("depositStatus=='failed'")) {
                return Stream.of();
            }
            if (selector.getFilter().contains("depositStatus=='submitted'")) {
                return Stream.of(deposit1, deposit2);
            }
            throw new RuntimeException("Fail test, should not happen");
        });

        // WHEN
        depositUpdater.doUpdate();

        // THEN
        verify(depositTaskHelper).processDepositStatus("dp-1");
        verify(depositTaskHelper).processDepositStatus("dp-2");

        ArgumentCaptor<PassClientSelector<Deposit>> argument = ArgumentCaptor.forClass(PassClientSelector.class);
        verify(passClient, times(2)).streamObjects(argument.capture());
        assertTrue(argument.getAllValues().get(0).getFilter().startsWith(
            "(depositStatus=='failed';submission.submittedDate>="));
        assertTrue(argument.getAllValues().get(1).getFilter().startsWith(
            "(depositStatus=='submitted';submission.submittedDate>="));
    }
}
