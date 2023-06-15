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

package org.dataconservancy.pass.deposit.messaging.service;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DepositUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(DepositUpdater.class);

    private static final String STATUS_ATTRIBUTE = "depositStatus";

    private PassClient passClient;

    private DepositTaskHelper depositHelper;

    @Autowired
    public DepositUpdater(PassClient passClient, DepositTaskHelper depositHelper) {
        this.passClient = passClient;
        this.depositHelper = depositHelper;
    }

    public void doUpdate() throws IOException {
        doUpdate(depositIdsToUpdate(passClient));
    }

    void doUpdate(Collection<String> depositUris) {
        depositUris.forEach(depositUri -> {
            try {
                depositHelper.processDepositStatus(depositUri);
            } catch (Exception e) {
                LOG.warn("Failed to update {}: {}", depositUri, e.getMessage(), e);
            }
        });
    }

    private static Collection<String> depositIdsToUpdate(PassClient passClient) throws IOException {
        PassClientSelector<Deposit> sel = new PassClientSelector<>(Deposit.class);
        sel.setFilter(RSQL.in(STATUS_ATTRIBUTE, DepositStatus.FAILED.getValue(), DepositStatus.SUBMITTED.getValue()));

        return passClient.streamObjects(sel).map(Deposit::getId).toList();
    }

}
