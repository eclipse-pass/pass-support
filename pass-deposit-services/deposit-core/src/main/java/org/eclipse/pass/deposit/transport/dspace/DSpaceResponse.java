/*
 * Copyright 2025 Johns Hopkins University
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
package org.eclipse.pass.deposit.transport.dspace;

import java.io.IOException;
import java.net.URI;

import org.eclipse.pass.deposit.service.DepositUtil;
import org.eclipse.pass.deposit.transport.TransportResponse;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.RepositoryCopy;

class DSpaceResponse implements TransportResponse {
    private final boolean success;
    private final Throwable throwable;
    private final String depositAccessUrl;

    DSpaceResponse(boolean success, String depositAccessUrl) {
        this(success, depositAccessUrl, null);
    }

    DSpaceResponse(boolean success, String depositAccessUrl, Throwable throwable) {
        this.success = success;
        this.depositAccessUrl = depositAccessUrl;
        this.throwable = throwable;
    }

    @Override
    public boolean success() {
        return success;
    }

    @Override
    public Throwable error() {
        return throwable;
    }

    @Override
    public void onSuccess(DepositUtil.DepositWorkerContext depositWorkerContext, PassClient passClient) {
        try {
            RepositoryCopy repositoryCopy = depositWorkerContext.repoCopy();
            repositoryCopy.setAccessUrl(URI.create(depositAccessUrl));
            repositoryCopy.getExternalIds().add(depositAccessUrl);
            repositoryCopy.setCopyStatus(CopyStatus.COMPLETE);
            passClient.updateObject(repositoryCopy);
            Deposit deposit = depositWorkerContext.deposit();
            deposit.setDepositStatus(DepositStatus.ACCEPTED);
            passClient.updateObject(deposit);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
