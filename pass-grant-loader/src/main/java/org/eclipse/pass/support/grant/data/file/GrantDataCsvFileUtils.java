/*
 * Copyright 2024 Johns Hopkins University
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
package org.eclipse.pass.support.grant.data.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public final class GrantDataCsvFileUtils {
    private GrantDataCsvFileUtils() {}

    /**
     * Reads CSV file containing grant data.
     * @param grantIngestCsv the CSV Resource
     * @return a list of GrantIngestRecord, each record containing one row of CSV data
     * @throws IOException
     */
    public static List<GrantIngestRecord> readGrantIngestCsv(Resource grantIngestCsv) throws IOException {
        try (
            InputStream inputStream = grantIngestCsv.getInputStream();
            Reader reader = new InputStreamReader(inputStream)
        ) {
            CSVFormat csvFormat = CSVFormat.RFC4180.builder()
                .setHeader(GrantIngestCsvHeaders.class)
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setNullString("")
                .build();
            Iterable<CSVRecord> records = csvFormat.parse(reader);
            List<GrantIngestRecord> grantIngestRecords = new ArrayList<>();
            for (CSVRecord record : records) {
                grantIngestRecords.add(GrantIngestRecord.parse(record));
            }
            return grantIngestRecords;
        }
    }

    /**
     * Writes CSV file containing grant data.
     * @param grantIngestRecords a list of GrantIngestRecord, each record containing one row of CSV data
     * @param grantIngestCsv the CSV Resource
     * @throws IOException
     */
    public static void writeGrantIngestCsv(List<GrantIngestRecord> grantIngestRecords, Resource grantIngestCsv)
        throws IOException {
        CSVFormat format = CSVFormat.RFC4180.builder().setHeader(GrantIngestCsvHeaders.class).build();
        try (
            OutputStream outputStream = ((WritableResource) grantIngestCsv).getOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream , StandardCharsets.UTF_8 );
            CSVPrinter printer = new CSVPrinter( writer , format );
        ) {
            for (GrantIngestRecord record : grantIngestRecords) {
                printer.printRecord(
                    record.getGrantNumber(),
                    record.getGrantTitle(),
                    record.getAwardNumber(),
                    record.getAwardStatus(),
                    record.getAwardDate(),
                    record.getAwardStart(),
                    record.getAwardEnd(),
                    record.getPrimaryFunderName(),
                    record.getPrimaryFunderCode(),
                    record.getDirectFunderName(),
                    record.getDirectFunderCode(),
                    record.getPiFirstName(),
                    record.getPiMiddleName(),
                    record.getPiLastName(),
                    record.getPiEmail(),
                    record.getPiInstitutionalId(),
                    record.getPiEmployeeId(),
                    record.getPiRole(),
                    record.getActivePiEmployeeId(),
                    record.getActivePiInstitutionalId(),
                    record.getUpdateTimeStamp()
                );
            }
        }
    }
}
