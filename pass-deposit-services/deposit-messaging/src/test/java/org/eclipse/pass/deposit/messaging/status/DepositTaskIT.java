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
package org.eclipse.pass.deposit.messaging.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.apache.abdera.i18n.iri.IRI;
import org.apache.abdera.model.Category;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.commons.io.IOUtils;
import org.eclipse.deposit.util.async.Condition;
import org.eclipse.pass.deposit.assembler.PackageOptions;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.assembler.PreassembledAssembler;
import org.eclipse.pass.deposit.messaging.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.messaging.service.AbstractSubmissionIT;
import org.eclipse.pass.deposit.messaging.service.DepositProcessor;
import org.eclipse.pass.deposit.messaging.support.swordv2.ResourceResolverImpl;
import org.eclipse.pass.deposit.transport.sword2.Sword2ClientFactory;
import org.eclipse.pass.deposit.transport.sword2.Sword2Transport;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.swordapp.client.DepositReceipt;
import org.swordapp.client.SWORDClient;
import org.swordapp.client.SWORDCollection;
import org.swordapp.client.SWORDError;
import org.swordapp.client.SWORDWorkspace;
import org.swordapp.client.ServiceDocument;
import org.swordapp.client.SwordIdentifier;

/**
 * This IT insures that the SWORD transport properly handles the Deposit.depositStatusRef field by updating the
 * Deposit.depositStatus field according to the SWORD state document.  It configures Deposit Services with an Assembler
 * that streams a pre-built package (the files actually submitted to Fedora in the Submission are ignored, and not
 * streamed to DSpace).  DSpace is the only concrete implementation of a SWORD server used by Deposit Services, so it is
 * employed here.
 *
 * Note this IT uses a specific runtime configuration for Deposit Services in the classpath resource
 * DepositTaskIT.json.  The status mapping indicates that by default the state of a Deposit will be SUBMITTED unless
 * the package is archived (SUCCESS) or withdrawn (REJECTED).  Now, if an exception occurs when performing the SWORD
 * deposit to DSpace (for example, if the package is corrupt), there will be no SWORD state to examine because the
 * package could not be ingested.  In the case of a corrupt package that is rejected without getting in the front door,
 * there will be no Deposit.depositStatusRef, and Deposit.depositStatus will be FAILED.
 *
 * Note that FAILED is an intermediate status.  This means that remedial action can be taken, and the package can be
 * re-submitted without creating a new Submission.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@TestPropertySource(properties = {
    "pass.deposit.repository.configuration=classpath:org/eclipse/pass/deposit/messaging/status/DepositTaskIT.json",
    "dspace.username=testuser",
    "dspace.password=testuserpassword",
    "dspace.baseuri=http://localhost",
    "dspace.collection.handle=foobartest"
})
// the repository configuration json pollutes the context
public class DepositTaskIT extends AbstractSubmissionIT {

    /**
     * Package specification URI identifying a DSpace SIP with METS metadata
     */
    private static final String SPEC = "http://purl.org/net/sword/package/METSDSpaceSIP";

    /**
     * Pre-built package conforming to the DSpace METS SIP packaging: http://purl.org/net/sword/package/METSDSpaceSIP
     */
    private static final String PACKAGE_PATH = "/packages/example.zip";
    private static final String CHECKSUM_PATH = PACKAGE_PATH + ".md5";

    /**
     * Pre-built package missing a file specified in the METS.xml
     */
    private final ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);

    @Autowired private PreassembledAssembler assembler;
    @Autowired private DepositProcessor depositProcessor;

    @MockBean private Sword2ClientFactory clientFactory;
    @MockBean private ResourceResolverImpl resourceResolver;
    @MockBean private Parser mockParser;

    @SpyBean(name = "errorHandler") private DepositServiceErrorHandler errorHandler;
    @SpyBean private DepositStatusProcessor depositStatusProcessor;
    @SpyBean private Sword2Transport sword2Transport;

    private SWORDClient mockSwordClient;

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

        submissionTestUtil.deleteDepositsInPass();
    }

    /**
     * A submission with a valid package should result in success.
     */
    @Test
    public void testDepositTask() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        submissionTestUtil.resetSubmissionStatuses(submission.getId());
        mockSword();

        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());

        // WHEN
        submissionProcessor.accept(actualSubmission);

        // Wait for the Deposit resource to show up as ACCEPTED (terminal state)
        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() != null);

        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1
            && DepositStatus.SUBMITTED == deposits.iterator().next().getDepositStatus()));

        c.getResult().forEach(deposit -> depositProcessor.accept(deposit));

        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
                                                DepositStatus.ACCEPTED == deposits.iterator().next()
                                                                                          .getDepositStatus()));
        Set<Deposit> deposits = c.getResult();
        Deposit deposit = deposits.iterator().next();

        // Insure a Deposit.depositStatusRef was set on the Deposit resource
        assertNotNull(deposit.getDepositStatusRef());

        // No exceptions should be handled by the error handler
        verifyNoInteractions(errorHandler);

        // Insure the DepositStatusProcessor processed the Deposit.depositStatusRef
        ArgumentCaptor<Deposit> processedDepositCaptor = ArgumentCaptor.forClass(Deposit.class);
        verify(depositStatusProcessor).process(processedDepositCaptor.capture(), any());
        assertEquals(deposit.getId(), processedDepositCaptor.getValue().getId());

        verify(sword2Transport).open(any());
        verify(mockSwordClient).deposit(any(SWORDCollection.class), any(), any());
    }

    @Test
    public void testDepositError() throws Exception {
        Submission submission = findSubmission(createSubmission(
            ResourceTestUtil.readSubmissionJson("sample2")));
        submissionTestUtil.resetSubmissionStatuses(submission.getId());
        mockSword();
        doThrow(new SWORDError(400, "Testing deposit error"))
            .when(mockSwordClient).deposit(any(SWORDCollection.class), any(), any());

        triggerSubmission(submission);
        final Submission actualSubmission = passClient.getObject(Submission.class, submission.getId());

        // WHEN
        submissionProcessor.accept(actualSubmission);

        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
                                                DepositStatus.FAILED == deposits.iterator().next()
                                                                                        .getDepositStatus()));
        Set<Deposit> deposits = c.getResult();
        assertNull(deposits.iterator().next().getDepositStatusRef());

        verify(errorHandler).handleError(throwableCaptor.capture());
        assertTrue(throwableCaptor.getValue().getCause().getMessage().contains("Testing deposit error"));
    }

    private void mockSword() throws Exception {
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
