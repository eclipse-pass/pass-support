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
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.aws.s3.enabled=true",
    "pass.policy.prop.path=s3://test-bucket/s3-policy.properties"
})
@Testcontainers
public class PolicyPropertiesS3IT {

    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:3.1.0");

    @Container
    static final LocalStackContainer localStack =
        new LocalStackContainer(LOCALSTACK_IMG)
            .withClasspathResourceMapping("/s3-policy.properties",
                "/tmp/tmp-test-s3-policy.properties", BindMode.READ_ONLY)
            .withServices(S3);

    @MockBean protected GrantLoaderCLIRunner grantLoaderCLIRunner;
    @Autowired @Qualifier("policyProperties") private Properties policyProperties;

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
        localStack.execInContainer("awslocal", "s3", "cp", "/tmp/tmp-test-s3-policy.properties",
            "s3://test-bucket/s3-policy.properties");
    }

    @Test
    public void testLoadRepositoryConfigurations() {
        assertNotNull(policyProperties);

        assertEquals(Set.of("s3-1", "s3-2"), policyProperties.stringPropertyNames());
    }

}
