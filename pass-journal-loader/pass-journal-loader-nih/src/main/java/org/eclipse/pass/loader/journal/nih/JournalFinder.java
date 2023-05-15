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

import java.util.List;

import org.eclipse.pass.support.client.model.Journal;

/**
 * @author apb@jhu.edu
 */
public interface JournalFinder {
    /**
     * Find the journal best matching the provided data.
     * If the journal has already been processed, return SKIP
     *
     * @param nlmta of journal
     * @param name of journal
     * @param issns of journal
     * @return matching journal id, SKIP, or null
     */
    String find(String nlmta, String name, List<String> issns);

    /**
     * Add a journal to be searched
     *
     * @param j the Journal to add
     */
    void add(Journal j);
}
