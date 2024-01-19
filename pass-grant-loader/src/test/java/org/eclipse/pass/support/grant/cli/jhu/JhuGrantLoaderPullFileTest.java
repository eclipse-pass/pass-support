/*
 * Copyright 2024 Johns Hopkins University
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
package org.eclipse.pass.support.grant.cli.jhu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.eclipse.pass.support.grant.TestUtil;
import org.eclipse.pass.support.grant.cli.PassCliException;
import org.eclipse.pass.support.grant.data.GrantConnector;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ExtendWith(MockitoExtension.class)
public class JhuGrantLoaderPullFileTest {

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(Path.of("src/test/resources/test-pull.csv"));
    }

    @Test
    public void testPullCvsFile() throws PassCliException, SQLException, IOException {
        // GIVEN
        System.setProperty("COEUS_HOME", "src/test/resources");
        Files.createFile(Path.of("src/test/resources/test-pull.csv"));
        JhuGrantLoaderApp app = new JhuGrantLoaderApp("2011-01-01 00:00:00", "01/01/2011", "grant",
            "pull", "src/test/resources/test-pull.csv", false, null);
        JhuGrantLoaderApp spyApp = spy(app);
        GrantConnector mockGrantConnector = mock(GrantConnector.class);
        doReturn(mockGrantConnector).when(spyApp).configureConnector(any());

        List<GrantIngestRecord> grantIngestRecordList = getTestIngestRecords();

        doReturn(grantIngestRecordList).when(mockGrantConnector).retrieveUpdates(anyString(), anyString(), anyString(),
            any(), any());
        // WHEN
        spyApp.run();

        // THEN
        String expectedContent = Files.readString(Path.of("src/test/resources/expected-csv.csv"));
        String content = Files.readString(Path.of("src/test/resources/test-pull.csv"));
        assertEquals(expectedContent, content);
    }

    private List<GrantIngestRecord> getTestIngestRecords() {
        GrantIngestRecord piRecord1 = TestUtil.makeGrantIngestRecord(0, 0, "P");
        GrantIngestRecord coPiRecord1 = TestUtil.makeGrantIngestRecord(0, 1, "C");
        GrantIngestRecord piRecord2 = TestUtil.makeGrantIngestRecord(3, 1, "P");
        return List.of(piRecord1, coPiRecord1, piRecord2);
    }

}
