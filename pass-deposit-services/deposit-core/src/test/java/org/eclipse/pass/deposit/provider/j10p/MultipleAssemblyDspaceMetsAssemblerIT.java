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
package org.eclipse.pass.deposit.provider.j10p;

import static org.eclipse.pass.deposit.provider.j10p.DspaceDepositTestUtil.getMetsXml;
import static org.eclipse.pass.deposit.util.DepositTestUtil.packageFile;

import java.io.File;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.pass.deposit.assembler.Assembler;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.util.DepositTestUtil;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.test.annotation.DirtiesContext;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class MultipleAssemblyDspaceMetsAssemblerIT extends AbstractDspaceMetsAssemblerIT {

    /**
     * Re-use the same assembler instance across tests.  This is to demonstrate that the collaborating objects,
     * including the DspaceMetsDomWriter, do not maintain state across invocations of {@link
     * Assembler#assemble(DepositSubmission, java.util.Map)}
     */
    private static DspaceMetsAssembler underTest;

    /**
     * Creates an instance of DsspaceMetsAssembler that is shared across test method invocations.
     * See {@link #assemblePackage(String, TestInfo)}.
     */
    @BeforeAll
    public static void initAssembler() {
        DspaceMetadataDomWriterFactory metsWriterFactory = new DspaceMetadataDomWriterFactory(
            DocumentBuilderFactory.newInstance());
        DspaceMetsPackageProviderFactory packageProviderFactory = new DspaceMetsPackageProviderFactory(
            metsWriterFactory);
        underTest = new DspaceMetsAssembler(metadataBuilderFactory(), resourceBuilderFactory(),
            packageProviderFactory, null);
    }

    @AfterEach
    void cleanUp() {
        PASS_CORE_CONTAINER.stop();
        PASS_CORE_CONTAINER.start();
    }

    @Override
    protected boolean shouldSetUpBaseSubmission() {
        return false;
    }

    /**
     * Mocks a submission, and invokes the assembler to create a package based on the resources under the
     * {@code sample1/} resource path.  Sets the {@link #extractedPackageDir} to the base directory of the newly created
     * and extracted package.
     */
    private void assemblePackage(String submissionName, TestInfo testInfo) throws Exception {
        mbf = metadataBuilderFactory();
        rbf = resourceBuilderFactory();

        InputStream jsonInputStream = ResourceTestUtil.readSubmissionJson(submissionName);
        prepareSubmission(jsonInputStream);
        prepareCustodialResources();

        // Both tests in this IT will execute assemble(...) on the same instance of DspaceMetsAssembler because the
        // field is static
        PackageStream stream = underTest.assemble(submission, getOptions());

        String testMethodName = testInfo.getTestMethod().get().getName();
        File packageArchive = DepositTestUtil.savePackage(packageFile(this.getClass(), testMethodName,
            stream.metadata()), stream);

        verifyStreamMetadata(stream.metadata());

        extractPackage(packageArchive, stream.metadata().archive(), stream.metadata().compression());
    }

    @DirtiesContext
    @Test
    public void assembleSample1(TestInfo testInfo) throws Exception {
        assemblePackage("sample1", testInfo);
        verifyPackageStructure(getMetsXml(extractedPackageDir), extractedPackageDir, custodialResources);
    }

    @DirtiesContext
    @Test
    public void assembleSample2(TestInfo testInfo) throws Exception {
        assemblePackage("sample2", testInfo);
        verifyPackageStructure(getMetsXml(extractedPackageDir), extractedPackageDir, custodialResources);
    }

}
