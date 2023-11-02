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

package org.eclipse.pass.deposit.provider.nihms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.eclipse.pass.deposit.assembler.Extension;
import org.eclipse.pass.deposit.assembler.MetadataBuilder;
import org.eclipse.pass.deposit.assembler.PackageOptions.Archive;
import org.eclipse.pass.deposit.assembler.PackageOptions.Compression;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NihmsAssemblerTest {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsAssemblerTest.class);

    private DepositSubmission submission;

    private PackageStream.Metadata metadata;

    private MetadataBuilder mdBuilder;

    ArgumentCaptor<String> packageNameCaptor;

    @BeforeEach
    public void setUp() throws Exception {
        submission = mock(DepositSubmission.class);
        metadata = mock(PackageStream.Metadata.class);
        mdBuilder = mock(MetadataBuilder.class);

        String expectedSubmissionUuid = UUID.randomUUID().toString();
        when(submission.getId()).thenReturn(expectedSubmissionUuid);

        when(mdBuilder.build()).thenReturn(metadata);

        when(metadata.archived()).thenReturn(true);
        when(metadata.compressed()).thenReturn(true);
        packageNameCaptor = ArgumentCaptor.forClass(String.class);
    }

    @Test
    public void packageNameForTarGz() {
        when(metadata.archive()).thenReturn(Archive.OPTS.TAR);
        when(metadata.compression()).thenReturn(Compression.OPTS.GZIP);

        NihmsAssembler.namePackage(submission, mdBuilder);

        verify(mdBuilder).name(packageNameCaptor.capture());

        validatePackageName(packageNameCaptor.getValue());
    }

    @Test
    public void packageNameForZip() {
        when(metadata.archive()).thenReturn(Archive.OPTS.ZIP);
        when(metadata.compression()).thenReturn(Compression.OPTS.ZIP);

        NihmsAssembler.namePackage(submission, mdBuilder);

        verify(mdBuilder).name(packageNameCaptor.capture());

        validatePackageName(packageNameCaptor.getValue());
    }

    private static void validatePackageName(String packageName) {
        LOG.debug("Validating NIHMS package name: '{}'", packageName);
        assertNotNull(packageName);

        String[] nameParts = packageName.split("\\.");

        // reverse through the parts, verifying the extensions first
        for (int i = nameParts.length - 1; i > -1; i--) {
            try {
                Extension.parseExt(nameParts[i]);
                continue;
            } catch (Exception e) {
                // ignore
            }

            // Verify there are no 'periods' in the name of the file after processing the extensions
            assertEquals(0, i);
        }
    }
}