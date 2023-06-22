/*
 * Copyright 2018 Johns Hopkins University
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

package org.eclipse.pass.deposit.builder.fedora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static submissions.SubmissionResourceUtil.lookupStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import org.eclipse.pass.deposit.builder.InvalidModel;
import org.eclipse.pass.deposit.builder.fs.FcrepoModelBuilder;
import org.eclipse.pass.deposit.builder.fs.PassJsonFedoraAdapter;
import org.eclipse.pass.deposit.messaging.DepositApp;
import org.eclipse.pass.deposit.messaging.config.spring.DepositConfig;
import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.model.JournalPublicationType;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource(properties = {
    "pass.client.url=http://localhost:8080",
    "pass.client.user=backend",
    "pass.client.password=backend"
})
@Testcontainers
@DirtiesContext
public class FcrepoModelBuilderIT {

    private static final DockerImageName PASS_CORE_IMG =
        DockerImageName.parse("ghcr.io/eclipse-pass/pass-core-main");

    @Container
    static final GenericContainer<?> PASS_CORE_CONTAINER = new GenericContainer<>(PASS_CORE_IMG)
        .withEnv("PASS_CORE_BASE_URL", "http://localhost:8080")
        .withEnv("PASS_CORE_BACKEND_USER", "backend")
        .withEnv("PASS_CORE_BACKEND_PASSWORD", "backend")
        .waitingFor(Wait.forHttp("/data/grant"))
        .withExposedPorts(8080);

    private static final String EXPECTED_JOURNAL_TITLE = "Food & Function";
    private static final String EXPECTED_DOI = "10.1039/c7fo01251a";
    private static final String EXPECTED_EMBARGO_END_DATE = "2018-06-30";
    private static final int EXPECTED_SUBMITER_COUNT = 1;
    private static final int EXPECTED_PI_COUNT = 1;
    private static final int EXPECTED_CO_PI_COUNT = 2;
    private static final int EXPECTED_AUTHOR_COUNT = 6;
    private static final String EXPECTED_NLMTA = "Food Funct";
    private static final Map<String, DepositMetadata.IssnPubType> EXPECTED_ISSNS =
        new HashMap<>() {
            {
                put("2042-650X", new DepositMetadata.IssnPubType("2042-650X", JournalPublicationType.OPUB));
                put("2042-6496", new DepositMetadata.IssnPubType("2042-6496", JournalPublicationType.PPUB));
            }
        };

    @DynamicPropertySource
    static void updateProperties(DynamicPropertyRegistry registry) {
        registry.add("pass.client.url",
            () -> "http://localhost:" + PASS_CORE_CONTAINER.getMappedPort(8080));
    }

    @Autowired private PassJsonFedoraAdapter passJsonFedoraAdapter;
    @Autowired private FcrepoModelBuilder fcrepoModelBuilder;

    @Test
    public void testElementValues() throws IOException, InvalidModel {
        // GIVEN
        List<PassEntity> entities = new LinkedList<>();
        Submission submissionEntity;
        try (InputStream is = new ClassPathResource("/submissions/sample1.json").getInputStream()) {
            submissionEntity = passJsonFedoraAdapter.jsonToFcrepo(is, entities);
        }

        // WHEN
        DepositSubmission submission = fcrepoModelBuilder.build(submissionEntity.getId());

        // THEN
        assertNotNull(submissionEntity);

        // Check that some basic things are in order
        assertNotNull(submission.getManifest());
        assertNotNull(submission.getMetadata());
        assertNotNull(submission.getMetadata().getManuscriptMetadata());
        assertNotNull(submission.getMetadata().getJournalMetadata());
        assertNotNull(submission.getMetadata().getArticleMetadata());
        assertNotNull(submission.getMetadata().getPersons());
        assertNotNull(submission.getSubmissionMeta());

        assertEquals(EXPECTED_DOI, submission.getMetadata().getArticleMetadata().getDoi().toString());

        assertNotNull(submission.getFiles());
//        assertEquals(8, submission.getFiles().size());

        // Confirm that some values were set correctly from the Submission metadata
        DepositMetadata.Journal journalMetadata = submission.getMetadata().getJournalMetadata();
        assertEquals(EXPECTED_JOURNAL_TITLE, journalMetadata.getJournalTitle());

        EXPECTED_ISSNS.values().forEach(expectedIssnPubType -> {
            journalMetadata.getIssnPubTypes().values().stream()
                           .filter(candidate ->
                                       candidate.equals(expectedIssnPubType))
                           .findAny().orElseThrow(() ->
                                                      new RuntimeException(
                                                          "Missing expected IssnPubType " + expectedIssnPubType));
        });
        assertEquals(EXPECTED_ISSNS.size(), journalMetadata.getIssnPubTypes().size());

        assertEquals(EXPECTED_NLMTA, journalMetadata.getJournalId());

        DepositMetadata.Manuscript manuscriptMetadata = submission.getMetadata().getManuscriptMetadata();
        assertNull(manuscriptMetadata.getManuscriptUrl());

        assertEquals(EXPECTED_EMBARGO_END_DATE, submission.getMetadata().getArticleMetadata().getEmbargoLiftDate()
                                                          .format(DateTimeFormatter.ofPattern("uuuu-MM-dd")));

        List<DepositMetadata.Person> persons = submission.getMetadata().getPersons();
        assertEquals(EXPECTED_SUBMITER_COUNT, persons.stream()
                                                     .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.submitter)
                                                     .count());
        assertEquals(EXPECTED_PI_COUNT, persons.stream()
                                               .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.pi).count());
        assertEquals(EXPECTED_CO_PI_COUNT, persons.stream()
                                                  .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.copi)
                                                  .count());
        assertEquals(EXPECTED_AUTHOR_COUNT, persons.stream()
                                                   .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.author)
                                                   .count());

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Tania Marchbank")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Nikki Mandir")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Denis Calnan")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Robert A. Goodlad")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Theo Podas")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Raymond J. Playford")));

        // Read something out of the submission metadata
        assertTrue(submission.getSubmissionMeta().has("agreements"));
        JsonObject agreement = submission.getSubmissionMeta().getAsJsonObject("agreements");
        assertTrue(agreement.has("JScholarship"));
    }

}