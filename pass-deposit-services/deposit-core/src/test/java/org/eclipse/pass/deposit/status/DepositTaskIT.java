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
package org.eclipse.pass.deposit.status;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.eclipse.deposit.util.async.Condition;
import org.eclipse.pass.deposit.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.service.AbstractDepositIT;
import org.eclipse.pass.deposit.service.DepositProcessor;
import org.eclipse.pass.deposit.service.DepositTaskHelper;
import org.eclipse.pass.deposit.transport.RepositoryConnectivityService;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
class DepositTaskIT extends AbstractDepositIT {

    /**
     * Pre-built package missing a file specified in the METS.xml
     */
    private final ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);

    @Autowired private DepositProcessor depositProcessor;
    @Autowired private DepositTaskHelper depositTaskHelper;

    @MockitoSpyBean(name = "errorHandler") private DepositServiceErrorHandler errorHandler;
    @MockitoBean private RepositoryConnectivityService repositoryConnectivityService;

    /**
     * A submission with a valid package should result in success.
     */
    @Test
    void testDepositTask() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        when(repositoryConnectivityService.verifyConnectByURL(anyString())).thenReturn(true);
        initDSpaceApiStubs();

        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());

        // WHEN
        submissionProcessor.accept(actualSubmission);

        // Wait for the Deposit resource to show up as ACCEPTED (terminal state)
        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            true);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
            DepositStatus.ACCEPTED == deposits.iterator().next().getDepositStatus()));

        Set<Deposit> deposits = c.getResult();
        Deposit deposit = deposits.iterator().next();

        assertEquals("http://localhost:9030/dspace/website/items/uuid",
            deposit.getRepositoryCopy().getAccessUrl().toString());

        // No exceptions should be handled by the error handler
        verifyNoInteractions(errorHandler);
        verifyDSpaceApiStubs(1);
    }

    @Test
    void testDepositError_FailedState() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        when(repositoryConnectivityService.verifyConnectByURL(anyString())).thenReturn(true);
        stubFor(get("/dspace/api/security/csrf").willReturn(WireMock.notFound().
            withHeader("DSPACE-XSRF-TOKEN", "csrftoken")));
        stubFor(post("/dspace/api/authn/login")
            .willReturn(WireMock.badRequest().withStatusMessage("Testing deposit error")));

        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());

        // WHEN
        submissionProcessor.accept(actualSubmission);

        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
                                                DepositStatus.FAILED == deposits.iterator().next()
                                                                                        .getDepositStatus()));
        Set<Deposit> deposits = c.getResult();
        assertNull(deposits.iterator().next().getDepositStatusRef());

        verify(errorHandler).handleError(throwableCaptor.capture());
        assertTrue(throwableCaptor.getValue().getCause().getMessage().contains("Testing deposit error"));
    }

    @Test
    void testDepositError_RetryState() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        when(repositoryConnectivityService.verifyConnectByURL(anyString())).thenReturn(false);
        initDSpaceApiStubs();

        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());

        // WHEN
        submissionProcessor.accept(actualSubmission);

        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> {
            Deposit deposit = deposits.iterator().next();
            return deposits.size() == 1 && DepositStatus.RETRY == deposit.getDepositStatus();
        }));
        Set<Deposit> deposits = c.getResult();
        assertNull(deposits.iterator().next().getDepositStatusRef());

        verify(errorHandler).handleError(throwableCaptor.capture());
        assertTrue(throwableCaptor.getValue().getCause().getMessage()
            .contains("Transport connectivity failed for deposit"));
        verifyDSpaceApiStubs(0);
    }

    @Test
    @DirtiesContext
    void testDepositError_NoRetryStateIfDisabled() throws Exception {
        ReflectionTestUtils.setField(depositTaskHelper, "retryFailedDepositsEnabled", false);
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        when(repositoryConnectivityService.verifyConnectByURL(anyString())).thenReturn(false);
        stubFor(get("/dspace/api/security/csrf").willReturn(WireMock.notFound().
            withHeader("DSPACE-XSRF-TOKEN", "csrftoken")));
        stubFor(post("/dspace/api/authn/login")
            .willReturn(WireMock.badRequest().withStatusMessage("Testing deposit error")));

        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());

        // WHEN
        submissionProcessor.accept(actualSubmission);

        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> {
            Deposit deposit = deposits.iterator().next();
            return deposits.size() == 1 && DepositStatus.FAILED == deposit.getDepositStatus();
        }));
        Set<Deposit> deposits = c.getResult();
        assertNull(deposits.iterator().next().getDepositStatusRef());

        verify(errorHandler).handleError(throwableCaptor.capture());
        assertTrue(throwableCaptor.getValue().getCause().getMessage().contains("Testing deposit error"));
        WireMock.verify(1, getRequestedFor(urlEqualTo("/dspace/api/security/csrf")));
        WireMock.verify(1, postRequestedFor(urlEqualTo("/dspace/api/authn/login")));
    }

}
