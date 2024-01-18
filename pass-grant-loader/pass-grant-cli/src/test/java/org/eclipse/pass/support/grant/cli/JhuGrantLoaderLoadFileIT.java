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

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class JhuGrantLoaderLoadFileIT {

    private final PassClient passClient = PassClient.newInstance();

    @Disabled
    @Test
    public void testLoadCvsFile() throws IOException {
        System.setProperty(
                "COEUS_HOME",
                "src/test/resources"
        );
        String[] args = {"-a", "load", "src/test/resources/test-load.csv"};
        JhuGrantLoaderCLI.main(args);

        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", "johnshopkins.edu:grant:123456"));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);
        assertNotNull(passGrant);
    }

}
