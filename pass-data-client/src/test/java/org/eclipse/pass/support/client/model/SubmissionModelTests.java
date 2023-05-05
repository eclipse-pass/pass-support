/*
 * Copyright 2023 Johns Hopkins University
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
package org.eclipse.pass.support.client.model;

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createSubmission;
import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure the equals / hashcode functions work.
 * Note that in these tests every field is set, though in a reality, either
 * submitter OR submitterName/submitterEmail would be set, not both at once.
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class SubmissionModelTests {
    @Test
    public void testSubmissionEqualsAndHashCode()  {
        Submission submission1 = createSubmission(TestValues.SUBMISSION_ID_1);
        Submission submission2 = createSubmission(TestValues.SUBMISSION_ID_1);

        assertEquals(submission1, submission2);
        assertEquals(submission1.hashCode(), submission2.hashCode());

        submission1.setSubmissionStatus(SubmissionStatus.CANCELLED);
        assertTrue(!submission1.equals(submission2));
    }

    /**
     * Verifies that we can use the "submitted" status related to a SubmissionStatus.
     */
    @Test
    public void testSubmissionStatusSubmitted()  {
        assertFalse(SubmissionStatus.APPROVAL_REQUESTED.isSubmitted());
        assertFalse(SubmissionStatus.CANCELLED.isSubmitted());
        assertTrue(SubmissionStatus.COMPLETE.isSubmitted());

        Submission submission = createSubmission(TestValues.SUBMISSION_ID_1);
        assertTrue(submission.getSubmissionStatus().isSubmitted());
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testSubmissionCopyConstructor()  {
        Submission submission = createSubmission(TestValues.SUBMISSION_ID_1);
        List<User> preparersOrig = new ArrayList<>(Arrays.asList(createUser(TestValues.USER_ID_1)));
        submission.setPreparers(preparersOrig);
        Submission submissionCopy = new Submission(submission);
        assertEquals(submission, submissionCopy);

        submissionCopy.setSubmissionStatus(SubmissionStatus.COMPLETE);
        assertEquals(SubmissionStatus.of(TestValues.SUBMISSION_STATUS), submission.getSubmissionStatus());
        assertEquals(SubmissionStatus.COMPLETE, submissionCopy.getSubmissionStatus());

        List<User> preparersNew = new ArrayList<>(
            Arrays.asList(createUser(TestValues.USER_ID_1), createUser(TestValues.USER_ID_2)));
        submissionCopy.setPreparers(preparersNew);
        assertEquals(preparersOrig, submission.getPreparers());
        assertEquals(preparersNew, submissionCopy.getPreparers());
    }

}
