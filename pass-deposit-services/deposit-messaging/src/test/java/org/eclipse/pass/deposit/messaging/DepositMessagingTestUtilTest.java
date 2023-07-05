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
package org.eclipse.pass.deposit.messaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.pass.deposit.messaging.policy.Policy;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests insuring that the Suppliers created by {@link DepositMessagingTestUtil} are congruent with the concrete
 * {@link Policy} implementations.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */

@SpringBootTest(classes = DepositApp.class)
@TestPropertySource("classpath:test-application.properties")
public class DepositMessagingTestUtilTest {

    @Autowired
    private Policy<DepositStatus> intermediateDepositStatusPolicy;

    @Autowired
    private Policy<DepositStatus> terminalDepositStatusPolicy;

    @Autowired
    private Policy<AggregatedDepositStatus> terminalSubmissionStatusPolicy;

    @Autowired
    private Policy<AggregatedDepositStatus> intermediateSubmissionStatusPolicy;

    private int tries = 20;

    @Test
    public void terminalAggregatedDepositStatusSupplier() {
        for (int i = 0; i < tries; i++) {
            assertTrue(terminalSubmissionStatusPolicy.test(
                DepositMessagingTestUtil.randomTerminalAggregatedDepositStatus.get()));
            assertFalse(intermediateSubmissionStatusPolicy.test(
                DepositMessagingTestUtil.randomTerminalAggregatedDepositStatus.get()));
        }
    }

    @Test
    public void intermediateAggregatedDepositStatusSupplier() {
        for (int i = 0; i < tries; i++) {
            assertFalse(terminalSubmissionStatusPolicy.test(
                DepositMessagingTestUtil.randomIntermediateAggregatedDepositStatus.get()));
            assertTrue(intermediateSubmissionStatusPolicy.test(
                DepositMessagingTestUtil.randomIntermediateAggregatedDepositStatus.get()));
        }
    }

    @Test
    public void intermediateDepositStatusSupplier() {
        for (int i = 0; i < tries; i++) {
            assertTrue(intermediateDepositStatusPolicy.test(
                DepositMessagingTestUtil.randomIntermediateDepositStatus.get()));
            assertFalse(terminalDepositStatusPolicy.test(
                DepositMessagingTestUtil.randomIntermediateDepositStatus.get()));
        }
    }

    @Test
    public void terminalDepositStatusSupplier() {
        for (int i = 0; i < tries; i++) {
            assertFalse(intermediateDepositStatusPolicy.test(
                DepositMessagingTestUtil.randomTerminalDepositStatus.get()));
            assertTrue(terminalDepositStatusPolicy.test(DepositMessagingTestUtil.randomTerminalDepositStatus.get()));
        }
    }
}