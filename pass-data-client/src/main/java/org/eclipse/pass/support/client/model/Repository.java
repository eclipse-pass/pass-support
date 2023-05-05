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

/**
 * Describes a Repository. A Repository is the target of a Deposit.
 *
 * @author Karen Hanson
 */

@Resource(type = "repository")
public class Repository implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * Name of repository e.g. "PubMed Central"
     */
    private String name;

    /**
     * Several sentence description of repository
     */
    private String description;

    /**
     * URL to the homepage of the repository so that PASS users can view the platform before deciding whether to
     * participate in it
     */
    private URI url;

    /**
     * The legal text that a submitter must agree to in order to submit a publication to this repository
     */
    private String agreementText;

    /**
     * Stringified JSON representing a form template to be loaded by the front-end when this Repository is selected
     */
    private String formSchema;

    /**
     * Type of integration PASS has with the Repository
     */
    private IntegrationType integrationType;

    /**
     * Key that is unique to this {@code Repository} instance.  Used to reference the {@code Repository} when its URI
     * is not available (e.g. prior to the creation of a {@code Repository} resource in Fedora).
     */
    private String repositoryKey;

    /**
     * URLs that link to JSON schema documents describing the repository's metadata requirements
     */
    private List<URI> schemas = new ArrayList<>();

    /**
     * Repository constructor
     */
    public Repository() {
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier to set
     */
    public Repository(String id) {
        this.id = id;
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param repository the repository to copy
     */
    public Repository(Repository repository) {
        this.id = repository.id;
        this.name = repository.name;
        this.description = repository.description;
        this.url = repository.url;
        this.agreementText = repository.agreementText;
        this.formSchema = repository.formSchema;
        this.integrationType = repository.integrationType;
        this.repositoryKey = repository.repositoryKey;
        this.schemas = new ArrayList<>(repository.schemas);
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
     * @return the agreement text
     */
    public String getAgreementText() {
        return agreementText;
    }

    /**
     * @param agreementText the agreement text to set
     */
    public void setAgreementText(String agreementText) {
        this.agreementText = agreementText;
    }

    /**
     * @return the formSchema
     */
    public String getFormSchema() {
        return formSchema;
    }

    /**
     * @param formSchema the form schema (typically, a stringified JSON blob)
     */
    public void setFormSchema(String formSchema) {
        this.formSchema = formSchema;
    }

    /**
     * @return the integrationType
     */
    public IntegrationType getIntegrationType() {
        return integrationType;
    }

    /**
     * @param integrationType the integrationType to set
     */
    public void setIntegrationType(IntegrationType integrationType) {
        this.integrationType = integrationType;
    }

    /**
     * Key that is unique to this {@code Repository} instance.  Used to look up the {@code Repository} when its URI
     * is not available (e.g. prior to the creation of a {@code Repository} resource in Fedora).
     *
     * @return a String unique to this {@code Repository} within PASS, may be {@code null}
     */
    public String getRepositoryKey() {
        return repositoryKey;
    }

    /**
     * Key that is unique to this {@code Repository} instance.  Used to look up the {@code Repository} when its URI
     * is not available (e.g. prior to the creation of a {@code Repository} resource in Fedora).
     *
     * @param repositoryKey a String unique to this {@code Repository} within PASS
     */
    public void setRepositoryKey(String repositoryKey) {
        this.repositoryKey = repositoryKey;
    }

    /**
     * @return URLs that link to JSON schema documents describing the repository's metadata requirements
     */
    public List<URI> getSchemas() {
        return schemas;
    }

    /**
     * @param schemas URLs that link to JSON schema documents describing the repository's metadata requirements
     */
    public void setSchemas(List<URI> schemas) {
        this.schemas = schemas;
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
        Repository other = (Repository) obj;
        return Objects.equals(agreementText, other.agreementText) && Objects.equals(description, other.description)
                && Objects.equals(formSchema, other.formSchema) && Objects.equals(id, other.id)
                && integrationType == other.integrationType && Objects.equals(name, other.name)
                && Objects.equals(repositoryKey, other.repositoryKey) && Objects.equals(schemas, other.schemas)
                && Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Repository [id=" + id + ", name=" + name + ", description=" + description + ", url=" + url
                + ", agreementText=" + agreementText + ", formSchema=" + formSchema + ", integrationType="
                + integrationType + ", repositoryKey=" + repositoryKey + ", schemas=" + schemas + "]";
    }
}
