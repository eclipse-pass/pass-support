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
package org.eclipse.pass.support.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jsonapi.Id;
import jsonapi.Resource;
import jsonapi.ToOne;

/**
 * Describes a Journal and the path of it's participation in PubMedCentral
 *
 * @author Karen Hanson
 */

@Resource(type = "journal")
public class Journal implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * Name of journal
     */
    private String journalName;

    /**
     * Array of ISSN(s) for Journal
     */
    private List<String> issns = new ArrayList<>();

    /**
     * The publisher
     */
    @ToOne(name = "publisher")
    private Publisher publisher;

    /**
     * National Library of Medicine Title Abbreviation
     */
    private String nlmta;

    /**
     * This field indicates whether a journal participates in the NIH Public Access Program by sending final
     * published article to PMC. If so, whether it requires additional processing fee.
     */
    private PmcParticipation pmcParticipation;

    /**
     * Journal constructor
     */
    public Journal() {
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier to set
     */
    public Journal(String id) {
        this.id = id;
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param journal the journal to copy
     */
    public Journal(Journal journal) {
        this.id = journal.id;
        this.journalName = journal.journalName;
        this.issns = new ArrayList<String>(journal.issns);
        this.publisher = journal.publisher;
        this.nlmta = journal.nlmta;
        this.pmcParticipation = journal.pmcParticipation;
    }

    /**
     * @return the journalName
     */
    public String getJournalName() {
        return journalName;
    }

    /**
     * @param journalName the journalName to set
     */
    public void setJournalName(String journalName) {
        this.journalName = journalName;
    }

    /**
     * @return the issns
     */
    public List<String> getIssns() {
        return issns;
    }

    /**
     * @param issn the issn list to set
     */
    public void setIssns(List<String> issn) {
        this.issns = issn;
    }

    /**
     * @return the publisher ID
     */
    public Publisher getPublisher() {
        return publisher;
    }

    /**
     * @param publisher the publisher to set
     */
    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }

    /**
     * @return the nlmta
     */
    public String getNlmta() {
        return nlmta;
    }

    /**
     * @param nlmta the nlmta to set
     */
    public void setNlmta(String nlmta) {
        this.nlmta = nlmta;
    }

    /**
     * @return the pmcParticipation
     */
    public PmcParticipation getPmcParticipation() {
        return pmcParticipation;
    }

    /**
     * @param pmcParticipation the pmcParticipation to set
     */
    public void setPmcParticipation(PmcParticipation pmcParticipation) {
        this.pmcParticipation = pmcParticipation;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Journal other = (Journal) obj;
        return Objects.equals(id, other.id) && Objects.equals(issns, other.issns)
                && Objects.equals(journalName, other.journalName) && Objects.equals(nlmta, other.nlmta)
                && pmcParticipation == other.pmcParticipation && Objects.equals(publisher, other.publisher);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, journalName);
    }

    @Override
    public String toString() {
        return "Journal [id=" + id + ", journalName=" + journalName + ", issns=" + issns + ", publisher=" + publisher
                + ", nlmta=" + nlmta + ", pmcParticipation=" + pmcParticipation + "]";
    }
}
