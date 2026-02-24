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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the NIH type A participation .csv file.
 *
 * <p>
 * See the spreadsheet definition at https://www.nlm.nih.gov/pubs/techbull/mj24/mj24_pmc_preview_journal_list.html
 * and the spreadsheet at https://cdn.ncbi.nlm.nih.gov/pmc/home/jlist.csv.
 * </p>
 *
 * @author apb@jhu.edu
 */
public class NihTypeAReader implements JournalReader {
    private static final String[] header = new String[] {
        "Journal Title",
        "NLM Title Abbreviation (TA)",
        "Publisher",
        "ISSN (print)",
        "ISSN (online)",
        "NLM Unique ID",
        "Most Recent",
        "Earliest",
        "Release Delay (Embargo)",
        "Agreement Status",
        "Agreement to Deposit",
        "Journal Note",
        "PMC URL"
    };

    private static final Logger LOG = LoggerFactory.getLogger(NihTypeAReader.class);

    private Stream<Journal> readJournals(Reader csv) throws IOException {
        CSVFormat csvFormat = CSVFormat.RFC4180.builder().setHeader(header).setSkipHeaderRecord(true).build();

        return csvFormat.parse(csv).stream().map(NihTypeAReader::toJournal).filter(Objects::nonNull);
    }

    private static Journal toJournal(final CSVRecord record) {
        LOG.debug("Parsing CSV record..");

        final Journal j = new Journal();

        try {
            j.setJournalName(record.get(0));
            j.setNlmta(record.get(1));

            addIssnIfPresent(j, record.get(3), "Print");
            addIssnIfPresent(j, record.get(4), "Online");

            String status = record.get(9).strip();

            if (status.equals("Active")) {
                j.setPmcParticipation(PmcParticipation.A);
            }

            return j;
        } catch (final Exception e) {
            LOG.warn("Could not create journal record for {}", j.getJournalName(), e);
            return null;
        }

    }

    private static void addIssnIfPresent(Journal journal, String issn, String type) {
        if (issn != null && !issn.strip().equals("")) {
            journal.getIssns().add(String.join(":", type, issn));
        }
    }

    @Override
    public Stream<Journal> readJournals(InputStream source, Charset charset) {
        try {
            return readJournals(new InputStreamReader(source, charset));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasPmcParticipation() {
        return true;
    }
}