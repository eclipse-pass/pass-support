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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class NihmsHarvesterTest {

    private static final NihAward AWARD_1 = new NihAward("R01", "EB", "019135");
    private static final NihAward AWARD_2 = new NihAward("K23", "HL", "153888");

    @TempDir
    Path dataDir;

    private GrantAwardSource grantAwardSource;
    private CrossrefClient crossrefClient;
    private PmcIdConverterClient pmcIdConverterClient;
    private NihmsHarvester harvester;

    @BeforeEach
    public void setUp() throws IOException {
        grantAwardSource = mock(GrantAwardSource.class);
        crossrefClient = mock(CrossrefClient.class);
        pmcIdConverterClient = mock(PmcIdConverterClient.class);
        harvester = new NihmsHarvester(grantAwardSource, crossrefClient, pmcIdConverterClient,
                                       new NihmsCsvWriter(dataDir.toString()));

        when(grantAwardSource.findNihAwards()).thenReturn(new LinkedHashSet<>(List.of(AWARD_1, AWARD_2)));
    }

    private List<CSVRecord> read(String prefix) throws IOException {
        List<Path> files;
        try (Stream<Path> list = Files.list(dataDir)) {
            files = list.filter(p -> p.getFileName().toString().startsWith(prefix + "_nihmspubs_")).toList();
        }
        assertEquals(1, files.size());
        assertTrue(files.get(0).getFileName().toString().endsWith(".csv"));
        try (Reader in = Files.newBufferedReader(files.get(0))) {
            return CSVFormat.DEFAULT.parse(in).getRecords();
        }
    }

    private long fileCount() throws IOException {
        try (Stream<Path> list = Files.list(dataDir)) {
            return list.count();
        }
    }

    @Test
    public void testHarvest() throws IOException {
        LocalDate start = LocalDate.now().minusMonths(6);
        // The first DOI acknowledges both awards and is in PMC, the second is not in PMC
        when(crossrefClient.findWorksByAward(eq(AWARD_1.searchVariants()), eq(start))).thenReturn(List.of(
            new CrossrefWork("10.1000/InPmc", "In PMC, with a comma"),
            new CrossrefWork("10.1000/missing", "Not in PMC")));
        when(crossrefClient.findWorksByAward(eq(AWARD_2.searchVariants()), eq(start))).thenReturn(List.of(
            new CrossrefWork("10.1000/inpmc", "In PMC, with a comma")));
        when(pmcIdConverterClient.lookupDois(eq(Set.of("10.1000/inpmc", "10.1000/missing")))).thenReturn(
            Map.of("10.1000/inpmc", new PmcIds("12345678", "PMC111", "NIHMS222")));

        harvester.harvest(EnumSet.allOf(NihmsStatus.class), 6);

        // Nothing is written for in-process
        assertEquals(2, fileCount());

        List<CSVRecord> compliant = read("compliant");
        assertEquals(List.of(NihmsCsvWriter.HEADERS), List.of(compliant.get(0).values()));
        assertEquals(3, compliant.size());
        assertEquals(List.of("12345678", "PMC111", "NIHMS222", "R01EB019135", "", "", "", "", "", "",
                             "In PMC, with a comma", "", "", "", "", "", "10.1000/inpmc"),
                     compliant.get(1).toList());
        assertEquals("K23HL153888", compliant.get(2).get(3));
        assertEquals("10.1000/inpmc", compliant.get(2).get(16));

        List<CSVRecord> nonCompliant = read("noncompliant");
        assertEquals(2, nonCompliant.size());
        assertEquals(List.of("", "", "", "R01EB019135", "", "", "", "", "", "",
                             "Not in PMC", "", "", "", "", "", "10.1000/missing"),
                     nonCompliant.get(1).toList());
    }

    @Test
    public void testHarvestOnlyRequestedStatus() throws IOException {
        when(crossrefClient.findWorksByAward(any(), any())).thenReturn(List.of());
        when(pmcIdConverterClient.lookupDois(any())).thenReturn(Map.of());

        harvester.harvest(EnumSet.of(NihmsStatus.NON_COMPLIANT), 12);

        assertEquals(1, fileCount());
        // A file with only the header row is still written
        assertEquals(1, read("noncompliant").size());
    }

    @Test
    public void testHarvestContinuesWhenOneAwardFails() throws IOException {
        when(crossrefClient.findWorksByAward(eq(AWARD_1.searchVariants()), any()))
            .thenThrow(new IOException("failed"));
        when(crossrefClient.findWorksByAward(eq(AWARD_2.searchVariants()), any())).thenReturn(List.of(
            new CrossrefWork("10.1000/missing", "Not in PMC")));
        when(pmcIdConverterClient.lookupDois(any())).thenReturn(Map.of());

        harvester.harvest(EnumSet.of(NihmsStatus.NON_COMPLIANT), 12);

        assertEquals(2, read("noncompliant").size());
    }

    @Test
    public void testHarvestFailsWhenEveryAwardFails() throws IOException {
        when(crossrefClient.findWorksByAward(any(), any())).thenThrow(new IOException("failed"));

        assertThrows(RuntimeException.class, () -> harvester.harvest(EnumSet.allOf(NihmsStatus.class), 12));
        assertEquals(0, fileCount());
    }

    @Test
    public void testHarvestFailsWhenPmcLookupFails() throws IOException {
        when(crossrefClient.findWorksByAward(any(), any())).thenReturn(List.of(new CrossrefWork("10.1/a", "A")));
        when(pmcIdConverterClient.lookupDois(any())).thenThrow(new IOException("failed"));

        assertThrows(RuntimeException.class, () -> harvester.harvest(EnumSet.allOf(NihmsStatus.class), 12));
        assertEquals(0, fileCount());
    }

    @Test
    public void testHarvestRequiresStatus() {
        assertThrows(RuntimeException.class, () -> harvester.harvest(Set.of(), 12));
    }
}
