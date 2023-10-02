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
package org.eclipse.pass.support.grant.data;

import static org.eclipse.pass.support.grant.data.DirectoryServiceUtil.DIRECTORY_SERVICE_BASE_URL;
import static org.eclipse.pass.support.grant.data.DirectoryServiceUtil.DIRECTORY_SERVICE_CLIENT_ID;
import static org.eclipse.pass.support.grant.data.DirectoryServiceUtil.DIRECTORY_SERVICE_CLIENT_SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


/**
 * This is a test class for a simple directory lookup service running at the endpoint specified by "serviceUrl" below
 * the service type completes the URL, and the client id and client secret are supplied as headers.
 * <p>
 * values for serviceUrl, clientId and clientSecret, must be supplied below.
 * <p>
 * This test has been run against the running service with valid parameters and arguments supplied to the methods -
 * this class has been
 * cleaned up after successful testing. because of the simplicity and isolation of this class, it does not need to be
 * tested
 * every build - just when something about the service changes. so we ignore it for now.
 * <p>
 * To test, provide real connection parameter values, and a real kopkins id / employee id pair
 *
 * @author jrm
 */
@Disabled
public class DirectoryServiceUtilTest {
    private DirectoryServiceUtil underTest;

    @BeforeEach
    public void setup() {
        final String serviceUrl = "https://the.service/url";
        final String clientId = "the-client-id";
        final String clientSecret = "the-client-secret";

        Properties connectionProperties = new Properties();
        connectionProperties.setProperty(DIRECTORY_SERVICE_BASE_URL, serviceUrl);
        connectionProperties.setProperty(DIRECTORY_SERVICE_CLIENT_ID, clientId);
        connectionProperties.setProperty(DIRECTORY_SERVICE_CLIENT_SECRET, clientSecret);
        underTest = new DirectoryServiceUtil(connectionProperties);
    }

    @Test
    public void testGetHopkinsId() throws java.io.IOException {
        String result = underTest.getHopkinsIdForEmployeeId("test-emp-id");
        assertEquals("actual-hopkins-id", result);
    }

}
