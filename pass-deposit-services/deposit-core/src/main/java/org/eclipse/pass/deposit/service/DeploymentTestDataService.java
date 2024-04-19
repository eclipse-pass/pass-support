/*
 * Copyright 2024 Johns Hopkins University
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
import java.time.ZonedDateTime;
import java.util.List;

import org.eclipse.pass.support.client.ModelUtil;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeploymentTestDataService {
    private static final Logger LOG = LoggerFactory.getLogger(DeploymentTestDataService.class);

    static final String PASS_E2E_TEST_GRANT = "PASS_E2E_TEST_GRANT";

    private final PassClient passClient;

    @Value("${pass.test.data.policy.title}")
    private String testPolicyTitle;

    @Value("${pass.test.data.user.email}")
    private String testUserEmail;

    @Autowired
    public DeploymentTestDataService(PassClient passClient) {
        this.passClient = passClient;
    }

    public void processTestData() throws IOException {
        LOG.warn("Deployment Test Data Service running...");
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);
        grantSelector.setFilter(RSQL.equals("projectName", PASS_E2E_TEST_GRANT));
        List<Grant> testGrants = passClient.streamObjects(grantSelector).toList();
        Grant testGrant = testGrants.isEmpty() ? createTestGrantData() : testGrants.get(0);
        deleteTestSubmissions(testGrant);
        LOG.warn("Done running Deployment Test Data Service");
    }

    private void deleteTestSubmissions(Grant testGrant) throws IOException {
        LOG.warn("Deleting Test Submissions");
        ZonedDateTime submissionToDate = ZonedDateTime.now().minusDays(1);
        PassClientSelector<Submission> testSubmissionSelector = new PassClientSelector<>(Submission.class);
        testSubmissionSelector.setFilter(RSQL.and(
            RSQL.equals("grants.id",  testGrant.getId()),
            RSQL.lte("submittedDate", ModelUtil.dateTimeFormatter().format(submissionToDate))
        ));
        testSubmissionSelector.setInclude("publication");
        List<Submission> testSubmissions = passClient.streamObjects(testSubmissionSelector).toList();
        testSubmissions.forEach(testSubmission -> {
            try {
                PassClientSelector<Deposit> testDepositSelector = new PassClientSelector<>(Deposit.class);
                testDepositSelector.setFilter(RSQL.equals("submission.id", testSubmission.getId()));
                testDepositSelector.setInclude("repositoryCopy");
                List<Deposit> testDeposits = passClient.streamObjects(testDepositSelector).toList();
                testDeposits.forEach(testDeposit -> {
                    deleteObject(testDeposit);
                    deleteObject(testDeposit.getRepositoryCopy());
                });
                deleteObject(testSubmission);
                deleteObject(testSubmission.getPublication());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        LOG.warn("Deleted {} Test Submissions", testSubmissions.size());
    }

    private void deleteObject(PassEntity entity) {
        try {
            passClient.deleteObject(entity);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Grant createTestGrantData() throws IOException {
        LOG.warn("Creating Test Grant Data");
        Journal testJournal = new Journal();
        testJournal.setJournalName("PASS_E2E_TEST_JOURNAL");
        testJournal.setIssns(List.of("Print:test-fake"));
        passClient.createObject(testJournal);

        PassClientSelector<Policy> policySelector = new PassClientSelector<>(Policy.class);
        policySelector.setFilter(RSQL.equals("title", testPolicyTitle));
        List<Policy> testPolicies = passClient.streamObjects(policySelector).toList();
        Policy testPolicy = testPolicies.get(0);

        Funder testFunder = new Funder();
        testFunder.setLocalKey("E2E_TEST_FUNDER_LK");
        testFunder.setName("PASS_E2E_TEST_FUNDER");
        testFunder.setPolicy(testPolicy);
        passClient.createObject(testFunder);

        Grant testGrant = new Grant();
        testGrant.setProjectName(PASS_E2E_TEST_GRANT);
        testGrant.setAwardNumber("TEST_E2E_AWD_NUM");
        testGrant.setLocalKey("PASS_E2E_TEST_GRANT_LK");
        testGrant.setAwardDate(ZonedDateTime.parse("2020-02-01T00:00:00Z"));
        testGrant.setStartDate(ZonedDateTime.parse("2020-01-01T00:00:00Z"));
        testGrant.setEndDate(ZonedDateTime.parse("2088-01-01T00:00:00Z"));
        testGrant.setAwardStatus(AwardStatus.ACTIVE);
        testGrant.setDirectFunder(testFunder);
        testGrant.setPrimaryFunder(testFunder);

        PassClientSelector<User> userSelector = new PassClientSelector<>(User.class);
        userSelector.setFilter(RSQL.equals("email", testUserEmail));
        List<User> testUsers = passClient.streamObjects(userSelector).toList();
        User testUser = testUsers.get(0);
        testGrant.setPi(testUser);

        passClient.createObject(testGrant);
        return testGrant;
    }

}
