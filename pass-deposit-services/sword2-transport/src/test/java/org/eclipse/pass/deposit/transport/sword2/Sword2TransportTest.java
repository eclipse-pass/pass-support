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

package org.eclipse.pass.deposit.transport.sword2;

import static org.eclipse.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_COLLECTION_URL;
import static org.eclipse.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_ON_BEHALF_OF_USER;
import static org.eclipse.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_SERVICE_DOC_URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.pass.deposit.transport.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.swordapp.client.AuthCredentials;
import org.swordapp.client.ProtocolViolationException;
import org.swordapp.client.SWORDClient;
import org.swordapp.client.SWORDClientException;
import org.swordapp.client.ServiceDocument;

public class Sword2TransportTest {

    private static final String SERVICE_DOC_URL = "http://localhost:8080/swordv2/servicedocument";

    private static final String COLLECTION_URL = "http://localhost:8080/swordv2/collection/1";

    private static final String USERNAME = "sworduser";

    private static final String PASSWORD = "swordpassword";

    private static final String ON_BEHALF_OF = "another_user";

    private static final Map<String, String> TRANSPORT_HINTS = Collections.unmodifiableMap(
        new HashMap<String, String>() {
            {
                put(SWORD_COLLECTION_URL, COLLECTION_URL);
                put(SWORD_ON_BEHALF_OF_USER, ON_BEHALF_OF);
                put(SWORD_SERVICE_DOC_URL, SERVICE_DOC_URL);
                put(Transport.TRANSPORT_AUTHMODE, Transport.AUTHMODE.userpass.name());
                put(Transport.TRANSPORT_USERNAME, USERNAME);
                put(Transport.TRANSPORT_PASSWORD, PASSWORD);
                put(Transport.TRANSPORT_PROTOCOL, Transport.PROTOCOL.SWORDv2.name());
            }
        });

    private SWORDClient swordClient;

    private Sword2Transport underTest;

    @BeforeEach
    public void setUp() throws Exception {
        ServiceDocument serviceDocument = mock(ServiceDocument.class);
        swordClient = mock(SWORDClient.class);
        Sword2ClientFactory clientFactory = mock(Sword2ClientFactory.class);

        when(swordClient.getServiceDocument(any(), any())).thenReturn(serviceDocument);
        when(clientFactory.newInstance(anyMap())).thenReturn(swordClient);

        underTest = new Sword2Transport(clientFactory);
    }

    @Test
    public void testNullFactory() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Sword2Transport(null);
        });
    }

    @Test
    public void testOpenMissingAuthUsernameKey() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            underTest.open(removeKey(Transport.TRANSPORT_USERNAME, TRANSPORT_HINTS));
        });
        assertEquals(String.format(Sword2Transport.MISSING_REQUIRED_HINT, Transport.TRANSPORT_USERNAME), ex.getMessage());
    }

    @Test
    public void testOpenMissingAuthPasswordKey() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            underTest.open(removeKey(Transport.TRANSPORT_PASSWORD, TRANSPORT_HINTS));
        });
        assertEquals(String.format(Sword2Transport.MISSING_REQUIRED_HINT, Transport.TRANSPORT_PASSWORD), ex.getMessage());
    }

    @Test
    public void testOpenMissingAuthModeKey() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            underTest.open(removeKey(Transport.TRANSPORT_AUTHMODE, TRANSPORT_HINTS));
        });
        assertEquals("This transport only supports AUTHMODE userpass (was: 'null'", ex.getMessage());
    }

    @Test
    public void testOpenUnsupportedAuthMode() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            underTest.open(replaceKey(Transport.TRANSPORT_AUTHMODE, "fooAuthMode", TRANSPORT_HINTS));
        });
        assertEquals("This transport only supports AUTHMODE userpass (was: 'fooAuthMode'", ex.getMessage());
    }

    @Test
    public void testOpenAuthenticationCredentials() throws Exception {
        Sword2TransportSession session = underTest.open(TRANSPORT_HINTS);
        assertNotNull(session.getAuthCreds());
        AuthCredentials authCredentials = session.getAuthCreds();

        assertEquals(USERNAME, authCredentials.getUsername());
        assertEquals(PASSWORD, authCredentials.getPassword());
        assertEquals(ON_BEHALF_OF, authCredentials.getOnBehalfOf());
    }

    @Test
    public void testGetServiceDocumentThrowsRuntimeException() throws Exception {
        when(swordClient.getServiceDocument(any(), any())).thenThrow(new RuntimeException());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            underTest.open(TRANSPORT_HINTS);
        });
        assertEquals("Error reading or parsing SWORD service document " +
            "'http://localhost:8080/swordv2/servicedocument'", ex.getMessage());

    }

    @Test
    public void testGetServiceDocumentThrowsSWORDClientException() throws Exception {
        when(swordClient.getServiceDocument(any(), any())).thenThrow(mock(SWORDClientException.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            underTest.open(TRANSPORT_HINTS);
        });
        assertEquals(ex.getCause().getClass(), SWORDClientException.class);
    }

    @Test
    public void testGetServiceDocumentThrowsProtocolViolationException() throws Exception {
        when(swordClient.getServiceDocument(any(), any())).thenThrow(mock(ProtocolViolationException.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            underTest.open(TRANSPORT_HINTS);
        });
        assertEquals(ex.getCause().getClass(), ProtocolViolationException.class);
    }

    /**
     * Returns a new map that omits the supplied {@code key} from {@code map}.
     *
     * @param key a key that may occur in {@code map}, to be omitted from the returned map.
     * @param map a map that may contain the supplied {@code key}
     * @return a new {@code Map} that does not contain {@code key}
     */
    private static Map<String, String> removeKey(String key, Map<String, String> map) {
        return map.entrySet()
                  .stream()
                  .filter((entry) -> !entry.getKey().equals(key))
                  .collect(
                      Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Returns a new map that replaces the supplied {@code key} in {@code map}.  If the {@code key} does not exist
     * in {@code map}, it is added in the returned {@code Map}.
     *
     * @param key      a key that may occur in {@code map}, whose value is replaced in the returned {@code Map}
     * @param newValue the new value of {@code key}
     * @param map      a map that may contain the supplied {@code key}
     * @return a new {@code Map} that contains {@code key} mapped to {@code newValue}
     */
    private static Map<String, String> replaceKey(String key, String newValue, Map<String, String> map) {
        Map<String, String> result = removeKey(key, map);
        result.put(key, newValue);

        return result;
    }
}