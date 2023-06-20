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
package org.eclipse.pass.deposit.messaging.service;

import static org.mockito.Mockito.mock;

import java.net.URI;

import org.eclipse.pass.deposit.builder.SubmissionBuilder;
import org.eclipse.pass.deposit.messaging.config.repository.Repositories;
import org.eclipse.pass.deposit.messaging.model.Packager;
import org.eclipse.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.JmsMessagePolicy;
import org.eclipse.pass.deposit.messaging.policy.Policy;
import org.eclipse.pass.deposit.messaging.policy.SubmissionPolicy;
import org.eclipse.pass.deposit.messaging.status.DepositStatusMapper;
import org.eclipse.pass.deposit.messaging.status.DepositStatusResolver;
import org.eclipse.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.junit.Before;
import org.springframework.core.task.TaskExecutor;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractSubmissionProcessorTest {

    PassClient passClient;

    SubmissionBuilder submissionBuilder;

    Registry<Packager> packagerRegistry;

    SubmissionPolicy submissionPolicy;

    Policy<DepositStatus> intermediateDepositStatusPolicy;

    Policy<DepositStatus> terminalDepositStatusPolicy;

    JmsMessagePolicy messagePolicy;

    TaskExecutor taskExecutor;

    DepositStatusMapper<SwordDspaceDepositStatus> dspaceStatusMapper;

    DepositStatusResolver<URI, SwordDspaceDepositStatus> atomStatusParser;

    CriticalRepositoryInteraction cri;

    DepositTaskHelper depositTaskHelper;

    Repositories repositories;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        passClient = mock(PassClient.class);
        submissionBuilder = mock(SubmissionBuilder.class);
        packagerRegistry = mock(Registry.class);
        submissionPolicy = mock(SubmissionPolicy.class);
        intermediateDepositStatusPolicy = mock(Policy.class);
        messagePolicy = mock(JmsMessagePolicy.class);
        taskExecutor = mock(TaskExecutor.class);
        dspaceStatusMapper = mock(DepositStatusMapper.class);
        atomStatusParser = mock(DepositStatusResolver.class);
        cri = mock(CriticalRepositoryInteraction.class);
        terminalDepositStatusPolicy = mock(Policy.class);
        repositories = mock(Repositories.class);
        depositTaskHelper = new DepositTaskHelper(passClient, taskExecutor, intermediateDepositStatusPolicy,
                                                  terminalDepositStatusPolicy, cri, repositories);
    }

}
