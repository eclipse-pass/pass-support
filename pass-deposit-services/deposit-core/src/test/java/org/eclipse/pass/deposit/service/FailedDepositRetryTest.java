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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.stream.Stream;

import org.eclipse.pass.deposit.builder.DepositSubmissionModelBuilder;
import org.eclipse.pass.deposit.model.Packager;
import org.eclipse.pass.deposit.model.Registry;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Deposit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class FailedDepositRetryTest {

    @SuppressWarnings("unchecked")
    @Test
    void testRetryFailedDeposits_Fail_NoPackagerFound() throws IOException {
        // GIVEN
        final PassClient passClient = mock(PassClient.class);
        final DepositTaskHelper depositTaskHelper = mock(DepositTaskHelper.class);
        final Registry<Packager> packagerRegistry = mock(Registry.class);
        final DepositSubmissionModelBuilder depositSubmissionModelBuilder = mock(DepositSubmissionModelBuilder.class);
        final FailedDepositRetry failedDepositRetry = new FailedDepositRetry(passClient, depositTaskHelper,
            packagerRegistry, depositSubmissionModelBuilder);
        Deposit deposit1 = new Deposit();
        deposit1.setId("dp-1");
        when(passClient.streamObjects(any())).thenReturn(Stream.of(deposit1));
        when(packagerRegistry.get(any())).thenReturn(null);

        // WHEN
        failedDepositRetry.retryFailedDeposits();

        // THEN
        verifyNoInteractions(depositTaskHelper);

        ArgumentCaptor<PassClientSelector<Deposit>> argument = ArgumentCaptor.forClass(PassClientSelector.class);
        verify(passClient).streamObjects(argument.capture());
        assertEquals("depositStatus=='failed'", argument.getValue().getFilter());
    }

}
