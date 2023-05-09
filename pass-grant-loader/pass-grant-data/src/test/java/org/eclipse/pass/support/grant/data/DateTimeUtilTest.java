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
package org.eclipse.pass.support.grant.data;

import org.junit.Assert;
import org.junit.Test;

import java.time.ZonedDateTime;

/**
 * Test class for static utility methods
 *
 * @author jrm@jhu.edu
 */
public class DateTimeUtilTest {

    /**
     * Test that this boolean valued method returns true if and only a valid timestamp string is supplied
     */
    @Test
    public void testVerifyDateTimeFormat() {
        //check that zeroes in the time of day are ok
        String date = "2018-12-22 00:00:00.0";
        Assert.assertTrue(DateTimeUtil.verifyDateTimeFormat(date));

        //check that the "." is correctly escaped
        date = "2018-01-01 12:14:55h1";
        Assert.assertFalse(DateTimeUtil.verifyDateTimeFormat(date));

        //zero month not allowed
        date = "2018-00-01 12:14:55.1";
        Assert.assertFalse(DateTimeUtil.verifyDateTimeFormat(date));

        //zero day not allowed
        date = "2018-12-00 00:00:00.0";
        Assert.assertFalse(DateTimeUtil.verifyDateTimeFormat(date));

        //24 hour times work
        date = "2018-01-01 23:14:55.0";
        Assert.assertTrue(DateTimeUtil.verifyDateTimeFormat(date));

        //hours limited to 23
        date = "2018-01-01 24:14:55.1";
        Assert.assertFalse(DateTimeUtil.verifyDateTimeFormat(date));

        //59 minutes work ...
        date = "2018-01-01 23:59:55.0";
        Assert.assertTrue(DateTimeUtil.verifyDateTimeFormat(date));

        // ... but 60 do not
        date = "2018-01-01 23:60:55.1";
        Assert.assertFalse(DateTimeUtil.verifyDateTimeFormat(date));

        //59 seconds work ...
        date = "2018-01-01 23:59:59.0";
        Assert.assertTrue(DateTimeUtil.verifyDateTimeFormat(date));

        // ... but 60 do not
        date = "2018-01-01 23:59:60.1";
        Assert.assertFalse(DateTimeUtil.verifyDateTimeFormat(date));

        // three decimal places for milliseconds are ok ...
        date = "2018-01-01 07:59:16.199";
        Assert.assertTrue(DateTimeUtil.verifyDateTimeFormat(date));

        // .. but not 4
        date = "2018-01-01 07:59:16.1998";
        Assert.assertFalse(DateTimeUtil.verifyDateTimeFormat(date));

    }

    /**
     * Test that our method to create {@code DateTime} from timestamps objects produces
     * correct output
     */
    @Test
    public void testCreateZonedDateTime() {
        String timestamp = "2018-01-30 23:59:58.0";
        ZonedDateTime dateTime = DateTimeUtil.createZonedDateTime(timestamp);
        Assert.assertNotNull(dateTime);

        Assert.assertEquals(2018, dateTime.getYear());
        Assert.assertEquals(1, dateTime.getMonthValue());
        Assert.assertEquals(30, dateTime.getDayOfMonth());

        Assert.assertEquals(23, dateTime.getHour());
        Assert.assertEquals(59, dateTime.getMinute());
        Assert.assertEquals(58, dateTime.getSecond());

        Assert.assertEquals(0, dateTime.getNano());

        String date = "01/30/2018";
        dateTime = DateTimeUtil.createZonedDateTime(date);
        Assert.assertEquals(2018, dateTime.getYear());
        Assert.assertEquals(1, dateTime.getMonthValue());
        Assert.assertEquals(30, dateTime.getDayOfMonth());

        Assert.assertEquals(0, dateTime.getHour());
        Assert.assertEquals(0, dateTime.getMinute());
        Assert.assertEquals(0, dateTime.getSecond());

        Assert.assertEquals(0, dateTime.getNano());
    }

    /**
     * Test that verifyDate works
     */
    @Test
    public void testDateVerify() {
        String date = "01/01/2011";
        Assert.assertTrue(DateTimeUtil.verifyDate(date));

        date = "02/30/1999";
        Assert.assertFalse(DateTimeUtil.verifyDate(date));

        Assert.assertFalse(DateTimeUtil.verifyDate(null));
    }

}