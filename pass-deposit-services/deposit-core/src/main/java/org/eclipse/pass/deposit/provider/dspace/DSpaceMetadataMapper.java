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
import org.eclipse.pass.deposit.provider.util.CitationUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DSpace metadata fields set:
 * <ul>
 * <li>dc.title for the Manuscript
 * <li>dc.publisher for the publisher name
 * <li>dc.identifier.citation for the Manuscript
 * <li>dc.identifier.doi for the DOI
 * <li>dc.contributor for each non-submitter associated with the Manuscript
 * <li>dc.description.abstract for the Manuscript
 * <li>dc.date.issued for the publication date
 * <li>DSPACE_FIELD_EMBARGO_LIFT   Date that the embargo is lifted
 * <li>DSPACE_FIELD_EMBARGO_TERMS  Date that the embargo is lifted
 * </ul>
 */
@Component
public class DSpaceMetadataMapper {
    // Section of workspace item form to add metadata
    private static final String SECTION = "traditionalpageone";

    private final String dspaceFieldEmbargoLift;
    private final String dspaceFieldEmbargoTerms;

    public DSpaceMetadataMapper(@Value("${dspace.field.embargo.lift}") String dspaceFieldEmbargoLift,
            @Value("${dspace.field.embargo.terms}") String dspaceFieldEmbargoTerms) {
        this.dspaceFieldEmbargoLift = dspaceFieldEmbargoLift;
        this.dspaceFieldEmbargoTerms = dspaceFieldEmbargoTerms;
    }

    public String patchWorkspaceItem(DepositSubmission submission) {
        DepositMetadata depositMd = submission.getMetadata();
        Journal journalMd = depositMd.getJournalMetadata();
        Article articleMd = depositMd.getArticleMetadata();
        Manuscript manuscriptMd = depositMd.getManuscriptMetadata();

        JSONArray metadata = new JSONArray();

        // Required by DSpace
        metadata.add(add_array(SECTION, "dc.title", manuscriptMd.getTitle()));

        if (journalMd != null && journalMd.getPublisherName() != null) {
            metadata.add(add_array(SECTION, "dc.publisher", journalMd.getPublisherName()));
        }

        if (articleMd.getDoi() != null) {
            metadata.add(add_array(SECTION, "dc.identifier.doi", articleMd.getDoi().toString()));
        }

        if (manuscriptMd.getMsAbstract() != null) {
            metadata.add(add_array(SECTION, "dc.description.abstract", manuscriptMd.getMsAbstract()));
        }

        String citation = CitationUtil.createCitation(submission);

        if (!citation.isEmpty()) {
            metadata.add(add_array(SECTION, "dc.identifier.citation", citation));
        }

        // Required by DSpace as ISO 8601 local date
        metadata.add(add_array(SECTION, "dc.date.issued", journalMd.getPublicationDate().
                format(DateTimeFormatter.ISO_LOCAL_DATE)));

        // Add non-submitters as authors
        String[] authors = depositMd.getPersons().stream().filter(
                p -> p.getType() != DepositMetadata.PERSON_TYPE.submitter).
                map(Person::getName).toArray(String[]::new);

        metadata.add(add_array(SECTION, "dc.contributor.author", authors));

        ZonedDateTime embargoLiftDate = articleMd.getEmbargoLiftDate();

        if (embargoLiftDate != null) {
            String liftDate = embargoLiftDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

            metadata.add(add_array(SECTION, dspaceFieldEmbargoLift, liftDate));
            metadata.add(add_array(SECTION, dspaceFieldEmbargoTerms, liftDate));
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
}
