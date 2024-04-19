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
package org.eclipse.pass.deposit.service;

import static org.eclipse.pass.deposit.service.DeploymentTestDataService.PASS_E2E_TEST_GRANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;

import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.File;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@TestPropertySource(properties = {
    "pass.test.data.policy.title=test-policy-title",
    "pass.test.data.user.email=test-user-email@foo"
})
public class DeploymentTestDataServiceIT extends AbstractDepositIT {

    @Autowired private DeploymentTestDataService deploymentTestDataService;

    @BeforeEach
    public void initPolicyAndUser() throws IOException {
        PassClientSelector<Policy> selector = new PassClientSelector<>(Policy.class);
        selector.setFilter(RSQL.equals("title", "test-policy-title"));
        List<Policy> testPolicies = passClient.streamObjects(selector).toList();
        if (testPolicies.isEmpty()) {
            Policy policy = new Policy();
            policy.setTitle("test-policy-title");
            passClient.createObject(policy);
            User testUser = new User();
            testUser.setFirstName("test");
            testUser.setLastName("user");
            testUser.setEmail("test-user-email@foo");
            passClient.createObject(testUser);
        }
    }

    @AfterEach
    public void cleanUp() throws IOException {
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("projectName", PASS_E2E_TEST_GRANT));
        List<Grant> testGrants = passClient.streamObjects(grantSelector).toList();
        testGrants.forEach(this::deleteObject);
        PassClientSelector<Deposit> depositSelector = new PassClientSelector<>(Deposit.class);
        List<Deposit> testDeposits = passClient.streamObjects(depositSelector).toList();
        testDeposits.forEach(this::deleteObject);
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        List<RepositoryCopy> testRepoCopies = passClient.streamObjects(repoCopySelector).toList();
        testRepoCopies.forEach(this::deleteObject);
        PassClientSelector<File> fileSelector = new PassClientSelector<>(File.class);
        List<File> testFiles = passClient.streamObjects(fileSelector).toList();
        testFiles.forEach(this::deleteFile);
        PassClientSelector<Submission> submissionSelector = new PassClientSelector<>(Submission.class);
        List<Submission> testSubmissions = passClient.streamObjects(submissionSelector).toList();
        testSubmissions.forEach(this::deleteObject);
        PassClientSelector<Publication> publicationSelector = new PassClientSelector<>(Publication.class);
        List<Publication> testPublications = passClient.streamObjects(publicationSelector).toList();
        testPublications.forEach(this::deleteObject);
    }

    private void deleteObject(PassEntity entity) {
        try {
            passClient.deleteObject(entity);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteFile(File file) {
        try {
            passClient.deleteFile(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testProcessData_TestGrantDataCreatedIfNotExist() throws Exception {
        // WHEN
        deploymentTestDataService.processTestData();

        // THEN
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("projectName", PASS_E2E_TEST_GRANT));
        grantSelector.setInclude("pi", "directFunder", "primaryFunder");
        List<Grant> testGrants = passClient.streamObjects(grantSelector).toList();
        assertEquals(1, testGrants.size());
        Grant actualTestGrant = testGrants.get(0);
        assertEquals(PASS_E2E_TEST_GRANT, actualTestGrant.getProjectName());
        assertEquals(AwardStatus.ACTIVE, actualTestGrant.getAwardStatus());
        assertEquals("test-user-email@foo", actualTestGrant.getPi().getEmail());
        assertEquals("PASS_E2E_TEST_FUNDER", actualTestGrant.getDirectFunder().getName());
        assertEquals("PASS_E2E_TEST_FUNDER", actualTestGrant.getPrimaryFunder().getName());
    }

    @Test
    public void testProcessData_DoesNotTestGrantDataCreatedIfExist() throws Exception {
        // GIVEN
        Grant testGrant = new Grant();
        testGrant.setProjectName(PASS_E2E_TEST_GRANT);
        testGrant.setAwardStatus(AwardStatus.ACTIVE);
        passClient.createObject(testGrant);
        Mockito.reset(passClient);

        // WHEN
        deploymentTestDataService.processTestData();

        // THEN
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("projectName", PASS_E2E_TEST_GRANT));
        List<Grant> testGrants = passClient.streamObjects(grantSelector).toList();
        assertEquals(1, testGrants.size());
        Grant actualTestGrant = testGrants.get(0);
        assertEquals(testGrant.getId(), actualTestGrant.getId());
        assertEquals(PASS_E2E_TEST_GRANT, actualTestGrant.getProjectName());
        assertEquals(AwardStatus.ACTIVE, actualTestGrant.getAwardStatus());
        verify(passClient, times(0)).createObject(any());
    }

    @Test
    public void testProcessData_DeleteTestSubmissionsForTestGrant() throws Exception {
        // GIVEN
        Grant testGrant = new Grant();
        testGrant.setProjectName(PASS_E2E_TEST_GRANT);
        testGrant.setAwardStatus(AwardStatus.ACTIVE);
        passClient.createObject(testGrant);
        initSubmissionAndDeposits(testGrant);

        // WHEN
        deploymentTestDataService.processTestData();

        // THEN
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("projectName", PASS_E2E_TEST_GRANT));
        List<Grant> testGrants = passClient.streamObjects(grantSelector).toList();
        assertEquals(1, testGrants.size());
        Grant actualTestGrant = testGrants.get(0);
        assertEquals(PASS_E2E_TEST_GRANT, actualTestGrant.getProjectName());
        assertEquals(AwardStatus.ACTIVE, actualTestGrant.getAwardStatus());
        PassClientSelector<Deposit> depositSelector = new PassClientSelector<>(Deposit.class);
        List<Deposit> testDeposits = passClient.streamObjects(depositSelector).toList();
        assertTrue(testDeposits.isEmpty());
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        List<RepositoryCopy> testRepoCopies = passClient.streamObjects(repoCopySelector).toList();
        assertTrue(testRepoCopies.isEmpty());
        PassClientSelector<Submission> submissionSelector = new PassClientSelector<>(Submission.class);
        List<Submission> testSubmissions = passClient.streamObjects(submissionSelector).toList();
        assertTrue(testSubmissions.isEmpty());
        PassClientSelector<Publication> publicationSelector = new PassClientSelector<>(Publication.class);
        List<Publication> testPublications = passClient.streamObjects(publicationSelector).toList();
        assertTrue(testPublications.isEmpty());
        PassClientSelector<File> fileSelector = new PassClientSelector<>(File.class);
        List<File> testFiles = passClient.streamObjects(fileSelector).toList();
        assertTrue(testFiles.isEmpty());
    }

    @Test
    public void testProcessData_DoesNotDeleteTestSubmissionsForOtherGrant() throws Exception {
        // GIVEN
        Grant deploymentGrant = new Grant();
        deploymentGrant.setProjectName(PASS_E2E_TEST_GRANT);
        deploymentGrant.setAwardStatus(AwardStatus.ACTIVE);
        passClient.createObject(deploymentGrant);
        initSubmissionAndDeposits(deploymentGrant);

        Grant testOtherGrant = new Grant();
        testOtherGrant.setProjectName("Some Other Grant");
        testOtherGrant.setAwardStatus(AwardStatus.ACTIVE);
        passClient.createObject(testOtherGrant);
        Submission testOtherSubmission = initSubmissionAndDeposits(testOtherGrant);

        // WHEN
        deploymentTestDataService.processTestData();

        // THEN
        final PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("projectName", PASS_E2E_TEST_GRANT));
        List<Grant> actualTestGrants = passClient.streamObjects(grantSelector).toList();
        assertEquals(1, actualTestGrants.size());
        Grant actualTestGrant = actualTestGrants.get(0);
        assertEquals(PASS_E2E_TEST_GRANT, actualTestGrant.getProjectName());
        assertEquals(AwardStatus.ACTIVE, actualTestGrant.getAwardStatus());

        PassClientSelector<Submission> submissionSelector = new PassClientSelector<>(Submission.class);
        List<Submission> testSubmissions = passClient.streamObjects(submissionSelector).toList();
        assertEquals(1, testSubmissions.size());
        Submission actualSubmission = testSubmissions.get(0);
        assertEquals(testOtherSubmission.getId(), actualSubmission.getId());
        assertEquals(testOtherGrant.getId(), actualSubmission.getGrants().get(0).getId());

        PassClientSelector<Deposit> depositSelector = new PassClientSelector<>(Deposit.class);
        List<Deposit> testDeposits = passClient.streamObjects(depositSelector).toList();
        assertEquals(1, testDeposits.size());
        Deposit actualDeposit = testDeposits.get(0);
        assertEquals(testOtherSubmission.getId(), actualDeposit.getSubmission().getId());

        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        List<RepositoryCopy> testRepoCopies = passClient.streamObjects(repoCopySelector).toList();
        assertEquals(1, testRepoCopies.size());
        RepositoryCopy actualRepoCopy = testRepoCopies.get(0);
        assertEquals(actualDeposit.getRepositoryCopy().getId(), actualRepoCopy.getId());

        PassClientSelector<Publication> publicationSelector = new PassClientSelector<>(Publication.class);
        List<Publication> testPublications = passClient.streamObjects(publicationSelector).toList();
        assertEquals(1, testPublications.size());
        Publication actualPublication = testPublications.get(0);
        assertEquals(actualRepoCopy.getPublication().getId(), actualPublication.getId());
        assertEquals(actualSubmission.getPublication().getId(), actualPublication.getId());

        PassClientSelector<File> fileSelector = new PassClientSelector<>(File.class);
        List<File> testFiles = passClient.streamObjects(fileSelector).toList();
        assertEquals(1, testFiles.size());
        File actualFile = testFiles.get(0);
        assertEquals(actualSubmission.getId(), actualFile.getSubmission().getId());
    }

    private Submission initSubmissionAndDeposits(Grant testGrant) throws Exception {
        Submission submission = new Submission();
        submission.setGrants(List.of(testGrant));
        submission.setSubmittedDate(ZonedDateTime.now().minusDays(2));
        passClient.createObject(submission);

        Repository repository = new Repository();
        repository.setName("test-repository");
        passClient.createObject(repository);

        Publication publication = new Publication();
        publication.setTitle("test-publication");
        passClient.createObject(publication);

        RepositoryCopy repositoryCopy = new RepositoryCopy();
        repositoryCopy.setRepository(repository);
        repositoryCopy.setPublication(publication);
        passClient.createObject(repositoryCopy);

        Deposit j10pDeposit = new Deposit();
        j10pDeposit.setSubmission(submission);
        j10pDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        j10pDeposit.setRepositoryCopy(repositoryCopy);
        passClient.createObject(j10pDeposit);

        submission.setPublication(publication);
        passClient.updateObject(submission);

        File file = new File();
        String data = "Test data file";
        file.setName("test_data_file.txt");
        URI data_uri = passClient.uploadBinary(file.getName(), data.getBytes(StandardCharsets.UTF_8));
        assertNotNull(data_uri);
        file.setUri(data_uri);
        file.setSubmission(submission);
        passClient.createObject(file);

        return submission;
    }

}
