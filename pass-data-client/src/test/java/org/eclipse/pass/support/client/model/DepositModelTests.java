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
package org.eclipse.pass.support.client.model;

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createDeposit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure that the equals / hashcode functions work.
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class DepositModelTests {
    @Test
    public void testDepositEqualsAndHashCode()  {

        Deposit deposit1 = createDeposit(TestValues.DEPOSIT_ID_1);
        Deposit deposit2 = createDeposit(TestValues.DEPOSIT_ID_1);

        assertEquals(deposit1, deposit2);
        assertEquals(deposit1.hashCode(), deposit2.hashCode());

        deposit1.setDepositStatusRef("different");
        assertTrue(!deposit1.equals(deposit2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testDepositCopyConstructor()  {
        Deposit deposit = createDeposit(TestValues.DEPOSIT_ID_1);
        Deposit depositCopy = new Deposit(deposit);
        assertEquals(deposit, depositCopy);

        depositCopy.setDepositStatus(DepositStatus.REJECTED);
        assertEquals(DepositStatus.of(TestValues.DEPOSIT_STATUS), deposit.getDepositStatus());
        assertEquals(DepositStatus.REJECTED, depositCopy.getDepositStatus());

        depositCopy.setId(TestValues.DEPOSIT_ID_2);
        assertEquals(TestValues.DEPOSIT_ID_1, deposit.getId());
        assertEquals(TestValues.DEPOSIT_ID_2, depositCopy.getId());
    }
}
