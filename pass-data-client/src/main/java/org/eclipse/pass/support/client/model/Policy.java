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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jsonapi.Id;
import jsonapi.Resource;
import jsonapi.ToMany;

/**
 * Describes a Policy. Policies determine the rules that need to be followed by a Submission.
 *
 * @author Karen Hanson
 */

@Resource(type = "policy")
public class Policy implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * Title of policy e.g. "NIH Public Access Policy"
     */
    private String title;

    /**
     * Several sentence description of policy
     */
    private String description;

    /**
     * A link to the actual policy on the policy-owner's page
     */
    private URI policyUrl;

    /**
     * List of repositories that can satisfying this policy
     */
    @ToMany(name = "repositories")
    private List<Repository> repositories = new ArrayList<>();

    /**
     * the Institution whose Policy this is (note: if institution has a value, funder should be null)
     */
    private URI institution;

    /**
     * Policy constructor
     */
    public Policy() {
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier to set
     */
    public Policy(String id) {
        this.id = id;
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param policy the policy to copy
     */
    public Policy(Policy policy) {
        this.id = policy.id;
        this.title = policy.title;
        this.description = policy.description;
        this.policyUrl = policy.policyUrl;
        this.repositories = new ArrayList<Repository>(policy.repositories);
        this.institution = policy.institution;
    }

    /**
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * @param title the title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the policy URL
     */
    public URI getPolicyUrl() {
        return policyUrl;
    }

    /**
     * @param policyUrl the policyUrl to set
     */
    public void setPolicyUrl(URI policyUrl) {
        this.policyUrl = policyUrl;
    }

    /**
     * @return the institution
     */
    public URI getInstitution() {
        return institution;
    }

    /**
     * @param institution the institution to set
     */
    public void setInstitution(URI institution) {
        this.institution = institution;
    }

    /**
     * @return the list of repositories
     */
    public List<Repository> getRepositories() {
        return repositories;
    }

    /**
     * @param repositories list repositories to set
     */
    public void setRepositories(List<Repository> repositories) {
        this.repositories = repositories == null ? new ArrayList<>() : repositories;
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
        Policy other = (Policy) obj;
        return Objects.equals(description, other.description) && Objects.equals(id, other.id)
                && Objects.equals(institution, other.institution) && Objects.equals(policyUrl, other.policyUrl)
                && Objects.equals(repositories, other.repositories) && Objects.equals(title, other.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title);
    }

    @Override
    public String toString() {
        return "Policy [id=" + id + ", title=" + title + ", description=" + description + ", policyUrl=" + policyUrl
                + ", repositories=" + repositories + ", institution=" + institution + "]";
    }
}
