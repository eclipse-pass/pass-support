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

package org.eclipse.pass.loader.nihms.integration;

import static org.eclipse.pass.client.nihms.NihmsPassClientService.ISSNS_FLD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

import org.eclipse.pass.support.client.PassClient;
//import org.eclipse.pass.client.PassClientDefault; //TODO: ???
import org.eclipse.pass.client.nihms.NihmsPassClientService;
import org.eclipse.pass.support.client.model.Journal;
import org.junit.Before;
import org.junit.Test;

public class NihmsPassClientServiceIT {

    private static final String ID_FLD = "@id";

    private NihmsPassClientService underTest;

    private PassClient passClient;

    @Before
    public void setUp() throws Exception {
        passClient = PassClient.newInstance();
        underTest = new NihmsPassClientService(passClient);
    }

    /**
     * Demonstrate that a Journal can be looked up by any of its ISSNs.
     */
    @Test
    public void lookupJournalByIssn() {
        Journal journal = new Journal();
        journal.setIssns(Arrays.asList("fooissn", "barissn"));
        journal.setJournalName("My Journal");

        journal = passClient.createAndReadResource(journal, Journal.class);
        URI journalId = journal.getId();

        // Wait for the Journal to be indexed

        Condition<Journal> journalIsIndexed = new Condition<>(() -> {
            URI u = passClient.findByAttribute(Journal.class, ID_FLD, journalId);
            if (u != null) {
                return passClient.readResource(u, Journal.class);
            } else {
                return null;
            }
        }, "Find Journal by ID");

        assertTrue(journalIsIndexed.awaitAndVerify(Objects::nonNull));

        // Knowing the Journal is in the index, try to find it by both of its ISSNs using the PASS client
        // and the NihmsPassClientService

        assertEquals(journalId, passClient.findByAttribute(Journal.class, ISSNS_FLD, "fooissn"));
        assertEquals(journalId, passClient.findByAttribute(Journal.class, ISSNS_FLD, "barissn"));

        assertEquals(journalId, underTest.findJournalByIssn("fooissn"));
        assertEquals(journalId, underTest.findJournalByIssn("barissn"));

        // and that a lookup by a non-existent issn returns null.

        assertNull(underTest.findJournalByIssn("nonexistentissn"));
    }
}
