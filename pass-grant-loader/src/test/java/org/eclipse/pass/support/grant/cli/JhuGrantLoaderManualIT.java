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
package org.eclipse.pass.support.grant.cli;

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

public class JhuGrantLoaderManualIT {

    /**
     * This is a manual test that can run locally to test loading the COEUS data.
     * You need to set the system prop COEUS_HOME to the path of the test/resouces dir.
     * You also need the connection.properties file in test/resources which contains connection props
     * for COEUS and the Directory Service.
     * Be careful with the startDateTime to no pull too much data.  Know what the impact is on pulling
     * data before running this test.
     */
    @Disabled
    @Test
    public void testLoadCoeusFile() {
        System.setProperty(
                "COEUS_HOME",
                "full_path_to/pass-support/pass-grant-loader/pass-grant-cli/src/test/resources"
        );
        String[] args = {"-a", "load", "full_path_to/testresults"};
        JhuGrantLoaderCLI.main(args);
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
