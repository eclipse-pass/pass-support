package org.eclipse.pass.support.client;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

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
        if (awardNumber.toUpperCase().matches("0*\\s*[A-Z][0-9]{2}\s*[A-Z]{2}[A-Z0-9]{6}-*[A-Z0-9]*")) {
            //remove leading zeros, whitespace and make uppercase
            awardNumber = awardNumber
                    .replaceFirst("^0+(?!$)", "")
                    .replaceAll("\\s", "")
                    .toUpperCase();
        }
        return awardNumber;
    }

    /**
     * Generate RSQL that will find any awardNumber that various patterns that an NIH grant award number
     * can come in
     * Along with the following variations from the standard pattern:
     *  1) a suffix denoted by a hyphen followed by characters, e.g. A01 1234567-01
     *  2) Leading and trailing spaces
     *  3) Multiple spaces in between the first set of characters and second set: A01  1234567
     *  4) Leading zeros in the first set of characters: 000A01 1234567
     * @param awardNumber
     * @return
     */
    public static String grantAwardNumberNormalizeSearch(String awardNumber, String rsqlFieldName) throws IOException {
        if (StringUtils.isEmpty(awardNumber)) {
            throw new IOException("Award number cannot be empty");
        }
        //tokenize award number between character sets
        String[] tokens = awardNumber.trim().split("\\s+");
        //loop through tokens and append the characters % to the end of each token. This way the query can
        //find any award number that has n many spaces between character sets
        StringBuilder awardNumberTokenized = new StringBuilder();
        for (String token : tokens) {
            awardNumberTokenized.append(token).append("%");
        }
        return RSQL.or(
                RSQL.equals(rsqlFieldName, awardNumber),
                RSQL.equals(rsqlFieldName, awardNumber.trim()),
                RSQL.equals(rsqlFieldName, awardNumber.trim().replaceAll("-.*$","")),
                RSQL.equals(rsqlFieldName, awardNumber.trim().replaceAll("\\s+","")),
                RSQL.equals(rsqlFieldName, awardNumber.trim().replaceAll("-.*$","")
                        .replaceAll("\\s+","")),
                RSQL.equals(rsqlFieldName, Util.grantAwardNumberNormalizer(awardNumber)),
                RSQL.equals(rsqlFieldName, awardNumber.trim().replaceFirst("^0+",""))/*,
                rsqlFieldName + "=like=%" + awardNumberTokenized*/
        );
    }
}
