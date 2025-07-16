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
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SSM;

import java.io.IOException;

import org.eclipse.pass.support.grant.data.GrantConnector;
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
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
        "spring.cloud.aws.credentials.access-key=noop",
        "spring.cloud.aws.credentials.secret-key=noop",
        "spring.cloud.aws.region.static=us-east-1",
        "pass.policy.prop.path=file:./src/test/resources/policy.properties",
        "pass.grant.update.ts.path=file:./src/test/resources/grant_update_timestamps",
        "pass.client.url=http://localhost:8080/",
        "pass.client.user=${PASS_CORE_USER:test}",
        "pass.client.password=${PASS_CORE_PASSWORD:test-pw}",
        "grant.db.url=${GRANT_DB_URL:test-grant-db-url}",
        "grant.db.username=${GRANT_DB_USER:test-grant-db-user}",
        "grant.db.password=${GRANT_DB_PASSWORD:test-grant-db-pw}"
    })
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@Testcontainers
class AwsParamStoreConfigTest {
    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:3.8.1");

    @Container
    private static final LocalStackContainer localStack = new LocalStackContainer(LOCALSTACK_IMG).withServices(SSM);

    @Autowired private Environment environment;
    @MockitoBean protected GrantConnector grantConnector;
    @MockitoBean protected GrantLoaderCLIRunner grantLoaderCLIRunner;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.parameterstore.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.parameterstore.region", localStack::getRegion);
        registry.add("spring.cloud.aws.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", localStack::getRegion);
        registry.add("spring.config.import[0]", () -> "aws-parameterstore:/config/pass-core-client/");
        registry.add("spring.config.import[1]", () -> "aws-parameterstore:/config/pass-grant/");
    }

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        localStack.execInContainer("awslocal", "ssm", "put-parameter",
            "--name", "/config/pass-core-client/PASS_CORE_PASSWORD",
            "--value", "aws-param-store-core-pw",
            "--type", "SecureString");
        localStack.execInContainer("awslocal", "ssm", "put-parameter",
            "--name", "/config/pass-grant/GRANT_DB_PASSWORD",
            "--value", "aws-param-store-grant-pw",
            "--type", "SecureString");
    }

    @Test
    public void testLoadPropFromParamStore() {
        String userNameProp = environment.getProperty("grant.db.username");
        assertEquals("test-grant-db-user", userNameProp);
        String userPwProp = environment.getProperty("grant.db.password");
        assertEquals("aws-param-store-grant-pw", userPwProp);
        String passClientPwProp = environment.getProperty("pass.client.password");
        assertEquals("aws-param-store-core-pw", passClientPwProp);
    }

}
