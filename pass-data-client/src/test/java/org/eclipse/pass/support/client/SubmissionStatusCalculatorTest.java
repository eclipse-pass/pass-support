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
package org.eclipse.pass.support.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.EventType;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.junit.jupiter.api.Test;

/**
 * Tests the SubmissionStatusCalculator utility functions
 *
 * @author Karen Hanson
 */
public class SubmissionStatusCalculatorTest {
    private final String repo1Id = "repository:1";
    private final String repo2Id = "repository:2";
    private final String repo3Id = "repository:3";
    private ZonedDateTime last = ZonedDateTime.now();

    private Deposit deposit(DepositStatus status, String repId) {
        Deposit d = new Deposit();
        d.setDepositStatus(status);
        d.setRepository(new Repository(repId));
        return d;
    }

    private RepositoryCopy repoCopy(CopyStatus status, String repoId) {
        RepositoryCopy r = new RepositoryCopy();
        r.setCopyStatus(status);
        r.setRepository(new Repository(repoId));
        return r;
    }

    // Make the performed date later on each call
    private SubmissionEvent submissionEvent(EventType eventType) {
        SubmissionEvent event = new SubmissionEvent();
        event.setPerformedDate(last);
        event.setEventType(eventType);

        last = last.plusDays(1);

        return event;
    }

