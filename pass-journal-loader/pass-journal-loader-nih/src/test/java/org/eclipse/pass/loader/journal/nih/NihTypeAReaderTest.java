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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.junit.Test;

/**
 * @author apb@jhu.edu
 */
public class NihTypeAReaderTest {

    @Test
    public void pmcParticipationTest() throws Exception {
        try (final InputStream in = this.getClass().getResourceAsStream("/data.csv")) {

            final NihTypeAReader toTest = new NihTypeAReader();

            final List<Journal> journals = toTest.readJournals(in, UTF_8).collect(Collectors.toList());

            assertEquals(2, journals.size());
            assertEquals(PmcParticipation.A, journals.get(0).getPmcParticipation());
            assertNull(journals.get(1).getPmcParticipation());
        }
    }

    @Test
    public void issnTest() throws Exception {
        try (final InputStream in = this.getClass().getResourceAsStream("/data.csv")) {

            final NihTypeAReader toTest = new NihTypeAReader();

            final List<Journal> journals = toTest.readJournals(in, UTF_8).collect(Collectors.toList());

            assertEquals(Arrays.asList("Print:2190-572X", "Online:2190-5738"), journals.get(0).getIssns());
            assertEquals(Collections.singletonList("Online:1550-7416"), journals.get(1).getIssns());
        }
    }

    @Test
    public void titleTest() throws Exception {
        try (final InputStream in = this.getClass().getResourceAsStream("/data.csv")) {

            final NihTypeAReader toTest = new NihTypeAReader();

            final List<Journal> journals = toTest.readJournals(in, UTF_8).collect(Collectors.toList());

            assertEquals("Journal 1", journals.get(0).getJournalName());
            assertEquals("Journal 2", journals.get(1).getJournalName());
        }
    }

    @Test
    public void nlmTaTest() throws Exception {
        try (final InputStream in = this.getClass().getResourceAsStream("/data.csv")) {

            final NihTypeAReader toTest = new NihTypeAReader();

            final List<Journal> journals = toTest.readJournals(in, UTF_8).collect(Collectors.toList());

            assertEquals("j1", journals.get(0).getNlmta());
            assertEquals("j2", journals.get(1).getNlmta());
        }
    }

    @Test
    public void providesPmcParticipationTest() {
        final NihTypeAReader toTest = new NihTypeAReader();
        assertTrue(toTest.hasPmcParticipation());
    }
}
