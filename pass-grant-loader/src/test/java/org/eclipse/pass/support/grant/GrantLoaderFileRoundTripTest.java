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
package org.eclipse.pass.support.grant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.grant.data.PassUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class GrantLoaderFileRoundTripTest extends AbstractRoundTripTest {

    private static final Path TEST_CSV_PATH = Path.of("src/test/resources/test-pull.csv");
    private static final Path GRANT_UPTS_PATH = Path.of("src/test/resources/grant_update_timestamps");

    @Autowired private PassUpdater passUpdater;

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(TEST_CSV_PATH);
        Files.deleteIfExists(GRANT_UPTS_PATH);
    }

    @Test
    public void testRoundTripCvsFile() throws PassCliException, IOException {
        // GIVEN
        Policy policy = new Policy();
        policy.setTitle("test policy");
        passClient.createObject(policy);

        // Use data from JHU grant source system and write the CSV
        Files.createFile(TEST_CSV_PATH);
        Files.createFile(GRANT_UPTS_PATH);

        // WHEN
        grantLoaderApp.run("2011-01-01 00:00:00", "2011-01-01",
            "grant", "pull", "file:./src/test/resources/test-pull.csv", null);

        // THEN
        String expectedContent = Files.readString(Path.of("src/test/resources/expected-csv.csv"));
        String content = Files.readString(TEST_CSV_PATH);
        assertEquals(expectedContent, content);

        // WHEN
        // Use CSV file create above and load into PASS
        grantLoaderApp.run("", "2011-01-01", "grant",
            "load", "file:./src/test/resources/test-pull.csv", null);

        // THEN
        verifyGrantOne();
        verifyGrantTwo();

        String contentUpTs = Files.readString(GRANT_UPTS_PATH);
        assertEquals(passUpdater.getLatestUpdate() + System.lineSeparator(), contentUpTs);
    }

}
