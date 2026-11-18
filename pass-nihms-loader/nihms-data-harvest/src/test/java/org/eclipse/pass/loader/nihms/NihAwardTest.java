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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class NihAwardTest {

    @Test
    public void testParseNihAwardNumbers() {
        for (String awardNumber : List.of("R01 EB019135", "R01EB019135", "r01 eb019135", " 5R01EB019135-03 ",
                                          "000-R01 EB019135", "5R01EB019135-03S1", "R01  EB019135")) {
            assertEquals(new NihAward("R01", "EB", "019135"), NihAward.parse(awardNumber), awardNumber);
        }
    }

    @Test
    public void testParseOtherActivityCodeLengths() {
        assertEquals(new NihAward("UL1", "TR", "003098"), NihAward.parse("UL1 TR003098"));
        assertEquals(new NihAward("K23", "HL", "153888"), NihAward.parse("1K23HL153888-01A1"));
    }

    @Test
    public void testParseNonNihAwardNumbers() {
        for (String awardNumber : List.of("", "NSF-1234567", "W81XWH-19-1-0123", "EB019135", "R01 EB01913")) {
            assertNull(NihAward.parse(awardNumber), awardNumber);
        }
    }

    @Test
    public void testParseNull() {
        assertNull(NihAward.parse(null));
    }

    @Test
    public void testCoreAndVariants() {
        NihAward award = new NihAward("R01", "EB", "019135");
        assertEquals("R01EB019135", award.core());
        assertEquals(Set.of("R01 EB019135", "R01EB019135", "R01-EB019135", "EB019135"), award.searchVariants());
    }
}
