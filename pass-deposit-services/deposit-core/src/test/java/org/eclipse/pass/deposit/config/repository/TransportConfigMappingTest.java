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

package org.eclipse.pass.deposit.config.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "dspace.baseuri=http://localhost:8181",
    "dspace.covid.handle=test-covid-handle",
    "dspace.nobel.handle=test-nobel-handle"
})
public class TransportConfigMappingTest extends AbstractJacksonMappingTest {

    private static final String MINIMAL_SFTP_TRANSPORT_CONFIG = "" +
        "{\n" +
        "      \"protocol-binding\": {\n" +
        "        \"protocol\": \"sftp\"\n" +
        "      }\n" +
        "\n" +
        "    }";

    @Test
    public void mapMinimalSftpTransportConfig() throws IOException {
        TransportConfig config = repositoriesMapper.readValue(MINIMAL_SFTP_TRANSPORT_CONFIG, TransportConfig.class);

        assertNull(config.getAuthRealms());
        assertNotNull(config.getProtocolBinding());
        assertTrue(config.getProtocolBinding() instanceof SftpBinding);

        SftpBinding binding = (SftpBinding) config.getProtocolBinding();

        assertEquals(SftpBinding.PROTO, binding.getProtocol());
    }

}
