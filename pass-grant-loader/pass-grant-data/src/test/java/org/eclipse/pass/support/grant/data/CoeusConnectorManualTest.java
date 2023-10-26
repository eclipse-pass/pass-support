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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test class for the COEUS connector.  This is strictly a manual test for querying the Coeus database.
 * This test is Disabled, you can enable it and run each query test if needed for validation.
 * <p>
 * In order to run the tests, you must put a connection.properties file with valid url and creds in the
 * test/resources dir.
 *
 * @author jrm@jhu.edu
 */
@Disabled
public class CoeusConnectorManualTest {

    private CoeusConnector connector;

    private final File policyPropertiesFile = new File(
        getClass().getClassLoader().getResource("policy.properties").getFile());

    private final File connectionPropertiesFile = new File(
        getClass().getClassLoader().getResource("connection.properties").getFile());

    private final Properties policyProperties = new Properties();

    @BeforeEach
    public void setup() throws Exception {

        try (InputStream resourceStream = new FileInputStream(policyPropertiesFile)) {
            policyProperties.load(resourceStream);
        }
        Properties connectionProperties = new Properties();
        try (InputStream resourceStream = new FileInputStream(connectionPropertiesFile)) {
            connectionProperties.load(resourceStream);
        }
        connector = new CoeusConnector(
            connectionProperties, policyProperties);
    }

    @Disabled
    @Test
    public void testGrantQuery() throws SQLException {
        List<Map<String, String>> results =
            connector.retrieveUpdates("2023-10-20 00:00:00", "01/01/2011", "grant", null);
        assertNotNull(results);
    }

    @Disabled
    @Test
    public void testUserQuery() throws SQLException {
        List<Map<String, String>> results =
            connector.retrieveUpdates("2023-10-20 00:00:00", null, "user", null);
        assertNotNull(results);
    }

    @Disabled
    @Test
    public void testFunderQuery() throws SQLException {
        List<Map<String, String>> results =
            connector.retrieveUpdates(null, null, "funder", null);
        assertNotNull(results);
    }

}


