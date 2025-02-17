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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

import org.apache.commons.lang3.StringUtils;

/**
 * This utility class provides static methods for intermunging ZonedDateTime objects and timestamp strings
 */
public class DateTimeUtil {
    private static final String DATE_TIME_PATTERN = "uuuu-MM-dd[ [HH][:mm][:ss][[.SSS][.SS][.S]]]";
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
        new DateTimeFormatterBuilder().appendPattern(DATE_TIME_PATTERN)
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .toFormatter();

    private DateTimeUtil () {
        //never called
    }

    /**
     * A method to convert a timestamp string from our database to a ZonedDateTime object
     *
     * @param dateString the timestamp string
     * @return the corresponding ZonedDateTime object or null if not able to parse string
     */
    public static ZonedDateTime createZonedDateTime(String dateString) throws GrantDataException {
        if (verifyDateTimeFormat(dateString)) {
            LocalDateTime localDateTime = LocalDateTime.parse(dateString, DATE_TIME_FORMATTER);
            return localDateTime.atZone(ZoneOffset.UTC);
        }
        if (StringUtils.isNotBlank(dateString)) {
            throw new GrantDataException("Invalid Format for " + dateString +
                ".  Valid Format is " + DATE_TIME_PATTERN);
        }
        return null;
    }

    /**
     * Dates must be specified in the format "yyyy-mm-dd hh:mm:ss[.mmm]" . We only check for this format, and not for
     * validity
     * (for example, "2018-02-31 ... " passes)
     *
     * @param dateTimeStr the datetime string to be checked
     * @return a boolean indicating whether the date matches the required format
     */
    public static boolean verifyDateTimeFormat(String dateTimeStr) {
        if (dateTimeStr == null) {
            return false;
        }
        try {
            DATE_TIME_FORMATTER.parse(dateTimeStr);
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
    public static String returnLaterUpdate(String currentUpdateString, String latestUpdateString)
        throws GrantDataException {
        ZonedDateTime currentUpdateTime = createZonedDateTime(currentUpdateString);
        ZonedDateTime latestUpdateTime = createZonedDateTime(latestUpdateString);
        return currentUpdateTime != null && currentUpdateTime.isAfter(latestUpdateTime)
                ? currentUpdateString
                : latestUpdateString;
    }
}
