/*
 * Copyright 2019 Johns Hopkins University
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
package org.eclipse.pass.deposit;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.deposit.util.async.Condition;
import org.eclipse.pass.deposit.util.SubmissionTestUtil;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.Source;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides convenience methods for depositing Submissions in PASS.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource("classpath:test-application.properties")
@TestPropertySource(properties = {
    "pass.client.url=http://localhost:8080",
    "pass.client.user=backend",
    "pass.client.password=backend"
})
@Testcontainers
@DirtiesContext
public abstract class AbstractDepositSubmissionIT {

    private static final DockerImageName PASS_CORE_IMG =
        DockerImageName.parse("ghcr.io/eclipse-pass/pass-core-main");

    @Container
    static final GenericContainer<?> PASS_CORE_CONTAINER = new GenericContainer<>(PASS_CORE_IMG)
        .withEnv("PASS_CORE_BASE_URL", "http://localhost:8080")
        .withEnv("PASS_CORE_BACKEND_USER", "backend")
        .withEnv("PASS_CORE_BACKEND_PASSWORD", "backend")
        .waitingFor(Wait.forHttp("/data/grant"))
        .withExposedPorts(8080);

    @Autowired protected SubmissionTestUtil submissionTestUtil;

    @Autowired
    protected PassClient passClient;

    @DynamicPropertySource
    static void updateProperties(DynamicPropertyRegistry registry) {
        registry.add("pass.client.url",
            () -> "http://localhost:" + PASS_CORE_CONTAINER.getMappedPort(8080));
    }

    /**
     * Populates Fedora with a Submission graph serialized as JSON, as if it was submitted interactively by a user of
     * the PASS UI.
     * <p>
     * The submission graph supplied by the {@code InputStream} must satisfy the following conditions, or an {@code
     * AssertionError} will be thrown:
     * </p>
     * <ul>
     *     <li>The {@code Submission.source} must be {@code Submission.Source.PASS}</li>
     *     <li>The {@code Submission.aggregatedDepositStatus} must be {@code
     *         Submission.AggregatedDepositStatus.NOT_STARTED}</li>
     * </ul>
     */
    public List<PassEntity> createSubmission(InputStream submissionGraph) throws IOException {
        submissionTestUtil.deleteDepositsInPass();
        List<PassEntity> entities = new LinkedList<>();
        submissionTestUtil.readSubmissionJsonAndAddToPass(submissionGraph, entities);
        Submission submission = findSubmission(entities);

        // verify state of the initial Submission
        assertEquals("Submission must have a Submission.source = Submission.Source.PASS",
                     Source.PASS, submission.getSource());
        assertEquals("Submission must have a Submission.aggregatedDepositStatus = " +
                     "Submission.AggregatedDepositStatus.NOT_STARTED",
                     AggregatedDepositStatus.NOT_STARTED, submission.getAggregatedDepositStatus());

        return entities;
    }

    public void triggerSubmission(Submission submission) throws IOException {
        submission.setSubmitted(true);
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        passClient.updateObject(submission);
    }

    /**
     * Returns the {@code Submission} from a {@code Map} of entities that represents the graph of entities linked to
     * by the {@code Submission}.
     * <p>
     * The supplied {@code Map} must contain exactly one {@code Submission}, or an {@code AssertionError} is thrown.
     * </p>
     *
     * @param entities a map of entities that comprise a graph rooted in the {@code Submission}
     * @return the {@code Submission}
     * @throws AssertionError if zero or more than one {@code Submission} is contained in the supplied entity {@code
     *                        Map}
     */
    public static Submission findSubmission(List<PassEntity> entities) {
        Predicate<PassEntity> submissionFilter = (entity) -> entity instanceof Submission;

        long count = entities
            .stream()
            .filter(submissionFilter)
            .count();

        assertEquals("Found " + count + " Submission resources, expected exactly 1", count, 1);

        return (Submission) entities
            .stream()
            .filter(submissionFilter)
            .findAny()
            .get();
    }

    /**
     * Answers a Condition that will await the creation of {@code expectedCount} {@code Deposit} resources that meet
     * the requirements of the supplied {@code filter}.
     *
     * @param submissionId the ID of the Submission
     * @param expectedCount the number of Deposit resources expected for the Submission (normally equal to the number of
     *                      Repository resources present on the Submission)
     * @param filter        filters for Deposit resources with a desired state (e.g., a certain deposit status)
     * @return the Condition
     */
    public Condition<Set<Deposit>> depositsForSubmission(String submissionId, int expectedCount,
                                                         BiPredicate<Deposit, Repository> filter) {
        Callable<Set<Deposit>> deposits = () -> {
            PassClientSelector<Deposit> depositSelector = new PassClientSelector<>(Deposit.class);
            depositSelector.setFilter(RSQL.equals("submission.id", submissionId));
            depositSelector.setInclude("repository");
            PassClientResult<Deposit> resultDeposits = passClient.selectObjects(depositSelector);

            return resultDeposits.getObjects().stream()
                .filter(deposit -> filter.test(deposit, deposit.getRepository()))
                .collect(Collectors.toSet());
        };

        Function<Set<Deposit>, Boolean> verification = (depositSet) -> depositSet.size() == expectedCount;

        String name = String.format("Searching for %s Deposits for Submission ID %s", expectedCount, submissionId);
        return new Condition<>(deposits, verification, name);
    }

}
