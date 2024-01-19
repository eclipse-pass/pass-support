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
package org.eclipse.pass.support.grant.data.file;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public final class GrantDataCsvFileUtils {
    private GrantDataCsvFileUtils() {}

    public static List<GrantIngestRecord> loadGrantIngestCsv(File grantIngestCsvFile) throws IOException {
        try (Reader in = new FileReader(grantIngestCsvFile)) {
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(GrantIngestCsvHeaders.class)
                .setSkipHeaderRecord(true)
                .build();
            Iterable<CSVRecord> records = csvFormat.parse(in);
            List<GrantIngestRecord> grantIngestRecords = new ArrayList<>();
            for (CSVRecord record : records) {
                grantIngestRecords.add(GrantIngestRecord.parse(record));
            }
            return grantIngestRecords;
        }
    }
}
