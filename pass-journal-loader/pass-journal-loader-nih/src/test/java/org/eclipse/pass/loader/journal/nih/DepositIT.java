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


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.junit.jupiter.api.Test;

/**
 * @author apb@jhu.edu
 */
public class DepositIT {
    private final PassClient client = PassClient.newInstance();

    @Test
    public void loadFromFileTest() throws Exception {
        // First, load all three journals using medline data

        System.setProperty("medline", DepositIT.class.getResource("/medline.txt").getPath());
        System.setProperty("pmc", "");
        Main.main(new String[] {});

        // We expect three journals, but no PMC A journals
        assertEquals(3, listJournals().size());
        assertEquals(0, typeA(listJournals()).size());

        System.setProperty("medline", "");
        System.setProperty("pmc", DepositIT.class.getResource("/pmc-1.csv").getPath());
        Main.main(new String[] {});

        // We still expect three journals in the repository, but now two are PMC A
        assertEquals(3, listJournals().size());
        assertEquals(2, typeA(listJournals()).size());

        System.setProperty("medline", "");
        System.setProperty("pmc", DepositIT.class.getResource("/pmc-2.csv").getPath());
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
