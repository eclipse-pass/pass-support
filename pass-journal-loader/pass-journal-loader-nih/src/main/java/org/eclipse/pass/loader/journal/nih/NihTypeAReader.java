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

import static java.util.stream.StreamSupport.stream;

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
 * Reads the NIH type A participation .csv file
 * <p>
 * See also: http://www.ncbi.nlm.nih.gov/pmc/front-page/NIH_PA_journal_list.csv
 * </p>
 *
 * @author apb@jhu.edu
 */
public class NihTypeAReader implements JournalReader {

    private static final Logger LOG = LoggerFactory.getLogger(NihTypeAReader.class);

    private Stream<Journal> readJournals(Reader csv) throws IOException {

        return stream(CSVFormat.RFC4180.parse(csv).spliterator(), false)
            .map(NihTypeAReader::toJournal)
            .filter(Objects::nonNull);
    }

    private static Journal toJournal(final CSVRecord record) {

        LOG.debug("Parsing CSV record..");

        //final Journal j = new PMCSource();
        final Journal j = new Journal();

        try {

            j.setJournalName(record.get(0));
            j.setNlmta(record.get(1));

            // columns 2, 3 are issns. column 2 is type "Print" and 3 is type "Online"
            // see https://publicaccess.nih.gov/testsite/field_definitions.htm
            addIssnIfPresent(j, record.get(2), "Print");
            addIssnIfPresent(j, record.get(3), "Online");

            // 4 is start date (we don't care)
            // 5 is end date (if ended, then it's not active)
            String endDate = null;
            if (record.size() > 5) { //csv file may lack trailing comma if this field is empty
                endDate = record.get(5);
            }
            final boolean isActive = (endDate == null || endDate.trim().equals(""));

            if (isActive) {
                j.setPmcParticipation(PmcParticipation.A);
            }

            return j;
        } catch (final Exception e) {
            LOG.warn("Could not create journal record for {}", j.getJournalName(), e);
            return null;
        }

    }

    private static void addIssnIfPresent(Journal journal, String issn, String type) {
        if (issn != null && !issn.trim().equals("")) {
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
