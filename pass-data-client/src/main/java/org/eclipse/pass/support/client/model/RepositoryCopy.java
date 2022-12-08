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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jsonapi.Id;
import jsonapi.Resource;
import jsonapi.ToOne;

/**
 * A Repository Copy represents a copy of a Publication that exists in a target Repository.
 *
 * @author Karen Hanson
 */

@Resource(type = "repositoryCopy")
public class RepositoryCopy implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * IDs assigned by the repository
     */
    private List<String> externalIds = new ArrayList<String>();

    /**
     * Status of deposit
     */
    private CopyStatus copyStatus;

    /**
     * URL to access the item in the repository
     */
    private URI accessUrl;

    /**
     * the Publication that this Repository Copy is a copy of
     */
    @ToOne(name = "publication")
    private Publication publication;

    /**
     * the Repository the Copy is in
     */
    @ToOne(name = "repository")
    private Repository repository;

    /**
     * RepositoryCopy constructor
     */
    public RepositoryCopy() {
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier to set
     */
    public RepositoryCopy(String id) {
        this.id = id;
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param repositoryCopy the repositoryCopy to copy
     */
    public RepositoryCopy(RepositoryCopy repositoryCopy) {
        this.id = repositoryCopy.id;
        this.externalIds = new ArrayList<String>(repositoryCopy.externalIds);
        this.copyStatus = repositoryCopy.copyStatus;
        this.accessUrl = repositoryCopy.accessUrl;
        this.publication = repositoryCopy.publication;
        this.repository = repositoryCopy.repository;
    }

    /**
     * @return the externalIds
     */
    public List<String> getExternalIds() {
        return externalIds;
    }

    /**
     * @param externalIds the externalIds to set
     */
    public void setExternalIds(List<String> externalIds) {
        this.externalIds = externalIds;
    }

    /**
     * @return the repository copy status
     */
    public CopyStatus getCopyStatus() {
        return copyStatus;
    }

    /**
     * @return the accessUrl
     */
    public URI getAccessUrl() {
        return accessUrl;
    }

    /**
     * @param accessUrl the accessUrl to set
     */
    public void setAccessUrl(URI accessUrl) {
        this.accessUrl = accessUrl;
    }

    /**
     * @param copyStatus The repository's status to set
     */
    public void setCopyStatus(CopyStatus copyStatus) {
        this.copyStatus = copyStatus;
    }

    /**
     * @return the publication
     */
    public Publication getPublication() {
        return publication;
    }

    /**
     * @param publication the publication to set
     */
    public void setPublication(Publication publication) {
        this.publication = publication;
    }

    /**
     * @return the repository
     */
    public Repository getRepository() {
        return repository;
    }

    /**
     * @param repository the repository to set
     */
    public void setRepository(Repository repository) {
        this.repository = repository;
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
        RepositoryCopy other = (RepositoryCopy) obj;
        return Objects.equals(accessUrl, other.accessUrl) && copyStatus == other.copyStatus
                && Objects.equals(externalIds, other.externalIds) && Objects.equals(id, other.id)
                && Objects.equals(publication, other.publication) && Objects.equals(repository, other.repository);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accessUrl);
    }

    @Override
    public String toString() {
        return "RepositoryCopy [id=" + id + ", externalIds=" + externalIds + ", copyStatus=" + copyStatus
                + ", accessUrl=" + accessUrl + ", publication=" + publication + ", repository=" + repository + "]";
    }
}
