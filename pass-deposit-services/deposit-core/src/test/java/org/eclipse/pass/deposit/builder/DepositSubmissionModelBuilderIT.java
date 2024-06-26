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
package org.eclipse.pass.deposit.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonObject;
import org.eclipse.pass.deposit.AbstractDepositSubmissionIT;
import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.model.JournalPublicationType;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Russ Poetker
 */
public class DepositSubmissionModelBuilderIT extends AbstractDepositSubmissionIT {

    private static final String EXPECTED_JOURNAL_TITLE = "Food & Function";
    private static final String EXPECTED_DOI = "10.1039/c7fo01251a";
    private static final String EXPECTED_EMBARGO_END_DATE = "2018-06-30";
    private static final int EXPECTED_SUBMITER_COUNT = 1;
    private static final int EXPECTED_PI_COUNT = 2;
    private static final int EXPECTED_CO_PI_COUNT = 4;
    private static final int EXPECTED_AUTHOR_COUNT = 6;
    private static final String EXPECTED_NLMTA = "Food Funct";
    private static final String EXPECTED_GRANT1 = "R01EY026617";
    private static final String EXPECTED_GRANT2 = "R01EY026618";
    private static final Map<String, DepositMetadata.IssnPubType> EXPECTED_ISSNS =
        new HashMap<>() {
            {
                put("2042-650X", new DepositMetadata.IssnPubType("2042-650X", JournalPublicationType.OPUB));
                put("2042-6496", new DepositMetadata.IssnPubType("2042-6496", JournalPublicationType.PPUB));
            }
        };

    @Autowired private DepositSubmissionModelBuilder depositSubmissionModelBuilder;

    @Test
    public void testBuildDepositSubmission() throws IOException {
        // GIVEN
        List<PassEntity> entities = new LinkedList<>();
        Submission submissionEntity;
        try (InputStream is = ResourceTestUtil.readSubmissionJson("sample1")) {
            submissionEntity = submissionTestUtil.readSubmissionJsonAndAddToPass(is, entities);
        }

        // WHEN
        DepositSubmission submission = depositSubmissionModelBuilder.build(submissionEntity.getId());

        // THEN
        assertNotNull(submissionEntity);

        // Check that some basic things are in order
        assertNotNull(submission.getManifest());
        assertNotNull(submission.getMetadata());
        assertNotNull(submission.getMetadata().getManuscriptMetadata());
        assertNotNull(submission.getMetadata().getJournalMetadata());
        assertNotNull(submission.getMetadata().getArticleMetadata());
        assertNotNull(submission.getMetadata().getPersons());
        assertNotNull(submission.getSubmissionMeta());

        assertEquals(EXPECTED_DOI, submission.getMetadata().getArticleMetadata().getDoi().toString());

        assertNotNull(submission.getFiles());
        assertEquals(8, submission.getFiles().size());
        assertTrue(submission.getFiles().stream()
            .allMatch(depositFile -> Objects.nonNull(depositFile.getPassFileId())));

        // Confirm that some values were set correctly from the Submission metadata
        DepositMetadata.Journal journalMetadata = submission.getMetadata().getJournalMetadata();
        assertEquals(EXPECTED_JOURNAL_TITLE, journalMetadata.getJournalTitle());

        EXPECTED_ISSNS.values().forEach(expectedIssnPubType -> {
            journalMetadata.getIssnPubTypes().values().stream()
                           .filter(candidate ->
                                       candidate.equals(expectedIssnPubType))
                           .findAny().orElseThrow(() ->
                                                      new RuntimeException(
                                                          "Missing expected IssnPubType " + expectedIssnPubType));
        });
        assertEquals(EXPECTED_ISSNS.size(), journalMetadata.getIssnPubTypes().size());

        assertEquals(EXPECTED_NLMTA, journalMetadata.getJournalId());

        DepositMetadata.Manuscript manuscriptMetadata = submission.getMetadata().getManuscriptMetadata();
        assertNull(manuscriptMetadata.getManuscriptUrl());

        assertEquals(EXPECTED_EMBARGO_END_DATE, submission.getMetadata().getArticleMetadata().getEmbargoLiftDate()
                                                          .format(DateTimeFormatter.ofPattern("uuuu-MM-dd")));

        List<DepositMetadata.Person> persons = submission.getMetadata().getPersons();
        assertEquals(EXPECTED_SUBMITER_COUNT, persons.stream()
                                                     .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.submitter)
                                                     .count());
        assertEquals(EXPECTED_PI_COUNT, persons.stream()
                                               .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.pi).count());
        assertEquals(EXPECTED_CO_PI_COUNT, persons.stream()
                                                  .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.copi)
                                                  .count());
        assertEquals(EXPECTED_AUTHOR_COUNT, persons.stream()
                                                   .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.author)
                                                   .count());

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Tania Marchbank")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Nikki Mandir")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Denis Calnan")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Robert A. Goodlad")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Theo Podas")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Raymond J. Playford")));

        //test the grants associated with the submission
        List<DepositMetadata.Grant> grants = submission.getMetadata().getGrantsMetadata();

        assertNotNull(grants.stream().filter(matchGrant -> matchGrant.getGrantId() == EXPECTED_GRANT1));
        assertNotNull(grants.stream().filter(matchGrant -> matchGrant.getGrantId() == EXPECTED_GRANT2));

        // Read something out of the submission metadata
        assertTrue(submission.getSubmissionMeta().has("agreements"));
        JsonObject agreement = submission.getSubmissionMeta().getAsJsonObject("agreements");
        assertTrue(agreement.has("JScholarship"));
    }
}