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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.impl.DefaultSftpClientFactory;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.transport.TransportResponse;
import org.eclipse.pass.deposit.transport.TransportSession;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
class SftpTransportSession implements TransportSession {

    private final Map<String, String> transportProps;

    SftpTransportSession(Map<String, String> transportProps) {
        this.transportProps = transportProps;
    }

    @Override
    public TransportResponse send(PackageStream packageStream, Map<String, String> metadata) {
        PackageStream.Metadata streamMetadata = packageStream.metadata();
        String fileName = streamMetadata.name();

        try (SshClient sshClient = SshClient.setUpDefaultClient()) {
            sshClient.start();
            HostConfigEntry hostConfigEntry = buildHostConfig();
            try (ClientSession clientSession = sshClient.connect(hostConfigEntry).verify().getClientSession()) {
                String password = transportProps.get(TRANSPORT_PASSWORD);
                clientSession.addPasswordIdentity(password);
                clientSession.auth().verify();
                return writeFile(clientSession, packageStream, fileName);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HostConfigEntry buildHostConfig() {
        String serverName = transportProps.get(TRANSPORT_SERVER_FQDN);
        String serverPort = transportProps.get(TRANSPORT_SERVER_PORT);
        String username = transportProps.get(TRANSPORT_USERNAME);
        HostConfigEntry hostConfigEntry = new HostConfigEntry();
        hostConfigEntry.setHostName(serverName);
        hostConfigEntry.setPort(Integer.parseInt(serverPort));
        hostConfigEntry.setUsername(username);
        return hostConfigEntry;
    }

    private TransportResponse writeFile(ClientSession clientSession, PackageStream packageStream, String fileName)
        throws IOException {
        try (SftpClient sftpClient =
                 DefaultSftpClientFactory.INSTANCE.createSftpClient(clientSession).singleSessionInstance()) {
            try (InputStream inputStream = packageStream.open()) {
                String baseDir = getBaseDir();
                createBaseDirIfNeeded(baseDir, sftpClient);
                try (OutputStream outputStream = sftpClient.write(baseDir + "/" + fileName)) {
                    IOUtils.copy(inputStream, outputStream);
                    return new SftpTransportResponse(true);
                }
            }
        }
    }

    private void createBaseDirIfNeeded(String path, SftpClient sftpClient) throws IOException {
        if (StringUtils.isNotEmpty(path)) {
            String[] dirs = path.split("/");
            StringBuilder fullPath = new StringBuilder();
            for (String dir : dirs) {
                if (!fullPath.isEmpty()) {
                    fullPath.append("/");
                }
                fullPath.append(dir);
                try {
                    sftpClient.stat(fullPath.toString());
                } catch (SftpException e) {
                    if (e.getStatus() != SftpConstants.SSH_FX_NO_SUCH_FILE) {
                        throw new RuntimeException(e);
                    }
                    sftpClient.mkdir(fullPath.toString());
                }
            }
        }
    }

    private String getBaseDir() {
        String baseDir = transportProps.get(SFTP_BASE_DIRECTORY);
        if (StringUtils.isNotEmpty(baseDir)) {
            return baseDir.contains("%s")
                ? String.format(baseDir, OffsetDateTime.now(ZoneId.of("UTC")).format(ISO_LOCAL_DATE))
                : baseDir;
        }
        return "";
    }

    @Override
    public boolean closed() {
        return true;
    }

    @Override
    public void close() throws Exception {
        // no-op resources are closed with try-with-resources
    }
}
