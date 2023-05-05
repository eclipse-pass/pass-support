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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.ZonedDateTime;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure the equals / hashcode functions work.
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class SubmissionEventModelTests {
    @Test
    public void testSubmissionEqualsAndHashCode()  {

        SubmissionEvent submissionEvent1 = createSubmissionEvent();
        SubmissionEvent submissionEvent2 = createSubmissionEvent();

        assertEquals(submissionEvent1, submissionEvent2);
        assertEquals(submissionEvent1.hashCode(), submissionEvent2.hashCode());

        submissionEvent1.setPerformerRole(PerformerRole.SUBMITTER);
        assertTrue(!submissionEvent1.equals(submissionEvent2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testSubmissionEventCopyConstructor()  {
        SubmissionEvent submissionEvent = createSubmissionEvent();
        SubmissionEvent submissionEventCopy = new SubmissionEvent(submissionEvent);
        assertEquals(submissionEvent, submissionEventCopy);

        URI newLink = URI.create("different:link");
        submissionEventCopy.setLink(newLink);
        assertEquals(URI.create(TestValues.SUBMISSIONEVENT_LINK), submissionEvent.getLink());
        assertEquals(newLink, submissionEventCopy.getLink());

        submissionEventCopy.setEventType(EventType.CANCELLED);
        assertEquals(EventType.of(TestValues.SUBMISSIONEVENT_EVENT_TYPE),
                     submissionEvent.getEventType());
        assertEquals(EventType.CANCELLED, submissionEventCopy.getEventType());
    }

    private SubmissionEvent createSubmissionEvent()  {
        SubmissionEvent submissionEvent = new SubmissionEvent();
        submissionEvent.setId(TestValues.SUBMISSIONEVENT_ID);
        submissionEvent.setEventType(EventType.of(TestValues.SUBMISSIONEVENT_EVENT_TYPE));
        ZonedDateTime zdt = ZonedDateTime.parse(TestValues.SUBMISSIONEVENT_PERFORMED_DATE_STR);
        submissionEvent.setPerformedDate(zdt);
        submissionEvent.setPerformedBy(createUser(TestValues.USER_ID_1));
        submissionEvent.setPerformerRole(PerformerRole.PREPARER);
        submissionEvent.setSubmission(createSubmission(TestValues.SUBMISSION_ID_1));
        submissionEvent.setComment(TestValues.SUBMISSIONEVENT_COMMENT);
        submissionEvent.setLink(URI.create(TestValues.SUBMISSIONEVENT_LINK));

        return submissionEvent;
    }
}
