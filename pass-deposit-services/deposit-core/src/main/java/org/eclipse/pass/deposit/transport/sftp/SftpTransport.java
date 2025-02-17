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
package org.eclipse.pass.deposit.transport.sftp;

import java.util.Map;

import org.eclipse.pass.deposit.transport.RepositoryConnectivityService;
import org.eclipse.pass.deposit.transport.Transport;
import org.eclipse.pass.deposit.transport.TransportSession;
import org.springframework.stereotype.Component;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Component
public class SftpTransport implements Transport {

    public static final String SFTP_BASE_DIRECTORY = "deposit.transport.protocol.sftp.basedir";

    private final RepositoryConnectivityService repositoryConnectivityService;

    public SftpTransport(RepositoryConnectivityService repositoryConnectivityService) {
        this.repositoryConnectivityService = repositoryConnectivityService;
    }

    @Override
    public PROTOCOL protocol() {
        return PROTOCOL.sftp;
    }

    @Override
    public TransportSession open(Map<String, String> hints) {
        return new SftpTransportSession(hints);
    }

    @Override
    public boolean checkConnectivity(Map<String, String> hints) {
        String serverName = hints.get(TRANSPORT_SERVER_FQDN);
        String serverPort = hints.get(TRANSPORT_SERVER_PORT);
        return repositoryConnectivityService.verifyConnect(serverName, Integer.parseInt(serverPort));
    }
}
