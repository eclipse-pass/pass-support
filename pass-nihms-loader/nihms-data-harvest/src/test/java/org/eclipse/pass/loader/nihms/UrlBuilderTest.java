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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = NihmsHarvesterCLI.class)
@TestPropertySource("classpath:test-application.properties")
public class UrlBuilderTest {

    @MockBean NihmsHarvester nihmsHarvester;
    @Autowired private UrlBuilder urlBuilder;

    @Test
    public void compliantUrl() {
        URL generatedUrl = urlBuilder.compliantUrl(Collections.emptyMap());
        assertEquals("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/c?" +
            "pdt=07%2F2019&pdf=07%2F2018&api-token=test-token&inst=JOHNS-HOPKINS-TEST&format=csv&ipf=4134401-TEST",
            generatedUrl.toString());
    }

    @Test
    public void inProcessUrl() {
        URL generatedUrl = urlBuilder.inProcessUrl(Collections.emptyMap());
        assertEquals("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/p?" +
                "pdt=07%2F2019&pdf=07%2F2018&api-token=test-token&inst=JOHNS-HOPKINS-TEST&format=csv&ipf=4134401-TEST",
            generatedUrl.toString());
    }

    @Test
    public void nonCompliantUrl() {
        URL generatedUrl = urlBuilder.nonCompliantUrl(Collections.emptyMap());
        assertEquals("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/n?" +
                "pdt=07%2F2019&pdf=07%2F2018&api-token=test-token&inst=JOHNS-HOPKINS-TEST&format=csv&ipf=4134401-TEST",
            generatedUrl.toString());
    }

    @Test
    public void urlWithParamOverride() {
        URL generatedUrl = urlBuilder.compliantUrl(Map.of("api-token", "api-key-value-over"));
        assertEquals("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/c?" +
                "pdt=07%2F2019&pdf=07%2F2018&api-token=api-key-value-over&inst=JOHNS-HOPKINS-TEST" +
                "&format=csv&ipf=4134401-TEST",
            generatedUrl.toString());
    }

    @Test
    public void urlWithAdditionalParam() {
        URL generatedUrl = urlBuilder.compliantUrl(Map.of("new-key", "moo"));
        assertEquals("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/c?" +
                "pdt=07%2F2019&pdf=07%2F2018&api-token=test-token&inst=JOHNS-HOPKINS-TEST&format=csv" +
                "&new-key=moo&ipf=4134401-TEST",
            generatedUrl.toString());
    }
}