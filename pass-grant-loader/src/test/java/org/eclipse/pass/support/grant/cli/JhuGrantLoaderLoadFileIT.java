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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.grant.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class JhuGrantLoaderLoadFileIT extends AbstractIntegrationTest {

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(Path.of("src/test/resources/grant_update_timestamps"));
    }

    @Test
    public void testLoadCvsFile() throws IOException, PassCliException {
        Policy policy = new Policy();
        policy.setTitle("test policy");
        passClient.createObject(policy);

        System.setProperty(
                "COEUS_HOME",
                "src/test/resources"
        );
        JhuGrantLoaderApp app = new JhuGrantLoaderApp("", "01/01/2011", false,
            "grant", "load", "src/test/resources/test-load.csv", false, null);
        app.run();

        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", "johnshopkins.edu:grant:138058"));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant1 = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant1.getTotal());
        Grant passGrant1 = resultGrant1.getObjects().get(0);
        assertNotNull(passGrant1.getId());
        assertEquals("johnshopkins.edu:grant:138058", passGrant1.getLocalKey());
        assertEquals("625628", passGrant1.getAwardNumber());
        assertEquals("NPTX2: Preserving memory circuits in normative aging and Alzheimer's disease",
            passGrant1.getProjectName());
        assertEquals(AwardStatus.ACTIVE, passGrant1.getAwardStatus());
        assertEquals("2021-09-29T00:00Z", passGrant1.getAwardDate().toString());
        assertEquals("2021-05-01T09:00Z", passGrant1.getStartDate().toString());
        assertEquals("2026-04-30T14:00Z", passGrant1.getEndDate().toString());

        Funder primaryFunder = passClient.getObject(passGrant1.getPrimaryFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:300869", primaryFunder.getLocalKey());
        assertEquals("NATIONAL INSTITUTE ON AGING", primaryFunder.getName());
        assertEquals("1", primaryFunder.getPolicy().getId());

        Funder directFunder = passClient.getObject(passGrant1.getDirectFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:301313", directFunder.getLocalKey());
        assertEquals("UNIV OF ARIZONA",directFunder.getName());
        assertEquals("1", directFunder.getPolicy().getId());

        assertEquals("UserOneFn", passGrant1.getPi().getFirstName());
        assertEquals("UserOneMn", passGrant1.getPi().getMiddleName());
        assertEquals("UserOneLn", passGrant1.getPi().getLastName());
        assertEquals("userone@jhu.edu", passGrant1.getPi().getEmail());
        assertEquals(List.of("johnshopkins.edu:employeeid:123456", "johnshopkins.edu:eppn:userone"),
            passGrant1.getPi().getLocatorIds());

        assertEquals(1, passGrant1.getCoPis().size());
        assertEquals("UserThreeFn", passGrant1.getCoPis().get(0).getFirstName());
        assertEquals("", passGrant1.getCoPis().get(0).getMiddleName());
        assertEquals("UserThreeLn", passGrant1.getCoPis().get(0).getLastName());
        assertEquals("userthree@jhu.edu", passGrant1.getCoPis().get(0).getEmail());
        assertEquals(List.of("johnshopkins.edu:employeeid:789123"),
            passGrant1.getCoPis().get(0).getLocatorIds());

        grantSelector.setFilter(RSQL.equals("localKey", "johnshopkins.edu:grant:130823"));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant2 = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant2.getTotal());
        Grant passGrant2 = resultGrant2.getObjects().get(0);
        assertNotNull(passGrant2);
    }

}
