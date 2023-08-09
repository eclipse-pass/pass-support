package org.eclipse.pass.support.client;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Utilities for working with the model.
 */
public class Util {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

    private Util() {}

    /**
     * The ZonedDateTime fields in the model must use this formatter.
     *
     * @return formatter
     */
    public static DateTimeFormatter dateTimeFormatter() {
        return FORMATTER;
    }

    /**
     * Normalizes an award number by removing leading/trailing whitespace and converting to uppercase.
     * Will attempt to detect the NIH format: https://www.era.nih.gov/files/Deciphering_NIH_Application.pdf
     * If the NIH format is detected it will attempt to normalize otherwise it will ignore as it may be a non-NIH
     * award number.
     * @param awardNumber award number to normalize
     * @return normalized award number
     * @throws IOException if the award number cannot be normalized
     */
    public static String grantAwardNumberNormalizer(String awardNumber) throws IOException {
        if (StringUtils.isEmpty(awardNumber)) {
            return null;
        }
        awardNumber = awardNumber.trim();

        //if matching the NIH format, then normalize it to the expected format by removing leading zeros
        if (awardNumber.toUpperCase().matches("[0-9]*-*\\s*[A-Z]{1,2}[0-9]{1,2}\s*[A-Z]{2}[A-Z0-9]{6}-*[A-Z0-9]*")) {
            //remove leading zeros, whitespace and make uppercase
            awardNumber = awardNumber
                    .replaceFirst("^0+-*(?!$)", "")
                    .replaceAll("\\s", "")
                    .toUpperCase();
        }
        return awardNumber;
    }

    /**
     * Generate RSQL that will find any awardNumber with various patterns that a NIH grant award number
     * can come in. It does this by finding the base components of a NIH grant award number and uses
     * wildcards to find any award number that matches the base components. If the award number does not meet the
     * criteria of a NIH award number then it will perform a plain text search on the award number and trimmed
     * leading/trailing whitespace version.
     *
     * Following patterns that will be matched on a NIH grant award number:
     *  1) A suffix denoted by a hyphen followed by characters, e.g. A01 BC123456-01
     *  2) Leading and trailing spaces
     *  3) Zero or many spaces in between the first set of characters and second set: A01  BC123456
     *  4) Leading zeros in the first set of the string: 000A01 BC123456
     *  5) Leading zeros following by a hyphen: 000-A01 BC123456
     *  5) Application type that has a leading number 1-9: 1A01 BC123456
     * @param awardNumber
     * @return
     */
    public static String grantAwardNumberNormalizeSearch(String awardNumber, String rsqlFieldName) throws IOException {
        if (StringUtils.isEmpty(awardNumber)) {
            throw new IOException("Award number cannot be empty");
        }
        String awardNumberNihMinSet = "";
        if (awardNumber.toUpperCase().matches("[0-9]*-*\\s*[A-Z]{1,2}[0-9]{1,2}\s*[A-Z]{2}[0-9]{6}-*[A-Z0-9]*")) {
            //find activity code, institute code and serial number, the minimum set for an NIH grant award number
            Pattern pattern = Pattern.compile("[A-Z]{1,2}[0-9]{1,2}\s*[A-Z]{2}[0-9]{6}");
            Matcher matcher = pattern.matcher(awardNumber);
            if (matcher.find()) {
                awardNumberNihMinSet = matcher.group();
                awardNumberNihMinSet = awardNumberNihMinSet.replaceAll("\\s", "");
                //break it up in the individual parts
                String activityCode = awardNumberNihMinSet.substring(0, 3);
                String instituteCode = awardNumberNihMinSet.substring(3, 5);
                String serialNumber = awardNumberNihMinSet.substring(5, 11);
                awardNumberNihMinSet = "*" + activityCode + instituteCode + serialNumber + "*";
            }
        }

        String normalizedNihGrant = Util.grantAwardNumberNormalizer(awardNumber);

        if (awardNumber.equals(normalizedNihGrant) && StringUtils.isEmpty(awardNumberNihMinSet)) {
            return RSQL.equals(rsqlFieldName, awardNumber);
        } else if (!awardNumber.equals(normalizedNihGrant) && StringUtils.isEmpty(awardNumberNihMinSet)) {
            return RSQL.or(
                    RSQL.equals(rsqlFieldName, awardNumber),
                    RSQL.equals(rsqlFieldName, normalizedNihGrant)
            );
        } else if (awardNumber.equals(normalizedNihGrant) && StringUtils.isNotEmpty(awardNumberNihMinSet)) {
            return RSQL.or(
                    RSQL.equals(rsqlFieldName, awardNumber),
                    RSQL.equals(rsqlFieldName, awardNumberNihMinSet)
            );
        } else {
            return RSQL.or(
                    RSQL.equals(rsqlFieldName, awardNumber),
                    RSQL.equals(rsqlFieldName, normalizedNihGrant),
                    RSQL.equals(rsqlFieldName, awardNumberNihMinSet)
            );
        }
    }
}
