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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Journal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzes journals in our repository in order to match incoming journals against our existing journals
 *
 * @author apb@jhu.edu
 */
public class BatchJournalFinder implements JournalFinder {
    private static final Logger LOG = LoggerFactory.getLogger(BatchJournalFinder.class);

    private final Map<String, Set<String>> issnMap = new HashMap<>();
    private final Map<String, Set<String>> nlmtaMap = new HashMap<>();
    private final Map<String, Set<String>> nameMap = new HashMap<>();
    private final Set<String> foundUris = new HashSet<>();

    private void load(PassClient client) throws IOException {
        LOG.info("Loading existing journals from PASS");

        AtomicInteger count = new AtomicInteger(0);

        PassClientSelector<Journal> sel = new PassClientSelector<>(Journal.class);
        client.streamObjects(sel).forEach(j -> {

            count.incrementAndGet();

            j.getIssns().forEach(issn -> {
                update(issnMap, issn, j.getId());
            });

            update(nlmtaMap, j.getNlmta(), j.getId());
            update(nameMap, j.getJournalName(), j.getId());
        });

        LOG.info("Loaded " + count.get() + " existing journals");
    }

    private static void update(Map<String, Set<String>> map, String key, String value) {
        Set<String> set = map.get(key);

        if (set == null) {
            set = new HashSet<>();
            map.put(key, set);
        }

        set.add(value);
    }

    /**
     * @param client the PassClient to use
     * @throws IOException on error
     */
    public BatchJournalFinder(PassClient client) throws IOException {

        LOG.info("Analyzing journals in PASS");

        load(client);

        LOG.info("Found {} existing ISSNs", issnMap.size());
        LOG.info("Found {} existing NLMTAs", nlmtaMap.size());
        LOG.info("Found {} existing NAMES", nameMap.size());
    }

    /**
     * @param nlmta the NLMTA supplied in out incoming journal data
     * @param name  the journal name
     * @param issns the list of issns
     * @return the URI string of the matching journal if found, null if nothing is found
     * or a directive to SKIP processing on this journal if the matching journal
     * has already been processed
     */
    @Override
    public synchronized String find(String nlmta, String name, List<String> issns) {
        Set<String> nlmtaUriSet = getIdsByNlmta(nlmta);
        Set<String> nameUriSet = getIdsByName(name);

        Map<String, Integer> uriScores = new HashMap<>();

        if (!issns.isEmpty()) {
            for (String issn : issns) {
                Set<String> issnList = getIdsByIssn(issn);
                if (issnList != null) {
                    for (String uri : issnList) {
                        Integer i = uriScores.putIfAbsent(uri, 1);
                        if (i != null) {
                            uriScores.put(uri, i + 1);
                        }
                    }
                }
            }
        }

        if (nlmtaUriSet != null) {
            for (String uri : nlmtaUriSet) {
                Integer i = uriScores.putIfAbsent(uri, 1);
                if (i != null) {
                    uriScores.put(uri, i + 1);
                }
            }
        }

        if (nameUriSet != null) {
            for (String uri : nameUriSet) {
                Integer i = uriScores.putIfAbsent(uri, 1);
                if (i != null) {
                    uriScores.put(uri, i + 1);
                }
            }
        }

        if (uriScores.size() > 0) { //we have a possible uri - find out if it is matchy enough
            Integer highScore = Collections.max(uriScores.values());
            int minimumQualifyingScore = 2;
            List<String> sortedUris = new ArrayList<>();

            for (int i = highScore; i >= minimumQualifyingScore; i--) {
                for (String uri : uriScores.keySet()) {
                    if (uriScores.get(uri) == i) {
                        sortedUris.add(uri);
                    }
                }
            }

            if (sortedUris.size() > 0) { // there are matching journals - decide if we have matched already
                String foundUri = null;
                for (String candidate : sortedUris) {
                    if (!foundUris.contains(candidate)) {
                        foundUri = candidate;
                        break;
                    }
                }
                if (foundUri != null) {
                    foundUris.add(foundUri);
                    return foundUri;
                } else { //this journal has been processed already
                    return "SKIP";
                }
            }
        }

        //nothing matches, create a new journal
        return null;
    }

    private synchronized Set<String> getIdsByIssn(String issn) {
        if (issnMap.containsKey(issn)) {
            return issnMap.get(issn);
        }

        String[] parts = issn.split(":");

        if (parts.length == 2) {
            return issnMap.get(parts[1]);
        }

        return null;
    }

    private synchronized Set<String> getIdsByNlmta(String nlmta) {
        if (nlmta != null && nlmta.length() > 0 && nlmtaMap.containsKey(nlmta)) {
            return nlmtaMap.get(nlmta);
        }

        return null;
    }

    private synchronized Set<String> getIdsByName(String name) {
        if (name != null && name.length() > 0 && nameMap.containsKey(name)) {
            return nameMap.get(name);
        }

        return null;
    }

    @Override
    public synchronized void add(Journal j) {

        String uri = j.getId().toString();

        String nlmta = j.getNlmta();
        if (nlmta != null && nlmta.length() > 0) {
            LOG.debug("Adding nlmta " + nlmta);
            if (!nlmtaMap.containsKey(nlmta)) {
                nlmtaMap.put(nlmta, new HashSet<>());
            }
            nlmtaMap.get(nlmta).add(uri);
        }

        for (final String issn : j.getIssns()) {
            LOG.debug("Adding issn " + issn);
            if (!issnMap.containsKey(issn)) {
                issnMap.put(issn, new HashSet<>());
            }
            issnMap.get(issn).add(uri);
        }

        String name = j.getJournalName();
        if (name != null && name.length() > 0) {
            LOG.debug("Adding name " + name);
            if (!nameMap.containsKey(j.getJournalName())) {
                nameMap.put(name, new HashSet<>());
            }
            nameMap.get(name).add(uri);
        }

        foundUris.add(uri);
    }
}
