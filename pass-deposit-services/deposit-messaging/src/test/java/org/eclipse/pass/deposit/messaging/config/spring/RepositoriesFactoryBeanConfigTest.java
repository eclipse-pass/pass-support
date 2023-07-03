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
package org.eclipse.pass.deposit.messaging.config.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.pass.deposit.messaging.config.repository.FtpBinding;
import org.eclipse.pass.deposit.messaging.config.repository.Repositories;
import org.eclipse.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.eclipse.pass.deposit.messaging.config.repository.SwordV2Binding;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@SpringBootTest(properties = {
    "pass.client.url=http://localhost:8080/",
    "pass.client.user=test",
    "pass.client.password=test",
    "pass.deposit.jobs.disabled=true"
})
public class RepositoriesFactoryBeanConfigTest {
    @Autowired
    private Repositories repositories;

    @Test
    public void foo() throws Exception {
        assertNotNull(repositories);

        assertEquals(4, repositories.keys().size());

        RepositoryConfig j10p = repositories.getConfig("JScholarship");
        assertNotNull(j10p);

        RepositoryConfig pubMed = repositories.getConfig("PubMed Central");
        assertNotNull(pubMed);

        assertEquals("JScholarship", j10p.getRepositoryKey());
        assertEquals("PubMed Central", pubMed.getRepositoryKey());

        assertNotNull(j10p.getTransportConfig());
        assertNotNull(j10p.getTransportConfig().getProtocolBinding());
        assertNotNull(j10p.getAssemblerConfig().getSpec());

        assertNotNull(pubMed.getTransportConfig());
        assertNotNull(pubMed.getTransportConfig().getProtocolBinding());
        assertNotNull(pubMed.getAssemblerConfig().getSpec());
    }
}