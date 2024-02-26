package org.eclipse.pass.support.grant;

import java.io.FileReader;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.pass.support.client.PassClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = GrantLoaderCLI.class)
@TestPropertySource("classpath:test-application.properties")
@Testcontainers
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
        .withEnv("PASS_CORE_BASE_URL", "http://localhost:8080")
        .withEnv("PASS_CORE_BACKEND_USER", "backend")
        .withEnv("PASS_CORE_BACKEND_PASSWORD", "backend")
        .waitingFor(Wait.forHttp("/data/grant").forStatusCode(401))
        .withExposedPorts(8080);

    @Autowired protected PassClient passClient;

    @MockBean protected GrantLoaderCLIRunner grantLoaderCLIRunner;

    @DynamicPropertySource
    static void updateProperties(DynamicPropertyRegistry registry) {
        registry.add("pass.client.url",
            () -> "http://localhost:" + PASS_CORE_CONTAINER.getMappedPort(8080));
    }
}
