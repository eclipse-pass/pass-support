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
package org.eclipse.pass.deposit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.eclipse.deposit.util.async.Condition;
import org.eclipse.pass.deposit.support.deploymenttest.DeploymentTestDataService;
import org.eclipse.pass.deposit.transport.devnull.DevNullTransport;
import org.eclipse.pass.deposit.transport.fs.FilesystemTransport;
import org.eclipse.pass.deposit.transport.inveniordm.InvenioRdmTransport;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.File;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@TestPropertySource(properties = {
    "pass.deposit.repository.configuration=classpath:/full-test-repositories.json",
    "inveniordm.api.token=test-invenio-api-token",
    "inveniordm.api.baseUrl=http://localhost:9030/api"
})
@WireMockTest(httpPort = 9030)
public class SubmissionProcessorIT extends AbstractSubmissionIT {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionProcessorIT.class);

    @Autowired private SubmissionStatusUpdater submissionStatusUpdater;
    @Autowired private DepositProcessor depositProcessor;
    @Autowired private DepositTaskHelper depositTaskHelper;
    @SpyBean private FilesystemTransport filesystemTransport;
    @SpyBean private DevNullTransport devNullTransport;
    @SpyBean private InvenioRdmTransport invenioRdmTransport;

    @BeforeEach
    void cleanUp() {
        ReflectionTestUtils.setField(depositTaskHelper, "skipDeploymentTestDeposits", Boolean.TRUE);
    }

    @Test
    void testSubmissionProcessing_Full() throws Exception {
        // GIVEN
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample1-unsubmitted")));
        resetGrantProjectName(submission, null);
        initInvenioApiStubs();

        // WHEN/THEN
        testSubmissionProcessor(submission, false);
        verify(filesystemTransport, times(3)).open(anyMap());
        verify(invenioRdmTransport, times(1)).open(anyMap());
        verify(devNullTransport, times(0)).open(anyMap());
        verifyInvenioApiStubs(1);
    }

    @Test
    void testSubmissionProcessing_Full_InvenioExistingRecord() throws Exception {
        // GIVEN
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample1-unsubmitted")));
        resetGrantProjectName(submission, null);
        initInvenioApiStubs();
        String searchRecordsJsonResponse = "{ \"hits\": { \"hits\": [{ \"id\": \"existing-record-id\", " +
            "\"is_published\": \"false\"} ] } }";
        stubFor(get("/api/user/records?q=metadata.title:%22Specific%20protein%20supplementation%20using%20" +
            "soya%2C%20casein%20or%20whey%20differentially%20affects%20regional%20gut%20growth%20and%20luminal%20" +
            "growth%20factor%20bioactivity%20in%20rats%3B%20implications%20for%20the%20treatment%20of%20gut%20" +
            "injury%20and%20stimulating%20repair%22")
            .willReturn(ok(searchRecordsJsonResponse)));
        stubFor(delete("/api/records/existing-record-id/draft")
            .willReturn(ok()));

        // WHEN/THEN
        testSubmissionProcessor(submission, false);
        verify(filesystemTransport, times(3)).open(anyMap());
        verify(invenioRdmTransport, times(1)).open(anyMap());
        verify(devNullTransport, times(0)).open(anyMap());
        verifyInvenioApiStubs(1);
        WireMock.verify(1, deleteRequestedFor(
            urlEqualTo("/api/records/existing-record-id/draft")));
    }

    @Test
    void testSubmissionProcessing_SkipTestSubmission() throws Exception {
        // GIVEN
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample1-unsubmitted")));
        resetGrantProjectName(submission, DeploymentTestDataService.PASS_E2E_TEST_GRANT);

        // WHEN/THEN
        testSubmissionProcessor(submission, true);
        verify(devNullTransport, times(4)).open(anyMap());
        verify(filesystemTransport, times(0)).open(anyMap());
        verify(invenioRdmTransport, times(0)).open(anyMap());
        verifyInvenioApiStubs(0);
    }

    @Test
    void testSubmissionProcessing_DontSkipTestSubmission() throws Exception {
        // GIVEN
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample1-unsubmitted")));
        resetGrantProjectName(submission, DeploymentTestDataService.PASS_E2E_TEST_GRANT);
        initInvenioApiStubs();
        ReflectionTestUtils.setField(depositTaskHelper, "skipDeploymentTestDeposits", Boolean.FALSE);

        // WHEN/THEN
        testSubmissionProcessor(submission, false);
        verify(devNullTransport, times(0)).open(anyMap());
        verify(filesystemTransport, times(3)).open(anyMap());
        verify(invenioRdmTransport, times(1)).open(anyMap());
        verifyInvenioApiStubs(1);
    }

    private void testSubmissionProcessor(Submission submission, boolean usingDevNull) throws IOException {
        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId(), "grants");

        // WHEN
        submissionProcessor.accept(actualSubmission);

        // After the SubmissionProcessor successfully processing a submission we should observe:
        // 1. Deposit resources created for each Repository associated with the Submission
        // 2. The Deposit resources should be in a ACCEPTED state
        // These statuses are dependent on the transport being used - because the TransportResponse.onSuccess(...)
        // method may modify the repository resources associated with the Submission.  Because the FilesystemTransport
        // is used, the Deposit resources will be in the ACCEPTED state, and RepositoryCopy resources in the ACCEPTED
        // state.
        // 3. The Submission's AggregateDepositStatus should be set to ACCEPTED
        // 4. The Submission's SubmissionStatus should be changed to COMPLETE

        // THEN
        // TODO replace with awaitility
        Condition<Set<Deposit>> deposits = depositsForSubmission(
            actualSubmission.getId(),
            actualSubmission.getRepositories().size(),
            (deposit, repo) -> {
                LOG.debug("Polling Submission {} for deposit-related resources", actualSubmission.getId());
                LOG.debug("  Deposit: {} {}", deposit.getDepositStatus(), deposit.getId());
                LOG.debug("  Repository: {} {}", repo.getName(), repo.getId());

                // Transport-dependent part: FilesystemTransport
                // .onSuccess(...) sets the correct statuses

                if (deposit.getRepositoryCopy() == null) {
                    return false;
                }

                RepositoryCopy repoCopy;
                try {
                    repoCopy = passClient.getObject(RepositoryCopy.class,
                        deposit.getRepositoryCopy().getId());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                LOG.debug("  RepositoryCopy: {} {} {} {}", repoCopy.getCopyStatus(), repoCopy.getAccessUrl(),
                    String.join(",", repoCopy.getExternalIds()), repoCopy.getId());

                return DepositStatus.ACCEPTED == deposit.getDepositStatus() &&
                    CopyStatus.COMPLETE == repoCopy.getCopyStatus() &&
                    repoCopy.getAccessUrl() != null &&
                    repoCopy.getExternalIds().size() > 0;
            });

        deposits.await();

        Set<Deposit> resultDeposits = deposits.getResult();
        assertEquals(actualSubmission.getRepositories().size(), resultDeposits.size());
        long actualSubmissionDepositCount = resultDeposits.stream()
            .filter(deposit -> deposit.getSubmission().getId().equals(actualSubmission.getId()))
            .count();
        assertEquals(actualSubmissionDepositCount, submission.getRepositories().size());
        assertTrue(resultDeposits.stream().allMatch(deposit -> deposit.getDepositStatus() == DepositStatus.ACCEPTED));

        List<String> repoKeys = resultDeposits.stream()
            .map(deposit -> deposit.getRepository().getRepositoryKey())
            .toList();
        List<String> expectedRepoKey = List.of("PubMed Central", "JScholarship", "BagIt", "InvenioRDM");
        assertTrue(repoKeys.size() == expectedRepoKey.size() && repoKeys.containsAll(expectedRepoKey)
            && expectedRepoKey.containsAll(repoKeys));
        Deposit pmcDeposit = resultDeposits.stream()
            .filter(deposit -> deposit.getRepository().getRepositoryKey().equals("PubMed Central"))
            .findFirst().get();
        assertTrue(pmcDeposit.getDepositStatusRef().startsWith("nihms-package:nihms-native-2022-05_"));
        assertEquals(DepositStatus.ACCEPTED, pmcDeposit.getDepositStatus());
        verifyAccessUrl(pmcDeposit, usingDevNull);
        Deposit j10pDeposit = resultDeposits.stream()
            .filter(deposit -> deposit.getRepository().getRepositoryKey().equals("JScholarship"))
            .findFirst().get();
        assertNull(j10pDeposit.getDepositStatusRef());
        assertEquals(DepositStatus.ACCEPTED, j10pDeposit.getDepositStatus());
        verifyAccessUrl(j10pDeposit, usingDevNull);
        Deposit bagItDeposit = resultDeposits.stream()
            .filter(deposit -> deposit.getRepository().getRepositoryKey().equals("BagIt"))
            .findFirst().get();
        assertNull(bagItDeposit.getDepositStatusRef());
        assertEquals(DepositStatus.ACCEPTED, bagItDeposit.getDepositStatus());
        verifyAccessUrl(bagItDeposit, usingDevNull);
        Deposit invenioRDMDeposit = resultDeposits.stream()
            .filter(deposit -> deposit.getRepository().getRepositoryKey().equals("InvenioRDM"))
            .findFirst().get();
        assertEquals(DepositStatus.ACCEPTED, invenioRDMDeposit.getDepositStatus());
        assertNull(invenioRDMDeposit.getDepositStatusRef());

        // WHEN
        submissionStatusUpdater.doUpdate();

        // THEN
        final Submission statusSubmission = passClient.getObject(Submission.class, submission.getId());
        assertEquals(SubmissionStatus.COMPLETE, statusSubmission.getSubmissionStatus());

        // WHEN
        Deposit deposit = resultDeposits.iterator().next();
        depositProcessor.accept(deposit);

        // THEN
        final Submission aggrStatusSubmission = passClient.getObject(Submission.class, submission.getId());
        assertEquals(AggregatedDepositStatus.ACCEPTED, aggrStatusSubmission.getAggregatedDepositStatus());
    }

    private void verifyAccessUrl(Deposit deposit, boolean usingDevNull) throws IOException {
        RepositoryCopy j10pRepoCopy = passClient.getObject(deposit.getRepositoryCopy());
        String accessUrl = j10pRepoCopy.getAccessUrl().toString();
        if (usingDevNull) {
            String expectedUrl = "https://devnull-fake-url/handle/" + j10pRepoCopy.getId();
            assertEquals(expectedUrl, accessUrl);
            assertEquals(1, j10pRepoCopy.getExternalIds().size());
            assertEquals(expectedUrl, j10pRepoCopy.getExternalIds().get(0));
        } else {
            assertTrue(accessUrl.startsWith("file:"));
            assertEquals(1, j10pRepoCopy.getExternalIds().size());
            assertTrue(j10pRepoCopy.getExternalIds().get(0).startsWith("file:"));
        }
    }

    private void resetGrantProjectName(Submission submission, String grantProjectName) throws IOException {
        String resolvedProjectName = resolveProjectName(grantProjectName);
        Grant grant = submission.getGrants().get(0);
        grant.setProjectName(resolvedProjectName);
        passClient.updateObject(grant);
    }

    private String resolveProjectName(String grantProjectName) throws IOException {
        if (Objects.isNull(grantProjectName)) {
            List<PassEntity> entities = loadSubmissionEntities();
            return entities.stream().filter(pe -> pe instanceof Grant)
                .map(pe -> ((Grant) pe).getProjectName())
                .findFirst().get();
        }
        return grantProjectName;
    }

    private List<String> getFileNames() throws IOException {
        List<PassEntity> entities = loadSubmissionEntities();
        return entities.stream()
            .filter(pe -> pe instanceof File)
            .map(file -> ((File) file).getName())
            .toList();
    }

    private List<PassEntity> loadSubmissionEntities() throws IOException {
        List<PassEntity> entities = new LinkedList<>();
        try (InputStream is = ResourceTestUtil.readSubmissionJson("sample1-unsubmitted")) {
            submissionTestUtil.createSubmissionFromJson(is, entities);
        }
        return entities;
    }

    private void initInvenioApiStubs() throws IOException {
        String searchRecordsJsonResponse = "{ \"hits\": { \"hits\": [] } }";
        stubFor(get("/api/user/records?q=metadata.title:%22Specific%20protein%20supplementation%20using%20" +
            "soya%2C%20casein%20or%20whey%20differentially%20affects%20regional%20gut%20growth%20and%20luminal%20" +
            "growth%20factor%20bioactivity%20in%20rats%3B%20implications%20for%20the%20treatment%20of%20gut%20" +
            "injury%20and%20stimulating%20repair%22")
            .willReturn(ok(searchRecordsJsonResponse)));

        String recordsJsonResponse = "{ \"id\": \"test-record-id\", \"links\": " +
            "{ \"self_html\": \"http://localhost:9030/records/test-record-id/latest\"} }";
        stubFor(post("/api/records")
            .willReturn(ok(recordsJsonResponse)));

        stubFor(post("/api/records/test-record-id/draft/files")
            .willReturn(ok()));

        List<String> fileNames = getFileNames();
        fileNames.forEach(fileName -> {
            stubFor(put("/api/records/test-record-id/draft/files/" + fileName + "/content")
                .willReturn(ok()));
            stubFor(post("/api/records/test-record-id/draft/files/" + fileName + "/commit")
                .willReturn(ok()));
        });

        stubFor(post("/api/records/test-record-id/draft/actions/publish")
            .willReturn(ok()));
    }

    private void verifyInvenioApiStubs(int expectedCount) throws IOException, URISyntaxException {
        WireMock.verify(expectedCount, getRequestedFor(
            urlEqualTo("/api/user/records?q=metadata.title:%22Specific%20protein%20supplementation%20using%20" +
                "soya%2C%20casein%20or%20whey%20differentially%20affects%20regional%20gut%20growth%20and%20luminal%20" +
                "growth%20factor%20bioactivity%20in%20rats%3B%20implications%20for%20the%20treatment%20of%20gut%20" +
                "injury%20and%20stimulating%20repair%22")));
        String recordPayload = Files.readString(
            Paths.get(SubmissionProcessorIT.class.getResource("expectedInvenioRecord.json").toURI()));
        WireMock.verify(expectedCount, postRequestedFor(urlEqualTo("/api/records"))
            .withRequestBody(equalTo(recordPayload)));
        List<String> fileNames = getFileNames();
        WireMock.verify(expectedCount * fileNames.size(), postRequestedFor(
            urlEqualTo("/api/records/test-record-id/draft/files")));
        fileNames.forEach(fileName -> {
            WireMock.verify(expectedCount, putRequestedFor(
                urlEqualTo("/api/records/test-record-id/draft/files/" + fileName + "/content")));
            WireMock.verify(expectedCount, postRequestedFor(
                urlEqualTo("/api/records/test-record-id/draft/files/" + fileName + "/commit")));
        });
        WireMock.verify(expectedCount, postRequestedFor(
            urlEqualTo("/api/records/test-record-id/draft/actions/publish")));
    }

}
