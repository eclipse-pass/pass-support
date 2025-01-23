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
package org.eclipse.pass.deposit.transport.inveniordm;

import java.util.Map;

import org.eclipse.pass.deposit.transport.RepositoryConnectivityService;
import org.eclipse.pass.deposit.transport.Transport;
import org.eclipse.pass.deposit.transport.TransportSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${inveniordm.api.baseUrl}')")
@Component
public class InvenioRdmTransport implements Transport {

    @Value("${inveniordm.api.baseUrl}")
    private String invenioBaseUrl;

    @Value("${inveniordm.api.token}")
    private String invenioApiToken;

    @Value("${inveniordm.api.verifySslCertificate}")
    private Boolean verifySslCertificate;

    private final RepositoryConnectivityService repositoryConnectivityService;

    public InvenioRdmTransport(RepositoryConnectivityService repositoryConnectivityService) {
        this.repositoryConnectivityService = repositoryConnectivityService;
    }

    @Override
    public PROTOCOL protocol() {
        return PROTOCOL.invenioRdm;
    }

    @Override
    public TransportSession open(Map<String, String> hints) {
        return new InvenioRdmSession(invenioBaseUrl,  invenioApiToken, verifySslCertificate);
    }

    @Override
    public boolean checkConnectivity(Map<String, String> hints)  {
        return repositoryConnectivityService.verifyConnectByURL(invenioBaseUrl);
    }

}
