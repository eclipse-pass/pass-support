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
package org.eclipse.pass.deposit.config.repository;

import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_AUTHMODE;
import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_PASSWORD;
import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_PROTOCOL;
import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_SERVER_FQDN;
import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_SERVER_PORT;
import static org.eclipse.pass.deposit.transport.Transport.TRANSPORT_USERNAME;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.pass.deposit.transport.Transport;
import org.eclipse.pass.deposit.transport.sftp.SftpTransport;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class SftpBinding extends ProtocolBinding {

    static final String PROTO = "sftp";

    private String username;

    private String password;

    @JsonProperty("default-directory")
    private String defaultDirectory;

    public SftpBinding() {
        this.setProtocol(PROTO);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDefaultDirectory() {
        return defaultDirectory;
    }

    public void setDefaultDirectory(String defaultDirectory) {
        this.defaultDirectory = defaultDirectory;
    }

    @Override
    public Map<String, String> asPropertiesMap() {
        Map<String, String> transportProperties = new HashMap<>();

        transportProperties.put(TRANSPORT_USERNAME, getUsername());
        transportProperties.put(TRANSPORT_PASSWORD, getPassword());
        transportProperties.put(TRANSPORT_AUTHMODE, Transport.AUTHMODE.userpass.name());
        transportProperties.put(TRANSPORT_PROTOCOL, Transport.PROTOCOL.sftp.name());
        transportProperties.put(TRANSPORT_SERVER_FQDN, getServerFqdn());
        transportProperties.put(TRANSPORT_SERVER_PORT, getServerPort());
        transportProperties.put(SftpTransport.SFTP_BASE_DIRECTORY, getDefaultDirectory());

        return transportProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SftpBinding that = (SftpBinding) o;
        return Objects.equals(username, that.username) &&
               Objects.equals(password, that.password) &&
               Objects.equals(defaultDirectory, that.defaultDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), username, password, defaultDirectory);
    }

    @Override
    public String toString() {
        return "SftpBinding{" + "username='" + username + '\'' + ", password='" +
               ((password != null) ? "xxxx" : "<null>") + '\'' +
               ", defaultDirectory='" + defaultDirectory + '\'' + "} " + super.toString();
    }
}
