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
package org.eclipse.pass.support.client.model;

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createPublisher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure the equals / hashcode functions work.
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class PublisherModelTests {
    @Test
    public void testPublisherEqualsAndHashCode()  {
        Publisher publisher1 = createPublisher(TestValues.PUBLISHER_ID_1);
        Publisher publisher2 = createPublisher(TestValues.PUBLISHER_ID_1);

        assertEquals(publisher1, publisher2);
        assertEquals(publisher1.hashCode(), publisher2.hashCode());

        publisher1.setName("different");
        assertTrue(!publisher1.equals(publisher2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testPublisherCopyConstructor()  {
        Publisher publisher = createPublisher(TestValues.PUBLISHER_ID_1);
        Publisher publisherCopy = new Publisher(publisher);
        assertEquals(publisher, publisherCopy);

        publisherCopy.setPmcParticipation(PmcParticipation.A);
        assertEquals(PmcParticipation.valueOf(TestValues.PUBLISHER_PMCPARTICIPATION), publisher.getPmcParticipation());
        assertEquals(PmcParticipation.A, publisherCopy.getPmcParticipation());
    }
}
