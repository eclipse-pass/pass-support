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

package org.eclipse.pass.deposit.builder;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.pass.deposit.builder.InvalidModel;
import org.eclipse.pass.deposit.builder.SubmissionBuilder;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.springframework.stereotype.Component;

/**
 * Builds a submission from a file on a locally mounted filesystem.
 * The file contains JSON data representing PassEntity objects that have unique IDs and link to each other.
 * The file must contain a single Submission object, which is the root of the data tree for a deposit.
 *
 * @author Ben Trumbore (wbt3@cornell.edu)
 */
@Component
public class FcrepoModelBuilder extends ModelBuilder implements SubmissionBuilder {

    private final PassJsonFedoraAdapter passJsonFedoraAdapter;

    public FcrepoModelBuilder(PassJsonFedoraAdapter passJsonFedoraAdapter) {
        this.passJsonFedoraAdapter = passJsonFedoraAdapter;
    }

    /***
     * Build a DepositSubmission from the JSON data in named file.
     * @param formDataUrl url to the local file containing the JSON data
     * @return a deposit submission data model
     * @throws InvalidModel if the JSON data cannot be successfully parsed into a valid submission model
     */
    @Override
    public DepositSubmission build(String submissionId) throws InvalidModel, IOException {
        List<PassEntity> entities = new LinkedList<>();
        Submission submissionEntity = passJsonFedoraAdapter.fcrepoToPass(submissionId, entities);
        return createDepositSubmission(submissionEntity, entities);
    }

}
