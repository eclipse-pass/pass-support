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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.List;

import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public abstract class AbstractRoundTripTest extends AbstractIntegrationTest {

    protected void verifyGrantOne() throws IOException {
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", "johnshopkins.edu:grant:10000001"));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant.getTotal());
        Grant passGrant = resultGrant.getObjects().get(0);
        assertNotNull(passGrant.getId());
        assertEquals("johnshopkins.edu:grant:10000001", passGrant.getLocalKey());
        assertEquals("B10000000", passGrant.getAwardNumber());
        assertEquals("Stupendous \"Research Project\" I",
            passGrant.getProjectName());
        assertEquals(AwardStatus.ACTIVE, passGrant.getAwardStatus());
        assertEquals("2021-01-01T00:00Z", passGrant.getAwardDate().toString());
        assertEquals("2021-07-01T00:00Z", passGrant.getStartDate().toString());
        assertEquals("2024-06-30T00:00Z", passGrant.getEndDate().toString());

        Funder primaryFunder = passClient.getObject(passGrant.getPrimaryFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:20000001", primaryFunder.getLocalKey());
        assertEquals("J L Gotrocks Foundation", primaryFunder.getName());
        assertEquals("1", primaryFunder.getPolicy().getId());

        Funder directFunder = passClient.getObject(passGrant.getDirectFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:20000000", directFunder.getLocalKey());
        assertEquals("Enormous State University",directFunder.getName());
        assertEquals("1", directFunder.getPolicy().getId());

        assertEquals("Amanda", passGrant.getPi().getFirstName());
        assertEquals("Bea", passGrant.getPi().getMiddleName());
        assertEquals("Reckondwith", passGrant.getPi().getLastName());
        assertEquals("arecko1@jhu.edu", passGrant.getPi().getEmail());
        assertEquals(List.of("johnshopkins.edu:employeeid:31000000", "johnshopkins.edu:eppn:arecko1"),
            passGrant.getPi().getLocatorIds());

        assertEquals(1, passGrant.getCoPis().size());
        assertEquals("Skip", passGrant.getCoPis().get(0).getFirstName());
        assertEquals("Avery", passGrant.getCoPis().get(0).getMiddleName());
        assertEquals("Class", passGrant.getCoPis().get(0).getLastName());
        assertEquals("sclass1@jhu.edu", passGrant.getCoPis().get(0).getEmail());
        assertEquals(List.of("johnshopkins.edu:employeeid:31000001", "johnshopkins.edu:eppn:sclass1"),
            passGrant.getCoPis().get(0).getLocatorIds());
    }

    protected void verifyGrantTwo() throws IOException {
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("localKey", "johnshopkins.edu:grant:10000002"));
        grantSelector.setInclude("primaryFunder", "directFunder", "pi", "coPis");
        PassClientResult<Grant> resultGrant1 = passClient.selectObjects(grantSelector);
        assertEquals(1, resultGrant1.getTotal());
        Grant passGrant = resultGrant1.getObjects().get(0);
        assertNotNull(passGrant.getId());
        assertEquals("johnshopkins.edu:grant:10000002", passGrant.getLocalKey());
        assertEquals("B10000003", passGrant.getAwardNumber());
        assertEquals("Stupendous Research ProjectIV", passGrant.getProjectName());
        assertEquals(AwardStatus.ACTIVE, passGrant.getAwardStatus());
        assertEquals("2014-01-01T00:00Z", passGrant.getAwardDate().toString());
        assertEquals("2014-07-01T00:00Z", passGrant.getStartDate().toString());
        assertEquals("2017-06-30T00:00Z", passGrant.getEndDate().toString());

        Funder primaryFunder = passClient.getObject(passGrant.getPrimaryFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:20000001", primaryFunder.getLocalKey());
        assertEquals("J L Gotrocks Foundation", primaryFunder.getName());
        assertEquals("1", primaryFunder.getPolicy().getId());

        Funder directFunder = passClient.getObject(passGrant.getDirectFunder(), "policy");
        assertEquals("johnshopkins.edu:funder:20000000", directFunder.getLocalKey());
        assertEquals("Enormous State University",directFunder.getName());
        assertEquals("1", directFunder.getPolicy().getId());

        assertEquals("Skip", passGrant.getPi().getFirstName());
        assertEquals("Avery", passGrant.getPi().getMiddleName());
        assertEquals("Class", passGrant.getPi().getLastName());
        assertEquals("sclass1@jhu.edu", passGrant.getPi().getEmail());
        assertEquals(List.of("johnshopkins.edu:employeeid:31000001", "johnshopkins.edu:eppn:sclass1"),
            passGrant.getPi().getLocatorIds());

        assertEquals(0, passGrant.getCoPis().size());
    }

}
