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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.abdera.i18n.iri.IRI;
import org.apache.abdera.model.Category;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.deposit.assembler.PackageOptions;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.assembler.PreassembledAssembler;
import org.eclipse.pass.deposit.support.swordv2.ResourceResolverImpl;
import org.eclipse.pass.deposit.transport.sword2.Sword2ClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.swordapp.client.DepositReceipt;
import org.swordapp.client.SWORDClient;
import org.swordapp.client.SWORDCollection;
import org.swordapp.client.SWORDWorkspace;
import org.swordapp.client.ServiceDocument;
import org.swordapp.client.SwordIdentifier;

@TestPropertySource(properties = {
    "pass.deposit.repository.configuration=classpath:org/eclipse/pass/deposit/messaging/status/DepositTaskIT.json",
    "dspace.username=testuser",
    "dspace.password=testuserpassword",
    "dspace.baseuri=http://localhost",
    "dspace.collection.handle=foobartest"
})
/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractDepositIT extends AbstractSubmissionIT {

    private static final String SPEC = "http://purl.org/net/sword/package/METSDSpaceSIP";
    private static final String PACKAGE_PATH = "/packages/example.zip";
    private static final String CHECKSUM_PATH = PACKAGE_PATH + ".md5";

    @Autowired private PreassembledAssembler assembler;
    @MockBean private Sword2ClientFactory clientFactory;
    @MockBean private ResourceResolverImpl resourceResolver;
    @MockBean private Parser mockParser;

    protected SWORDClient mockSwordClient;

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

        mockSwordClient = mock(SWORDClient.class);
        when(clientFactory.newInstance(any())).thenReturn(mockSwordClient);

    }

    protected void mockSword() throws Exception {
        ServiceDocument mockServiceDoc = mock(ServiceDocument.class);
        SWORDWorkspace mockSwordWorkspace = mock(SWORDWorkspace.class);
        SWORDCollection mockSwordCollection = mock(SWORDCollection.class);
        when(mockSwordCollection.getHref()).thenReturn(mock(IRI.class));
        when(mockSwordCollection.getHref().toString())
            .thenReturn("http://localhost/swordv2/collection/foobartest");
        when(mockSwordWorkspace.getCollections()).thenReturn(List.of(mockSwordCollection));
        when(mockServiceDoc.getWorkspaces()).thenReturn(List.of(mockSwordWorkspace));
        doReturn(mockServiceDoc).when(mockSwordClient).getServiceDocument(any(), any());

        DepositReceipt mockReceipt = mock(DepositReceipt.class);
        when(mockReceipt.getStatusCode()).thenReturn(200);
        when(mockReceipt.getSplashPageLink()).thenReturn(mock(SwordIdentifier.class));
        when(mockReceipt.getSplashPageLink().getHref()).thenReturn("http://foobarsplashlink");
        when(mockReceipt.getAtomStatementLink()).thenReturn(mock(SwordIdentifier.class));
        when(mockReceipt.getAtomStatementLink().getIRI()).thenReturn(mock(IRI.class));
        when(mockReceipt.getAtomStatementLink().getIRI().toURI()).thenReturn(mock(URI.class));
        when(mockReceipt.getAtomStatementLink().getIRI().toURI().toString())
            .thenReturn("http://localhost/swordv2");
        doReturn(mockReceipt).when(mockSwordClient).deposit(any(SWORDCollection.class), any(), any());

        Resource mockResource = mock(Resource.class);
        when(mockResource.getInputStream()).thenReturn(mock(InputStream.class));
        doReturn(mockResource).when(resourceResolver).resolve(any(), any());

        Document mockParserDoc = mock(Document.class);
        when(mockParserDoc.getRoot()).thenReturn(mock(Feed.class));
        Category category = mock(Category.class);
        when(category.getTerm()).thenReturn("http://dspace.org/state/archived");
        when(((Feed) mockParserDoc.getRoot()).getCategories(any())).thenReturn(List.of(category));
        doReturn(mockParserDoc).when(mockParser).parse(any(InputStream.class));
    }

}
