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
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import org.eclipse.pass.support.client.model.Policy;
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
public class GrantLoaderS3RoundTripTest extends AbstractRoundTripTest {

    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:3.8.1");

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
    public void testRoundTripCvsFileS3() throws PassCliException, IOException {
        // GIVEN
        Policy policy = new Policy();
        policy.setTitle("test policy");
        passClient.createObject(policy);

        // WHEN
        grantLoaderApp.run("2011-01-01 00:00:00", "2011-01-01",
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
        grantLoaderApp.run("", "2011-01-01", "grant",
            "load", "s3://test-bucket/test-pull.csv", null);

        // THEN
        verifyGrantOne();
        verifyGrantTwo();

        S3Resource actualTestGrantUpTs1 = s3Template.download("test-bucket", "s3-testgrantupdatets");
        try (InputStream inputStream = actualTestGrantUpTs1.getInputStream()) {
            String contentUpTs = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(passUpdater.getLatestUpdate() + System.lineSeparator(), contentUpTs);
        }

        // WHEN - run again to verify grant update timestamps
        String firstLastUpdate = passUpdater.getLatestUpdate();
        grantLoaderApp.run("", "2011-01-01", "grant",
            "load", "s3://test-bucket/test-pull.csv", null);

        S3Resource actualTestGrantUpTs2 = s3Template.download("test-bucket", "s3-testgrantupdatets");
        String expectedGrantUpdateTs = firstLastUpdate + System.lineSeparator() + passUpdater.getLatestUpdate()
                + System.lineSeparator();
        try (InputStream inputStream = actualTestGrantUpTs2.getInputStream()) {
            String contentUpTs = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(expectedGrantUpdateTs, contentUpTs);
        }
    }

}
