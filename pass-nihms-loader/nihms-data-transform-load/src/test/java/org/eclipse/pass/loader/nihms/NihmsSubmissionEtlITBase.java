/*
 * Copyright 2023 Johns Hopkins University
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.pass.loader.nihms.client.NihmsPassClientService;
import org.eclipse.pass.loader.nihms.entrez.PmidLookup;
import org.eclipse.pass.loader.nihms.entrez.PubMedEntrezRecord;
import org.eclipse.pass.support.client.ModelUtil;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.User;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * @author Karen Hanson
 */
@SpringBootTest
@TestPropertySource("classpath:test-application.properties")
@Testcontainers
@DirtiesContext
public abstract class NihmsSubmissionEtlITBase {
    //including high ascii to test for ascii handling
    protected final String title = "Article A ε4";
    protected final String doi = "10.1000/a.abcd.1234";
    protected final String issue = "3";

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
            MountableFile.forClasspathResource("/saml2/"),
            "/tmp/saml2/"
        )
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("/application-test.yml"),
            "/tmp/application-test.yml"
        )
        .withEnv("PASS_SAML_PATH", "file:/tmp/")
        .withEnv("PASS_CORE_JAVA_OPTS", "-Dspring.config.import=file:/tmp/application-test.yml")
        .waitingFor(Wait.forHttp("/data/grant").forStatusCode(200).withBasicCredentials("backend", "moo"))
        .withExposedPorts(8080);

    @DynamicPropertySource
    static void updateProperties(DynamicPropertyRegistry registry) {
        registry.add("pass.client.url",
            () -> "http://localhost:" + PASS_CORE_CONTAINER.getMappedPort(8080));
    }

    //use when need to return reliable record information instead of using entrez api
    @MockBean protected PmidLookup mockPmidLookup;

    // Needed so tests can run after application starts
    @MockBean private NihmsTransformLoadCLIRunner nihmsTransformLoadCLIRunner;

    @Autowired protected NihmsPassClientService nihmsPassClientService;
    @Autowired protected CompletedPublicationsCache completedPubsCache;
    @Autowired protected NihmsTransformLoadService nihmsTransformLoadService;
    @Autowired protected NihmsPublicationToSubmission nihmsPublicationToSubmission;
    @Autowired protected PassClient passClient;

    @Value("${nihmsetl.data.dir}")
    protected String dataDir;

    protected String nihmsRepoId;

    @BeforeEach
    public void startup() throws IOException {
        nihmsRepoId = initiateNihmsRepo();
        ReflectionTestUtils.setField(nihmsPassClientService, "nihmsRepositoryId", nihmsRepoId);
        ReflectionTestUtils.setField(nihmsPublicationToSubmission, "nihmsRepositoryId", nihmsRepoId);
    }

    @AfterEach
    public void cleanup() throws IOException {
        completedPubsCache.clear();
        nihmsPassClientService.clearCache();

        /*
            Clean out all data from the following (note Grant IDs added in the createGrant() method as we
            don't want to delete preloaded data). Need to check that the objects were actually deleted
            otherwise this will cause issues with the next test run.
            NOTE: these need to be run in this order due to the FK constraints: Deposit, Submission, RepositoryCopy,
            Repository, Publication
        */

        PassClientSelector<Deposit> depoSelector = new PassClientSelector<>(Deposit.class);
        depoSelector.setFilter(RSQL.notEquals("id", "-1"));
        for (Deposit deposit : passClient.selectObjects(depoSelector).getObjects()) {
            passClient.deleteObject(deposit);
        }

        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        subSelector.setFilter(RSQL.notEquals("id", "-1"));
        for (Submission submission : passClient.selectObjects(subSelector).getObjects()) {
            passClient.deleteObject(submission);
        }

        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        repoCopySelector.setFilter(RSQL.notEquals("id", "-1"));
        for (RepositoryCopy repoCopy : passClient.selectObjects(repoCopySelector).getObjects()) {
            passClient.deleteObject(repoCopy);
        }

        PassClientSelector<Repository> repoSelector = new PassClientSelector<>(Repository.class);
        repoSelector.setFilter(RSQL.notEquals("id", "-1"));
        for (Repository repo : passClient.selectObjects(repoSelector).getObjects()) {
            passClient.deleteObject(repo);
        }

        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        pubSelector.setFilter(RSQL.notEquals("id", "-1"));
        for (Publication publication : passClient.selectObjects(pubSelector).getObjects()) {
            passClient.deleteObject(publication);
        }
    }

    private static ZonedDateTime dt(String s) {
        return ZonedDateTime.parse(s, ModelUtil.dateTimeFormatter());
    }

    protected Grant createGrant(String awardNumber, User user) throws Exception {
        Funder primaryFunder = new Funder();
        Funder directFunder = new Funder();
        passClient.createObject(primaryFunder);
        passClient.createObject(directFunder);
        User coPi = new User();
        passClient.createObject(coPi);
        Grant grant = new Grant();
        grant.setAwardNumber(awardNumber);
        grant.setPi(user);
        grant.setPrimaryFunder(primaryFunder);
        grant.setDirectFunder(directFunder);
        grant.setAwardStatus(AwardStatus.ACTIVE);
        List<User> copis = new ArrayList<>();
        copis.add(coPi);
        grant.setCoPis(copis);
        grant.setProjectName("test");
        grant.setAwardDate(dt("2014-03-28T00:00:00.000Z"));
        grant.setStartDate(dt("2026-01-11T02:12:13.040Z"));

        passClient.createObject(grant);
        return grant;
    }

    /*
     * Try invoking a runnable until it succeeds.
     *
     * @param times  The number of times to run
     * @param thingy The runnable.
     */
    void attempt(final int times, final Runnable thingy) {
        attempt(times, () -> {
            thingy.run();
            return null;
        });
    }

    /*
     * Try invoking a callable until it succeeds.
     *
     * @param times Number of times to try
     * @param it    the thing to call.
     * @return the result from the callable, when successful.
     */
    <T> T attempt(final int times, final Callable<T> it) {

        Throwable caught = null;

        for (int tries = 0; tries < times; tries++) {
            try {
                return it.call();
            } catch (final Throwable e) {
                caught = e;
                try {
                    Thread.sleep(3000);
                } catch (final InterruptedException i) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        throw new RuntimeException("Failed executing task", caught);
    }

    protected void setMockPMRecord(String pmid) throws IOException {
        String json = IOUtils.toString(getClass().getClassLoader().
                getResourceAsStream("pmidrecord.json"), StandardCharsets.UTF_8);
        JSONObject rootObj = new JSONObject(json);
        PubMedEntrezRecord pmr = new PubMedEntrezRecord(rootObj);
        when(mockPmidLookup.retrievePubMedRecord(eq(pmid))).thenReturn(pmr);
    }

    protected String initiateNihmsRepo() throws IOException {
        Repository nihmsRepo = new Repository();
        nihmsRepo.setName("NIHMS");
        nihmsRepo.setRepositoryKey("nihms");
        passClient.createObject(nihmsRepo);
        return nihmsRepo.getId();
    }

    protected void verifySubmission(Submission preexistingSub, Submission reloadedSub) {
        assertEquals(preexistingSub.getId(), reloadedSub.getId());
        assertEquals(preexistingSub.getSource(), reloadedSub.getSource());
        assertEquals(preexistingSub.getSubmitted(), reloadedSub.getSubmitted());
        assertEquals(preexistingSub.getSubmittedDate(), reloadedSub.getSubmittedDate());
        assertEquals(preexistingSub.getAggregatedDepositStatus(), reloadedSub.getAggregatedDepositStatus());

        //test publications
        Publication preexistingPub = preexistingSub.getPublication();
        Publication reloadedPub = reloadedSub.getPublication();
        assertEquals(preexistingPub.getId(), reloadedPub.getId());
        assertEquals(preexistingPub.getTitle(), reloadedPub.getTitle());
        assertEquals(preexistingPub.getDoi(), reloadedPub.getDoi());
        assertEquals(preexistingPub.getPmid(), reloadedPub.getPmid());

        //test the first grant
        Grant preexistingGrants = preexistingSub.getGrants().get(0);
        Grant reloadedGrants = reloadedSub.getGrants().get(0);
        assertEquals(preexistingGrants.getId(), reloadedGrants.getId());
        assertEquals(preexistingGrants.getAwardNumber(), reloadedGrants.getAwardNumber());
        assertEquals(preexistingGrants.getAwardDate().toInstant(), reloadedGrants.getAwardDate().toInstant());
        assertEquals(preexistingGrants.getAwardStatus(), reloadedGrants.getAwardStatus());
        assertEquals(preexistingGrants.getLocalKey(), reloadedGrants.getLocalKey());
    }
}
