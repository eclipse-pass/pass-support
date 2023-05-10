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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author apb@jhu.edu
 */

@RunWith(MockitoJUnitRunner.class)
public class LoaderEngineTest {
    @Mock
    PassClient client;

    @Mock
    BatchJournalFinder finder;

    @Captor
    ArgumentCaptor<Journal> journalCaptor;

    private LoaderEngine toTest;

    @Before
    public void setUp() {

        toTest = new LoaderEngine(client, finder);
    }

    @Test
    public void addPmcParticipationUpdateTest() throws IOException {

        final Journal existing = new Journal();
        existing.setId("test:addPmcParticipation");
        existing.setJournalName("My Journal");
        existing.getIssns().add("000-123");

        when(client.getObject(eq(Journal.class), eq(existing.getId()))).thenReturn(existing);
        when(finder.find(existing.getNlmta(), existing.getJournalName(), existing.getIssns())).thenReturn(
            existing.getId().toString());

        //final Journal toAdd = new PMCSource();
        Journal toAdd = new Journal();
        toAdd.setIssns(existing.getIssns());
        toAdd.setJournalName(existing.getJournalName());
        toAdd.setPmcParticipation(PmcParticipation.A);

        toTest.load(Stream.of(toAdd), true);

        verify(client).updateObject(journalCaptor.capture());

        final Journal updated = journalCaptor.getValue();
        assertEquals(existing.getId(), updated.getId());
        assertEquals(existing.getJournalName(), updated.getJournalName());
        assertEquals(existing.getIssns(), updated.getIssns());
        assertEquals(updated.getPmcParticipation(), toAdd.getPmcParticipation());
    }

    @Test
    public void removePmcParticipationTest() throws IOException {
        final Journal existing = new Journal();
        existing.setId("test:removePmcParticipation");
        existing.setJournalName("My Journal");
        existing.getIssns().add("000-123");
        existing.setPmcParticipation(PmcParticipation.A);

        when(client.getObject(eq(Journal.class), eq(existing.getId()))).thenReturn(existing);
        when(finder.find(existing.getNlmta(), existing.getJournalName(), existing.getIssns())).thenReturn(
            existing.getId().toString());

        //final Journal toAdd = new PMCSource();
        final Journal toAdd = new Journal();
        toAdd.setIssns(existing.getIssns());
        toAdd.setJournalName(existing.getJournalName());

        toTest.load(Stream.of(toAdd), true);

        verify(client).updateObject(journalCaptor.capture());

        final Journal updated = journalCaptor.getValue();
        assertEquals(existing.getId(), updated.getId());
        assertEquals(existing.getJournalName(), updated.getJournalName());
        assertEquals(existing.getIssns(), updated.getIssns());
        assertEquals(updated.getPmcParticipation(), toAdd.getPmcParticipation());
    }

    @Test
    public void noUpdateTest() throws IOException {
        final Journal existing = new Journal();
        existing.setId("test:noUpdateTest");
        existing.setJournalName("My Journal");
        existing.getIssns().add("000-123");
        existing.setPmcParticipation(PmcParticipation.A);

        when(client.getObject(eq(Journal.class), eq(existing.getId()))).thenReturn(existing);
        when(finder.find(existing.getNlmta(), existing.getJournalName(), existing.getIssns())).thenReturn(
            existing.getId().toString());

        final Journal toAdd = new Journal();
        toAdd.setIssns(existing.getIssns());
        toAdd.setJournalName(existing.getJournalName());

        toTest.load(Stream.of(toAdd), false);

        verify(client, times(0)).updateObject(any());
    }

    @Test
    public void createSkipDuplicatesTest() throws IOException {

        final Journal newJournal = new Journal();

        newJournal.setJournalName("My Journal");
        newJournal.setId("test:createSkipUpdatesTest");
        newJournal.getIssns().add("000-123");
        newJournal.setPmcParticipation(PmcParticipation.A);

        when(client.getObject(Journal.class, "test:createSkipUpdatesTest")).thenReturn(newJournal);
        when(finder.find(newJournal.getNlmta(), newJournal.getJournalName(), newJournal.getIssns())).thenReturn(null).
                    thenReturn(
                        URI.create(
                               "test:createSkipUpdatesTest")
                           .toString());

        toTest.load(Stream.of(newJournal, newJournal), true);

        verify(client, times(1)).createObject(eq(newJournal));
        verify(client, times(0)).updateObject(any());
    }
}
