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
package org.eclipse.pass.support.grant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Grant;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
public class GrantLoaderManualTest {

    @Autowired private GrantLoaderApp grantLoaderApp;
    @MockBean private GrantLoaderCLIRunner grantLoaderCLIRunner;

    /**
     * This is a manual test that can run locally to test pulling the grant data into a file.
     * You also need the grant db connection props in the test-application.properties file.
     * Be careful with the startDateTime to no pull too much data.  Know what the impact is on pulling
     * data before running this test.
     */
    @Disabled
    @Test
    public void testPullGrantFile() throws PassCliException {
        grantLoaderApp.run("2023-04-01 00:00:00", "04/01/2023",
            "grant", "pull", "file:./src/test/resources/your-file.csv", null);
    }

    /**
     * This is a manual test that can run locally to test loading the grant data.
     * You also need to set the test pass.core props in the test-application.properties file.
     */
    @Disabled
    @Test
    public void testLoadGrantFile() throws PassCliException {
        grantLoaderApp.run("2023-04-01 00:00:00", "04/01/2023",
            "grant", "load", "file:./src/test/resources/your-file.csv", null);
    }

    @Disabled
    @Test
    void testCheckGrant() throws IOException {
        System.setProperty("pass.core.url","http://localhost:8080");
        System.setProperty("pass.core.user","<test_user>");
        System.setProperty("pass.core.password","<test_pw>");
        PassClient passClient = PassClient.newInstance();

        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", "johnshopkins.edu:grant:143377"));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);
        assertNotNull(passGrant);
    }

}
