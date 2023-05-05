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

import java.util.Objects;

import jsonapi.Id;
import jsonapi.Resource;
import jsonapi.ToOne;

/**
 * A Submission can have multiple Deposits, each to a different Repository. This describes a single deposit to a
 * Repository and captures
 * its current status.
 *
 * @author Karen Hanson
 */

@Resource(type = "deposit")
public class Deposit implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * A URL or some kind of reference that can be dereferenced, entity body parsed, and used to determine the status
     * of Deposit
     */
    private String depositStatusRef;

    /**
     * Status of deposit
     */
    private DepositStatus depositStatus;

    /**
     * The Submission that this Deposit is a part of
     */
    @ToOne(name = "submission")
    private Submission submission;

    /**
     * The Repository being deposited to
     */
    @ToOne(name = "repository")
    private Repository repository;

    /**
     * The Repository Copy representing the copy that is reltaed to this Deposit. The value is null if there
     * is no copy
     */
    @ToOne(name = "repositoryCopy")
    private RepositoryCopy repositoryCopy;

    /**
     * Deposit constructor
     */
    public Deposit() {
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier
     */
    public Deposit(String id) {
        this.id = id;
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param deposit the deposit to copy
     */
    public Deposit(Deposit deposit) {
        this.id = deposit.id;
        this.depositStatusRef = deposit.depositStatusRef;
        this.depositStatus = deposit.depositStatus;
        this.submission = deposit.submission;
        this.repository = deposit.repository;
        this.repositoryCopy = deposit.repositoryCopy;
    }

    /**
     * @return the deposit status
     */
    public DepositStatus getDepositStatus() {
        return depositStatus;
    }

    /**
     * @param depositStatus status the deposit status to set
     */
    public void setDepositStatus(DepositStatus depositStatus) {
        this.depositStatus = depositStatus;
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

    /**
     * @return the depositStatusRef
     */
    public String getDepositStatusRef() {
        return depositStatusRef;
    }

    /**
     * @param depositStatusRef the depositStatusRef to set
     */
    public void setDepositStatusRef(String depositStatusRef) {
        this.depositStatusRef = depositStatusRef;
    }

    /**
     * @return the submission
     */
    public Submission getSubmission() {
        return submission;
    }

    /**
     * @param submission the submission to set
     */
    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    /**
     * @return the repositoryCopy
     */
    public RepositoryCopy getRepositoryCopy() {
        return repositoryCopy;
    }

    /**
     * @param repositoryCopy the repositoryCopy to set
     */
    public void setRepositoryCopy(RepositoryCopy repositoryCopy) {
        this.repositoryCopy = repositoryCopy;
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
        Deposit other = (Deposit) obj;
        return depositStatus == other.depositStatus && Objects.equals(depositStatusRef, other.depositStatusRef)
                && Objects.equals(id, other.id) && Objects.equals(repository, other.repository)
                && Objects.equals(repositoryCopy, other.repositoryCopy) && Objects.equals(submission, other.submission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, depositStatus, depositStatusRef);
    }

    @Override
    public String toString() {
        return "Deposit [id=" + id + ", depositStatusRef=" + depositStatusRef + ", depositStatus=" + depositStatus
                + ", submission=" + submission + ", repository=" + repository + ", repositoryCopy=" + repositoryCopy
                + "]";
    }
}
