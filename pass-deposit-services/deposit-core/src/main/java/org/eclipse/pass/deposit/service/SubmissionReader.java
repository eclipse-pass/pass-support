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
package org.eclipse.pass.deposit.service;

import java.io.IOException;
import java.util.List;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.File;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.springframework.stereotype.Service;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Service
public class SubmissionReader {

    private final PassClient passClient;

    public SubmissionReader(PassClient passClient) {
        this.passClient = passClient;
    }

    public Submission readPassSubmission(String submissionId, List<PassEntity> entities) throws IOException {

        Submission submission = passClient.getObject(Submission.class, submissionId, "publication",
            "repositories", "submitter", "preparers", "grants", "effectivePolicies");

        List<Grant> populatedGrants = submission.getGrants().stream()
            .map(grant -> {
                try {
                    return passClient.getObject(grant, "primaryFunder", "directFunder", "pi", "coPis");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        submission.setGrants(populatedGrants);

        PassClientSelector<File> fileSelector = new PassClientSelector<>(File.class);
        fileSelector.setFilter(RSQL.equals("submission.id", submission.getId()));
        PassClientResult<File> resultFile = passClient.selectObjects(fileSelector);
        entities.addAll(resultFile.getObjects());
        return submission;
    }
}
