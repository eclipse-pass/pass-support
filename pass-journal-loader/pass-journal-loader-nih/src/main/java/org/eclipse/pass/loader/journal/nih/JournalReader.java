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

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.stream.Stream;

import org.eclipse.pass.support.client.model.Journal;

/**
 * @author apb@jhu.edu
 */
public interface JournalReader {

    /**
     * @param source the InputStream to read journals from
     * @param charset the Charset of the text
     * @return Stream of Journal
     */
    Stream<Journal> readJournals(InputStream source, Charset charset);

    /**
     * @return whether or not PMC participation is known
     */
    boolean hasPmcParticipation();
}
