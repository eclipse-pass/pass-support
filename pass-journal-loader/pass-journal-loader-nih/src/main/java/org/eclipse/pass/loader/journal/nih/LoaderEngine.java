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

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Journal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads journal or updates records into the repository
 * <p>
 * Uses ISSN, name and NLMTA data to correlate parsed journals with journals in the repository, updates the
 * repository if
 * pmc participation, NLMTA, or ISSNS have changed
 * </p>
 *
 * @author apb@jhu.edu
 */
public class LoaderEngine implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(LoaderEngine.class);
    private final PassClient client;
    private final JournalFinder finder;
    private boolean dryRun = false;
    private final AtomicInteger numCreated = new AtomicInteger(0);
    private final AtomicInteger numUpdated = new AtomicInteger(0);
    private final AtomicInteger numSkipped = new AtomicInteger(0);
    private final AtomicInteger numOk = new AtomicInteger(0);
    private final AtomicInteger numError = new AtomicInteger(0);
    private final AtomicInteger numDup = new AtomicInteger(0);

    LoaderEngine(PassClient client, JournalFinder finder) {
        this.client = client;
        this.finder = finder;
    }

    void load(Stream<Journal> journals, boolean hasPmcParticipation) {
        journals
            .forEach(j -> load(j, hasPmcParticipation));
    }

    void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    public void close() {
        if (dryRun) {
            LOG.info("Dry run: would have created {} new journals", numCreated);
            LOG.info("Dry run: would have updated {} journals", numUpdated);
            LOG.info("Dry run: {} journals did not need updating", numOk);
            LOG.info("Dry run: Skipped {} journals due to lack of ISSN and NLMTA", numSkipped);
            LOG.info("Dry run: Skipped {} journals due to suspected duplication", numDup);
            LOG.info("Dry run: Could not load or update {} journals due to an error", numError);
        } else {
            LOG.info("Created {} new journals", numCreated);
            LOG.info("Updated {} journals", numUpdated);
            LOG.info("{} journals did not need updating", numOk);
            LOG.info("Skipped {} journals due to lack of ISSN and NLMTA", numSkipped);
            LOG.info("Skipped {} journals due to suspected duplication", numDup);
            LOG.info("Could not load or update {} journals due to an error", numError);
        }
    }

    private void load(Journal j, boolean hasPmcParticipation) {
        if (j.getIssns().isEmpty() && (j.getNlmta() == null || j.getNlmta().isEmpty())) {
            LOG.warn("Journal has no ISSNs or NLMTA: {}", j.getJournalName());
            numSkipped.incrementAndGet();
            return;
        }

        String found = finder.find(j.getNlmta(), j.getJournalName(), j.getIssns());
        LOG.info(found);
        if (found == null) { //create a new journal
            if (!dryRun) {
                try {
                    client.createObject(j);
                    finder.add(j);

                    LOG.info("Loaded journal {} at {}", j.getJournalName(), j.getId());
                    numCreated.incrementAndGet();
                } catch (IOException e) {
                    LOG.error("Could not load journal " + j.getJournalName(), e);
                    numError.getAndIncrement();
                }
            } else {
                j.setId(UUID.randomUUID().toString());
                finder.add(j);
                numCreated.incrementAndGet();
            }
        } else if (found.equals("SKIP")) { //this matched something that was already processed
            numDup.getAndIncrement();
            LOG.info("We have already processed this journal, skipping: {}", j.getJournalName());
        } else { //update this journal
            boolean update = false;

            try {
                final Journal toUpdate = client.getObject(Journal.class, found);

                if (hasPmcParticipation && toUpdate.getPmcParticipation() != j.getPmcParticipation()) {
                    toUpdate.setPmcParticipation(j.getPmcParticipation());
                    update = true;
                }

                if (j.getIssns() != null && (toUpdate.getIssns() == null || !toUpdate.getIssns()
                        .containsAll(j.getIssns()))) {
                    toUpdate.setIssns(j.getIssns());
                    update = true;
                }

                if (toUpdate.getNlmta() == null && j.getNlmta() != null) {
                    toUpdate.setNlmta(j.getNlmta());
                    update = true;
                }

                if (!update) {
                    LOG.debug("No need to update Journal {} ID {}", toUpdate.getJournalName(), toUpdate.getId());
                }

                if (!dryRun) {
                    if (update) {
                        client.updateObject(toUpdate);

                        numUpdated.incrementAndGet();
                        LOG.info("Updated journal {} ID {}", toUpdate.getJournalName(), toUpdate.getId());
                    } else {
                        numOk.incrementAndGet();
                    }
                } else {
                    if (update) {
                        numUpdated.incrementAndGet();
                    } else {
                        numOk.getAndIncrement();
                    }
                }
            } catch (IOException e) {
                LOG.error("Could not update journal " + j.getJournalName(), e);
                numError.getAndIncrement();
            }
        }
    }
}
