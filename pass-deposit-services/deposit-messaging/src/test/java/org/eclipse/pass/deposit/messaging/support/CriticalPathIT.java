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
package org.eclipse.pass.deposit.messaging.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.pass.deposit.AbstractDepositSubmissionIT;
import org.eclipse.pass.deposit.cri.CriticalPath;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@DirtiesContext
public class CriticalPathIT extends AbstractDepositSubmissionIT {

    @Autowired
    private CriticalPath criticalPath;

    @Autowired
    private PassClient passClient;

    @Test
    public void simpleTest() throws Exception {
        // create a resource, put it in the repo
        Deposit deposit = new Deposit();
        passClient.createObject(deposit);

        // simply use critical path to update its deposit status

        CriticalRepositoryInteraction.CriticalResult<Deposit, Deposit> result = criticalPath.performCritical(
            deposit.getId(), Deposit.class, (d) -> d.getDepositStatus() == null,
            (d) -> d.getDepositStatus() == DepositStatus.SUBMITTED, (d) -> {
                d.setDepositStatus(DepositStatus.SUBMITTED);
                return d;
            }, true);

        assertNotNull(result);
        assertTrue(result.success());
        assertNotNull(result.resource());
        assertEquals(DepositStatus.SUBMITTED, result.resource().get().getDepositStatus());
        assertNotSame(deposit, result.resource());
    }

    @Test
    public void endStateFailure() throws Exception {
        Boolean[] probe = {Boolean.FALSE};

        // create a resource, put it in the repo
        Deposit deposit = new Deposit();
        passClient.createObject(deposit);

        // simply use critical path to update its deposit status

        CriticalRepositoryInteraction.CriticalResult<Deposit, Deposit> result = criticalPath.performCritical(
            deposit.getId(), Deposit.class, (d) -> d.getDepositStatus() == null, (d) -> {
                probe[0] = Boolean.TRUE;
                return d.getDepositStatus() == DepositStatus.REJECTED;
            }, (d) -> {
                d.setDepositStatus(DepositStatus.SUBMITTED);
                return d;
            }, true);

        assertNotNull(result);
        assertFalse(result.success());
        assertNotNull(result.resource());
        assertEquals(DepositStatus.SUBMITTED, result.resource().get().getDepositStatus());
        assertNotSame(deposit, result.resource());
        assertTrue(probe[0]);
    }

    @Test
    public void initialStateFailure() throws Exception {
        Boolean[] probe = {Boolean.FALSE};

        // create a resource, put it in the repo
        Deposit deposit = new Deposit();
        passClient.createObject(deposit);

        // execute serial updates, the second one should fail because the initial state condition
        // (deposit status == null) is not met

        CriticalRepositoryInteraction.CriticalResult<Deposit, Deposit> first = criticalPath.performCritical(
            deposit.getId(), Deposit.class, (d) -> d.getDepositStatus() == null,
            (d) -> d.getDepositStatus() == DepositStatus.SUBMITTED, (d) -> {

                d.setDepositStatus(DepositStatus.SUBMITTED);
                return d;
            }, true);

        CriticalRepositoryInteraction.CriticalResult<Deposit, Deposit> second = criticalPath.performCritical(
            deposit.getId(),
            Deposit.class, (d) -> d.getDepositStatus() == null,
            (d) -> d.getDepositStatus() == DepositStatus.REJECTED, (d) -> {
                probe[0] = Boolean.TRUE;
                d.setDepositStatus(DepositStatus.REJECTED);
                return d;
            }, true);

        assertTrue(first.success());
        assertFalse(second.success());
        assertFalse(probe[0]);
        assertEquals(DepositStatus.SUBMITTED, first.resource().get().getDepositStatus());
        assertEquals(DepositStatus.SUBMITTED, second.resource().get().getDepositStatus());
    }

}
