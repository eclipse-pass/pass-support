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
        awardNumber = awardNumber.trim().toUpperCase();

        //if matching the NIH format, then normalize it to the expected format by removing leading zeros
        if (awardNumber.matches("[A-Z0-9]{3}\s*[A-Z0-9]{8}")) {
            //remove leading zeros & whitespace
            awardNumber = awardNumber.replaceFirst("^0+(?!$)", "").replaceAll("\\s", "");
        }
        return awardNumber;
    }
}
