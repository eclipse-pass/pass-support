/*
 * Copyright 2018 Johns Hopkins University
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
package org.eclipse.pass.deposit.config.repository;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
class AssemblerOptionsMappingTest extends AbstractJacksonMappingTest {

    private static final String OPTIONS_CONFIG = "" +
                                                 "{\n" +
                                                 "        \"archive\": \"ZIP\",\n" +
                                                 "        \"compression\": \"NONE\",\n" +
                                                 "        \"algorithms\": [\n" +
                                                 "          \"sha512\"" +
                                                 "        ]\n" +
                                                 "}";

    private static final String OPTIONS_CONFIG_ADDITIONAL_VALUES = "" +
                                                                   "{\n" +
                                                                   "        \"archive\": \"ZIP\",\n" +
                                                                   "        \"compression\": \"NONE\",\n" +
                                                                   "        \"algorithms\": [\n" +
                                                                   "          \"sha512\"" +
                                                                   "        ],\n" +
                                                                   "        \"stringkey\": \"stringvalue\",\n" +
                                                                   "        \"arraykey\": [\n" +
                                                                   "          \"arrayvalue\"\n" +
                                                                   "        ]\n" +
                                                                   "}";

    @Test
    void mapOptions() throws IOException {
        AssemblerOptions options = repositoriesMapper.readValue(OPTIONS_CONFIG, AssemblerOptions.class);

        assertEquals("ZIP", options.getArchive());
        assertEquals("NONE", options.getCompression());
        assertEquals(1, options.getAlgorithms().size());
        assertTrue(options.getAlgorithms().contains("sha512"));
    }

    @Test
    void mapOptionsWithAddtionalValues() throws IOException {
        AssemblerOptions options = repositoriesMapper.readValue(OPTIONS_CONFIG_ADDITIONAL_VALUES,
            AssemblerOptions.class);

        assertEquals("ZIP", options.getArchive());
        assertEquals("NONE", options.getCompression());
        assertEquals(1, options.getAlgorithms().size());
        assertTrue(options.getAlgorithms().contains("sha512"));

        assertEquals("stringvalue", options.getOptionsMap().get("stringkey"));
        assertEquals(singletonList("arrayvalue"), options.getOptionsMap().get("arraykey"));
    }
}