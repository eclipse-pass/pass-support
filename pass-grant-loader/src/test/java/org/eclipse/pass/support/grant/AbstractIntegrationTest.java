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

import java.io.FileReader;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.grant.data.GrantConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
@Testcontainers
@DirtiesContext
public abstract class AbstractIntegrationTest {

    static {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader("pom.xml"));
            String version = model.getParent().getVersion();
            PASS_CORE_IMG = DockerImageName.parse("ghcr.io/eclipse-pass/pass-core-main:" + version);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final DockerImageName PASS_CORE_IMG;

    @Container
    protected static final GenericContainer<?> PASS_CORE_CONTAINER = new GenericContainer<>(PASS_CORE_IMG)
        .withCopyFileToContainer(
            MountableFile.forHostPath("../pass-core-test-config/"),
            "/tmp/pass-core-test-config/"
        )
        .withEnv("PASS_CORE_JAVA_OPTS", "-Dspring.config.import=file:/tmp/pass-core-test-config/application-test.yml")
        .waitingFor(Wait.forHttp("/data/grant").forStatusCode(200).withBasicCredentials("backend", "backend"))
        .withExposedPorts(8080);

    @SpyBean protected PassClient passClient;
    @Autowired protected GrantLoaderApp grantLoaderApp;
    @MockBean protected GrantConnector grantConnector;
    @MockBean protected GrantLoaderCLIRunner grantLoaderCLIRunner;

    @DynamicPropertySource
    static void updateProperties(DynamicPropertyRegistry registry) {
        registry.add("pass.client.url",
            () -> "http://localhost:" + PASS_CORE_CONTAINER.getMappedPort(8080));
    }
}
