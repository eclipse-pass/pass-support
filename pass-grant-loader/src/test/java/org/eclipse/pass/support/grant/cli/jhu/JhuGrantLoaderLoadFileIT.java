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
package org.eclipse.pass.support.grant.cli.jhu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.grant.AbstractIntegrationTest;
import org.eclipse.pass.support.grant.cli.PassCliException;
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
        // GIVEN
        Policy policy = new Policy();
        policy.setTitle("test policy");
        passClient.createObject(policy);

        System.setProperty("APP_HOME", "src/test/resources");
        JhuGrantLoaderApp app = new JhuGrantLoaderApp("", "01/01/2011", "grant",
            "load", "src/test/resources/test-load.csv", false, null);

        // WHEN
        app.run();

        // THEN
        verifyGrantOne();
        verifyGrantTwo();
    }

    private void verifyGrantOne() throws IOException {
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", "johnshopkins.edu:grant:138058"));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);
        assertNotNull(passGrant.getId());
        assertEquals("johnshopkins.edu:grant:138058", passGrant.getLocalKey());
        assertEquals("625628", passGrant.getAwardNumber());
        assertEquals("NPTX2: Preserving memory circuits in normative aging and Alzheimer's disease",
            passGrant.getProjectName());
        assertEquals(AwardStatus.ACTIVE, passGrant.getAwardStatus());
        assertEquals("2021-09-29T00:00Z", passGrant.getAwardDate().toString());
        assertEquals("2021-05-01T09:00Z", passGrant.getStartDate().toString());
        assertEquals("2026-04-30T14:00Z", passGrant.getEndDate().toString());

        Funder primaryFunder = passClient.getObject(passGrant.getPrimaryFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:301313", primaryFunder.getLocalKey());
        assertEquals("UNIV OF ARIZONA", primaryFunder.getName());
        assertEquals("1", primaryFunder.getPolicy().getId());

        Funder directFunder = passClient.getObject(passGrant.getDirectFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:301313", directFunder.getLocalKey());
        assertEquals("UNIV OF ARIZONA",directFunder.getName());
        assertEquals("1", directFunder.getPolicy().getId());

        assertEquals("UserOneFn", passGrant.getPi().getFirstName());
        assertEquals("UserOneMn", passGrant.getPi().getMiddleName());
        assertEquals("UserOneLn", passGrant.getPi().getLastName());
        assertEquals("userone@jhu.edu", passGrant.getPi().getEmail());
        assertEquals(List.of("johnshopkins.edu:employeeid:123456", "johnshopkins.edu:eppn:userone"),
            passGrant.getPi().getLocatorIds());

        assertEquals(1, passGrant.getCoPis().size());
        assertEquals("UserThreeFn", passGrant.getCoPis().get(0).getFirstName());
        assertNull(passGrant.getCoPis().get(0).getMiddleName());
        assertEquals("UserThreeLn", passGrant.getCoPis().get(0).getLastName());
        assertEquals("userthree@jhu.edu", passGrant.getCoPis().get(0).getEmail());
        assertEquals(List.of("johnshopkins.edu:employeeid:789123"),
            passGrant.getCoPis().get(0).getLocatorIds());
    }

    private void verifyGrantTwo() throws IOException {
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", "johnshopkins.edu:grant:130823"));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant1 = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant1.getTotal());
        Grant passGrant = resultGrant1.getObjects().get(0);
        assertNotNull(passGrant.getId());
        assertEquals("johnshopkins.edu:grant:130823", passGrant.getLocalKey());
        assertEquals("107-33664-1000055108", passGrant.getAwardNumber());
        assertEquals("The Subclinical Vascular Contributions to Alzheimer's Disease: " +
                "The Multi-Ethnic Study of Atherosclerosis (MESA)", passGrant.getProjectName());
        assertEquals(AwardStatus.ACTIVE, passGrant.getAwardStatus());
        assertEquals("2018-12-14T00:00Z", passGrant.getAwardDate().toString());
        assertEquals("2018-08-15T10:00Z", passGrant.getStartDate().toString());
        assertEquals("2025-05-31T00:00Z", passGrant.getEndDate().toString());

        Funder primaryFunder = passClient.getObject(passGrant.getPrimaryFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:300865", primaryFunder.getLocalKey());
        assertEquals("NATIONAL INSTITUTES OF HEALTH", primaryFunder.getName());
        assertEquals("1", primaryFunder.getPolicy().getId());

        Funder directFunder = passClient.getObject(passGrant.getDirectFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:304106", directFunder.getLocalKey());
        assertEquals("WAKE FOREST UNIV",directFunder.getName());
        assertEquals("1", directFunder.getPolicy().getId());

        assertEquals("UserTwoFn", passGrant.getPi().getFirstName());
        assertEquals("UserTwoMn", passGrant.getPi().getMiddleName());
        assertEquals("UserTwoLn", passGrant.getPi().getLastName());
        assertEquals("usertwo@jhu.edu", passGrant.getPi().getEmail());
        assertEquals(List.of("johnshopkins.edu:eppn:usertwo"), passGrant.getPi().getLocatorIds());

        assertEquals(3, passGrant.getCoPis().size());
        User copi1 = passGrant.getCoPis().stream().filter(copi -> copi.getEmail().equals("userone@jhu.edu"))
            .findFirst().get();
        assertEquals("UserOneFn", copi1.getFirstName());
        assertEquals("UserOneMn", copi1.getMiddleName());
        assertEquals("UserOneLn", copi1.getLastName());
        assertEquals("userone@jhu.edu", copi1.getEmail());
        assertEquals(List.of("johnshopkins.edu:employeeid:123456", "johnshopkins.edu:eppn:userone"),
            copi1.getLocatorIds());

        User copi2 = passGrant.getCoPis().stream().filter(copi -> copi.getEmail().equals("userthree@jhu.edu"))
            .findFirst().get();
        assertEquals("UserThreeFn", copi2.getFirstName());
        assertNull(copi2.getMiddleName());
        assertEquals("UserThreeLn", copi2.getLastName());
        assertEquals("userthree@jhu.edu", copi2.getEmail());
        assertEquals(List.of("johnshopkins.edu:employeeid:789123"), copi2.getLocatorIds());

        User copi3 = passGrant.getCoPis().stream().filter(copi -> copi.getEmail().equals("userfour@jhu.edu"))
            .findFirst().get();
        assertEquals("UserFourFn", copi3.getFirstName());
        assertEquals("UserFourMn", copi3.getMiddleName());
        assertEquals("UserFourLn", copi3.getLastName());
        assertEquals("userfour@jhu.edu", copi3.getEmail());
        assertEquals(List.of("johnshopkins.edu:eppn:userfour"), copi3.getLocatorIds());
    }

}
