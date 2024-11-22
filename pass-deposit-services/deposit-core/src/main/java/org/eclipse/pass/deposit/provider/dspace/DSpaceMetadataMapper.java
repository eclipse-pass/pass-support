/*
 * Copyright 2024 Johns Hopkins University
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
package org.eclipse.pass.deposit.provider.dspace;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.DepositMetadata.Article;
import org.eclipse.pass.deposit.model.DepositMetadata.Journal;
import org.eclipse.pass.deposit.model.DepositMetadata.Manuscript;
import org.eclipse.pass.deposit.model.DepositMetadata.Person;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * dc.title for the Manuscript
 * dc.publisher for the publisher name
 * dc.identifier.citation for the Manuscript
 * dc.identifier.doi for the DOI
 * dc.contributor for each non-submitter associated with the Manuscript
 * dc.description.abstract for the Manuscript
 * dc.date.issued for the publication date
 * DSPACE_FIELD_EMBARGO_LIFT   Date that the embargo is lifted
 * DSPACE_FIELD_EMBARGO_TERMS  Date that the embargo is lifted
 */
@Component
public class DSpaceMetadataMapper {
    @Value("${dspace.field.embargo.lift}")
    private String dspaceFieldEmbargoLift;

    @Value("${dspace.field.embargo.terms}")
    private String dspaceFieldEmbargoTerms;

    public String patchWorkspaceItem(DepositSubmission submission) {
        DepositMetadata depositMd = submission.getMetadata();
        Journal journalMd = depositMd.getJournalMetadata();
        Article articleMd = depositMd.getArticleMetadata();
        Manuscript manuscriptMd = depositMd.getManuscriptMetadata();

        JSONArray metadata = new JSONArray();

        String title = manuscriptMd.getTitle();

        // Required by DSpace
        metadata.add(add_array("traditionalpageone", "dc.title", title));

        if (journalMd != null && journalMd.getPublisherName() != null) {
            metadata.add(add_array("traditionalpageone", "dc.publisher", journalMd.getPublisherName()));
        }

        if (articleMd.getDoi() != null) {
            metadata.add(add_array("traditionalpageone", "dc.identifier.doi", articleMd.getDoi().toString()));
        }

        if (manuscriptMd.getMsAbstract() != null) {
            metadata.add(add_array("traditionalpageone", "dc.description.abstract", manuscriptMd.getMsAbstract()));
        }

        String citation = createCitation(submission);

        if (!citation.isEmpty()) {
            metadata.add(add_array("traditionalpageone", "dc.identifier.citation", citation));
        }

        // TODO: Must format YYYY-MM-DD
        // Required by DSpace
        String publicationDate = journalMd.getPublicationDate();
        metadata.add(add_array("traditionalpageone", "dc.date.issued", publicationDate));

        // Add non-submitters as authors
        // TODO This is different from before
        String[] authors = depositMd.getPersons().stream().filter(
                p -> p.getType() != DepositMetadata.PERSON_TYPE.submitter).
                map(Person::getName).toArray(String[]::new);

        metadata.add(add_array("traditionalpageone", "dc.contributor.author", authors));

        ZonedDateTime embargoLiftDate = articleMd.getEmbargoLiftDate();

        if (embargoLiftDate != null) {
            String liftDate = embargoLiftDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

            metadata.add(add_array("traditionalpageone", dspaceFieldEmbargoLift, liftDate));
            metadata.add(add_array("traditionalpageone", dspaceFieldEmbargoTerms, liftDate));
        }

        // Required by DSpace
        metadata.add(add("license", "granted", "true"));

        return metadata.toString();
    }

    private JSONObject add(String section, String key, String value) {
        JSONObject op = new JSONObject();

        op.put("op", "add");
        op.put("path", "/sections/" + section + "/" + key);
        op.put("value", value);

        return op;
    }

    private JSONObject add_array(String section, String key, String... values) {
        JSONObject op = new JSONObject();

        op.put("op", "add");
        op.put("path", "/sections/" + section + "/" + key);

        JSONArray op_value = new JSONArray();
        for (String value : values) {
            op_value.add(array_value(value));
        }

        op.put("value", op_value);

        return op;
    }

    private JSONObject array_value(String value) {
        JSONObject obj = new JSONObject();
        obj.put("value", value);
        return obj;
    }

    // TODO Could we use citation in CrossRef metadata?
    private String createCitation(DepositSubmission submission) {
        DepositMetadata submissionMd = submission.getMetadata();
        DepositMetadata.Article articleMd = submissionMd.getArticleMetadata();
        DepositMetadata.Journal journalMd = submissionMd.getJournalMetadata();

        StringBuilder citationBldr = new StringBuilder();

        int authorIndex = 0;
        for (Person p: submissionMd.getPersons()) {
            if (p.getType() == DepositMetadata.PERSON_TYPE.author) {
                // Citation: For author 0, add name.  For authorIndex 1 and 2, add comma then name.
                // For author 3, add comma and "et al".  For later authorIndex, do nothing.
                if (authorIndex == 0) {
                    citationBldr.append(p.getReversedName());
                } else if (authorIndex <= 2) {
                    citationBldr.append(", " + p.getReversedName());
                } else if (authorIndex == 3) {
                    citationBldr.append(", et al");
                }
                authorIndex++;
            }
        }

        // Add period at end of author list in citation

        if (citationBldr.length() > 0) {
            citationBldr.append(".");
        }

        // Attach a <dc:identifier:citation> if not empty
        // publication date - after a single space, in parens, followed by "."
        if (journalMd != null && journalMd.getPublicationDate() != null && !journalMd.getPublicationDate().isEmpty()) {
            citationBldr.append(" (" + journalMd.getPublicationDate() + ").");
        }
        // article title - after single space, in double quotes with "." inside
        if (articleMd != null && articleMd.getTitle() != null && !articleMd.getTitle().isEmpty()) {
            citationBldr.append(" \"" + articleMd.getTitle() + ".\"");
        }
        // journal title - after single space, followed by "."
        if (journalMd != null && journalMd.getJournalTitle() != null && !journalMd.getJournalTitle().isEmpty()) {
            citationBldr.append(" " + journalMd.getJournalTitle() + ".");
        }
        // volume - after single space
        if (articleMd != null && articleMd.getVolume() != null && !articleMd.getVolume().isEmpty()) {
            citationBldr.append(" " + articleMd.getVolume());
        }
        // issue - after single space, inside parens, followed by "."
        if (articleMd != null && articleMd.getIssue() != null && !articleMd.getIssue().isEmpty()) {
            citationBldr.append(" (" + articleMd.getIssue() + ").");
        }
        // DOI - after single space, followed by "."
        if (articleMd != null && articleMd.getDoi() != null) {
            citationBldr.append(" " + articleMd.getDoi().toString() + ".");
        }

        return citationBldr.toString();
    }
}
