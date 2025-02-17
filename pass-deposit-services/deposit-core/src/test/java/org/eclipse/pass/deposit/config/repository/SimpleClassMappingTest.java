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

public class SimpleClassMappingTest extends AbstractJacksonMappingTest {
    private static final String AUTH_REALM_JSON = "" +
                                                  "{\n" +
                                                  "          \"mech\": \"basic\",\n" +
                                                  "          \"username\": \"user\",\n" +
                                                  "          \"password\": \"pass\",\n" +
                                                  "          \"url\": \"https://jscholarship.library.jhu.edu/\",\n" +
                                                  "          \"realm-name\": \"foo realm\"\n" +
                                                  "        }";

    private static final String SWORD_BINDING_JSON = "" +
                                                     "{\n" +
                                                     "        \"protocol\": \"SWORDv2\",\n" +
                                                     "        \"username\": \"sworduser\",\n" +
                                                     "        \"password\": \"swordpass\",\n" +
                                                     "        \"service-doc\": \"http://${dspace.server}" +
                                                     "/swordv2/servicedocument\",\n" +
                                                     "        \"default-collection\": \"http://${dspace.server}" +
                                                     "/swordv2/collection/123456789/2\",\n" +
                                                     "        \"on-behalf-of\": null,\n" +
                                                     "        \"deposit-receipt\": true,\n" +
                                                     "        \"user-agent\": \"pass-deposit/x.y.z\"\n" +
                                                     "      }";

    private static final String SFTP_BINDING_JSON = "" +
        "{\n" +
        "        \"protocol\": \"sftp\",\n" +
        "        \"username\": \"sftpuser\",\n" +
        "        \"password\": \"sftppass\",\n" +
        "        \"server-fqdn\": \"${pmc.ftp.host}\",\n" +
        "        \"server-port\": \"${pmc.ftp.port}\",\n" +
        "        \"default-directory\": \"/logs/upload/%s\"\n" +
        "      }";


    private static final String J10P_STATUS_MAPPING_JSON = "" +
                                                           "{\n" +
                                                           "      \"http://dspace.org/state/archived\": " +
                                                           "\"http://oapass.org/status/deposit#accepted\",\n" +
                                                           "      \"http://dspace.org/state/withdrawn\": " +
                                                           "\"http://oapass.org/status/deposit#rejected\",\n" +
                                                           "      \"default-mapping\": \"http://oapass" +
                                                           ".org/status/deposit#submitted\"\n" +
                                                           "    }";

    @Test
    public void mapAuthRealmFromJson() throws IOException {
        BasicAuthRealm realm = repositoriesMapper.readValue(AUTH_REALM_JSON, BasicAuthRealm.class);

        assertNotNull(realm);
        assertEquals("basic", realm.getMech());
        assertEquals("user", realm.getUsername());
        assertEquals("pass", realm.getPassword());
        assertEquals("https://jscholarship.library.jhu.edu/", realm.getBaseUrl().toString());
        assertEquals("foo realm", realm.getRealmName());
    }

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
    public void mapAuthRealmFromJsonRoundTrip() throws IOException {
        BasicAuthRealm realm = repositoriesMapper.readValue(AUTH_REALM_JSON, BasicAuthRealm.class);

        assertRoundTrip(realm, BasicAuthRealm.class);
    }

    @Test
    public void mapSwordBinding() throws IOException {
        SwordV2Binding swordBinding = repositoriesMapper.readValue(SWORD_BINDING_JSON, SwordV2Binding.class);

        assertNotNull(swordBinding);
        assertEquals("SWORDv2", swordBinding.getProtocol());
        assertEquals("sworduser", swordBinding.getUsername());
        assertEquals("swordpass", swordBinding.getPassword());
        assertEquals("http://test-dspace-host:8000/swordv2/servicedocument",
            swordBinding.getServiceDocUrl());
        assertEquals("http://test-dspace-host:8000/swordv2/collection/123456789/2",
                     swordBinding.getDefaultCollectionUrl());
        assertNull(swordBinding.getOnBehalfOf());
        assertTrue(swordBinding.isDepositReceipt());
        assertEquals("pass-deposit/x.y.z", swordBinding.getUserAgent());
    }

    @Test
    public void mapSwordBindingFromJavaRoundTrip() throws IOException {
        SwordV2Binding swordBinding = new SwordV2Binding();
        swordBinding.setDefaultCollectionUrl("http://example.org/sword/collection/1");
        swordBinding.setDepositReceipt(true);
        swordBinding.setOnBehalfOf(null);
        swordBinding.setUsername("user");
        swordBinding.setPassword("pass");
        swordBinding.setServiceDocUrl("http://example.org/sword/servicedoc");
        swordBinding.setUserAgent("custom-user-agent-string");

        assertRoundTrip(swordBinding, SwordV2Binding.class);
    }

    @Test
    public void mapSwordBindingFromJsonRoundTrip() throws IOException {
        SwordV2Binding swordBinding = repositoriesMapper.readValue(SWORD_BINDING_JSON, SwordV2Binding.class);

        assertRoundTrip(swordBinding, SwordV2Binding.class);
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

    @Test
    public void mapJ10PStatus() throws IOException {
        StatusMapping mapping = repositoriesMapper.readValue(J10P_STATUS_MAPPING_JSON, StatusMapping.class);

        assertNotNull(mapping);
        assertEquals("http://oapass.org/status/deposit#submitted", mapping.getDefaultMapping());
        assertEquals("http://oapass.org/status/deposit#rejected",
                     mapping.getStatusMap().get("http://dspace.org/state/withdrawn"));
        assertEquals("http://oapass.org/status/deposit#accepted",
                     mapping.getStatusMap().get("http://dspace.org/state/archived"));
    }

    @Test
    public void mapJ10PStatusFromJavaRoundTrip() throws IOException {
        StatusMapping mapping = new StatusMapping();
        mapping.setDefaultMapping("http://oapass.org/status/deposit#submitted");
        mapping.addStatusEntry("http://dspace.org/state/withdrawn", "http://oapass.org/status/deposit#rejected");
        mapping.addStatusEntry("http://dspace.org/state/archived", "http://oapass.org/status/deposit#accepted");

        assertRoundTrip(mapping, StatusMapping.class);
    }

    @Test
    public void mapJ10SatusFromJsonRoundTrip() throws IOException {
        StatusMapping mapping = repositoriesMapper.readValue(J10P_STATUS_MAPPING_JSON, StatusMapping.class);

        assertRoundTrip(mapping, StatusMapping.class);
    }

}
