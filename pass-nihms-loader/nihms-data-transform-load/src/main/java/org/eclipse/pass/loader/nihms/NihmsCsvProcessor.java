/*
 * Copyright 2023 Johns Hopkins University
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

import static org.eclipse.pass.loader.nihms.util.ProcessingUtil.nullOrEmpty;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NIHMS CSV Reader expects a Submissions CSV from the NIHMS site and will read it into a List,
 * mapping the relevant fields to the NihmsPublication model.
 *
 * @author Karen Hanson
 * @version $Id$
 */
public class NihmsCsvProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsCsvProcessor.class);

    // columns and column numbers for csv import
    private static final String PMID_HEADING = "PMID";
    private static final Integer PMID_COLNUM = 0;
    private static final String PMCID_HEADING = "PMCID";
    private static final Integer PMCID_COLNUM = 1;
    private static final String NIHMSID_HEADING = "NIHMSID";
    private static final Integer NIHMSID_COLNUM = 2;
    private static final String GRANTID_HEADING = "Grant number";
    private static final Integer GRANTID_COLNUM = 3;
    private static final String FILEDEPOSIT_HEADING = "NIHMS file deposited";
    private static final Integer FILEDEPOSIT_COLNUM = 6;
    private static final String INITIALAPPROVAL_HEADING = "NIHMS initial approval";
    private static final Integer INITIALAPPROVAL_COLNUM = 7;
    private static final String TAGGINGCOMPLETE_HEADING = "NIHMS tagging complete";
    private static final Integer TAGGINGCOMPLETE_COLNUM = 8;
    private static final String FINALAPPROVAL_HEADING = "NIHMS final approval";
    private static final Integer FINALAPPROVAL_COLNUM = 9;
    private static final String ARTICLETITLE_HEADING = "Article Title";
    private static final Integer ARTICLETITLE_COLNUM = 10;

    /**
     * Lists expected headers and their column number to support header validation
     */
    private static final Map<Integer, String> EXPECTED_HEADERS = new HashMap<Integer, String>();

    static {
        EXPECTED_HEADERS.put(PMID_COLNUM, PMID_HEADING);
        EXPECTED_HEADERS.put(PMCID_COLNUM, PMCID_HEADING);
        EXPECTED_HEADERS.put(NIHMSID_COLNUM, NIHMSID_HEADING);
        EXPECTED_HEADERS.put(GRANTID_COLNUM, GRANTID_HEADING);
        EXPECTED_HEADERS.put(FILEDEPOSIT_COLNUM, FILEDEPOSIT_HEADING);
        EXPECTED_HEADERS.put(INITIALAPPROVAL_COLNUM, INITIALAPPROVAL_HEADING);
        EXPECTED_HEADERS.put(TAGGINGCOMPLETE_COLNUM, TAGGINGCOMPLETE_HEADING);
        EXPECTED_HEADERS.put(FINALAPPROVAL_COLNUM, FINALAPPROVAL_HEADING);
        EXPECTED_HEADERS.put(ARTICLETITLE_COLNUM, ARTICLETITLE_HEADING);
    }

    /**
     * Counter for number of records processed so far
     */
    private int recCount = 0;

    /**
     * Counter for number of records that failed so far
     */
    private int failCount = 0;

    /**
     * Path to NIHMS CSV to read in
     */
    private Path filePath = null;

    /**
     * Status of NIHMS deposit to pass to NihmsPublication
     */
    private NihmsStatus status = null;

    public NihmsCsvProcessor(Path filePath, NihmsStatus status) {
        this.filePath = filePath;
        this.status = status;
    }

    /**
     * Cycles through the CSV that is loaded, converting to a NihmsPublication, and then
     * using the consumer provided to process the record
     *
     * @param pubConsumer the consumer
     */
    public void processCsv(Consumer<NihmsPublication> pubConsumer) {

        try (BufferedReader br = Files.newBufferedReader(filePath)) {

            LOG.info("Starting to process file: {}", filePath);

            Iterator<CSVRecord> csvRecords = CSVFormat.DEFAULT.parse(br).iterator();

            CSVRecord record = csvRecords.next();
            if (!hasValidHeaders(record)) {
                LOG.error("File at path \"{}\" has unrecognized headers", filePath.toString());
                throw new RuntimeException("The headers were not as expected, aborting import");
            }

            csvRecords.forEachRemaining(row -> consumeRow(row, pubConsumer));

        } catch (Exception e) {
            String msg = String.format("A problem occurred while processing the csv with path %s", filePath.toString());
            throw new RuntimeException(msg, e);
        }

        LOG.info("{} records were processed with {} failures.", recCount, failCount);

    }

    /**
     * Converts Row to a NihmsPublication object and passes it the consumer provided
     *
     * @param row         the row
     * @param pubConsumer the row consumer
     */
    private void consumeRow(CSVRecord row, Consumer<NihmsPublication> pubConsumer) {
        if (row == null) {
            return;
        }
        if (nullOrEmpty(row.get(PMID_COLNUM))) {
            return;
        } //not a valid row
        recCount = recCount + 1;
        NihmsPublication pub = null;
        try {
            pub = new NihmsPublication(status, row.get(PMID_COLNUM), row.get(GRANTID_COLNUM), row.get(NIHMSID_COLNUM),
                                       row.get(PMCID_COLNUM), row.get(FILEDEPOSIT_COLNUM),
                                       row.get(INITIALAPPROVAL_COLNUM),
                                       row.get(TAGGINGCOMPLETE_COLNUM), row.get(FINALAPPROVAL_COLNUM),
                                       row.get(ARTICLETITLE_COLNUM));

            LOG.info("NIHMS record pmid={} is being processed", pub.getPmid());
            pubConsumer.accept(pub);
            LOG.info("NIHMS record pmid={} was processed successfully", pub.getPmid());
        } catch (Exception ex) {
            failCount = failCount + 1;
            LOG.error(
                "A problem occurred while processing csv row {} with pmid {}. The record was not imported " +
                "successfully.",
                recCount + 1, pub.getPmid(), ex);
        }
    }

    /**
     * Validates that the headers in the spreadsheet match what is expected before retrieving data from them
     * This will go through all headers even if the first one is not valid so that all issues with headers are
     * logged as errors before exiting
     *
     * @param headers CSVRrecord headers
     * @return true if the headers match the {@link #EXPECTED_HEADERS}
     */
    private boolean hasValidHeaders(CSVRecord headers) {
        boolean valid = true;
        LOG.debug("Checking CSV headers match expected");
        for (Entry<Integer, String> header : EXPECTED_HEADERS.entrySet()) {
            Integer colNum = header.getKey();
            String expectedName = header.getValue();
            if (!headers.get(colNum).toLowerCase().equals(expectedName.toLowerCase())) {
                valid = false;
                LOG.error("Expected header \"{}\" but was \"{}\"", expectedName, headers.get(colNum));
            }
        }
        return valid;
    }

    /**
     * Cycles through Submission status types, and matches it to the filepath to determine
     * the status of the rows in the CSV file. If no match is found, an exception is thrown.
     *
     * @param path the file path
     * @return the status
     * @throws RuntimeException if the status could not be determined
     */
    public static NihmsStatus nihmsStatus(Path path) {
        String filename = path.getFileName().toString();

        for (NihmsStatus status : NihmsStatus.values()) {
            if (filename.startsWith(status.toString())) {
                return status;
            }
        }
        throw new RuntimeException(
            "Could not determine the Status of the publications being imported. Please ensure filenames are prefixed " +
            "according to the Submission status.");
    }

}
