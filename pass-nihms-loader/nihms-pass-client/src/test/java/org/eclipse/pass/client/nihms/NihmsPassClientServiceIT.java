/*
 *
 *  * Copyright 2023 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.eclipse.pass.client.nihms;

import static org.eclipse.pass.client.nihms.NihmsPassClientService.ISSNS_FLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Journal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NihmsPassClientServiceIT {

    private static final String ID_FLD = "@id";

    private  NihmsPassClientService underTest;

    private PassClient passClient;

    @BeforeEach
    public void setUp() throws Exception {
        System.setProperty("pass.core.url","http://localhost:8080");
        System.setProperty("pass.core.user","<test_user>");
        System.setProperty("pass.core.password","<test_pw>");
        passClient = PassClient.newInstance();
        underTest = new NihmsPassClientService(passClient);
    }

    /**
     * Demonstrate that a Journal can be looked up by any of its ISSNs.
     */
    @Test
    public void lookupJournalByIssn() throws IOException {
        Journal journal = new Journal();
        journal.setIssns(Arrays.asList("fooissn", "barissn"));
        journal.setJournalName("My Journal");

        //journal = passClient.createAndReadResource(journal, Journal.class);
        passClient.createObject(journal);
        String journalId = journal.getId();

        String journalFilterFoo = RSQL.equals(ISSNS_FLD, "fooissn");
        PassClientSelector<Journal> journalFooSelector = new PassClientSelector<>(Journal.class);
        journalFooSelector.setFilter(journalFilterFoo);
        PassClientResult<Journal> journalFooResult = passClient.selectObjects(journalFooSelector);
        List<Journal> journalsFoo = journalFooResult.getObjects();

        String journalFilterBar = RSQL.equals(ISSNS_FLD, "barissn");
        PassClientSelector<Journal> journalBarSelector = new PassClientSelector<>(Journal.class);
        journalFooSelector.setFilter(journalFilterBar);
        PassClientResult<Journal> journalBarResult = passClient.selectObjects(journalBarSelector);
        List<Journal> journalsBar = journalBarResult.getObjects();

        assertEquals(1, journalsFoo.size());
        assertEquals(journalId, journalsFoo.get(0).getId());

        assertEquals(1, journalsBar.size());
        assertEquals(journalId, journalsBar.get(0).getId());

        assertEquals(journalId, underTest.findJournalByIssn("fooissn"));
        assertEquals(journalId, underTest.findJournalByIssn("barissn"));

        // and that a lookup by a non-existent issn returns null.
        assertNull(underTest.findJournalByIssn("nonexistentissn"));
    }
}
