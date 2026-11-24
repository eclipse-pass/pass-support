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
package org.eclipse.pass.loader.nihms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.ParameterType;

@SpringBootTest(
    properties = {
        "spring.cloud.aws.credentials.access-key=noop",
        "spring.cloud.aws.credentials.secret-key=noop",
        "spring.cloud.aws.region.static=us-east-1",
        "pass.client.url=http://localhost:8080/",
        "pass.client.user=${PASS_CORE_USER:test}",
        "pass.client.password=${PASS_CORE_PASSWORD:test-pw}",
    })
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@Testcontainers
class AwsParamStoreConfigTest {
    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:4.14.0");

    @Container
    private static final LocalStackContainer localStack = new LocalStackContainer(LOCALSTACK_IMG).withServices("ssm");

    @Autowired private Environment environment;

    @MockitoBean private NihmsTransformLoadCLIRunner nihmsTransformLoadCLIRunner;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.parameterstore.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.parameterstore.region", localStack::getRegion);
        registry.add("spring.cloud.aws.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", localStack::getRegion);
        registry.add("spring.config.import[0]", () -> "aws-parameterstore:/config/pass-core-client/");
        registry.add("spring.config.import[1]", () -> "aws-parameterstore:/config/pass-nihms/");
    }

    @BeforeAll
    static void beforeAll() {
        try (SsmClient ssmClient = SsmClient.builder()
            .endpointOverride(localStack.getEndpoint())
            .region(Region.of(localStack.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
            .build()) {
            ssmClient.putParameter(request -> request
                .name("/config/pass-core-client/PASS_CORE_PASSWORD")
                .value("aws-param-store-core-pw")
                .type(ParameterType.SECURE_STRING));
            ssmClient.putParameter(request -> request
                .name("/config/pass-nihms/NIHMS_REPO_ID")
                .value("aws-param-store-repo-id")
                .type(ParameterType.SECURE_STRING));
        }
    }

    @Test
    public void testLoadPropFromParamStore() {
        String clientUrlNameProp = environment.getProperty("pass.client.url");
        assertEquals("http://localhost:8080/", clientUrlNameProp);
        String repoIdProp = environment.getProperty("nihmsetl.repository.id");
        assertEquals("aws-param-store-repo-id", repoIdProp);
        String passClientPwProp = environment.getProperty("pass.client.password");
        assertEquals("aws-param-store-core-pw", passClientPwProp);
    }

}
