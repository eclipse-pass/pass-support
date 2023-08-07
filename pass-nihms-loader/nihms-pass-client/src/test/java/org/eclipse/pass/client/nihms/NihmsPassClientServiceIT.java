/*
 *
 *  * Copyright 2023 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.eclipse.pass.client.nihms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Arrays;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.Util;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Journal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NihmsPassClientServiceIT {

    private  NihmsPassClientService underTest;

    private PassClient passClient;

    @BeforeEach
    public void setUp() {
        System.setProperty("pass.core.url","http://localhost:8080");
        System.setProperty("pass.core.user","backend");
        System.setProperty("pass.core.password","backend");
        passClient = PassClient.newInstance();
        underTest = new NihmsPassClientService(passClient);
    }

    /**
     * Demonstrate that a Journal can be looked up by any of its ISSNs.
     */
    @Test
    public void lookupJournalByIssn() throws IOException {
        Journal journal = new Journal();
        journal.setIssns(Arrays.asList("fooissn", "barissn"));
        journal.setJournalName("My Journal");

        passClient.createObject(journal);
        String journalId = journal.getId();

        assertEquals(journalId, underTest.findJournalByIssn("fooissn"));
        assertEquals(journalId, underTest.findJournalByIssn("barissn"));

        // and that a lookup by a non-existent issn returns null.
        assertNull(underTest.findJournalByIssn("nonexistentissn"));
    }

    /**
     * Using different variants of the same award number, demonstrate that normalized award numbers and non-normalized
     * are found using different variants of an award number.
     */
    @Test
    public void shouldFindNihGrantAwardNumber() throws IOException, URISyntaxException {
        Grant grant1 = new Grant("1");
        grant1.setAwardNumber("R01AR074846");
        grant1.setStartDate(ZonedDateTime.now());

        Grant grant2 = new Grant("2");
        grant2.setAwardNumber("UM1 AI068613-01");
        grant2.setStartDate(ZonedDateTime.now());

        Grant grant3 = new Grant("3");
        grant3.setAwardNumber("K23 HL151758");
        grant3.setStartDate(ZonedDateTime.now());

        Grant grant4 = new Grant("4");
        grant4.setAwardNumber("F32 NS120940-A1");
        grant4.setStartDate(ZonedDateTime.now());

        Grant grant5 = new Grant("5");
        grant5.setAwardNumber("0P50 DA044123-B2");
        grant5.setStartDate(ZonedDateTime.now());

        Grant grant6 = new Grant("6");
        grant6.setAwardNumber("00-P50 DA044123-B2");
        grant6.setStartDate(ZonedDateTime.now());

        Grant grant7 = new Grant("7");
        grant7.setAwardNumber("5 R01 ES020425-02");
        grant7.setStartDate(ZonedDateTime.now());

        passClient.createObject(grant1);
        passClient.createObject(grant2);
        passClient.createObject(grant3);
        passClient.createObject(grant4);
        passClient.createObject(grant5);
        passClient.createObject(grant6);

        //test different variants of R01AR074846
        assertEquals(grant1.getAwardNumber(),
                underTest.findMostRecentGrantByAwardNumber("R01AR074846").getAwardNumber());
        assertEquals(grant1.getAwardNumber(),
                underTest.findMostRecentGrantByAwardNumber("R01 AR074846").getAwardNumber());
        /*assertEquals(grant1.getAwardNumber(),
                underTest.findMostRecentGrantByAwardNumber("000-R01 AR074846").getAwardNumber());*/
        System.out.println("1R01AR074846-A1".trim().replaceAll("^[0-9]", "")
                .replaceAll("-.*$", "")
                .replaceAll("\\s+",""));
        System.out.print(Util.grantAwardNumberNormalizeSearch("1R01AR074846-A1", "awardNumber"));
        assertEquals(grant1.getAwardNumber(),
                underTest.findMostRecentGrantByAwardNumber("1R01AR074846-A1").getAwardNumber());
        assertEquals(grant1.getAwardNumber(),
                underTest.findMostRecentGrantByAwardNumber("1R01 AR074846-A1").getAwardNumber());
        assertEquals(grant1.getAwardNumber(),
                underTest.findMostRecentGrantByAwardNumber("R01 AR074846-A1").getAwardNumber());
        assertEquals(grant1.getAwardNumber(),
                underTest.findMostRecentGrantByAwardNumber("R01AR074846-A1").getAwardNumber());
        assertEquals(grant1.getAwardNumber(),
                underTest.findMostRecentGrantByAwardNumber("R01AR074846").getAwardNumber());

    }

    /**
     * Generate grants based on a large list of known award numbers in PASS. The search should return the grant that
     * match the award numbers and not return any false positives.
     */
    /*@Test
    public void shouldFindNonNormalizedNihGrantAwardNumber() throws IOException, URISyntaxException {
        URI testAwardNumberUri = NihmsPassClientServiceTest.class.getResource("/valid_award_numbers.csv").toURI();
        List<String> awardNumbers = Files.readAllLines(Paths.get(testAwardNumberUri));
        int grantId = 0;
        for (String awardNumber : awardNumbers) {
            Grant grant = new Grant();
        }
    }*/

}
