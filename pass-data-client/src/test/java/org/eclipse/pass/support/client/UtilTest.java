package org.eclipse.pass.support.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

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
    public void testAwardNumberCorrectFormat() throws IOException {
        String awardNumber1 = "A12 RH345678";
        String awardNumber2 = "UL1 TR001079";
        String awardNumber3 = "U01 AG032947";
        String awardNumber4 = "R35 GM136665";
        String awardNumber5 = "T32 CA060441";
        String awardNumber6 = "P50 CA062924";
        assertEquals(awardNumber1, Util.grantAwardNumberNormalizer(awardNumber1));
        assertEquals(awardNumber2, Util.grantAwardNumberNormalizer(awardNumber2));
        assertEquals(awardNumber3, Util.grantAwardNumberNormalizer(awardNumber3));
        assertEquals(awardNumber4, Util.grantAwardNumberNormalizer(awardNumber4));
        assertEquals(awardNumber5, Util.grantAwardNumberNormalizer(awardNumber5));
        assertEquals(awardNumber6, Util.grantAwardNumberNormalizer(awardNumber6));
    }

    @Test
    public void testAwardNumberCorrectFormatWithHyphen() throws IOException {
        String awardNumber = "A12 RH345678-A1";
        String expectedAwardNumber = "A12 RH345678";
        assertEquals(expectedAwardNumber, Util.grantAwardNumberNormalizer(awardNumber));
    }

    @Test
    public void testAwardNumberLeadTrailSpaces() throws IOException {
        String awardNumber = "  ABC 12345678   ";
        String expected = "ABC 12345678";
        assertEquals(expected, Util.grantAwardNumberNormalizer(awardNumber));
    }

    @Test
    public void testInvalidAwardNumbersThrowsException() {
        String awardNumber1 = "A12 K134567809384-A3";
        String awardNumber2 = "  A12 K134?5678";
        String awardNumber3 = "A K1345678";
        String awardNumber4 = "K1234567890";
        String awardNumber5 = "K124 H4567890";
        assertThrows(IOException.class, () -> {
            Util.grantAwardNumberNormalizer(awardNumber1);
        });
        assertThrows(IOException.class, () -> {
            Util.grantAwardNumberNormalizer(awardNumber2);
        });
        assertThrows(IOException.class, () -> {
            Util.grantAwardNumberNormalizer(awardNumber3);
        });
        assertThrows(IOException.class, () -> {
            Util.grantAwardNumberNormalizer(awardNumber4);
        });
        assertThrows(IOException.class, () -> {
            Util.grantAwardNumberNormalizer(awardNumber5);
        });
    }

}
