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
package org.eclipse.pass.deposit.support.deploymenttest;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.eclipse.pass.deposit.support.deploymenttest.DeploymentTestDataService.PASS_E2E_TEST_GRANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.eclipse.pass.deposit.service.AbstractDepositIT;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.EventType;
import org.eclipse.pass.support.client.model.File;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@TestPropertySource(properties = {
    "dspace.server.api.protocol=http",
    "dspace.server.api.path=/server/api",
    "pass.test.data.job.enabled=true",
    "pass.test.data.policy.title=test-policy-title",
    "pass.test.data.user.email=test-user-email@foo",
    "pass.test.dspace.repo.key=TestDspace"
})
@WireMockTest(httpPort = 9020)
public class DeploymentTestDataServiceIT extends AbstractDepositIT {

    @Autowired private DeploymentTestDataService deploymentTestDataService;
    @SpyBean private DspaceDepositService dspaceDepositService;

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
        ReflectionTestUtils.setField(deploymentTestDataService, "skipDeploymentTestDeposits", Boolean.TRUE);
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
        PassClientSelector<SubmissionEvent> subEventSelector = new PassClientSelector<>(SubmissionEvent.class);
        List<SubmissionEvent> testSubEvents = passClient.streamObjects(subEventSelector).toList();
        testSubEvents.forEach(this::deleteObject);
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
    public void testProcessTestData_TestGrantDataCreatedIfNotExist() throws Exception {
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
    public void testProcessTestData_DoesNotTestGrantDataCreatedIfExist() throws Exception {
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
    public void testProcessTestData_DeleteTestSubmissionsForTestGrant() throws Exception {
        // GIVEN
        Grant testGrant = new Grant();
        testGrant.setProjectName(PASS_E2E_TEST_GRANT);
        testGrant.setAwardStatus(AwardStatus.ACTIVE);
        passClient.createObject(testGrant);
        initSubmissionAndDeposits(testGrant);
        initDspaceApiStubs("Test-Title");
        ReflectionTestUtils.setField(deploymentTestDataService, "skipDeploymentTestDeposits", Boolean.FALSE);

        // WHEN
        deploymentTestDataService.processTestData();

        // THEN
        verifyTestGrantDeleted();
        verify(dspaceDepositService, times(1)).deleteDeposit(
            argThat(deposit -> deposit.getRepository().getRepositoryKey().equals("TestDspace")),
            any(DspaceDepositService.AuthContext.class));
        verify(dspaceDepositService, times(0)).deleteDeposit(
            argThat(deposit -> deposit.getRepository().getRepositoryKey().equals("TestNihms")),
            any(DspaceDepositService.AuthContext.class));
        verifyDspaceApiStubs(1);
    }

    @Test
    public void testProcessTestData_DoesNotDeleteTestSubmissionsForOtherGrant() throws Exception {
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
        initDspaceApiStubs("Test-Title");
        ReflectionTestUtils.setField(deploymentTestDataService, "skipDeploymentTestDeposits", Boolean.FALSE);

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
        assertEquals(2, testDeposits.size());
        Deposit actualDeposit = testDeposits.get(0);
        assertEquals(testOtherSubmission.getId(), actualDeposit.getSubmission().getId());

        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        List<RepositoryCopy> testRepoCopies = passClient.streamObjects(repoCopySelector).toList();
        assertEquals(2, testRepoCopies.size());
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

        PassClientSelector<SubmissionEvent> subEventSelector = new PassClientSelector<>(SubmissionEvent.class);
        List<SubmissionEvent> testSubEvents = passClient.streamObjects(subEventSelector).toList();
        assertEquals(1, testSubEvents.size());
        SubmissionEvent actualSubEvent = testSubEvents.get(0);
        assertEquals(actualSubmission.getId(), actualSubEvent.getSubmission().getId());

        verify(dspaceDepositService, times(1)).deleteDeposit(
            argThat(deposit -> deposit.getRepository().getRepositoryKey().equals("TestDspace")),
            any(DspaceDepositService.AuthContext.class));
        verify(dspaceDepositService, times(0)).deleteDeposit(
            argThat(deposit -> deposit.getRepository().getRepositoryKey().equals("TestNihms")),
            any(DspaceDepositService.AuthContext.class));
        verifyDspaceApiStubs(1);
    }

    @Test
    public void testProcessTestData_DoesNotDeleteDspaceDepositIfSkip() throws Exception {
        // GIVEN
        Grant testGrant = new Grant();
        testGrant.setProjectName(PASS_E2E_TEST_GRANT);
        testGrant.setAwardStatus(AwardStatus.ACTIVE);
        passClient.createObject(testGrant);
        initSubmissionAndDeposits(testGrant);
        initDspaceApiStubs("Test-Title");

        // WHEN
        deploymentTestDataService.processTestData();

        // THEN
        verifyTestGrantDeleted();
        verify(dspaceDepositService, times(0)).deleteDeposit(
            argThat(deposit -> deposit.getRepository().getRepositoryKey().equals("TestDspace")),
            any(DspaceDepositService.AuthContext.class));
        verify(dspaceDepositService, times(0)).deleteDeposit(
            argThat(deposit -> deposit.getRepository().getRepositoryKey().equals("TestNihms")),
            any(DspaceDepositService.AuthContext.class));
        verifyDspaceApiStubs(0);
    }

    @Test
    public void testProcessTestData_DoesNotDeleteDspaceDepositIfNameMismatch() throws Exception {
        // GIVEN
        Grant testGrant = new Grant();
        testGrant.setProjectName(PASS_E2E_TEST_GRANT);
        testGrant.setAwardStatus(AwardStatus.ACTIVE);
        passClient.createObject(testGrant);
        initSubmissionAndDeposits(testGrant);
        initDspaceApiStubs("Test-Title-Mismatch");
        ReflectionTestUtils.setField(deploymentTestDataService, "skipDeploymentTestDeposits", Boolean.FALSE);

        // WHEN
        deploymentTestDataService.processTestData();

        // THEN
        verifyTestGrantDeleted();
        verify(dspaceDepositService, times(1)).deleteDeposit(
            argThat(deposit -> deposit.getRepository().getRepositoryKey().equals("TestDspace")),
            any(DspaceDepositService.AuthContext.class));
        verify(dspaceDepositService, times(0)).deleteDeposit(
            argThat(deposit -> deposit.getRepository().getRepositoryKey().equals("TestNihms")),
            any(DspaceDepositService.AuthContext.class));
        WireMock.verify(1, getRequestedFor(urlEqualTo("/server/api/security/csrf")));
        WireMock.verify(1, postRequestedFor(urlEqualTo("/server/api/authn/login")));
        WireMock.verify(1, getRequestedFor(
            urlEqualTo("/server/api/discover/search/objects?query=handle:a.1234%2Fabcd&dsoType=item")));
        WireMock.verify(0, getRequestedFor(urlEqualTo("/server/api/core/items/12345-aabb/bundles")));
        WireMock.verify(0, deleteRequestedFor(urlEqualTo("/server/api/core/bundles/aa-11")));
        WireMock.verify(0, deleteRequestedFor(urlEqualTo("/server/api/core/bundles/bb-22")));
        WireMock.verify(0, deleteRequestedFor(urlEqualTo("/server/api/core/bundles/cc-33")));
        WireMock.verify(0, deleteRequestedFor(urlEqualTo("/server/api/core/items/12345-aabb")));
    }

    private Submission initSubmissionAndDeposits(Grant testGrant) throws Exception {
        Submission submission = new Submission();
        submission.setMetadata(
            "{\"issns\":[{\"issn\":\"1234\",\"pubType\":\"Print\"}],\"journal-title\":\"Test-Journal\"," +
                "\"title\":\"Test-Title\",\"authors\":[{\"author\":\"test-user\"}],\"agreements\":" +
                "[{\"TestDspace\":\"Test agreement\"}]}"
        );
        submission.setGrants(List.of(testGrant));
        submission.setSubmittedDate(ZonedDateTime.now().minusDays(2));
        passClient.createObject(submission);

        Repository repositoryDspace = new Repository();
        repositoryDspace.setName("test-repository-dspace");
        repositoryDspace.setRepositoryKey("TestDspace");
        passClient.createObject(repositoryDspace);

        Repository repositoryNihms = new Repository();
        repositoryNihms.setName("test-repository-nihms");
        repositoryNihms.setRepositoryKey("TestNihms");
        passClient.createObject(repositoryNihms);

        Publication publication = new Publication();
        publication.setTitle("test-publication");
        passClient.createObject(publication);

        RepositoryCopy repositoryCopyDspace = new RepositoryCopy();
        repositoryCopyDspace.setRepository(repositoryDspace);
        repositoryCopyDspace.setPublication(publication);
        repositoryCopyDspace.setAccessUrl(URI.create("http://localhost:9020/handle/a.1234/abcd"));
        passClient.createObject(repositoryCopyDspace);

        RepositoryCopy repositoryCopyNihms = new RepositoryCopy();
        repositoryCopyNihms.setRepository(repositoryNihms);
        repositoryCopyNihms.setPublication(publication);
        repositoryCopyDspace.setAccessUrl(URI.create("http://foobar/nihms-fake"));
        passClient.createObject(repositoryCopyNihms);

        Deposit dspaceDeposit = new Deposit();
        dspaceDeposit.setSubmission(submission);
        dspaceDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        dspaceDeposit.setRepositoryCopy(repositoryCopyDspace);
        dspaceDeposit.setRepository(repositoryDspace);
        passClient.createObject(dspaceDeposit);

        Deposit nihmsDeposit = new Deposit();
        nihmsDeposit.setSubmission(submission);
        nihmsDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        nihmsDeposit.setRepositoryCopy(repositoryCopyNihms);
        nihmsDeposit.setRepository(repositoryNihms);
        passClient.createObject(nihmsDeposit);

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

        PassClientSelector<User> selectorUser = new PassClientSelector<>(User.class);
        selectorUser.setFilter(RSQL.equals("email", "test-user-email@foo"));
        User testUser = passClient.streamObjects(selectorUser).toList().get(0);

        SubmissionEvent submissionEvent = new SubmissionEvent();
        submissionEvent.setSubmission(submission);
        submissionEvent.setEventType(EventType.SUBMITTED);
        submissionEvent.setPerformedBy(testUser);
        passClient.createObject(submissionEvent);

        return submission;
    }

    private void verifyTestGrantDeleted() throws IOException {
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
        PassClientSelector<SubmissionEvent> subEventSelector = new PassClientSelector<>(SubmissionEvent.class);
        List<SubmissionEvent> testSubEvents = passClient.streamObjects(subEventSelector).toList();
        assertTrue(testSubEvents.isEmpty());
    }

    private void initDspaceApiStubs(String expectedName) throws Exception {
        stubFor(get("/server/api/security/csrf")
            .willReturn(ok().withHeader("DSPACE-XSRF-TOKEN", "test-csrf-token")));
        stubFor(post("/server/api/authn/login")
            .willReturn(ok().withHeader("Authorization", "test-auth-token")));

        String searchJson = Files.readString(
            Paths.get(DeploymentTestDataServiceIT.class.getResource("/dspace-resp/search.json").toURI()))
            .replaceAll("<item_name>", expectedName);
        stubFor(get("/server/api/discover/search/objects?query=handle:a.1234%2Fabcd&dsoType=item")
            .willReturn(ok(searchJson)));

        String bundlesJson = Files.readString(
            Paths.get(DeploymentTestDataServiceIT.class.getResource("/dspace-resp/bundles.json").toURI()));
        stubFor(get("/server/api/core/items/12345-aabb/bundles")
            .willReturn(ok(bundlesJson)));

        stubFor(delete("/server/api/core/bundles/aa-11")
            .willReturn(ok()));
        stubFor(delete("/server/api/core/bundles/bb-22")
            .willReturn(ok()));
        stubFor(delete("/server/api/core/bundles/cc-33")
            .willReturn(ok()));

        stubFor(delete("/server/api/core/items/12345-aabb")
            .willReturn(ok()));
    }

    private void verifyDspaceApiStubs(int expectedCount) {
        WireMock.verify(expectedCount, getRequestedFor(urlEqualTo("/server/api/security/csrf")));
        WireMock.verify(expectedCount, postRequestedFor(urlEqualTo("/server/api/authn/login")));
        WireMock.verify(expectedCount, getRequestedFor(
            urlEqualTo("/server/api/discover/search/objects?query=handle:a.1234%2Fabcd&dsoType=item")));
        WireMock.verify(expectedCount, getRequestedFor(urlEqualTo("/server/api/core/items/12345-aabb/bundles")));
        WireMock.verify(expectedCount, deleteRequestedFor(urlEqualTo("/server/api/core/bundles/aa-11")));
        WireMock.verify(expectedCount, deleteRequestedFor(urlEqualTo("/server/api/core/bundles/bb-22")));
        WireMock.verify(expectedCount, deleteRequestedFor(urlEqualTo("/server/api/core/bundles/cc-33")));
        WireMock.verify(expectedCount, deleteRequestedFor(urlEqualTo("/server/api/core/items/12345-aabb")));
    }

}
