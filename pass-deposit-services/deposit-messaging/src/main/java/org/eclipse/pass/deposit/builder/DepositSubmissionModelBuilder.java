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
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.springframework.stereotype.Component;

/**
 * @author Ben Trumbore (wbt3@cornell.edu)
 */
@Component
public class DepositSubmissionModelBuilder {

    private final DepositSubmissionMapper depositSubmissionMapper;
    private final PassClient passClient;

    public DepositSubmissionModelBuilder(PassClient passClient, DepositSubmissionMapper depositSubmissionMapper) {
        this.passClient = passClient;
        this.depositSubmissionMapper = depositSubmissionMapper;
    }

    /***
     * Build a DepositSubmission using a Submission from PASS with the ID of submissionId.
     * @param submissionId id of the PASS Submission
     * @return a deposit submission data model
     */
    public DepositSubmission build(String submissionId) throws IOException {
        List<PassEntity> entities = new LinkedList<>();
        Submission submissionEntity = readPassSubmission(submissionId, entities);
        return depositSubmissionMapper.createDepositSubmission(submissionEntity, entities);
    }

    private Submission readPassSubmission(String submissionId, List<PassEntity> entities) throws IOException {

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
        // TODO Deposit service port pending

//        // Add File resources that reference this Submission to the entity list.
//        Map<String, Collection<URI>> incomingLinks = client.getIncoming(submissionUri);
//        Collection<URI> uris = incomingLinks.get(Submission.class.getSimpleName().toLowerCase());
//        if (uris != null) {
//            for (URI uri : uris) {
//                try {
//                    File file = client.readResource(uri, File.class);
//                    entities.put(uri, file);
//                } catch (RuntimeException e) {
//                    // Ignore non-File entities, which throw invalid type exceptions.
//                    boolean tolerate = false;
//                    Throwable cause = e.getCause();
//                    while (cause != null) {
//                        if (cause instanceof InvalidTypeIdException) {
//                            tolerate = true;
//                            break;
//                        }
//                        cause = cause.getCause();
//                    }
//                    if (!tolerate) {
//                        // There was some other kind of exception
//                        throw e;
//                    }
//                }
//            }
//        }
//
//        return submission;
        return submission;
    }

}
