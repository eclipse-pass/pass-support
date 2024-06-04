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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;

import org.eclipse.pass.support.grant.GrantLoaderCLIRunner;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author jrm@jhu.edu
 */
@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
public class JhuGrantDbConnectorTest {

    @MockBean protected GrantLoaderCLIRunner grantLoaderCLIRunner;
    @Autowired private JhuGrantDbConnector connector;

    @Test
    public void testGrantQuery() throws SQLException {
        List<GrantIngestRecord> results =
            connector.retrieveUpdates("2011-01-01 00:00:00", "2011-01-01", "grant", null);
        List<String> actualAwardNums = results.stream().map(GrantIngestRecord::getAwardNumber).distinct().toList();
        assertEquals(2, actualAwardNums.size());
        assertTrue(actualAwardNums.contains("B10000000"));
        assertTrue(actualAwardNums.contains("B10000003"));
    }

    @Test
    public void testGrantQueryStartDate() throws SQLException {
        List<GrantIngestRecord> results =
            connector.retrieveUpdates("2023-03-01 00:00:00", "2011-01-01", "grant", null);
        List<String> actualAwardNums = results.stream().map(GrantIngestRecord::getAwardNumber).distinct().toList();
        assertEquals(1, actualAwardNums.size());
        assertTrue(actualAwardNums.contains("B10000000"));
    }

    @Test
    public void testGrantQueryAwardEndDate() throws SQLException {
        List<GrantIngestRecord> results =
            connector.retrieveUpdates("2011-01-01 00:00:00", "2024-01-01", "grant", null);
        List<String> actualAwardNums = results.stream().map(GrantIngestRecord::getAwardNumber).distinct().toList();
        assertEquals(1, actualAwardNums.size());
        assertTrue(actualAwardNums.contains("B10000000"));
    }

    @Test
    public void testUserQuery() throws SQLException {
        List<GrantIngestRecord> results =
            connector.retrieveUpdates("2023-03-01 00:00:00", null, "user", null);
        assertEquals(1, results.size());
        assertEquals("sclass1@jhu.edu", results.get(0).getPiEmail());
    }

    @Test
    public void testFunderQuery() throws SQLException {
        List<GrantIngestRecord> results =
            connector.retrieveUpdates(null, null, "funder", null);
        assertEquals(2, results.size());
        List<String> actualNames = results.stream().map(GrantIngestRecord::getPrimaryFunderName).toList();
        assertTrue(actualNames.contains("Sponsor 1"));
        assertTrue(actualNames.contains("Sponsor 2"));
    }

}
