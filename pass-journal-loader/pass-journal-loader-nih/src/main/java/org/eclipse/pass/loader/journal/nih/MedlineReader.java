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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.pass.support.client.model.Journal;

/**
 * Parses Medline journals See also: ftp://ftp.ncbi.nih.gov/pubmed/J_Medline.txt
 *
 * @author apb@jhu.edu
 */
public class MedlineReader implements JournalReader {

    private static final String BOUNDARY = "----";

    private static final String TITLE_FIELD = "JournalTitle";

    private static final String ISSN_FIELD = "ISSN";

    private static final String ABBR_FIELD = "MedAbbr";

    @Override
    public Stream<Journal> readJournals(InputStream source, Charset charset) {

        final BufferedReader reader = new BufferedReader(new InputStreamReader(source, charset));

        final Iterable<Journal> i = () -> new Iterator<Journal>() {

            Journal next = read(reader);

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Journal next() {
                try {
                    return next;
                } finally {
                    next = read(reader);
                }
            }
        };

        return StreamSupport.stream(i.spliterator(), false);

    }

    private Journal read(BufferedReader reader) {
        try {
            if (reader.readLine() != null) { // Boundary
                final Journal j = new Journal();

                for (String line = reader.readLine(); !(line == null || line.contains(BOUNDARY)); line = reader
                    .readLine()) {
                    if (line.startsWith(TITLE_FIELD)) {
                        j.setJournalName(extract(line));
                    } else if (line.startsWith(ISSN_FIELD)) {
                        final String issn = extract(line);
                        final String type = extractType(line);
                        if (issn.length() > 0) {
                            j.getIssns().add(String.join(":", type, issn));
                        }
                    } else if (line.startsWith(ABBR_FIELD)) {
                        j.setNlmta(extract(line));
                    }
                }

                return j;
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error reading journal stream: ", e);
        }

        return null;
    }

    private String extract(String line) {
        return line.substring(line.indexOf(':') + 1).trim();
    }

    private String extractType(String line) {
        return line.substring(line.indexOf("(") + 1, line.indexOf(")")).trim();
    }

    @Override
    public boolean hasPmcParticipation() {
        return false;
    }

}
