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
package org.eclipse.pass.deposit.messaging.service;

import org.eclipse.pass.deposit.messaging.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class EmptySubmissionIT extends AbstractSubmissionIT {

    @Test
    public void submissionWithNoFiles() {
        DepositServiceRuntimeException ex = assertThrows(DepositServiceRuntimeException.class, () -> {
            Submission submission = findSubmission(createSubmission(
                ResourceTestUtil.readSubmissionJson("sample1-no-files")));

            // This submission should fail off the bat because there's no files in the submission.
            submissionProcessor.accept(submission);
        });

        assertEquals("Unable to update status of 13 to 'IN_PROGRESS': Update postcondition failed for 13: " +
            "the DepositSubmission has no files attached! (Hint: check the incoming links to the Submission)",
            ex.getMessage());
    }

}
