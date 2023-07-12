/*
 * Copyright 2019 Johns Hopkins University
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
package org.eclipse.pass.deposit.provider.bagit;

import static org.eclipse.pass.deposit.provider.bagit.BagItWriter.COLON;
import static org.eclipse.pass.deposit.provider.bagit.BagItWriter.CR;
import static org.eclipse.pass.deposit.provider.bagit.BagItWriter.CR_ENCODED;
import static org.eclipse.pass.deposit.provider.bagit.BagItWriter.LF;
import static org.eclipse.pass.deposit.provider.bagit.BagItWriter.LF_ENCODED;
import static org.eclipse.pass.deposit.provider.bagit.BagItWriter.PERCENT;
import static org.eclipse.pass.deposit.provider.bagit.BagItWriter.PERCENT_ENCODED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class BagItWriterTest {

    @Test
    public void validateLabelSuccess() {
        BagItWriter.validateLabel("foo");
    }

    @Test
    public void validateLabelCr() {
        assertThrows(RuntimeException.class, () -> BagItWriter.validateLabel("foo" + CR));
    }

    @Test
    public void validateLabelLf() {
        assertThrows(RuntimeException.class, () -> BagItWriter.validateLabel("foo" + LF));
    }

    @Test
    public void validateLabelColon() {
        assertThrows(RuntimeException.class, () -> BagItWriter.validateLabel("foo" + COLON + "bar"));
    }

    @Test
    public void validateLabelCrLf() {
        assertThrows(RuntimeException.class, () -> BagItWriter.validateLabel("foo" + CR + LF));
    }

    @Test
    public void validateLabelContainsSpace() {
        BagItWriter.validateLabel("f o o");
    }

    @Test
    public void validateLabelContainsTab() {
        BagItWriter.validateLabel("f    o   o");
    }

    @Test
    public void validateLabelStartsWithTab() {
        assertThrows(RuntimeException.class, () -> BagItWriter.validateLabel(" foo"));
    }

    @Test
    public void validateLabelStartsWithSpace() {
        assertThrows(RuntimeException.class, () -> BagItWriter.validateLabel(" foo"));
    }

    @Test
    public void validateLabelEndsWithSpace() {
        assertThrows(RuntimeException.class, () -> BagItWriter.validateLabel("foo "));
    }

    @Test
    public void validateLabelEndsWithTab() {
        assertThrows(RuntimeException.class, () -> BagItWriter.validateLabel("foo  "));
    }

    @Test
    public void encodeLineNoop() {
        assertEquals("foo", BagItWriter.encodeLine("foo"));
    }

    @Test
    public void encodeLineCrAtStart() {
        assertEquals(CR_ENCODED + "foo", BagItWriter.encodeLine(CR + "foo"));
    }

    @Test
    public void encodeLineCrAtEnd() {
        assertEquals("foo" + CR, BagItWriter.encodeLine("foo" + CR));
    }

    @Test
    public void encodeLineLfAtEnd() {
        assertEquals("foo" + LF, BagItWriter.encodeLine("foo" + LF));
    }

    @Test
    public void encodeLineCrLfAtEnd() {
        assertEquals("foo" + CR + LF, BagItWriter.encodeLine("foo" + CR + LF));
    }

    @Test
    public void encodeLineCrLf() {
        assertEquals("foo" + CR_ENCODED + LF_ENCODED + "bar",
                     BagItWriter.encodeLine("foo" + CR + LF + "bar"));
    }

    @Test
    public void encodeLinePercentWithLfAtEnd() {
        assertEquals(PERCENT_ENCODED + "foo" + PERCENT_ENCODED + LF,
                     BagItWriter.encodeLine(PERCENT + "foo" + PERCENT + LF));
    }

    @Test
    public void encodePathNoop() {
        assertEquals("foo", BagItWriter.encodePath("foo"));
    }

    @Test
    public void encodePathCrAtStart() {
        assertEquals(CR_ENCODED + "foo", BagItWriter.encodePath(CR + "foo"));
    }

    @Test
    public void encodePathCrAtEnd() {
        assertEquals("foo" + CR_ENCODED, BagItWriter.encodePath("foo" + CR));
    }

    @Test
    public void encodePathLfAtEnd() {
        assertEquals("foo" + LF_ENCODED, BagItWriter.encodePath("foo" + LF));
    }

    @Test
    public void encodePathCrLfAtEnd() {
        assertEquals("foo" + CR_ENCODED + LF_ENCODED, BagItWriter.encodePath("foo" + CR + LF));
    }

    @Test
    public void encodePathCrLf() {
        assertEquals("foo" + CR_ENCODED + LF_ENCODED + "bar",
                     BagItWriter.encodePath("foo" + CR + LF + "bar"));
    }

    @Test
    public void encodePathPercentWithLfAtEnd() {
        assertEquals(PERCENT_ENCODED + "foo" + PERCENT_ENCODED + LF_ENCODED,
                     BagItWriter.encodePath(PERCENT + "foo" + PERCENT + LF));
    }

    @Test
    public void usesCharsetOnConstruction() throws IOException {
        // e with acute encoded as UTF-16 is 0x00E9
        String acuteEUtf16 = "\u00e9";

        // e with acute encoded as UTF-8 is 0xC3A9
        // byte array will be the 2's complement of 0xC3A9
        byte[] expectedBytes = new byte[] {(byte) 0xC3, (byte) 0xA9};

        // Create a bag writer that should encode using UTF-8
        BagItWriter writer = new BagItWriter(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // the UTF-16 character should be encoded as UTF-8 in the output
        writer.writeTagLine(out, "test", acuteEUtf16);

        byte[] result = out.toByteArray();  // should end with 2's complement of 0xC3A9 followed by a LF
        assertArrayEquals(expectedBytes, new byte[] {result[result.length - 3], result[result.length - 2]});
    }
}