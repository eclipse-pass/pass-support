/*
 *
 *  * Copyright 2024 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package org.eclipse.pass.support.grant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest(classes = GrantLoaderCLI.class,
    args = {"--startDateTime=2024-01-01T00:00:00", "-awardEndDate=2025-01-01", "-action=pull", "test-pull-file.csv"})
@TestPropertySource(
    locations = "classpath:test-application.properties",
    properties = """
    grant.db.url=test-grant-db-url
    grant.db.username=test-grant-db-user
    grant.db.password=test-grant-db-pw
    """
)
public class GrantLoaderCLITest {

    @MockBean GrantLoaderApp grantLoaderApp;

    @Test
    public void testHarvesterCLI() throws PassCliException {
        // GIVEN/WHEN
        // THEN
        verify(grantLoaderApp).run(eq("2024-01-01T00:00:00"), eq("2025-01-01"), eq("grant"),
            eq("pull"), eq("test-pull-file.csv"), eq(null));
    }

}