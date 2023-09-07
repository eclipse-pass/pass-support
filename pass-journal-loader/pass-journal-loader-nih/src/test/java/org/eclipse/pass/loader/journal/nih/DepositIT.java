/*
 * Copyright 2017 Johns Hopkins University
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

package org.eclipse.pass.loader.journal.nih;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.junit.jupiter.api.Test;

/**
 * @author apb@jhu.edu
 */
@WireMockTest
public class DepositIT {
    private final PassClient client = PassClient.newInstance();

    @Test
    public void loadFromFileTest(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        String jmedlineJournals = Files.readString(
            Paths.get(DepositIT.class.getResource("/medline.txt").toURI()));
        stubFor(get("/pubmed/J_Medline.txt")
            .willReturn(ok(jmedlineJournals)));
        String pmcJournlas1 = Files.readString(
            Paths.get(DepositIT.class.getResource("/pmc-1.csv").toURI()));
        stubFor(get("/pmc/front-page/NIH_PA_journal_list-1.csv")
            .willReturn(ok(pmcJournlas1)));
        String pmcJournlas2 = Files.readString(
            Paths.get(DepositIT.class.getResource("/pmc-2.csv").toURI()));
        stubFor(get("/pmc/front-page/NIH_PA_journal_list-2.csv")
            .willReturn(ok(pmcJournlas2)));

        final int wmPort = wmRuntimeInfo.getHttpPort();
        System.setProperty("medline", "http://localhost:" + wmPort + "/pubmed/J_Medline.txt");
        System.setProperty("pmc", "");
        Main.main(new String[] {});

        // We expect three journals, but no PMC A journals
        assertEquals(3, listJournals().size());
        assertEquals(0, typeA(listJournals()).size());

        System.setProperty("medline", "");
        System.setProperty("pmc", "http://localhost:" + wmPort + "/pmc/front-page/NIH_PA_journal_list-1.csv");
        Main.main(new String[] {});

        // We still expect three journals in the repository, but now two are PMC A
        assertEquals(3, listJournals().size());
        assertEquals(2, typeA(listJournals()).size());

        System.setProperty("medline", "");
        System.setProperty("pmc", "http://localhost:" + wmPort + "/pmc/front-page/NIH_PA_journal_list-2.csv");
        Main.main(new String[] {});

        // The last dataset removed a type A journal, so now we expect only one
        assertEquals(3, listJournals().size());
        assertEquals(1, typeA(listJournals()).size());
    }

    private List<PmcParticipation> typeA(List<Journal> journals) {
        return journals.stream()
                   .map(Journal::getPmcParticipation)
                   .filter(Objects::nonNull)
                   .collect(Collectors.toList());
    }

    private List<Journal> listJournals() throws Exception {
        PassClientSelector<Journal> sel = new PassClientSelector<>(Journal.class);

        return client.selectObjects(sel).getObjects();
    }
}
