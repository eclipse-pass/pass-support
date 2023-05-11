/*
 * Copyright 2017 Johns Hopkins University
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

package org.eclipse.pass.loader.journal.nih;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * @author apb@jhu.edu
 */
public class BatchJournalFinderTest {
    private PassClient client;
    private BatchJournalFinder toTest;
    private Journal journal1;
    private Journal journal2;
    private Journal journal3;
    private Journal journal4;
    private Journal journal5;

    @BeforeEach
    public void setupTest() throws Exception {
        journal1 = new Journal();
        journal1.setId("test:1");
        journal1.setJournalName("Journal One");
        journal1.setNlmta("NLMTA1");
        journal1.setIssns(Arrays.asList("0000-0001", "0000-0002"));

        journal2 = new Journal();
        journal2.setId("test:2");
        journal2.setJournalName("Journal Two");
        journal1.setNlmta("NLMTA2");
        journal2.setIssns(Arrays.asList("0000-0003", "0000-0004"));

        journal3 = new Journal();
        journal3.setId("test:3");
        journal3.setJournalName("Journal Three");
        journal3.setNlmta("NLMTA3");
        journal3.setIssns(Arrays.asList("0000-0004", "0000-0005"));

        journal4 = new Journal();
        journal4.setId("test:4");
        journal4.setJournalName(journal3.getJournalName());
        journal4.setNlmta(journal3.getNlmta());
        journal4.setIssns(Arrays.asList("0000-0006"));

        journal5 = new Journal();
        journal5.setId("test:5");
        journal5.setJournalName(journal3.getJournalName());
        journal5.setIssns(Arrays.asList("0000-0005"));

        List<PassEntity> journals = new ArrayList<>();
        journals.add(journal1);
        journals.add(journal2);
        journals.add(journal3);
        journals.add(journal4);
        journals.add(journal5);

        client = Mockito.mock(PassClient.class);
        when(client.streamObjects(any())).thenReturn(journals.stream());

        toTest = new BatchJournalFinder(client);
    }

    @Test
    public void journalFindTest() throws Exception {
        assertNotNull(toTest.find("hmm1", journal1.getJournalName(), Collections.singletonList("0000-0001")));
        assertNotNull(toTest.find("hmm2", journal2.getJournalName(), Collections.singletonList("0000-0003")));
        assertNotNull(toTest.find("hmm3", journal3.getJournalName(), Collections.singletonList("0000-0005")));

        // next would resolve to test:2, but that was just found, so we skip processing
        // it
        assertEquals("SKIP", toTest.find(null, journal2.getJournalName(), Collections.singletonList("0000-0004")));
    }

    @Test
    public void journalNotFoundTest() throws Exception {
        assertNull(toTest.find(null, null, Collections.singletonList("0000-000")));
    }

    @Test
    public void manualAddTest() {
        final String ID = "blah";
        final String ISSN1 = "000-001";
        final String ISSN2 = "000-002";

        final Journal toAdd = new Journal();
        toAdd.setId(ID);
        toAdd.setIssns(Arrays.asList(ISSN1, ISSN2));
        toAdd.setPmcParticipation(PmcParticipation.A);

        toTest.add(toAdd);

        final String found = toTest.find(null, null, Arrays.asList(ISSN1, ISSN2));
        assertNotNull(found);
    }

    @Test
    public void insufficientMatchTest() throws Exception {

        // only one element matches - this should return null
        final String found = toTest.find(null, null, Collections.singletonList("0000-0001"));
        assertNull(found);
    }

    @Test
    public void nameAndOneIssnMatchTest() throws Exception {
        // two elements match -
        final String found = toTest.find(null, "Journal One", Collections.singletonList("0000-0001"));
        assertNotNull(found);
        assertEquals(journal1.getId(), found);
    }

    @Test
    public void twoIssnsMatchTest() throws Exception {
        // two elements match -
        final String found = toTest.find(null, null, Arrays.asList("0000-0001", "0000-0002"));
        assertNotNull(found);
        assertEquals(journal1.getId(), found);
    }

    @Test
    public void nlmtaAndIssnMatchTest() throws Exception {
        // two elements match -
        final String found = toTest.find(journal1.getNlmta(), null, Collections.singletonList("0000-0002"));
        assertNotNull(found);
        assertEquals(journal1.getId(), found);
    }

    @Test
    public void duplicateJournalTest() throws Exception {
        // two elements match -
        String found = toTest.find("NLMTA1", "Journal One", Collections.singletonList("0000-0001"));
        assertNotNull(found);
        assertEquals(journal1.getId(), found);

        // should be flagged as a duplicate
        found = toTest.find("NLMTA1", "Journal One", Collections.singletonList("0000-0001"));
        assertNotNull(found);
        assertEquals("SKIP", found);
    }

    @Test
    public void cascadingJournalTest() throws Exception {
        // two elements match -
        String found = toTest.find(journal3.getNlmta(), journal3.getJournalName(), journal3.getIssns());
        assertNotNull(found);
        assertEquals(journal3.getId(), found);

        // first uri is now removed from consideration - find next best qualifying match
        found = toTest.find(journal3.getNlmta(), journal3.getJournalName(), Arrays.asList("0000-0005", "0000-0006"));
        assertNotNull(found);
        assertEquals(journal4.getId(), found);

        found = toTest.find(journal3.getNlmta(), journal3.getJournalName(), Arrays.asList("0000-0005", "0000-0006"));
        assertNotNull(found);
        assertEquals(journal5.getId(), found);
    }

    @Test
    public void newStyleIssnTest() throws Exception {
        // two elements match -
        final String found = toTest.find(journal2.getNlmta(), null,
                Arrays.asList("Print:0000-0003", "Online:0000-0004"));
        assertNotNull(found);
        String uri2 = "test:2";
        assertEquals(uri2, found);
    }
}
