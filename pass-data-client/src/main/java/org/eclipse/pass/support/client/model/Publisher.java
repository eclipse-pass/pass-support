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

import java.util.Objects;

import jsonapi.Id;
import jsonapi.Resource;

/**
 * Describes a Publisher and its related Journals, also the path of it's participation in PubMedCentral
 *
 * @author Karen Hanson
 */

@Resource(type = "publisher")
public class Publisher implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * Name of publisher
     */
    private String name;

    /**
     * This field indicates whether a journal participates in the NIH Public Access Program by sending final
     * published article to PMC. If so, whether it requires additional processing fee.
     */
    private PmcParticipation pmcParticipation;

    /**
     * Publisher constructor
     */
    public Publisher() {
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier to set
     */
    public Publisher(String id) {
        this.id = id;
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param publisher the publisher to copy
     */
    public Publisher(Publisher publisher) {
        this.id = publisher.id;
        this.name = publisher.name;
        this.pmcParticipation = publisher.pmcParticipation;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
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
        Publisher other = (Publisher) obj;
        return Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && pmcParticipation == other.pmcParticipation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Publisher [id=" + id + ", name=" + name + ", pmcParticipation=" + pmcParticipation + "]";
    }
}
