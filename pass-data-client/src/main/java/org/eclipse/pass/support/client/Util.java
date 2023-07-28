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
     * Normalizes an award number by standardizing to the format: [A-Z0-9]{3}\s[A-Z0-9]{8}
     * e.g. K23 HL153778
     * @param awardNumber
     * @return
     */
    public static String grantAwardNumberNormalizer(String awardNumber) throws IOException {
        if (StringUtils.isEmpty(awardNumber)) {
            return null;
        }
        if (awardNumber.matches("^[A-Z0-9]{3}\s[A-Z0-9]{8}$")) {
            return awardNumber;
        }
        String regex = "[A-Z0-9]{3}\\s[A-Z0-9]{8}";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(awardNumber);
        //if not in the format we expect, try to normalize it
        if (matcher.find()) {
            return matcher.group();
        } else {
            throw new IOException("Award number cannot be normalized: " + awardNumber);
        }
    }
}
