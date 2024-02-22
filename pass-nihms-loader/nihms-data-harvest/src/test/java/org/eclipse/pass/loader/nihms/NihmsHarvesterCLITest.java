/*
 *
 *  * Copyright 2024 Johns Hopkins University
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;

import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest(classes = NihmsHarvesterCLI.class)
@TestPropertySource("classpath:test-application.properties")
public class NihmsHarvesterCLITest {

    @SpyBean NihmsHarvester nihmsHarvester;
    @MockBean NihmsHarvesterDownloader nihmsHarvesterDownloader;

    @Test
    public void testHarvesterCLI() throws IOException, InterruptedException {
        // GIVEN/WHEN
        // THEN
        verify(nihmsHarvester).harvest(eq(EnumSet.allOf(NihmsStatus.class)), any());
        verify(nihmsHarvesterDownloader).download(
            eq(new URL("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/c?pdt=07%2F2019&pdf=07%2F2018" +
                "&api-token=test-token&inst=JOHNS-HOPKINS-TEST&format=csv&ipf=4134401-TEST")),
            eq(NihmsStatus.COMPLIANT));
        verify(nihmsHarvesterDownloader).download(
            eq(new URL("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/p?pdt=07%2F2019&pdf=07%2F2018" +
                "&api-token=test-token&inst=JOHNS-HOPKINS-TEST&format=csv&ipf=4134401-TEST")),
            eq(NihmsStatus.IN_PROCESS));
        verify(nihmsHarvesterDownloader).download(
            eq(new URL("https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/n?pdt=07%2F2019&pdf=07%2F2018" +
                "&api-token=test-token&inst=JOHNS-HOPKINS-TEST&format=csv&ipf=4134401-TEST")),
            eq(NihmsStatus.NON_COMPLIANT));
    }

}