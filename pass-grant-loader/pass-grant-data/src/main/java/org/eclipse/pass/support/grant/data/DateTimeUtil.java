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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * This utility class provides static methods for intermunging ZonedDateTime objects and timestamp strings
 */
public class DateTimeUtil {

    static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss[[.SSS][.SS][.S]]")
                .withResolverStyle(ResolverStyle.STRICT);
    static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MM/dd/uuuu")
                .withResolverStyle(ResolverStyle.STRICT);

    private DateTimeUtil () {
        //never called
    }

    /**
     * A method to convert a timestamp string from our database to a ZonedDateTime object
     *
     * @param dateString the timestamp string
     * @return the corresponding ZonedDateTime object or null if not able to parse string
     */
    public static ZonedDateTime createZonedDateTime(String dateString) {
        if (verifyDateTimeFormat(dateString)) {
            LocalDateTime localDateTime = LocalDateTime.parse(dateString, DATE_TIME_FORMATTER);
            return localDateTime.atZone(ZoneOffset.UTC);
        }
        if (verifyDate(dateString)) { //we may have just a date - date format is mm/day/year
            LocalDate localDate = LocalDate.parse(dateString, DATE_FORMATTER);
            return localDate.atStartOfDay(ZoneOffset.UTC);
        }
        return null;
    }

    /**
     * Dates must be specified in the format "yyyy-mm-dd hh:mm:ss[.mmm]" . We only check for this format, and not for
     * validity
     * (for example, "2018-02-31 ... " passes)
     *
     * @param dateStr the date string to be checked
     * @return a boolean indicating whether the date matches the required format
     */
    public static boolean verifyDateTimeFormat(String dateStr) {
        return checkDateTimeFormat(dateStr, DATE_TIME_FORMATTER);
    }

    /**
     * Date must be in the form "mm/dd/yyyy"
     * @param date the date to verify
     * @return true if date format is valid, false if not
     */
    public static boolean verifyDate(String date) {
        return checkDateTimeFormat(date, DATE_FORMATTER);
    }

    private static boolean checkDateTimeFormat(String dateTimeStr, DateTimeFormatter formatter) {
        if (dateTimeStr == null) {
            return false;
        }
        try {
            formatter.parse(dateTimeStr);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    /**
     * Compare two timestamps and return the later of them
     *
     * @param currentUpdateString the current latest timestamp string
     * @param latestUpdateString  the new timestamp to be compared against the current latest timestamp
     * @return the later of the two parameters
     */
    static String returnLaterUpdate(String currentUpdateString, String latestUpdateString) {
        ZonedDateTime currentUpdateTime = createZonedDateTime(currentUpdateString);
        ZonedDateTime latestUpdateTime = createZonedDateTime(latestUpdateString);
        return currentUpdateTime != null && currentUpdateTime.isAfter(latestUpdateTime)
                ? currentUpdateString
                : latestUpdateString;
    }
}
