/*
 * Copyright 2024 Johns Hopkins University
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
package org.eclipse.pass.deposit.transport.devnull;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction.CriticalResult;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.service.DepositUtil;
import org.eclipse.pass.deposit.transport.Transport;
import org.eclipse.pass.deposit.transport.TransportResponse;
import org.eclipse.pass.deposit.transport.TransportSession;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Component
public class DevNullTransport implements Transport {

    private static final Logger LOG = LoggerFactory.getLogger(DevNullTransport.class);

    private final CriticalRepositoryInteraction cri;

    @Autowired
    public DevNullTransport(CriticalRepositoryInteraction cri) {
        this.cri = cri;
    }

    @Override
    public PROTOCOL protocol() {
        return PROTOCOL.devnull;
    }

    @Override
    public TransportSession open(Map<String, String> hints) {
        return new DevNullTransportSession();
    }

    class DevNullTransportSession implements TransportSession {

        @Override
        public TransportResponse send(PackageStream packageStream, Map<String, String> metadata) {
            DepositSubmission depositSubmission = packageStream.getDepositSubmission();
            LOG.warn("Processing Deposit to DevNull for Submission: {}", depositSubmission.getId());
            // no-op, just return successful response
            return new TransportResponse() {
                @Override
                public boolean success() {
                    return true;
                }

                @Override
                public Throwable error() {
                    return null;
                }

                @Override
                public void onSuccess(DepositUtil.DepositWorkerContext depositWorkerContext, PassClient passClient) {
                    Submission submission = depositWorkerContext.submission();
                    Deposit deposit = depositWorkerContext.deposit();
                    RepositoryCopy repositoryCopy = depositWorkerContext.repoCopy();
                    LOG.trace("Invoking onSuccess for tuple [{} {} {}]",
                              submission.getId(), deposit.getId(), repositoryCopy.getId());
                    CriticalResult<RepositoryCopy, RepositoryCopy> rcCr =
                        cri.performCritical(repositoryCopy.getId(), RepositoryCopy.class,
                                            (rc) -> true,
                                            (rc) -> true,
                                            (rc) -> {
                                                String accessUrl = "https://devnull-fake-url/handle/" +
                                                    repositoryCopy.getId();
                                                rc.getExternalIds().add(accessUrl);
                                                rc.setCopyStatus(CopyStatus.COMPLETE);
                                                rc.setAccessUrl(URI.create(accessUrl));
                                                return rc;
                                            }, true);

                    LOG.trace("onSuccess updated RepositoryCopy {}", rcCr.resource().get().getId());

                    CriticalResult<Deposit, Deposit> depositCr =
                        cri.performCritical(deposit.getId(), Deposit.class,
                                            (criDeposit) -> DepositStatus.SUBMITTED == criDeposit.getDepositStatus(),
                                            (criDeposit) -> DepositStatus.ACCEPTED == criDeposit.getDepositStatus(),
                                            (criDeposit) -> {
                                                criDeposit.setDepositStatus(DepositStatus.ACCEPTED);
                                                return criDeposit;
                                            }, true);

                    LOG.trace("onSuccess updated Deposit {}", depositCr.resource().get().getId());
                }
            };
        }

        @Override
        public boolean closed() {
            return false;
        }

        @Override
        public void close() throws IOException {
            // no-op
        }

    }

    @Override
    public boolean checkConnectivity(Map<String, String> hints) {
        return true;
    }

}
