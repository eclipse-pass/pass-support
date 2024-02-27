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
package org.eclipse.pass.support.grant.data.jhu;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.SQLException;
import java.util.List;

import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Test class for the COEUS connector.  This is strictly a manual test for querying the Coeus database.
 * This test is Disabled, you can enable it and run each query test if needed for validation.
 * <p>
 * In order to run the tests, you must put a connection.properties file with valid url and creds in the
 * test/resources dir.
 *
 * @author jrm@jhu.edu
 */
@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
@Disabled
public class CoeusConnectorManualTest {

    @Autowired private CoeusConnector connector;

    @Disabled
    @Test
    public void testGrantQuery() throws SQLException {
        List<GrantIngestRecord> results =
            connector.retrieveUpdates("2023-10-20 00:00:00", "01/01/2011", "grant", null);
        assertNotNull(results);
    }

    @Disabled
    @Test
    public void testUserQuery() throws SQLException {
        List<GrantIngestRecord> results =
            connector.retrieveUpdates("2023-10-20 00:00:00", null, "user", null);
        assertNotNull(results);
    }

    @Disabled
    @Test
    public void testFunderQuery() throws SQLException {
        List<GrantIngestRecord> results =
            connector.retrieveUpdates(null, null, "funder", null);
        assertNotNull(results);
    }

}


