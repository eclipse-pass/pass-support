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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.pass.support.client.model.Journal;
import org.junit.jupiter.api.Test;

/**
 * @author apb@jhu.edu
 */
public class MedlineReaderTest {

    @Test
    public void medlineIssnTest() throws Exception {
        try (final InputStream in = this.getClass().getResourceAsStream("/medline.txt")) {

            final MedlineReader toTest = new MedlineReader();

            final List<Journal> records = toTest.readJournals(in, UTF_8).collect(Collectors.toList());

            assertEquals(3, records.size());

            assertEquals(records.get(0).getIssns(), Collections.singletonList("Print:0000-0001"));
            assertEquals(records.get(1).getIssns(), Collections.singletonList("Online:0000-0002"));
            assertEquals(records.get(2).getIssns(), Arrays.asList("Print:0000-0003", "Online:0000-0004"));
        }
    }

    @Test
    public void medlineTitlesAndAbbreviationsTest() throws Exception {
        try (final InputStream in = this.getClass().getResourceAsStream("/medline.txt")) {

            final MedlineReader toTest = new MedlineReader();

            final List<Journal> records = toTest.readJournals(in, UTF_8).collect(Collectors.toList());

            assertEquals(3, records.size());

            assertEquals("First Journal", records.get(0).getJournalName());
            assertEquals("Second Journal", records.get(1).getJournalName());
            assertEquals("Third Journal", records.get(2).getJournalName());

            assertEquals("1jr", records.get(0).getNlmta());
            assertEquals("2jr", records.get(1).getNlmta());
            assertEquals("3jr", records.get(2).getNlmta());
        }
    }

}
