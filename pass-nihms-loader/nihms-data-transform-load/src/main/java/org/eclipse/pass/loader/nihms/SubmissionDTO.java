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
package org.eclipse.pass.loader.nihms;

import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;

/**
 * Data transfer object to hold the various components of a NIHMS Submission up to the point of
 * update or create.
 *
 * @author Karen Hanson
 */
public class SubmissionDTO {

    private Submission submission = null;

    private Publication publication = null;

    private RepositoryCopy repositoryCopy = null;

    private Deposit deposit = null;

    private boolean updatePublication = false;

    private boolean updateSubmission = false;

    private boolean updateRepositoryCopy = false;

    private boolean updateDeposit = false;

    private String grantId = null;

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

    /**
     * @return the deposit
     */
    public Deposit getDeposit() {
        return deposit;
    }

    /**
     * @param deposit the deposit to set
     */
    public void setDeposit(Deposit deposit) {
        this.deposit = deposit;
    }

    /**
     * @return the grantId
     */
    public String getGrantId() {
        return grantId;
    }

    /**
     * @param grantId the grantId to set
     */
    public void setGrantId(String grantId) {
        this.grantId = grantId;
    }

    /**
     * @return updatePublication true if update should be performed
     */
    public boolean doUpdatePublication() {
        return updatePublication;
    }

    /**
     * @param updatePublication the updatePublication to set
     */
    public void setUpdatePublication(boolean updatePublication) {
        this.updatePublication = updatePublication;
    }

    /**
     * @return updateSubmission true if update should be performed
     */
    public boolean doUpdateSubmission() {
        return updateSubmission;
    }

    /**
     * @param updateSubmission the updateSubmission to set
     */
    public void setUpdateSubmission(boolean updateSubmission) {
        this.updateSubmission = updateSubmission;
    }

    /**
     * @return updateRepositoryCopy true if update should be performed
     */
    public boolean doUpdateRepositoryCopy() {
        return updateRepositoryCopy;
    }

    /**
     * @return updateRepositoryCopy true if update should be performed
     */
    public boolean doUpdate() {
        return (updateRepositoryCopy || updateSubmission || updatePublication || updateDeposit);
    }

    /**
     * @param updateRepositoryCopy the updateRepositoryCopy to set
     */
    public void setUpdateRepositoryCopy(boolean updateRepositoryCopy) {
        this.updateRepositoryCopy = updateRepositoryCopy;
    }

    /**
     * @param updateDeposit the updateDeposit to set
     */
    public void setUpdateDeposoit(boolean updateDeposit) {
        this.updateDeposit = updateDeposit;
    }

    /**
     * @return updateDeposit true if update should be performed
     */
    public boolean doUpdateDeposit() {
        return updateDeposit;
    }
}