    /**
     * 3 repositories listed, deposits only, no repositoryCopies. As long as none are rejected, it
     * should always be SUBMITTED
     *
     * @throws Exception
     */
    @Test
    public void testPostSubmissionStatusDepositOnlySubmitted() throws Exception {
        List<String> repositories = Arrays.asList(repo1Id, repo2Id, repo3Id);

        List<Deposit> deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                               deposit(DepositStatus.FAILED, repo2Id));
        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, deposits, null));

        deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                 deposit(DepositStatus.FAILED, repo2Id),
                                 deposit(DepositStatus.ACCEPTED, repo3Id));

        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, deposits, null));

        deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                 deposit(DepositStatus.ACCEPTED, repo2Id),
                                 deposit(DepositStatus.ACCEPTED, repo3Id));
        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, deposits, null));

    }

    /**
     * 3 repositories listed, no deposits, repositoryCopies only. As long as none are rejected, it
     * should always be SUBMITTED
     *
     * @throws Exception
     */
    @Test
    public void testPostSubmissionStatusRepoCopyOnlySubmitted() throws Exception {
        List<String> repositories = Arrays.asList(repo1Id, repo2Id, repo3Id);
        List<RepositoryCopy> repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id));

        assertEquals(SubmissionStatus.SUBMITTED,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));

        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id),
                                         repoCopy(CopyStatus.COMPLETE, repo2Id));
        assertEquals(SubmissionStatus.SUBMITTED,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));

        //add one more in-progress repocopy
        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id),
                                         repoCopy(CopyStatus.COMPLETE, repo2Id),
                                         repoCopy(CopyStatus.IN_PROGRESS, repo3Id));
        assertEquals(SubmissionStatus.SUBMITTED,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));

    }

    /**
     * 1 repositories listed, no deposits, repositoryCopy only. copyStatus is null
     *
     * @throws Exception
     */
    @Test
    public void testSubmittedNoDepositCopyNoStatus() throws Exception {
        List<String> repositories = Arrays.asList(repo1Id);
        List<RepositoryCopy> repositoryCopies = Arrays.asList(repoCopy(null, repo1Id));

        assertEquals(SubmissionStatus.SUBMITTED,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));
    }

    /**
     * 1 repositories listed, no deposits, repositoryCopy only. copyStatus is null
     *
     * @throws Exception
     */
    @Test
    public void testSubmittedDepositNullStatusNoRepoCopy() throws Exception {
        List<String> repositories = Arrays.asList(repo1Id);
        List<Deposit> deposits = Arrays.asList(deposit(null, repo1Id));

        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, deposits, null));
    }

    /**
     * 3 repositories with various states for deposits and repositoryCopies all of which should come out as
     * having the status of SUBMITTED
     *
     * @throws Exception
     */
    @Test
    public void testPostSubmissionStatusDepositAndRepoCopySubmitted() throws Exception {
        List<String> repositories = Arrays.asList(repo1Id, repo2Id, repo3Id);

        List<Deposit> deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                               deposit(DepositStatus.FAILED, repo2Id));

        List<RepositoryCopy> repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id));

        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id),
                                         repoCopy(CopyStatus.COMPLETE, repo2Id));
        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                 deposit(DepositStatus.FAILED, repo2Id),
                                 deposit(DepositStatus.ACCEPTED, repo3Id));
        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        //add one more in-progress repocopy
        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id),
                                         repoCopy(CopyStatus.COMPLETE, repo2Id),
                                         repoCopy(CopyStatus.IN_PROGRESS, repo3Id));
        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));
    }

    /**
     * Tests various situations with Deposits only that should trigger {@code SubmissionStatus.NEEDS_ATTENTION}
     * as the {@code Submission.submissionStatus} value
     *
     * @throws Exception
     */
    @Test
    public void testPostSubmissionStatusDepositOnlyNeedsAttention() throws Exception {
        //1 repo 1 rejected deposit
        List<String> repositories = Arrays.asList(repo1Id);
        List<Deposit> deposits = Arrays.asList(deposit(DepositStatus.REJECTED, repo1Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, deposits, null));

        // 3 repo, 2 deposits, 1 rejected
        repositories = Arrays.asList(repo1Id, repo2Id, repo3Id);
        deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                 deposit(DepositStatus.REJECTED, repo2Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, deposits, null));

        // 3 repo, 3 deposits, 1 rejected
        deposits = Arrays.asList(deposit(DepositStatus.REJECTED, repo1Id),
                                 deposit(DepositStatus.ACCEPTED, repo2Id),
                                 deposit(DepositStatus.ACCEPTED, repo3Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, deposits, null));

    }

    /**
     * Tests various situations with RepositoryCopies only that should trigger {@code SubmissionStatus.NEEDS_ATTENTION}
     * as the {@code Submission.submissionStatus} value
     *
     * @throws Exception
     */
    @Test
    public void testPostSubmissionStatusRepoCopyOnlyNeedsAttention() throws Exception {
        List<String> repositories = Arrays.asList(repo1Id);
        List<RepositoryCopy> repositoryCopies = Arrays.asList(repoCopy(CopyStatus.REJECTED, repo1Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));

        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.STALLED, repo1Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));

        repositories = Arrays.asList(repo1Id, repo2Id, repo3Id);
        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id),
                                         repoCopy(CopyStatus.STALLED, repo2Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));

        //add one more in-progress repocopy
        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id),
                                         repoCopy(CopyStatus.REJECTED, repo2Id),
                                         repoCopy(CopyStatus.IN_PROGRESS, repo3Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));
    }

    /**
     * Various numbers of repositories with various states for deposits and repositoryCopies all of which
     * should come out as having status of {@code SubmissionStatus.NEEDS_ATTENTION}
     *
     * @throws Exception
     */
    @Test
    public void testPostSubmissionStatusDepositAndRepoCopyNeedsAttention() throws Exception {
        List<String> repositories = Arrays.asList(repo1Id, repo2Id);
        List<Deposit> deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                               deposit(DepositStatus.REJECTED, repo2Id));
        List<RepositoryCopy> repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                 deposit(DepositStatus.ACCEPTED, repo2Id));
        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id),
                                         repoCopy(CopyStatus.STALLED, repo2Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.IN_PROGRESS, repo1Id),
                                         repoCopy(CopyStatus.REJECTED, repo2Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        repositories = Arrays.asList(repo1Id, repo2Id, repo3Id);
        deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                 deposit(DepositStatus.ACCEPTED, repo2Id),
                                 deposit(DepositStatus.FAILED, repo2Id));
        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo2Id),
                                         repoCopy(CopyStatus.STALLED, repo3Id));
        assertEquals(SubmissionStatus.NEEDS_ATTENTION,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

    }

    /**
     * This confirms that if there are completed repository copies for each repository listed, the status is complete,
     * regardless of whether there is a deposit for each one or not.
     *
     * @throws Exception
     */
    @Test
    public void testPostSubmissionStatusComplete() throws Exception {
        List<String> repositories = Arrays.asList(repo1Id);
        List<RepositoryCopy> repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id));
        assertEquals(SubmissionStatus.COMPLETE,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));

        List<Deposit> deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id));
        assertEquals(SubmissionStatus.COMPLETE,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        repositories = Arrays.asList(repo1Id, repo2Id, repo3Id);
        deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                 deposit(DepositStatus.REJECTED, repo2Id));
        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id),
                                         repoCopy(CopyStatus.COMPLETE, repo2Id),
                                         repoCopy(CopyStatus.COMPLETE, repo3Id));
        assertEquals(SubmissionStatus.COMPLETE,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        assertEquals(SubmissionStatus.COMPLETE,
                     SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories, null, repositoryCopies));

    }

    /**
     * This confirms that if you try to provide a null for all values, it will assume submitted.
     * This would only happen if the only repositories were web linked and therefore there is no
     * Deposit to process.
     *
     * @throws Exception
     */
    @Test
    public void testPostSubmissionStatusNulls() throws Exception {
        SubmissionStatus status = SubmissionStatusCalculator.calculatePostSubmissionStatus(null, null, null);
        assertEquals(SubmissionStatus.SUBMITTED, status);
    }

    /**
     * This confirms that even if a Deposit is rejected, a RepositoryCopy's status will override the Deposit status
     *
     * @throws Exception
     */
    @Test
    public void testPostSubmissionStatusRepoCopyOverrideDeposit() throws Exception {
        List<String> repositories = Arrays.asList(repo1Id);
        List<Deposit> deposits = Arrays.asList(deposit(DepositStatus.REJECTED, repo1Id));
        List<RepositoryCopy> repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id));
        assertEquals(SubmissionStatus.COMPLETE,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.IN_PROGRESS, repo1Id));
        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));

        repositories = Arrays.asList(repo1Id, repo2Id);
        deposits = Arrays.asList(deposit(DepositStatus.ACCEPTED, repo1Id),
                                 deposit(DepositStatus.REJECTED, repo2Id));
        repositoryCopies = Arrays.asList(repoCopy(CopyStatus.COMPLETE, repo1Id),
                                         repoCopy(CopyStatus.IN_PROGRESS, repo2Id));
        assertEquals(SubmissionStatus.SUBMITTED,
                SubmissionStatusCalculator.calculatePostSubmissionStatus(repositories,
                        deposits, repositoryCopies));
    }

    /**
     * Confirms that MANUSCRIPT_REQUIRED is appropriately assigned as a pre-submission status.
     */
    @Test
    public void testPreSubmissionStatusManuscriptExpected() {
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED,
                SubmissionStatusCalculator.calculatePreSubmissionStatus(null, null));
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(new ArrayList<SubmissionEvent>(), null));
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(null,
                             SubmissionStatus.MANUSCRIPT_REQUIRED));
        assertEquals(SubmissionStatus.MANUSCRIPT_REQUIRED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(Collections.emptyList(),
                             SubmissionStatus.MANUSCRIPT_REQUIRED));
    }

    @Test
    public void testPreSubmissionStatusDraftExpected() {
        assertEquals(SubmissionStatus.DRAFT, SubmissionStatusCalculator.calculatePreSubmissionStatus(null,
                SubmissionStatus.DRAFT));

        // in fact, the logic inside the calculator will default to supplied status if there are no submission events
        // provided, rather than hard-coding a return.
        assertEquals(SubmissionStatus.SUBMITTED, SubmissionStatusCalculator.calculatePreSubmissionStatus(null,
                SubmissionStatus.SUBMITTED));
    }

    /**
     * Tests various scenarios where outcome should be APPROVAL_REQUESTED
     */
    @Test
    public void testPreSubmissionStatusApprovalRequested() {
        List<SubmissionEvent> submissionEvents =
            Arrays.asList(submissionEvent(EventType.APPROVAL_REQUESTED));
        assertEquals(SubmissionStatus.APPROVAL_REQUESTED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, null));

        submissionEvents =
            Arrays.asList(submissionEvent(EventType.APPROVAL_REQUESTED_NEWUSER));
        assertEquals(SubmissionStatus.APPROVAL_REQUESTED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, null));

        submissionEvents =
            Arrays.asList(submissionEvent(EventType.APPROVAL_REQUESTED_NEWUSER),
                          submissionEvent(EventType.CHANGES_REQUESTED),
                          submissionEvent(EventType.APPROVAL_REQUESTED));
        assertEquals(SubmissionStatus.APPROVAL_REQUESTED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, null));

        assertEquals(SubmissionStatus.APPROVAL_REQUESTED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, SubmissionStatus.DRAFT));
    }

    /**
     * Tests various scenarios in which the pre-submission status should be "CHANGES_REQUESTED"
     */
    @Test
    public void testPreSubmissionStatusChangesRequested() {
        List<SubmissionEvent> submissionEvents =
            Arrays.asList(submissionEvent(EventType.CHANGES_REQUESTED));
        assertEquals(SubmissionStatus.CHANGES_REQUESTED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, null));

        submissionEvents =
            Arrays.asList(submissionEvent(EventType.APPROVAL_REQUESTED),
                          submissionEvent(EventType.CHANGES_REQUESTED));
        assertEquals(SubmissionStatus.CHANGES_REQUESTED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, null));

        submissionEvents =
            Arrays.asList(submissionEvent(EventType.APPROVAL_REQUESTED),
                          submissionEvent(EventType.CHANGES_REQUESTED),
                          submissionEvent(EventType.APPROVAL_REQUESTED),
                          submissionEvent(EventType.CHANGES_REQUESTED));
        assertEquals(SubmissionStatus.CHANGES_REQUESTED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, null));

        assertEquals(SubmissionStatus.CHANGES_REQUESTED,
                     SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, SubmissionStatus.DRAFT));
    }

    /**
     * Tests various scenarios in which the pre-submission status should be "CANCELLED"
     */
    @Test
    public void testPreSubmissionStatusCancelled() {
        List<SubmissionEvent> submissionEvents =
            Arrays.asList(submissionEvent(EventType.CANCELLED));
        assertEquals(SubmissionStatus.CANCELLED,
                SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, null));

        submissionEvents =
            Arrays.asList(submissionEvent(EventType.APPROVAL_REQUESTED),
                          submissionEvent(EventType.CANCELLED));
        assertEquals(SubmissionStatus.CANCELLED,
                SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, null));

        submissionEvents =
            Arrays.asList(submissionEvent(EventType.APPROVAL_REQUESTED),
                          submissionEvent(EventType.CHANGES_REQUESTED),
                          submissionEvent(EventType.APPROVAL_REQUESTED),
                          submissionEvent(EventType.CANCELLED));
        assertEquals(SubmissionStatus.CANCELLED,
                SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, null));

        assertEquals(SubmissionStatus.CANCELLED,
                SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents, SubmissionStatus.DRAFT));
    }

    /**
     * Makes sure typical status changes log as valid. Some of these will generate warnings
     * since this service is not supposed to change the pre-Submission statuses - it will warn
     * when one is being changed.
     */
    @Test
    public void testValidateStatusChangeOK() {
        //if any of there throw exception the test fails:
        SubmissionStatusCalculator.validateStatusChange(false, SubmissionStatus.APPROVAL_REQUESTED,
                SubmissionStatus.CHANGES_REQUESTED);
        SubmissionStatusCalculator.validateStatusChange(false, SubmissionStatus.CHANGES_REQUESTED,
                SubmissionStatus.APPROVAL_REQUESTED);
        SubmissionStatusCalculator.validateStatusChange(false, SubmissionStatus.CHANGES_REQUESTED,
                SubmissionStatus.CANCELLED);

        SubmissionStatusCalculator.validateStatusChange(false, SubmissionStatus.DRAFT,
                SubmissionStatus.APPROVAL_REQUESTED);
        SubmissionStatusCalculator.validateStatusChange(false, SubmissionStatus.DRAFT,
                SubmissionStatus.CHANGES_REQUESTED);
        SubmissionStatusCalculator.validateStatusChange(false, SubmissionStatus.DRAFT,
                SubmissionStatus.CANCELLED);

        SubmissionStatusCalculator.validateStatusChange(true, SubmissionStatus.APPROVAL_REQUESTED,
                SubmissionStatus.SUBMITTED);
        SubmissionStatusCalculator.validateStatusChange(true, SubmissionStatus.MANUSCRIPT_REQUIRED,
                SubmissionStatus.SUBMITTED);
        SubmissionStatusCalculator.validateStatusChange(true, SubmissionStatus.SUBMITTED,
                SubmissionStatus.COMPLETE);
        SubmissionStatusCalculator.validateStatusChange(true, SubmissionStatus.SUBMITTED,
                SubmissionStatus.NEEDS_ATTENTION);
        SubmissionStatusCalculator.validateStatusChange(true, SubmissionStatus.NEEDS_ATTENTION,
                SubmissionStatus.SUBMITTED);
        SubmissionStatusCalculator.validateStatusChange(true, SubmissionStatus.NEEDS_ATTENTION,
                SubmissionStatus.COMPLETE);
        SubmissionStatusCalculator.validateStatusChange(true, SubmissionStatus.DRAFT,
                SubmissionStatus.SUBMITTED);
        SubmissionStatusCalculator.validateStatusChange(true, SubmissionStatus.DRAFT,
                SubmissionStatus.COMPLETE);
    }

    /**
     * Should fail based on assigning submitted status to unsubmitted
     */
    @Test
    public void testSettingStatusUnsubmittedShouldFail() {
        assertThrows(Exception.class, () -> {
            SubmissionStatusCalculator.validateStatusChange(true, SubmissionStatus.APPROVAL_REQUESTED,
                    SubmissionStatus.CHANGES_REQUESTED);
        });
    }

    /**
     * Should fail based on change from submitted to unsubmitted status
     */
    @Test
    public void testChangingStatusToUnsubmittedShouldFail() {
        assertThrows(Exception.class, () -> {
            SubmissionStatusCalculator.validateStatusChange(false, SubmissionStatus.SUBMITTED,
                    SubmissionStatus.CANCELLED);
        });
    }

    /**
     * Should fail based on Submission have submitted=false but trying to assign post-submission status
     */
    @Test
    public void testAssigningToNonsubmittedShouldFail() {
        assertThrows(Exception.class, () -> {
            SubmissionStatusCalculator.validateStatusChange(false, SubmissionStatus.APPROVAL_REQUESTED,
                    SubmissionStatus.SUBMITTED);
        });
    }

    /**
     * Should be OK, but log should show a warning
     *
     * todo: Doesn't actually check logs for the warning
     */
    @Test
    public void testApprovalRequestToChangesRequestedShouldLogWarning() {
        try {
            SubmissionStatusCalculator.validateStatusChange(false, SubmissionStatus.APPROVAL_REQUESTED,
                    SubmissionStatus.CHANGES_REQUESTED);
        } catch (Exception ex) {
            fail("Exception should not have been thrown, just a warning for this.");
        }
    }
}
