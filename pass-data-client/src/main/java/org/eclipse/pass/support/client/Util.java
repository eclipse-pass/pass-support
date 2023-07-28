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
     * @param awardNumber award number to normalize
     * @return normalized award number in the format of [A-Z0-9]{3}\s[A-Z0-9]{8}, e.g. K23 HL153778
     * @throws IOException if the award number cannot be normalized
     */
    public static String grantAwardNumberNormalizer(String awardNumber) throws IOException {
        if (StringUtils.isEmpty(awardNumber)) {
            return null;
        }
        if (awardNumber.matches("^[A-Z0-9]{3}\s[A-Z0-9]{8}$")) {
            return awardNumber;
        }
        awardNumber = awardNumber.trim().toUpperCase();
        // Pattern for award numbers, typically a character followed by 2 digits, a space, and a mix of letters
        // and digits totaling 8 characters.
        String regex = "^[A-Z0-9]{3}\\s[A-Z0-9]{8}($|-[A-Z0-9]{0,4}$|\\s+[A-Z0-9]{0,4})";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(awardNumber);
        //if not in the various formats we expect, try to normalize it by finding the first match substring
        if (matcher.find()) {
            //if matched the different variations of the awardNumber, then return the normalized version
            String regexSubstring = "^[A-Z0-9]{3}\\s[A-Z0-9]{8}";
            Pattern patternSubstring = Pattern.compile(regexSubstring);
            Matcher matcherSubstring = patternSubstring.matcher(awardNumber);
            matcherSubstring.find();
            return matcherSubstring.group();
        } else {
            throw new IOException("Award number cannot be normalized: " + awardNumber);
        }
    }
}
