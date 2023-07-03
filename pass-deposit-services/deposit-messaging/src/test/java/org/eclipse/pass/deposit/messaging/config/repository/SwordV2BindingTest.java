/*
 * Copyright 2020 Johns Hopkins University
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
package org.eclipse.pass.deposit.messaging.config.repository;

import static org.eclipse.pass.deposit.transport.sword2.Sword2TransportHints.HINT_TUPLE_SEPARATOR;
import static org.eclipse.pass.deposit.transport.sword2.Sword2TransportHints.HINT_URL_SEPARATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SwordV2BindingTest {

    private SwordV2Binding underTest;

    @BeforeEach
    public void setUp() throws Exception {
        underTest = new SwordV2Binding();
    }

    @Test
    public void hintsToPropertyString() {
        String covid = "https://jscholarship.library.jhu.edu/handle/1774.2/58585";
        String nobel = "https://jscholarship.library.jhu.edu/handle/1774.2/33532";
        Map<String, String> hints = Map.of("covid", covid, "nobel", nobel);

        String expected1 = "covid" + HINT_URL_SEPARATOR + covid + HINT_TUPLE_SEPARATOR +
                "nobel" + HINT_URL_SEPARATOR + nobel;
        String expected2 = "nobel" + HINT_URL_SEPARATOR + nobel + HINT_TUPLE_SEPARATOR +
                "covid" + HINT_URL_SEPARATOR + covid;

        String result = underTest.hintsToPropertyString(hints);

        assertTrue(expected1.equals(result) || expected2.equals(result));
        assertEquals(2, underTest.hintsToPropertyString(hints).split(" ").length);
    }

    @Test
    public void hintsToPropertyStringTrailingOrLeadingSpace() {
        String leading = " https://jscholarship.library.jhu.edu/handle/1774.2/58585";
        String trailing = "https://jscholarship.library.jhu.edu/handle/1774.2/33532 ";
        Map<String, String> hints = Map.of("covid", leading, "nobel", trailing);

        String expected1 = "covid" + HINT_URL_SEPARATOR + leading.trim() + HINT_TUPLE_SEPARATOR +
                "nobel" + HINT_URL_SEPARATOR + trailing.trim();
        String expected2 = "nobel" + HINT_URL_SEPARATOR + trailing.trim() + HINT_TUPLE_SEPARATOR + "covid" +
            HINT_URL_SEPARATOR + leading.trim();

        String result = underTest.hintsToPropertyString(hints);

        assertTrue(expected1.equals(result) || expected2.equals(result));
        assertEquals(2, underTest.hintsToPropertyString(hints).split(" ").length);
    }

    @Test
    public void hintsToPropertyStringEncodedSpace() {
        String encodedSpace = "https://jscholarship.library.jhu.edu/handle/1774.2/58585?moo=%20cow%20";
        Map<String, String> hints = Map.of("covid", encodedSpace);

        String expected = "covid" + HINT_URL_SEPARATOR + encodedSpace;

        assertEquals(expected, underTest.hintsToPropertyString(hints));
        assertEquals(1, underTest.hintsToPropertyString(hints).split(" ").length);
    }

    @Test
    public void hintsToPropertyStringNullMap() {
        assertEquals("", underTest.hintsToPropertyString(null));
    }

    @Test
    public void hintsToPropertyStringEmptyMap() {
        assertEquals("", underTest.hintsToPropertyString(Collections.emptyMap()));
    }

}