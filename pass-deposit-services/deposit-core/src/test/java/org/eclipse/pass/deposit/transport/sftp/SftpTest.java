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

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_PASSWORD;
import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_SERVER_FQDN;
import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_SERVER_PORT;
import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_USERNAME;
import static org.eclipse.pass.deposit.transport.sftp.SftpTransport.SFTP_BASE_DIRECTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.input.NullInputStream;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.StaticPasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.impl.DefaultSftpClientFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.transport.TransportResponse;
import org.eclipse.pass.deposit.transport.TransportSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class SftpTest {
    private final static long ONE_MIB = 2 ^ 20;

    private static SshServer sshd;

    @BeforeAll
    public static void setup() throws IOException {
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost("localhost");
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setPasswordAuthenticator(new StaticPasswordAuthenticator(true));
        sshd.start();

    }

    @AfterAll
    public static void tearDown() throws IOException {
        String dateDirName = OffsetDateTime.now(ZoneId.of("UTC")).format(ISO_LOCAL_DATE);
        deleteDirOnSftpServer(dateDirName);
        sshd.stop();
    }

    @Test
    public void testCreateFile() {
        // GIVEN
        Map<String, String> hints = Map.of(
            TRANSPORT_SERVER_FQDN, "localhost",
            TRANSPORT_SERVER_PORT, String.valueOf(sshd.getPort()),
            TRANSPORT_USERNAME, "dummyUser",
            TRANSPORT_PASSWORD, "dummyPass",
            SFTP_BASE_DIRECTORY, "/upload/test/%s"
        );
        String testFileName = System.currentTimeMillis() + "package.tar.gz";
        NullInputStream content = new NullInputStream(ONE_MIB);
        PackageStream stream = mock(PackageStream.class);
        PackageStream.Metadata streamMetadata = mock(PackageStream.Metadata.class);
        when(stream.metadata()).thenReturn(streamMetadata);
        when(streamMetadata.name()).thenReturn(testFileName);
        when(stream.open()).thenReturn(content);

        SftpTransport sftpTransport = new SftpTransport();
        TransportSession transportSession = sftpTransport.open(hints);

        // WHEN
        TransportResponse transportResponse = transportSession.send(stream, new HashMap<>());

        // THEN
        assertTrue(transportResponse.success());
        String dateDirName = OffsetDateTime.now(ZoneId.of("UTC")).format(ISO_LOCAL_DATE);
        verifyFileOnSftpServer(String.format("upload/test/%s/", dateDirName) + testFileName);
    }

    @Test
    public void testCreateFile_Fail_MissingBaseDir() {
        // GIVEN
        Map<String, String> hints = Map.of(
            TRANSPORT_SERVER_FQDN, "localhost",
            TRANSPORT_SERVER_PORT, String.valueOf(sshd.getPort()),
            TRANSPORT_USERNAME, "dummyUser",
            TRANSPORT_PASSWORD, "dummyPass"
        );
        String testFileName = System.currentTimeMillis() + "package.tar.gz";
        NullInputStream content = new NullInputStream(ONE_MIB);
        PackageStream stream = mock(PackageStream.class);
        PackageStream.Metadata streamMetadata = mock(PackageStream.Metadata.class);
        when(stream.metadata()).thenReturn(streamMetadata);
        when(streamMetadata.name()).thenReturn(testFileName);
        when(stream.open()).thenReturn(content);

        SftpTransport sftpTransport = new SftpTransport();
        TransportSession transportSession = sftpTransport.open(hints);

        // WHEN
        DepositServiceRuntimeException ex = assertThrows(DepositServiceRuntimeException.class, () -> {
            transportSession.send(stream, new HashMap<>());
        });

        // THEN
        assertEquals("Sftp requires \"default-directory\" in protocol-binding", ex.getMessage());
    }

    private void verifyFileOnSftpServer(String path) {
        try (SshClient sshClient = SshClient.setUpDefaultClient()) {
            sshClient.start();
            HostConfigEntry hostConfigEntry = getHostConfig();
            try (ClientSession clientSession = sshClient.connect(hostConfigEntry).verify().getClientSession()) {
                clientSession.addPasswordIdentity("dummyPass");
                clientSession.auth().verify();
                try (SftpClient sftpClient =
                         DefaultSftpClientFactory.INSTANCE.createSftpClient(clientSession).singleSessionInstance()) {
                    sftpClient.stat(path);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteDirOnSftpServer(String dateDirName) {
        try (SshClient sshClient = SshClient.setUpDefaultClient()) {
            sshClient.start();
            HostConfigEntry hostConfigEntry = getHostConfig();
            try (ClientSession clientSession = sshClient.connect(hostConfigEntry).verify().getClientSession()) {
                clientSession.addPasswordIdentity("dummyPass");
                clientSession.auth().verify();
                try (SftpClient sftpClient =
                         DefaultSftpClientFactory.INSTANCE.createSftpClient(clientSession).singleSessionInstance()) {
                    try (SftpClient.CloseableHandle handle = sftpClient.openDir("upload/test/" + dateDirName)) {
                        for (SftpClient.DirEntry dirEntry : sftpClient.listDir(handle)) {
                            if (dirEntry.getAttributes().isRegularFile()) {
                                sftpClient.remove("upload/test/" + dateDirName + "/" + dirEntry.getFilename());
                            }
                        }
                    }
                    sftpClient.rmdir("upload/test/" + dateDirName);
                    sftpClient.rmdir("upload/test");
                    sftpClient.rmdir("upload");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static HostConfigEntry getHostConfig() {
        HostConfigEntry hostConfigEntry = new HostConfigEntry();
        hostConfigEntry.setHostName("localhost");
        hostConfigEntry.setPort(sshd.getPort());
        hostConfigEntry.setUsername("dummyUser");
        return hostConfigEntry;
    }
}
