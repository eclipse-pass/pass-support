package org.eclipse.pass.support.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.stream.Stream;

import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.EventType;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for simple App.
 */
@ExtendWith(MockitoExtension.class)
public class SubmissionStatusServiceTest {
    @Mock
    private PassClient client;

    private SubmissionStatusService service;

    /**
     * Basic test to ensure that appropriate calls are made to client when
     * calculating status of submitted Submission
     *
     * @throws Exception
     */
    @Test
    public void testCalcSubmissionStatusPostSubmission() throws Exception {
        Repository repo1 = new Repository("repo1");
        Repository repo2 = new Repository("repo2");

        Deposit dep1 = new Deposit("dep1");
        Deposit dep2 = new Deposit("dep2");

        RepositoryCopy rc1 = new RepositoryCopy("rc1");
        RepositoryCopy rc2 = new RepositoryCopy("rc2");

        Publication pub = new Publication("publication:1");

        Submission submission = new Submission("submission:1");
        submission.setRepositories(Arrays.asList(repo1, repo2));
        submission.setPublication(pub);
        submission.setSubmitted(true);
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);

        dep1.setSubmission(submission);
        dep1.setRepository(repo1);
        dep1.setRepositoryCopy(rc1);
        dep1.setDepositStatus(DepositStatus.ACCEPTED);

        dep2.setSubmission(submission);
        dep2.setRepository(repo2);
        dep2.setRepositoryCopy(rc2);
        dep2.setDepositStatus(DepositStatus.ACCEPTED);

        rc1.setPublication(pub);
        rc1.setCopyStatus(CopyStatus.ACCEPTED);
        rc1.setRepository(repo1);

        rc2.setPublication(pub);
        rc2.setCopyStatus(CopyStatus.ACCEPTED);
        rc2.setRepository(repo2);

        service = new SubmissionStatusService(client);

        when(client.streamObjects(Mockito.any())).thenReturn(Stream.of(dep1, dep2)).thenReturn(Stream.of(rc1, rc2));

        SubmissionStatus newStatus = service.calculateSubmissionStatus(submission);
        assertEquals(SubmissionStatus.SUBMITTED, newStatus);
    }

    /**
     * Basic test to ensure that appropriate calls are made to client when
     * calculating status of Submission not yet submitted.
     *
     * @throws Exception
     */
    @Test
    public void testCalcSubmissionStatusPreSubmission() throws Exception {
        Repository repo1 = new Repository("repo1");
        Repository repo2 = new Repository("repo2");

        Publication pub = new Publication("publication:1");

        SubmissionEvent ev1 = new SubmissionEvent("ev1");
        SubmissionEvent ev2 = new SubmissionEvent("ev2");

        Submission submission = new Submission("submission:1");
        submission.setRepositories(Arrays.asList(repo1, repo2));
        submission.setPublication(pub);
        submission.setSubmitted(false);

        ev1.setSubmission(submission);
        ev1.setEventType(EventType.APPROVAL_REQUESTED);
        ev1.setPerformedDate(ZonedDateTime.now());
        ev2.setSubmission(submission);
        ev2.setEventType(EventType.CHANGES_REQUESTED);
        ev2.setPerformedDate(ev1.getPerformedDate().plusHours(2));

        service = new SubmissionStatusService(client);

        when(client.streamObjects(Mockito.any())).thenReturn(Stream.of(ev1, ev2));

        SubmissionStatus newStatus = service.calculateSubmissionStatus(submission);
        assertEquals(SubmissionStatus.CHANGES_REQUESTED, newStatus);
    }
}
