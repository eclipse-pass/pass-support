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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Set;

import org.eclipse.deposit.util.async.Condition;
import org.eclipse.pass.deposit.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.service.AbstractDepositIT;
import org.eclipse.pass.deposit.service.DepositProcessor;
import org.eclipse.pass.deposit.transport.sword2.Sword2Transport;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.swordapp.client.SWORDCollection;
import org.swordapp.client.SWORDError;

/**
 * This IT insures that the SWORD transport properly handles the Deposit.depositStatusRef field by updating the
 * Deposit.depositStatus field according to the SWORD state document.  It configures Deposit Services with an Assembler
 * that streams a pre-built package (the files actually submitted to Pass Core in the Submission are ignored, and not
 * streamed to DSpace).  DSpace is the only concrete implementation of a SWORD server used by Deposit Services, so it is
 * employed here.
 *
 * Note this IT uses a specific runtime configuration for Deposit Services in the classpath resource
 * DepositTaskIT.json.  The status mapping indicates that by default the state of a Deposit will be SUBMITTED unless
 * the package is archived (SUCCESS) or withdrawn (REJECTED).  Now, if an exception occurs when performing the SWORD
 * deposit to DSpace (for example, if the package is corrupt), there will be no SWORD state to examine because the
 * package could not be ingested.  In the case of a corrupt package that is rejected without getting in the front door,
 * there will be no Deposit.depositStatusRef, and Deposit.depositStatus will be FAILED.
 *
 * Note that FAILED is an intermediate status.  This means that remedial action can be taken, and the package can be
 * re-submitted without creating a new Submission.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
// the repository configuration json pollutes the context
public class DepositTaskIT extends AbstractDepositIT {

    /**
     * Pre-built package missing a file specified in the METS.xml
     */
    private final ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);

    @Autowired private DepositProcessor depositProcessor;

    @SpyBean(name = "errorHandler") private DepositServiceErrorHandler errorHandler;
    @SpyBean private DepositStatusProcessor depositStatusProcessor;
    @SpyBean private Sword2Transport sword2Transport;

    /**
     * A submission with a valid package should result in success.
     */
    @Test
    public void testDepositTask() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        mockSword();

        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());

        // WHEN
        submissionProcessor.accept(actualSubmission);

        // Wait for the Deposit resource to show up as ACCEPTED (terminal state)
        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() != null);

        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1
            && DepositStatus.SUBMITTED == deposits.iterator().next().getDepositStatus()));

        c.getResult().forEach(deposit -> depositProcessor.accept(deposit));

        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
                                                DepositStatus.ACCEPTED == deposits.iterator().next()
                                                                                          .getDepositStatus()));
        Set<Deposit> deposits = c.getResult();
        Deposit deposit = deposits.iterator().next();

        // Insure a Deposit.depositStatusRef was set on the Deposit resource
        assertNotNull(deposit.getDepositStatusRef());

        // No exceptions should be handled by the error handler
        verifyNoInteractions(errorHandler);

        // Insure the DepositStatusProcessor processed the Deposit.depositStatusRef
        ArgumentCaptor<Deposit> processedDepositCaptor = ArgumentCaptor.forClass(Deposit.class);
        verify(depositStatusProcessor).process(processedDepositCaptor.capture(), any());
        assertEquals(deposit.getId(), processedDepositCaptor.getValue().getId());

        verify(sword2Transport).open(any());
        verify(mockSwordClient).deposit(any(SWORDCollection.class), any(), any());
    }

    @Test
    public void testDepositError() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        mockSword();
        doThrow(new SWORDError(400, "Testing deposit error"))
            .when(mockSwordClient).deposit(any(SWORDCollection.class), any(), any());

        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());

        // WHEN
        assertThrows(DepositServiceRuntimeException.class, () -> submissionProcessor.accept(actualSubmission));

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

}
