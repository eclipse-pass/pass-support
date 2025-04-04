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

package org.eclipse.pass.deposit.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.deposit.util.spring.EncodingClassPathResource;
import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.model.DepositFile;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.support.client.PassClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

/**
 * Russ Poetker (rpoetke1@jh.edu)
 */
class AbstractAssemblerTest {

    private static Stream<Arguments> provideSanitizeFilename() {
        return Stream.of(
            Arguments.of("foo", "foo"),
            Arguments.of("f.o.o", "f.o.o"),
            Arguments.of("foo" + '\u00F6', "foo%C3%B6"),
            Arguments.of("../foo", "..%2Ffoo"),
            Arguments.of("f o o", "f%20o%20o"),
            Arguments.of("foo-", "foo-"),
            Arguments.of("fo-o", "fo-o"),
            Arguments.of("f_oo", "f_oo"),
            Arguments.of("_foo_", "_foo_")
        );
    }

    private static Stream<Arguments> provideFileLocations() {
        return Stream.of(
            Arguments.of("file:///test_deposit_file", UrlResource.class, null),
            Arguments.of("http://test_deposit_file", UrlResource.class, null),
            Arguments.of("https://test_deposit_file", UrlResource.class, null),
            Arguments.of("jar:file:/test_deposit_file.jar!/test.xml", UrlResource.class, null),
            Arguments.of("classpath:/test_deposit_file", ClassPathResource.class, null),
            Arguments.of("classpath*:/test_deposit_file", ClassPathResource.class, null),
            Arguments.of("encodedclasspath:/test_deposit_file", EncodingClassPathResource.class, null),
            Arguments.of("/test_deposit_file", FileSystemResource.class, null),
            Arguments.of("\\test_deposit_file", FileSystemResource.class, null),
            Arguments.of("pass_test_file", PassFileResource.class, "test_id")
        );
    }

    @ParameterizedTest
    @MethodSource("provideSanitizeFilename")
    void testSanitize(String testString, String expectedResult) {
        // GIVEN WHEN THEN
        assertEquals(expectedResult, AbstractAssembler.sanitizeFilename(testString));
        assertThrows(IllegalArgumentException.class,
            () -> AbstractAssembler.sanitizeFilename(null));
        assertThrows(IllegalArgumentException.class,
            () -> AbstractAssembler.sanitizeFilename(""));
    }

    @ParameterizedTest
    @MethodSource("provideFileLocations")
    void testResolveCustodialResources(String fileLocation, Class<? extends Resource> expectedResourceClass,
                                       String expectedPassFileId) {
        // GIVEN
        Assembler assembler = getAssembler();
        DepositSubmission submission = new DepositSubmission();
        submission.setName("test_submission");
        List<DepositFile> depositFiles = new ArrayList<>();
        submission.setFiles(depositFiles);
        DepositFile depositFile = new DepositFile();
        depositFile.setLocation(fileLocation);
        depositFile.setPassFileId(expectedPassFileId);
        depositFiles.add(depositFile);

        // WHEN
        PackageStream packageStream = assembler.assemble(submission, new HashMap<>());

        // THEN
        assertNotNull(packageStream);
        DepositFileResource depositFileResource = packageStream.getCustodialContent().get(0);
        assertNotNull(depositFileResource);
        assertEquals(expectedResourceClass, depositFileResource.getResource().getClass());
    }

    @Test
    void testResolveCustodialResourcesUnknownResource() {
        // GIVEN
        Assembler assembler = getAssembler();
        DepositSubmission submission = new DepositSubmission();
        submission.setName("test_submission");
        List<DepositFile> depositFiles = new ArrayList<>();
        submission.setFiles(depositFiles);
        DepositFile depositFile = new DepositFile();
        depositFile.setLocation("invalid_resource");
        depositFiles.add(depositFile);
        Map<String, Object> options = new HashMap<>();

        // WHEN
        DepositServiceRuntimeException exception =
            assertThrows(DepositServiceRuntimeException.class, () -> assembler.assemble(submission, options));

        // THEN
        assertEquals("Unable to resolve the location of a submitted file ('invalid_resource') to " +
            "a Spring Resource type.", exception.getMessage());
    }

    @Test
    void testResolveCustodialResourcesInvalidUrl() {
        // GIVEN
        Assembler assembler = getAssembler();
        DepositSubmission submission = new DepositSubmission();
        submission.setName("test_submission");
        List<DepositFile> depositFiles = new ArrayList<>();
        submission.setFiles(depositFiles);
        DepositFile depositFile = new DepositFile();
        depositFile.setLocation("https://test:^^^/");
        depositFiles.add(depositFile);
        Map<String, Object> options = new HashMap<>();

        // WHEN
        DepositServiceRuntimeException exception =
            assertThrows(DepositServiceRuntimeException.class, () -> assembler.assemble(submission, options));

        // THEN
        assertEquals("Invalid resource URL: https://test:^^^/", exception.getMessage());
    }

    private Assembler getAssembler() {
        MetadataBuilderFactory mbfMock = new DefaultMetadataBuilderFactory();
        ResourceBuilderFactory rbfMock = mock(ResourceBuilderFactory.class);
        PassClient passClientMock = mock(PassClient.class);
        return new AbstractAssembler(mbfMock, rbfMock, passClientMock) {
            @Override
            protected PackageStream createPackageStream(DepositSubmission submission,
                                                        List<DepositFileResource> custodialResources,
                                                        MetadataBuilder mdb, ResourceBuilderFactory rbf,
                                                        Map<String, Object> options) {
                return new SimplePackageStream(submission, custodialResources, mdb);
            }
        };
    }

}