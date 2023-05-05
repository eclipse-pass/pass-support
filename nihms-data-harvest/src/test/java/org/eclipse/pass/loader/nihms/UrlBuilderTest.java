/*
 *
 *  * Copyright 2023 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.eclipse.pass.loader.nihms;

import static java.lang.String.format;
import static java.lang.String.join;
import static org.eclipse.pass.loader.nihms.NihmsHarvesterConfig.API_URL_PARAM_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class UrlBuilderTest {

    private UrlBuilder underTest;

    private URL generatedUrl;

    private Map<String, String> overrides = Collections.emptyMap();

    private Map<String, String> additional = Collections.emptyMap();

    @BeforeClass
    public static void apiParams() throws Exception {
        /*
        nihmsetl.api.url.param.filter =
        nihmsetl.api.url.param.format = csv
        nihmsetl.api.url.param.inst = JOHNS HOPKINS
        nihmsetl.api.url.param.ipf = 4134401
        nihmsetl.api.url.param.rd = 07/02/2019
        nihmsetl.api.url.param.pdf = 07/2018
        nihmsetl.api.url.param.pdt = 07/2019
         */

        System.setProperty(join("", API_URL_PARAM_PREFIX, "filter"), "");
        System.setProperty(join("", API_URL_PARAM_PREFIX, "format"), "csv");
        System.setProperty(join("", API_URL_PARAM_PREFIX, "inst"), "JOHNS HOPKINS");
        System.setProperty(join("", API_URL_PARAM_PREFIX, "ipf"), "4134401");
        System.setProperty(join("", API_URL_PARAM_PREFIX, "rd"), "07/02/2019");
        System.setProperty(join("", API_URL_PARAM_PREFIX, "pdf"), "07/2018");
        System.setProperty(join("", API_URL_PARAM_PREFIX, "pdt"), "07/2019");

        assertEquals("", NihmsHarvesterConfig.getApiUrlParams().get("filter"));
        assertEquals("csv", NihmsHarvesterConfig.getApiUrlParams().get("format"));
        assertEquals("JOHNS HOPKINS", NihmsHarvesterConfig.getApiUrlParams().get("inst"));
        assertEquals("4134401", NihmsHarvesterConfig.getApiUrlParams().get("ipf"));
        assertEquals("07/02/2019", NihmsHarvesterConfig.getApiUrlParams().get("rd"));
        assertEquals("07/2018", NihmsHarvesterConfig.getApiUrlParams().get("pdf"));
        assertEquals("07/2019", NihmsHarvesterConfig.getApiUrlParams().get("pdt"));
    }

    @AfterClass
    public static void cleanUpSystemProps() {
        System.getProperties()
              .entrySet()
              .stream()
              .filter((entry) -> ((String) entry.getKey()).startsWith(API_URL_PARAM_PREFIX))
              .collect(Collectors.toSet())
              .forEach(entry -> System.clearProperty((String) entry.getKey()));
    }

    @Before
    public void setUp() {
        underTest = new UrlBuilder();
    }

    @After
    public void verifyScheme() {
        assertEquals(NihmsHarvesterConfig.getApiScheme(), generatedUrl.getProtocol());
    }

    @After
    public void verifyHost() {
        assertEquals(NihmsHarvesterConfig.getApiHost(), generatedUrl.getHost());
    }

    @After
    public void verifyPath() throws Exception {
        assertTrue(generatedUrl.getPath().startsWith(NihmsHarvesterConfig.getApiPath()));
    }

    @After
    public void verifyParams() throws URISyntaxException {
        String query = generatedUrl.toURI().getQuery(); // .toURI() will decode the query parameter values
        assertNotNull(query);
        String[] parts = query.split("&");
        assertEquals(
            format("Unexpected number of URL parameters.  Wanted %s, got %s", additional.size() + 7, parts.length),
            additional.size() + 7, parts.length);

        Stream.of(parts).forEach(part -> {
            String[] subpart = part.split("=");
            String param = subpart[0];
            String value = subpart.length > 1 ? subpart[1] : null;

            switch (param) {
                case "format":
                    assertEquals(overrides.getOrDefault("format", "csv"), value);
                    break;
                case "filter":
                    assertEquals(overrides.getOrDefault("filter", null), value);
                    break;
                case "inst":
                    assertEquals(overrides.getOrDefault("inst", "JOHNS HOPKINS"), value);
                    break;
                case "ipf":
                    assertEquals(overrides.getOrDefault("ipf", "4134401"), value);
                    break;
                case "rd":
                    assertEquals(overrides.getOrDefault("rd", "07/02/2019"), value);
                    break;
                case "pdf":
                    assertEquals(overrides.getOrDefault("pdf", "07/2018"), value);
                    break;
                case "pdt":
                    assertEquals(overrides.getOrDefault("pdt", "07/2019"), value);
                    break;
                default:
                    if (additional.containsKey(param)) {
                        assertEquals(additional.get(param), value);
                    } else {
                        throw new RuntimeException("Unknown URL parameter '" + param + "' with value '" + value + "'");
                    }
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        System.err.println(generatedUrl.toString());
    }

    @Test
    public void compliantUrl() {
        generatedUrl = underTest.compliantUrl();
        assertTrue(generatedUrl.getPath().endsWith(format("/%s", UrlType.COMPLIANT.getCode())));
    }

    @Test
    public void inProcessUrl() {
        generatedUrl = underTest.inProcessUrl();
        assertTrue(generatedUrl.getPath().endsWith(format("/%s", UrlType.IN_PROCESS.getCode())));
    }

    @Test
    public void nonCompliantUrl() {
        generatedUrl = underTest.nonCompliantUrl();
        assertTrue(generatedUrl.getPath().endsWith(format("/%s", UrlType.NON_COMPLIANT.getCode())));
    }

    @Test
    public void compliantUrlWithParamOverride() {
        overrides = new HashMap<String, String>() {
            {
                put("format", "moo");
            }
        };

        generatedUrl = underTest.compliantUrl(overrides);
        assertTrue(generatedUrl.getPath().endsWith(format("/%s", UrlType.COMPLIANT.getCode())));
    }

    @Test
    public void compliantUrlWithAdditionalParam() {
        additional = new HashMap<String, String>() {
            {
                put("api-key", "api-key-value");
            }
        };

        generatedUrl = underTest.compliantUrl(additional);
        assertTrue(generatedUrl.getPath().endsWith(format("/%s", UrlType.COMPLIANT.getCode())));
    }

    @Test
    public void nonCompliantUrlWithParamOverride() {
        overrides = new HashMap<String, String>() {
            {
                put("format", "moo");
            }
        };

        generatedUrl = underTest.nonCompliantUrl(overrides);
        assertTrue(generatedUrl.getPath().endsWith(format("/%s", UrlType.NON_COMPLIANT.getCode())));
    }

    @Test
    public void nonCompliantUrlWithAdditionalParam() {
        additional = new HashMap<String, String>() {
            {
                put("api-key", "api-key-value");
            }
        };

        generatedUrl = underTest.nonCompliantUrl(additional);
        assertTrue(generatedUrl.getPath().endsWith(format("/%s", UrlType.NON_COMPLIANT.getCode())));
    }

    @Test
    public void inProcessUrlWithParamOverride() {
        overrides = new HashMap<String, String>() {
            {
                put("format", "moo");
            }
        };

        generatedUrl = underTest.inProcessUrl(overrides);
        assertTrue(generatedUrl.getPath().endsWith(format("/%s", UrlType.IN_PROCESS.getCode())));
    }

    @Test
    public void inProcessUrlWithAdditionalParam() {
        additional = new HashMap<String, String>() {
            {
                put("api-key", "api-key-value");
            }
        };

        generatedUrl = underTest.inProcessUrl(additional);
        assertTrue(generatedUrl.getPath().endsWith(format("/%s", UrlType.IN_PROCESS.getCode())));
    }
}