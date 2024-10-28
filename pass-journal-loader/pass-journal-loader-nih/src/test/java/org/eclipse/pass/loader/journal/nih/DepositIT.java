/*
 * Copyright 2017 Johns Hopkins University
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

package org.eclipse.pass.loader.journal.nih;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * @author apb@jhu.edu
 */
@Testcontainers
@WireMockTest
public class DepositIT {
    private final PassClient client = PassClient.newInstance();

    private static final DockerImageName PASS_CORE_IMG;

    static {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader("pom.xml"));
            String version = model.getParent().getVersion();
            PASS_CORE_IMG = DockerImageName.parse("ghcr.io/eclipse-pass/pass-core-main:" + version);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        System.setProperty("pass.core.url", "http://localhost:8080");
        System.setProperty("pass.core.user", "backend");
        System.setProperty("pass.core.password", "backend");
    }

    @Container
    private static final GenericContainer<?> PASS_CORE_CONTAINER = new GenericContainer<>(PASS_CORE_IMG)
        .withCopyFileToContainer(
            MountableFile.forHostPath("../../pass-core-test-config/"),
            "/tmp/pass-core-test-config/"
        )
        .withEnv("PASS_CORE_JAVA_OPTS", "-Dspring.config.import=file:/tmp/pass-core-test-config/application-test.yml")
        .waitingFor(Wait.forHttp("/data/grant").forStatusCode(200).withBasicCredentials("backend", "backend"))
        .withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(8080),
                new ExposedPort(8080)));
        })
        .withExposedPorts(8080);

    @Test
    public void loadFromFileTest(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        String jmedlineJournals = Files.readString(
            Paths.get(DepositIT.class.getResource("/medline.txt").toURI()));
        stubFor(get("/pubmed/J_Medline.txt")
            .willReturn(ok(jmedlineJournals)));
        String pmcJournlas1 = Files.readString(
            Paths.get(DepositIT.class.getResource("/pmc-1.csv").toURI()));
        stubFor(get("/pmc/front-page/NIH_PA_journal_list-1.csv")
            .willReturn(ok(pmcJournlas1)));
        String pmcJournlas2 = Files.readString(
            Paths.get(DepositIT.class.getResource("/pmc-2.csv").toURI()));
        stubFor(get("/pmc/front-page/NIH_PA_journal_list-2.csv")
            .willReturn(ok(pmcJournlas2)));

        final int wmPort = wmRuntimeInfo.getHttpPort();
        System.setProperty("medline", "http://localhost:" + wmPort + "/pubmed/J_Medline.txt");
        System.setProperty("pmc", "");
        Main.main(new String[] {});

        // We expect three journals, but no PMC A journals
        assertEquals(4, listJournals().size());
        assertEquals(0, typeA(listJournals()).size());

        System.setProperty("medline", "");
        System.setProperty("pmc", "http://localhost:" + wmPort + "/pmc/front-page/NIH_PA_journal_list-1.csv");
        Main.main(new String[] {});

        // We still expect three journals in the repository, but now two are PMC A
        assertEquals(4, listJournals().size());
        assertEquals(2, typeA(listJournals()).size());

        System.setProperty("medline", "");
        System.setProperty("pmc", "http://localhost:" + wmPort + "/pmc/front-page/NIH_PA_journal_list-2.csv");
        Main.main(new String[] {});

        // The last dataset removed a type A journal, so now we expect only one
        assertEquals(4, listJournals().size());
        assertEquals(1, typeA(listJournals()).size());
    }

    private List<PmcParticipation> typeA(List<Journal> journals) {
        return journals.stream()
                   .map(Journal::getPmcParticipation)
                   .filter(Objects::nonNull)
                   .collect(Collectors.toList());
    }

    private List<Journal> listJournals() throws Exception {
        PassClientSelector<Journal> sel = new PassClientSelector<>(Journal.class);

        return client.selectObjects(sel).getObjects();
    }
}
