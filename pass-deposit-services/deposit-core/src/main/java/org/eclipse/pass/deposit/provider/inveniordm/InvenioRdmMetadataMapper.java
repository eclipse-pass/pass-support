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
package org.eclipse.pass.deposit.provider.inveniordm;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.DepositSubmission;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class InvenioRdmMetadataMapper {

    /**
     * Maps DepositSubmission to InvenioRDM metadata as JSON.
     * @param depositSubmission the source DepositSubmission
     * @return a JSONObject representing InvenioRDM metadata
     */
    public JSONObject toInvenioMetadata(DepositSubmission depositSubmission) {
        /* TODO
        This is a first attempt at creating the metadata for the InvenioRDM record.
        https://inveniordm.docs.cern.ch/reference/metadata/#metadata
        The following points should be considered in a future iteration if required:
        - See if we can get the Person ORCID value and set it
        - Use the embargo element if supported in PASS
        - Set funder element
        - Figure out the details of resource_type.id.  We may have to provide a way to set a list of possible values.
            - https://inveniordm.docs.cern.ch/customize/vocabularies/resource_types/
         */
        final DepositMetadata depositMetadata = depositSubmission.getMetadata();
        JSONObject invenioMetadata = new JSONObject();
        JSONArray creators = mapCreators(depositMetadata);
        invenioMetadata.put("creators", creators);
        String title = depositSubmission.getSubmissionMeta().get("title").getAsString();
        invenioMetadata.put("title", title);

        invenioMetadata.put("publication_date", depositMetadata.getJournalMetadata().
                getPublicationDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        JSONObject resourceType = new JSONObject();
        resourceType.put("id", "publication-article");
        invenioMetadata.put("resource_type", resourceType);
        JSONObject rootInvenioMetadata = new JSONObject();
        rootInvenioMetadata.put("metadata", invenioMetadata);
        return rootInvenioMetadata;
    }

    private JSONArray mapCreators(DepositMetadata depositMetadata) {
        JSONArray creators = new JSONArray();
        depositMetadata.getPersons().forEach(person -> {
            JSONObject invenioPerson = new JSONObject();
            invenioPerson.put("name", person.getReversedName());
            String givenName = Objects.nonNull(person.getMiddleName())
                ? person.getFirstName() + " " + person.getMiddleName()
                : person.getFirstName();
            invenioPerson.put("given_name", givenName);
            invenioPerson.put("family_name", person.getLastName());
            invenioPerson.put("type", "personal");
            JSONObject personOrOrg = new JSONObject();
            personOrOrg.put("person_or_org", invenioPerson);
            creators.add(personOrOrg);
        });
        return creators;
    }
}
