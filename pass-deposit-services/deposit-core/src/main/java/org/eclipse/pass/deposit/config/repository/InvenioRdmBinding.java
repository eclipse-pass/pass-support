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
package org.eclipse.pass.deposit.config.repository;

import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_PROTOCOL;

import java.util.Map;

import org.eclipse.pass.deposit.transport.Transport;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class InvenioRdmBinding extends ProtocolBinding {

    static final String PROTO = "invenioRdm";

    public InvenioRdmBinding() {
        this.setProtocol(PROTO);
    }

    @Override
    public Map<String, String> asPropertiesMap() {
        return Map.of(TRANSPORT_PROTOCOL, Transport.PROTOCOL.invenioRdm.name());
    }

}