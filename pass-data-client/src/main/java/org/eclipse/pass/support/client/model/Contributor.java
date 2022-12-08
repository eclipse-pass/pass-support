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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jsonapi.Id;
import jsonapi.Resource;
import jsonapi.ToOne;

/**
 * A Contributor is a person who contributed to a Publication. The contributor
 * model captures the person information as well as the roles they played in
 * creating the publication (e.g. author).
 *
 * @author Karen Hanson
 */

@Resource(type = "contributor")
public class Contributor implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * First name(s) of person
     */
    private String firstName;

    /**
     * Middle name(s) of person
     */
    private String middleName;

    /**
     * Last name(s) of person
     */
    private String lastName;

    /**
     * Name for display. Separate names may not be available, but a person should
     * always at least have a display name.
     */
    private String displayName;

    /**
     * Contact email for person
     */
    private String email;

    /**
     * ORCID ID for person
     */
    private String orcidId;

    /**
     * Affiliation string for person. Where Person is embedded in Submission or
     * Grant, this is the affiliation relevant to that item
     */
    private Set<String> affiliation = new HashSet<>();

    /**
     * One or more roles that this Contributor performed for the associated
     * Publication
     */
    private List<ContributorRole> roles = new ArrayList<ContributorRole>();

    /**
     * The publication that this contributor is associated with
     */
    @ToOne(name = "publication")
    private Publication publication;

    /**
     * The user that represents the same person as this Contributor, where
     * relevant
     */
    @ToOne(name = "user")
    private User user;

    /**
     * Contributor constructor
     */
    public Contributor() {
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier
     */
    public Contributor(String id) {
        this.id = id;
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param contributor the contributor to copy
     */
    public Contributor(Contributor contributor) {
        this.id = contributor.id;
        this.firstName = contributor.firstName;
        this.middleName = contributor.middleName;
        this.lastName = contributor.lastName;
        this.displayName = contributor.displayName;
        this.email = contributor.email;
        this.orcidId = contributor.orcidId;
        this.affiliation = contributor.affiliation;
        this.roles = new ArrayList<ContributorRole>(contributor.roles);
        this.publication = contributor.publication;
        this.user = contributor.user;
    }

    /**
     * @return the firstName
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * @param firstName the firstName to set
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * @return the middleName
     */
    public String getMiddleName() {
        return middleName;
    }

    /**
     * @param middleName the middleName to set
     */
    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    /**
     * @return the lastName
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * @param lastName the lastName to set
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName the displayName to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the email
     */
    public String getEmail() {
        return email;
    }

    /**
     * @param email the email to set
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * @return the affiliation
     */
    public Set<String> getAffiliation() {
        return affiliation;
    }

    /**
     * @param affiliation the affiliation to set
     */
    public void setAffiliation(Set<String> affiliation) {
        this.affiliation = affiliation;
    }

    /**
     * @return the orcidId
     */
    public String getOrcidId() {
        return orcidId;
    }

    /**
     * @param orcidId the orcidId to set
     */
    public void setOrcidId(String orcidId) {
        this.orcidId = orcidId;
    }

    /**
     * @return the list of roles
     */
    public List<ContributorRole> getRoles() {
        return roles;
    }

    /**
     * @param roles the roles list to set
     */
    public void setRoles(List<ContributorRole> roles) {
        this.roles = roles;
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
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
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
    public int hashCode() {
        return Objects.hash(id, email);
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
        Contributor other = (Contributor) obj;
        return Objects.equals(affiliation, other.affiliation) && Objects.equals(displayName, other.displayName)
                && Objects.equals(email, other.email) && Objects.equals(firstName, other.firstName)
                && Objects.equals(id, other.id) && Objects.equals(lastName, other.lastName)
                && Objects.equals(middleName, other.middleName) && Objects.equals(orcidId, other.orcidId)
                && Objects.equals(publication, other.publication) && Objects.equals(roles, other.roles)
                && Objects.equals(user, other.user);
    }

    @Override
    public String toString() {
        return "Contributor [id=" + id + ", firstName=" + firstName + ", middleName=" + middleName + ", lastName="
                + lastName + ", displayName=" + displayName + ", email=" + email + ", orcidId=" + orcidId
                + ", affiliation=" + affiliation + ", roles=" + roles + ", publication=" + publication + ", user="
                + user + "]";
    }
}
