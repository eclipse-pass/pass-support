/*
 * Copyright 2023 Johns Hopkins University
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

import java.net.URI;
import java.util.Objects;

import jsonapi.Id;
import jsonapi.Resource;
import jsonapi.ToOne;

/**
 * The funder or sponsor of Grant or award.
 *
 * @author Karen Hanson
 */

@Resource(type = "funder")
public class Funder implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * Funder name
     */
    private String name;

    /**
     * Funder URL
     */
    private URI url;

    /**
     * The Policy associated with funder
     */
    @ToOne(name = "policy")
    private Policy policy;

    /**
     * Local key assigned to the funder within the researcher's institution to support matching between
     * PASS and a local system. In the case of JHU this is the key assigned in COEUS
     */
    private String localKey;

    /**
     * Funder constructor
     */
    public Funder() {
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param funder the funder to copy
     */
    public Funder(Funder funder) {
        this.id = funder.id;
        this.name = funder.name;
        this.url = funder.url;
        this.policy = funder.policy;
        this.localKey = funder.localKey;
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier to set
     */
    public Funder(String id) {
        this.id = id;
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
     * @return the url
     */
    public URI getUrl() {
        return url;
    }

    /**
     * @param url the url to set
     */
    public void setUrl(URI url) {
        this.url = url;
    }

    /**
     * @return the the policy
     */
    public Policy getPolicy() {
        return policy;
    }

    /**
     * @param policy the policy to set
     */
    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    /**
     * @return the localKey
     */
    public String getLocalKey() {
        return localKey;
    }

    /**
     * @param localKey the localKey to set
     */
    public void setLocalKey(String localKey) {
        this.localKey = localKey;
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
    public String toString() {
        return "Funder [id=" + id + ", name=" + name + ", url=" + url + ", policy=" + policy + ", localKey=" + localKey
                + "]";
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
        Funder other = (Funder) obj;
        return Objects.equals(id, other.id) && Objects.equals(localKey, other.localKey)
                && Objects.equals(name, other.name) && Objects.equals(policy, other.policy)
                && Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, localKey);
    }
}
