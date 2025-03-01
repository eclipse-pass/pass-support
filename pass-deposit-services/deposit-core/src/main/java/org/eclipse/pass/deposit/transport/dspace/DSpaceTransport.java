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

import java.util.Map;

import org.eclipse.pass.deposit.provider.dspace.DSpaceMetadataMapper;
import org.eclipse.pass.deposit.support.dspace.DSpaceDepositService;
import org.eclipse.pass.deposit.transport.Transport;
import org.eclipse.pass.deposit.transport.TransportSession;
import org.springframework.stereotype.Component;

@Component
public class DSpaceTransport implements Transport {
    private final DSpaceDepositService dspaceDepositService;
    private final DSpaceMetadataMapper dspaceMetadataMapper;

    public DSpaceTransport(DSpaceDepositService dspaceDepositService, DSpaceMetadataMapper dspaceMetadataMapper) {
        this.dspaceDepositService = dspaceDepositService;
        this.dspaceMetadataMapper = dspaceMetadataMapper;
    }

    @Override
    public PROTOCOL protocol() {
        return PROTOCOL.DSpace;
    }

    @Override
    public TransportSession open(Map<String, String> hints) {
        return new DSpaceSession(dspaceDepositService, dspaceMetadataMapper);
    }

    @Override
    public boolean checkConnectivity(Map<String, String> hints) {
        return dspaceDepositService.verifyConnectivity();
    }
}
