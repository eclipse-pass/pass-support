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

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The core of an NIH award number: activity code, institute code and serial number, e.g. R01 EB 019135.
 * Application type prefixes, leading zeros and support year suffixes are not part of the core, so every
 * PASS grant for the same NIH project maps to the same core.
 *
 * @param activityCode  the activity code, e.g. R01
 * @param instituteCode the two letter institute code, e.g. EB
 * @param serialNumber  the six digit serial number
 */
public record NihAward(String activityCode, String instituteCode, String serialNumber) {

    // Same shape as the NIH detection in ModelUtil.createAwardNumberQuery
    private static final Pattern NIH_AWARD_NUMBER = Pattern.compile(
        "[0-9]*-*\\s*([A-Z]{1,2}[0-9]{1,2})\\s*([A-Z]{2})([0-9]{6})-*[A-Z0-9]*");

    /**
     * Parse an award number.
     *
     * @param awardNumber an award number as held by a PASS Grant
     * @return the NIH core of the award number, or null if it does not look like an NIH award number
     */
    public static NihAward parse(String awardNumber) {
        if (awardNumber == null) {
            return null;
        }
        Matcher matcher = NIH_AWARD_NUMBER.matcher(awardNumber.trim().toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        return new NihAward(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    /**
     * The form written to the CSV files, which the transform resolves back to a PASS Grant.
     *
     * @return the core without separators, e.g. R01EB019135
     */
    public String core() {
        return activityCode + instituteCode + serialNumber;
    }

    /**
     * The ways this award is commonly written in Crossref funding metadata. Crossref matches award
     * numbers as whole strings, ignoring case, so each form has to be asked for.
     *
     * @return the variants to search for
     */
    public Set<String> searchVariants() {
        Set<String> variants = new LinkedHashSet<>();
        variants.add(activityCode + " " + instituteCode + serialNumber);
        variants.add(core());
        variants.add(activityCode + "-" + instituteCode + serialNumber);
        variants.add(instituteCode + serialNumber);
        return variants;
    }
}
