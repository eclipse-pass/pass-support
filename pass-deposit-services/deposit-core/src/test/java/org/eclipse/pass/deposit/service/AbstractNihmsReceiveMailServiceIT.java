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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.eclipse.pass.deposit.service.NihmsReceiveMailService.NIHMS_DEP_STATUS_REF_PREFIX;
import static org.eclipse.pass.deposit.util.ResourceTestUtil.findByNameAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.pass.deposit.AbstractDepositSubmissionIT;
import org.eclipse.pass.deposit.provider.nihms.NihmsAssembler;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.mock.mockito.SpyBean;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public abstract class AbstractNihmsReceiveMailServiceIT extends AbstractDepositSubmissionIT {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAPS)
        .withConfiguration(new GreenMailConfiguration().withUser("testnihms@localhost", "testnihmspassword"))
        .withPerMethodLifecycle(false);

    @SpyBean private NihmsReceiveMailService nihmsReceiveMailService;

    @Test
    void testHandleReceivedMail() throws Exception {
        // GIVEN
        Submission testSubmission = initSubmissionDeposit();
        final String subject = "Bulk submission";
        final String body = findByNameAsString("nihmsemail-success.html", this.getClass())
            .replace("{test-submission-id}", testSubmission.getId());
        Session smtpSession = GreenMailUtil.getSession(greenMail.getImaps().getServerSetup());
        MimeMessage mimeMessage = new MimeMessage(smtpSession);
        mimeMessage.setRecipients(Message.RecipientType.TO, "testnihms@localhost");
        mimeMessage.setFrom("test-from@localhost");
        mimeMessage.setSubject(subject);
        mimeMessage.setContent(body, "text/html; charset=\"utf-8\"");
        mimeMessage.saveChanges();
        GreenMailUser user = greenMail.setUser("testnihms@localhost", "testnihmspassword");

        // WHEN
        user.deliver(mimeMessage);

        // THEN
        // wait for email poller to run, every 2 seconds plus few seconds for processing
        await().atMost(4, SECONDS).untilAsserted(() -> {
            verify(nihmsReceiveMailService, times(1))
                .handleReceivedMail(any());
            PassClientSelector<Deposit> sel = new PassClientSelector<>(Deposit.class);
            sel.setFilter(RSQL.equals("submission.id", testSubmission.getId()));
            sel.setInclude("repositoryCopy");
            List<Deposit> actualDeposits = passClient.selectObjects(sel).getObjects();
            assertEquals(1, actualDeposits.size());
            Deposit pmcDeposit = actualDeposits.get(0);
            assertEquals(DepositStatus.SUBMITTED, pmcDeposit.getDepositStatus());
            assertEquals(CopyStatus.IN_PROGRESS, pmcDeposit.getRepositoryCopy().getCopyStatus());
            assertEquals(NIHMS_DEP_STATUS_REF_PREFIX + "test-nihms-id", pmcDeposit.getDepositStatusRef());
            assertEquals("Accepted by the NIHMS workflow. NIHMS-ID: test-nihms-id", pmcDeposit.getStatusMessage());
        });
    }

    private Submission initSubmissionDeposit() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        submission.setSubmittedDate(ZonedDateTime.now());
        passClient.updateObject(submission);
        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());
        RepositoryCopy repositoryCopy = new RepositoryCopy();
        repositoryCopy.setCopyStatus(CopyStatus.IN_PROGRESS);
        repositoryCopy.setRepository(actualSubmission.getRepositories().get(0));
        passClient.createObject(repositoryCopy);
        final RepositoryCopy actualRepoCopy = passClient.getObject(RepositoryCopy.class, repositoryCopy.getId());
        Deposit pmcDeposit = new Deposit();
        pmcDeposit.setSubmission(actualSubmission);
        // There is only the pmc repo on this submission
        pmcDeposit.setRepository(actualSubmission.getRepositories().get(0));
        pmcDeposit.setRepositoryCopy(actualRepoCopy);
        pmcDeposit.setDepositStatus(DepositStatus.SUBMITTED);
        pmcDeposit.setDepositStatusRef(NihmsAssembler.NIHMS_PKG_DEP_REF_PREFIX +
            "nihms-native-2017-07_2023-10-23_13-10-30_" + actualSubmission.getId());
        passClient.createObject(pmcDeposit);
        return actualSubmission;
    }
}
