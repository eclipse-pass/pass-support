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

import java.io.IOException;

import org.junit.jupiter.api.Test;

public class SimpleClassMappingTest extends AbstractJacksonMappingTest {

    private static final String SFTP_BINDING_JSON = "" +
        "{\n" +
        "        \"protocol\": \"sftp\",\n" +
        "        \"username\": \"sftpuser\",\n" +
        "        \"password\": \"sftppass\",\n" +
        "        \"server-fqdn\": \"${pmc.ftp.host}\",\n" +
        "        \"server-port\": \"${pmc.ftp.port}\",\n" +
        "        \"default-directory\": \"/logs/upload/%s\"\n" +
        "      }";

    @Test
    public void mapAuthRealmFromJavaRoundTrip() throws IOException {
        BasicAuthRealm realm = new BasicAuthRealm();
        realm.setBaseUrl("http://example.org/");
        realm.setUsername("user");
        realm.setPassword("pass");
        realm.setRealmName("ream name");

        assertRoundTrip(realm, BasicAuthRealm.class);
    }

    @Test
    public void mapSftpBinding() throws IOException {
        SftpBinding sftpBinding = repositoriesMapper.readValue(SFTP_BINDING_JSON, SftpBinding.class);

        assertEquals("sftp", sftpBinding.getProtocol());
        assertEquals("sftpuser", sftpBinding.getUsername());
        assertEquals("sftppass", sftpBinding.getPassword());
        assertEquals("test-ftp-host", sftpBinding.getServerFqdn());
        assertEquals("test-ftp-port", sftpBinding.getServerPort());
        assertEquals("/logs/upload/%s", sftpBinding.getDefaultDirectory());
    }

}
