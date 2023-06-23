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

import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.springframework.stereotype.Component;

/**
 * @author Ben Trumbore (wbt3@cornell.edu)
 */
@Component
public class PassModelBuilder extends ModelBuilder {

    private final PassJsonFedoraAdapter passJsonFedoraAdapter;

    public PassModelBuilder(PassJsonFedoraAdapter passJsonFedoraAdapter) {
        this.passJsonFedoraAdapter = passJsonFedoraAdapter;
    }

    /***
     * Build a DepositSubmission using a Submission from PASS with the ID of submissionId.
     * @param submissionId id of the PASS Submission
     * @return a deposit submission data model
     */
    public DepositSubmission build(String submissionId) throws IOException {
        List<PassEntity> entities = new LinkedList<>();
        Submission submissionEntity = passJsonFedoraAdapter.fcrepoToPass(submissionId, entities);
        return createDepositSubmission(submissionEntity, entities);
    }

}
