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
package org.eclipse.pass.deposit.config.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.eclipse.pass.deposit.DepositApp;
import org.eclipse.pass.deposit.config.repository.Repositories;
import org.eclipse.pass.deposit.config.repository.RepositoryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource("classpath:test-application.properties")
@TestPropertySource(
    locations = "/test-application.properties",
    properties = {
        "pass.deposit.repository.configuration=classpath:/full-test-repositories.json",
        "inveniordm.api.token=test-invenio-api-token",
        "inveniordm.api.baseUrl=http://localhost:9030/api",
        "dspace.server=localhost:9030",
        "dspace.api.url=http://localhost:9030/dspace/api",
        "dspace.website.url=http://localhost:9030/dspace/website",
        "dspace.collection.handle=collectionhandle"
    })
class RepositoriesFactoryBeanConfigTest {
    @Autowired
    private Repositories repositories;

    @Test
    void testLoadRepositoryConfigurationsSize() {
        assertNotNull(repositories);
        assertEquals(5, repositories.getAllConfigs().size());
    }

    @Test
    void testLoadRepositoryConfigurationJS() {
        RepositoryConfig j10p = repositories.getConfig("JScholarship");
        assertEquals("JScholarship", j10p.getRepositoryKey());
        assertNull(j10p.getTransportConfig().getAuthRealms());
        assertEquals("filesystem", j10p.getTransportConfig().getProtocolBinding().getProtocol());
        assertNull(j10p.getTransportConfig().getProtocolBinding().getServerFqdn());
        assertNull(j10p.getTransportConfig().getProtocolBinding().getServerPort());
        assertEquals("simple", j10p.getAssemblerConfig().getSpec());
        assertEquals("simpleAssembler", j10p.getAssemblerConfig().getBeanName());
        assertEquals("NONE", j10p.getAssemblerConfig().getOptions().getCompression());
        assertEquals("ZIP", j10p.getAssemblerConfig().getOptions().getArchive());
        assertEquals(List.of("sha512"), j10p.getAssemblerConfig().getOptions().getAlgorithms());
        assertEquals(0, j10p.getAssemblerConfig().getOptions().getOptionsMap().size());
        assertEquals("RepositoryConfig{repositoryKey='JScholarship', transportConfig=TransportConfig{" +
            "authRealms=null, protocolBinding=FilesystemBinding{baseDir='target/packages', overwrite='true', " +
            "createIfMissing='true'} ProtocolBinding{protocol='filesystem', serverFqdn='null', serverPort='null'}}, " +
            "assemblerConfig=AssemblerConfig{spec='simple', options=AssemblerOptions{compression='NONE', " +
            "archive='ZIP', algorithms=[sha512]}, beanName='simpleAssembler'}}", j10p.toString());
    }

    @Test
    void testLoadRepositoryConfigurationsPMC() {
        RepositoryConfig pubMed = repositories.getConfig("PubMed Central");
        assertEquals("PubMed Central", pubMed.getRepositoryKey());
        assertNull(pubMed.getTransportConfig().getAuthRealms());
        assertEquals("filesystem", pubMed.getTransportConfig().getProtocolBinding().getProtocol());
        assertNull(pubMed.getTransportConfig().getProtocolBinding().getServerFqdn());
        assertNull(pubMed.getTransportConfig().getProtocolBinding().getServerPort());
        assertEquals("nihms-native-2017-07", pubMed.getAssemblerConfig().getSpec());
        assertEquals("nihmsAssembler", pubMed.getAssemblerConfig().getBeanName());
        assertEquals("GZIP", pubMed.getAssemblerConfig().getOptions().getCompression());
        assertEquals("TAR", pubMed.getAssemblerConfig().getOptions().getArchive());
        assertEquals(List.of("sha512"), pubMed.getAssemblerConfig().getOptions().getAlgorithms());
        assertEquals(35,
            ((Map<?, ?>) pubMed.getAssemblerConfig().getOptions().getOptionsMap().get("funder-mapping")).size());
    }

    @Test
    void testLoadRepositoryConfigurationsDS() {
        RepositoryConfig dSpace = repositories.getConfig("DSpace");
        assertEquals("DSpace", dSpace.getRepositoryKey());
        assertNull(dSpace.getTransportConfig().getAuthRealms());
        assertEquals("DSpace", dSpace.getTransportConfig().getProtocolBinding().getProtocol());
        assertNull(dSpace.getTransportConfig().getProtocolBinding().getServerFqdn());
        assertNull(dSpace.getTransportConfig().getProtocolBinding().getServerPort());
        assertEquals("DSpace", dSpace.getAssemblerConfig().getSpec());
        assertEquals("DSpaceAssembler", dSpace.getAssemblerConfig().getBeanName());
        assertNull(dSpace.getAssemblerConfig().getOptions());
    }
}