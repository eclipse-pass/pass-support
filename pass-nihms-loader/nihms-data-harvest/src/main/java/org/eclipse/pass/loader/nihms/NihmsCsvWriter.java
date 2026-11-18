/*
 * Copyright 2026 Johns Hopkins University
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
package org.eclipse.pass.loader.nihms;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Writes harvested records as the CSV files read by the NIHMS transform and load step. The layout is
 * that of the files the NIH Public Access Compliance Monitor used to supply, with a DOI column added.
 * The status of the records in a file is given by the prefix of its name.
 */
@Component
public class NihmsCsvWriter {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsCsvWriter.class);

    // The transform reads columns by position, so the unused columns have to be kept
    static final String[] HEADERS = {
        "PMID", "PMCID", "NIHMSID", "Grant number", "PI Name", "Publication Date", "NIHMS file deposited",
        "NIHMS initial approval", "NIHMS tagging complete", "NIHMS final approval", "Article Title",
        "First Author Name", "Journal Title", "Journal Publisher", "Method A Journal", "NIHMS Person", "DOI"
    };

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path dataDirectory;

    /**
     * @param dataDirectory the directory to write the files to. It is created if it does not exist.
     */
    public NihmsCsvWriter(@Value("${nihmsetl.data.dir}") String dataDirectory) {
        this.dataDirectory = Path.of(dataDirectory);
        if (!Files.isDirectory(this.dataDirectory)) {
            LOG.warn("Data directory does not exist at path provided. A new directory will be created at path: {}",
                     this.dataDirectory);
            try {
                Files.createDirectories(this.dataDirectory);
            } catch (IOException e) {
                throw new RuntimeException("A new data directory could not be created at path: " +
                                           this.dataDirectory + ". Please provide a valid path", e);
            }
        }
    }

    /**
     * Write the records of one status to a new file.
     *
     * @param status  the status of all of the records
     * @param records the records
     * @return the file written
     * @throws IOException if the file cannot be written
     */
    public Path write(NihmsStatus status, Collection<HarvestRecord> records) throws IOException {
        Path file = dataDirectory.resolve(status + "_nihmspubs_" + LocalDateTime.now().format(TIMESTAMP) + ".csv");
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(HEADERS).get();
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(out, format)) {
            for (HarvestRecord rec : records) {
                printer.printRecord(rec.pmid(), rec.pmcid(), rec.nihmsId(), rec.grantNumber(), null, null,
                                    null, null, null, null, rec.title(), null, null, null, null, null, rec.doi());
            }
        }
        LOG.info("Saved {} {} publications as file {}", records.size(), status, file);
        return file;
    }
}
