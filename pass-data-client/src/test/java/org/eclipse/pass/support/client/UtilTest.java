package org.eclipse.pass.support.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * This is a test class to test the utility methods in the pass-data-client Util class.
 */
public class UtilTest {
    @Test
    public void testNullAndEmptyAwardNumber() throws IOException {
        assertNull(Util.grantAwardNumberNormalizer(null));
        assertNull(Util.grantAwardNumberNormalizer(""));
    }

    @Test
    public void testNihAwardNumberCorrectFormatWithSpace() throws IOException {
        String awardNumber1 = "K99 NS062901";
        String awardNumber2 = "P50 AI074285";
        String awardNumber1Expected = "K99NS062901";
        String awardNumber2Expected = "P50AI074285";
        assertEquals(awardNumber1Expected, Util.grantAwardNumberNormalizer(awardNumber1));
        assertEquals(awardNumber2Expected, Util.grantAwardNumberNormalizer(awardNumber2));
    }

    @Test
    public void testAwardNumberCorrectFormatWithHyphen() throws IOException {
        String awardNumber1 = "A12 RH345678-A1";
        String awardNumber2 = "U01 CA078284-05S2";
        String awardNumber3 = "000U01 CA078284-05S2";
        String awardNumber4 = "1R01 AR074846-A1";
        String expectedAwardNumber1 = "A12RH345678-A1";
        String expectedAwardNumber2 = "U01CA078284-05S2";
        String expectedAwardNumber3 = "U01CA078284-05S2";
        String expectedAwardNumber4 = "1R01AR074846-A1";
        assertEquals(expectedAwardNumber1, Util.grantAwardNumberNormalizer(awardNumber1));
        assertEquals(expectedAwardNumber2, Util.grantAwardNumberNormalizer(awardNumber2));
        assertEquals(expectedAwardNumber3, Util.grantAwardNumberNormalizer(awardNumber3));
        assertEquals(expectedAwardNumber4, Util.grantAwardNumberNormalizer(awardNumber4));
    }

    @Test
    public void testAwardNumberRemoveSpaces() throws IOException {
        String awardNumberNih1 = "  A01 BE345678   ";
        String awardNumberNih2 = "  K99 NS062901   ";
        String awardNumberNonNih1 = "   000 000844   ";
        String awardNumberNonNih2 = "   CBET0732580   ";

        String expectedNih1 = "A01BE345678";
        String expectedNih2 = "K99NS062901";
        String expectedNonNih1 = "000 000844";
        String expectedNonNih2 = "CBET0732580";

        assertEquals(expectedNih1, Util.grantAwardNumberNormalizer(awardNumberNih1));
        assertEquals(expectedNih2, Util.grantAwardNumberNormalizer(awardNumberNih2));
        assertEquals(expectedNonNih1, Util.grantAwardNumberNormalizer(awardNumberNonNih1));
        assertEquals(expectedNonNih2, Util.grantAwardNumberNormalizer(awardNumberNonNih2));
    }

    @Test
    public void testNihAwardNumbersLeadingZerosShouldNormalize() throws IOException {
        String awardNumber1 = "000A01 RH123456";
        String awardNumber2 = "000 A01 RH123456";
        String awardNumber3 = "000-A01 RH123456";
        String awardNumber4 = "000-A01 RH123456-A1";

        String expectedNumber = "A01RH123456";
        String expectedNumber2 = "A01RH123456-A1";

        assertEquals(expectedNumber, Util.grantAwardNumberNormalizer(awardNumber1));
        assertEquals(expectedNumber, Util.grantAwardNumberNormalizer(awardNumber2));
        assertEquals(expectedNumber, Util.grantAwardNumberNormalizer(awardNumber3));

        // This one is a special case, because the A1 is part of the award number,and should normalize with it
        assertEquals(expectedNumber2, Util.grantAwardNumberNormalizer(awardNumber4));
    }

    /**
     * Reads in a list of valid award numbers from a file and tests that the normalizer works for all
     * award numbers in the file by not modifying them. These are non-NIH award numbers, and NIH award numbers
     * that are already in the normalized format.
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void testAwardNumberWithValidData() throws IOException, URISyntaxException {
        URI testAwardNumberUri = UtilTest.class.getResource("/valid_award_numbers.csv").toURI();
        List<String> awardNumbers = Files.readAllLines(Paths.get(testAwardNumberUri));
        for (String awardNumber : awardNumbers) {
            assertEquals(awardNumber, Util.grantAwardNumberNormalizer(awardNumber));
        }
    }

    /**
     * Test that the correct RSQL generation is done for the given award number. There are four different types of
     * RSQL that can be generated depending on the state of the original award number:
     *
     * 1. award number and award number normalized are distinct and the NIH wild card award number is empty
     * 2. award number and award number normalized are NOT distinct and the NIH wild card award number is empty
     * 3. award number and award number normalized are distinct and the NIH wild card award number is NOT empty
     * 4. award number and award number normalized are NOT distinct and the NIH wild card award number is NOT empty
     */
    @Test
    public void testRsqlGenerationForSearchType() throws IOException {
        String awardNumberCase1 = " test1234 1234";
        String awardNumberCase2 = "test1234";
        String awardNumberCase3 = " R01 CA078284";
        String awardNumberCase4 = "R01CA078284";

        String expectedCase1 = "(awardNumber==' test1234 1234',awardNumber=='test1234 1234')";
        String expectedCase2 = "awardNumber=='test1234'";
        String expectedCase3 = "(awardNumber==' R01 CA078284',awardNumber=='R01CA078284',awardNumber=='*R01CA078284*')";
        String expectedCase4 = "(awardNumber=='R01CA078284',awardNumber=='*R01CA078284*')";

        assertEquals(expectedCase1, Util.grantAwardNumberNormalizeSearch(awardNumberCase1,"awardNumber"));
        assertEquals(expectedCase2, Util.grantAwardNumberNormalizeSearch(awardNumberCase2,"awardNumber"));
        assertEquals(expectedCase3, Util.grantAwardNumberNormalizeSearch(awardNumberCase3,"awardNumber"));
        assertEquals(expectedCase4, Util.grantAwardNumberNormalizeSearch(awardNumberCase4,"awardNumber"));

    }

}