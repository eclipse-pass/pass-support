/*
 * Copyright 2019 Johns Hopkins University
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
import java.util.stream.Stream;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.SubmissionStatusService;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Recalculates the {@code Submission.submissionStatus} for a collection of Submission URIs.
 * <p>
 * The calculation of {@code Submission.submissionStatus} is handled by the {@link SubmissionStatusService}.  Any
 * {@code Submission} with a {@code submissionStatus} that is <em>not </em> {@code COMPLETE} or {@code CANCELLED} make
 * up the collection of Submission URIs to be processed.
 * </p>
 * <p>
 * The criteria for determining which Submissions need to have their {@code submissionStatus} updated is hard-coded and
 * limited in part by the {@link PassClient}.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class SubmissionStatusUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionStatusUpdater.class);

    private final SubmissionStatusService statusService;
    private final PassClient passClient;

    @Autowired
    public SubmissionStatusUpdater(SubmissionStatusService statusService, PassClient passClient) {
        this.statusService = statusService;
        this.passClient = passClient;
    }

    /**
     * Determines the Submissions to be updated, and updates the status of each in turn.
     * @throws IOException io exception
     */
    public void doUpdate() throws IOException {
        PassClientSelector<Submission> sel = new PassClientSelector<>(Submission.class);
        sel.setFilter(
            RSQL.and(
                RSQL.in("submissionStatus", getSubmissionStatusFilter()),
                RSQL.equals("submitted", "true")
            )
        );
        List<Submission> submissions = passClient.streamObjects(sel).toList();
        LOG.warn("Submission Count for updating: " + submissions.size());

        submissions.forEach(submission -> {
            try {
                LOG.info("Processing Submission.submissionStatus for {}", submission.getId());
                SubmissionStatus newStatus = statusService.calculateSubmissionStatus(submission);
                if (newStatus != submission.getSubmissionStatus()) {
                    LOG.info("Status changed for Submission {} from {} to {}", submission.getId(),
                        submission.getSubmissionStatus(), newStatus);
                    submission.setSubmissionStatus(newStatus);
                    passClient.updateObject(submission);
                }
            } catch (Exception e) {
                LOG.warn("Unable to update the 'submissionStatus' of {}", submission.getId(), e);
            }
        });
    }

    private String[] getSubmissionStatusFilter() {
        return Stream.of(SubmissionStatus.values())
            .filter(status -> status != SubmissionStatus.COMPLETE)
            .filter(status -> status != SubmissionStatus.CANCELLED)
            .map(SubmissionStatus::getValue).toArray(String[]::new);
    }

}
