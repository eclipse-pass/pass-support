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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.eclipse.pass.support.grant.data.PassUpdater;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@TestPropertySource(properties = {
    "spring.cloud.aws.s3.enabled=true",
    "pass.policy.prop.path=s3://test-bucket/s3-policy.properties",
    "pass.grant.update.ts.path=s3://test-bucket/s3-testgrantupdatets"
})
public class GrantLoaderS3RoundTripTest extends AbstractIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:3.1.0");

    @Container
    static final LocalStackContainer localStack =
        new LocalStackContainer(LOCALSTACK_IMG)
            .withClasspathResourceMapping("/policy.properties",
                "/tmp/test-s3-policy.properties", BindMode.READ_ONLY)
            .withClasspathResourceMapping("/s3-testgrantupdatets",
                "/tmp/test-s3-testgrantupdatets", BindMode.READ_ONLY)
            .withServices(S3);

    @Autowired private S3Template s3Template;
    @Autowired private PassUpdater passUpdater;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.region.static", localStack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", localStack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localStack::getSecretKey);
        registry.add("spring.cloud.aws.s3.endpoint", () -> localStack.getEndpointOverride(S3).toString());
    }

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        localStack.execInContainer("awslocal", "s3", "mb", "s3://test-bucket");
        localStack.execInContainer("awslocal", "s3", "cp", "/tmp/test-s3-policy.properties",
            "s3://test-bucket/s3-policy.properties");
        localStack.execInContainer("awslocal", "s3", "cp", "/tmp/test-s3-testgrantupdatets",
            "s3://test-bucket/s3-testgrantupdatets");
    }

    @Test
    public void testRoundTripCvsFileS3() throws PassCliException, SQLException, IOException {
        // GIVEN
        Policy policy = new Policy();
        policy.setTitle("test policy");
        passClient.createObject(policy);

        List<GrantIngestRecord> grantIngestRecordList = getTestIngestRecords();
        doReturn(grantIngestRecordList).when(grantConnector).retrieveUpdates(anyString(), anyString(), anyString(),
            any());

        // WHEN
        grantLoaderApp.run("2011-01-01 00:00:00", "01/01/2011",
            "grant", "pull", "s3://test-bucket/test-pull.csv", null);

        // THEN
        String expectedContent = Files.readString(Path.of("src/test/resources/expected-csv.csv"));

        S3Resource actualTestPullCsv = s3Template.download("test-bucket", "test-pull.csv");
        try (InputStream inputStream = actualTestPullCsv.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(expectedContent, content);
        }

        // WHEN
        // Use CSV file create above and load into PASS
        grantLoaderApp.run("", "01/01/2011", "grant",
            "load", "s3://test-bucket/test-pull.csv", null);

        // THEN
        verifyGrantOne();
        verifyGrantTwo();

        S3Resource actualTestGrantUpTs = s3Template.download("test-bucket", "s3-testgrantupdatets");
        try (InputStream inputStream = actualTestGrantUpTs.getInputStream()) {
            String contentUpTs = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(passUpdater.getLatestUpdate() + "\n", contentUpTs);
        }
    }

    private List<GrantIngestRecord> getTestIngestRecords() {
        GrantIngestRecord piRecord1 = TestUtil.makeGrantIngestRecord(0, 0, "P");
        GrantIngestRecord coPiRecord1 = TestUtil.makeGrantIngestRecord(0, 1, "C");
        GrantIngestRecord piRecord2 = TestUtil.makeGrantIngestRecord(3, 1, "P");
        return List.of(piRecord1, coPiRecord1, piRecord2);
    }

    private void verifyGrantOne() throws IOException {
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
        assertEquals("1999-01-01T00:00Z", passGrant.getAwardDate().toString());
        assertEquals("2000-07-01T00:00Z", passGrant.getStartDate().toString());
        assertEquals("2004-06-30T00:00Z", passGrant.getEndDate().toString());

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

    private void verifyGrantTwo() throws IOException {
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
        assertEquals("2004-01-01T00:00Z", passGrant.getAwardDate().toString());
        assertEquals("2004-07-01T00:00Z", passGrant.getStartDate().toString());
        assertEquals("2007-06-30T00:00Z", passGrant.getEndDate().toString());

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
