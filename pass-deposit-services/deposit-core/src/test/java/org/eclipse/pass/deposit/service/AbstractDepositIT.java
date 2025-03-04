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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.deposit.assembler.PackageOptions;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.assembler.PreassembledAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@TestPropertySource(properties = {
    "pass.deposit.repository.configuration=classpath:org/eclipse/pass/deposit/service/DepositTaskIT.json",
    "dspace.user=test-dspace-user",
    "dspace.password=test-dspace-password",
    "dspace.server=localhost:9030",
    "dspace.api.url=http://localhost:9030/dspace/api",
    "dspace.website.url=http://localhost:9030/dspace/website",
    "dspace.collection.handle=collectionhandle"
})
@WireMockTest(httpPort = 9030)
public abstract class AbstractDepositIT extends AbstractSubmissionIT {

    private static final String SPEC = "http://purl.org/net/sword/package/METSDSpaceSIP";
    private static final String PACKAGE_PATH = "/packages/example.zip";
    private static final String CHECKSUM_PATH = PACKAGE_PATH + ".md5";

    @Autowired private PreassembledAssembler assembler;

    /**
     * Mocks up the {@link #assembler} so that it streams back a {@link #PACKAGE_PATH package} conforming to the
     * DSpace METS SIP profile.
     *
     * @throws Exception
     */
    @BeforeEach
    public void setUpSuccess() throws Exception {
        InputStream packageFile = this.getClass().getResourceAsStream(PACKAGE_PATH);
        PackageStream.Checksum checksum = mock(PackageStream.Checksum.class);
        when(checksum.algorithm()).thenReturn(PackageOptions.Checksum.OPTS.MD5);
        when(checksum.asHex()).thenReturn(IOUtils.resourceToString(CHECKSUM_PATH, StandardCharsets.UTF_8));

        assembler.setSpec(SPEC);
        assembler.setPackageStream(packageFile);
        assembler.setPackageName("example.zip");
        assembler.setChecksum(checksum);
        assembler.setPackageLength(33849);
        assembler.setCompression(PackageOptions.Compression.OPTS.ZIP);
        assembler.setArchive(PackageOptions.Archive.OPTS.ZIP);
    }

}
