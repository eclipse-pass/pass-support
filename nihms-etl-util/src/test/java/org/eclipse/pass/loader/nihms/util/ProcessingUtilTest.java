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
package org.eclipse.pass.loader.nihms.util;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;

/**
 * Tests for ValidateUtil
 *
 * @author Karen Hanson
 */
public class ProcessingUtilTest {

    /**
     * Check formatDate util is working to convert date as formatted in NIHMS spreadsheet to DateTime
     */
    @Test
    public void testFormatDate() {
        String dateStr = "12/11/2018";
        DateTime newDate = ProcessingUtil.formatDate(dateStr, "MM/dd/yyyy");
        assertEquals(12, newDate.getMonthOfYear());
        assertEquals(11, newDate.getDayOfMonth());
        assertEquals(2018, newDate.getYear());
        assertEquals(0, newDate.getMinuteOfHour());
        assertEquals(0, newDate.getSecondOfMinute());
        assertEquals(0, newDate.getMillisOfSecond());
    }

}
