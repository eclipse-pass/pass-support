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
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SSM;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
        "spring.cloud.aws.credentials.access-key=noop",
        "spring.cloud.aws.credentials.secret-key=noop",
        "spring.cloud.aws.region.static=us-east-1",
        "spring.jms.listener.auto-startup=false"
    })
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@Testcontainers
class AwsParamStoreConfigTest {
    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:3.1.0");

    @Container
    private static final LocalStackContainer localStack = new LocalStackContainer(LOCALSTACK_IMG).withServices(SSM);

    @Autowired private Environment environment;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.parameterstore.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.parameterstore.region", localStack::getRegion);
        registry.add("spring.cloud.aws.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", localStack::getRegion);
        registry.add("spring.config.import[0]", () -> "aws-parameterstore:/config/pass-core-client/");
        registry.add("spring.config.import[1]", () -> "aws-parameterstore:/config/pass-deposit/");
    }

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        localStack.execInContainer("awslocal", "ssm", "put-parameter",
            "--name", "/config/pass-core-client/PASS_CORE_PASSWORD",
            "--value", "aws-param-store-core-pw",
            "--type", "SecureString");
        localStack.execInContainer("awslocal", "ssm", "put-parameter",
            "--name", "/config/pass-deposit/DSPACE_PASSWORD",
            "--value", "aws-param-store-dspace-pw",
            "--type", "SecureString");
    }

    @Test
    public void testLoadPropFromParamStore() {
        String userNameProp = environment.getProperty("dspace.user");
        assertEquals("test@test.edu", userNameProp);
        String dspacePwProp = environment.getProperty("dspace.password");
        assertEquals("aws-param-store-dspace-pw", dspacePwProp);
        String passClientPwProp = environment.getProperty("pass.client.password");
        assertEquals("aws-param-store-core-pw", passClientPwProp);
    }

}
