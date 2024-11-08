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
package org.eclipse.pass.deposit.config.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import java.io.IOException;

import org.eclipse.pass.deposit.DepositApp;
import org.eclipse.pass.deposit.config.repository.Repositories;
import org.eclipse.pass.deposit.config.repository.RepositoryConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource(
    locations = "/test-application.properties",
    properties = {
        "spring.jms.listener.auto-startup=false",
        "spring.cloud.aws.s3.enabled=true",
        "pass.deposit.repository.configuration=s3://test-bucket/s3-test-repositories.json"
    })
@Testcontainers
public class RepositoriesConfigS3IT {

    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:3.8.1");

    @Container
    static final LocalStackContainer localStack =
        new LocalStackContainer(LOCALSTACK_IMG)
            .withClasspathResourceMapping("/full-test-s3-repositories.json",
                "/tmp/tmp-test-repositories.json", BindMode.READ_ONLY)
            .withServices(S3);

    @Autowired
    private Repositories repositories;

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
        localStack.execInContainer("awslocal", "s3", "cp", "/tmp/tmp-test-repositories.json",
            "s3://test-bucket/s3-test-repositories.json");
    }

    @Test
    public void testLoadRepositoryConfigurations() {
        assertNotNull(repositories);

        assertEquals(4, repositories.getAllConfigs().size());

        RepositoryConfig j10p = repositories.getConfig("JScholarship-S3");
        assertNotNull(j10p);

        RepositoryConfig pubMed = repositories.getConfig("PubMed Central-S3");
        assertNotNull(pubMed);

        assertEquals("JScholarship-S3", j10p.getRepositoryKey());
        assertEquals("PubMed Central-S3", pubMed.getRepositoryKey());

        assertNotNull(j10p.getTransportConfig());
        assertNotNull(j10p.getTransportConfig().getProtocolBinding());
        assertNotNull(j10p.getAssemblerConfig().getSpec());

        assertNotNull(pubMed.getTransportConfig());
        assertNotNull(pubMed.getTransportConfig().getProtocolBinding());
        assertNotNull(pubMed.getAssemblerConfig().getSpec());
    }

}
