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

import static org.eclipse.pass.deposit.provider.nihms.NihmsAssembler.APPLICATION_GZIP;
import static org.eclipse.pass.deposit.provider.nihms.NihmsAssembler.SPEC_NIHMS_NATIVE_2017_07;
import static org.eclipse.pass.deposit.util.DepositTestUtil.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.eclipse.pass.deposit.assembler.AbstractAssembler;
import org.eclipse.pass.deposit.assembler.AbstractAssemblerIT;
import org.eclipse.pass.deposit.assembler.PackageOptions.Archive;
import org.eclipse.pass.deposit.assembler.PackageOptions.Compression;
import org.eclipse.pass.deposit.assembler.PackageOptions.Spec;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.model.DepositFile;
import org.eclipse.pass.deposit.model.DepositFileType;
import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.DepositMetadata.Person;
import org.eclipse.pass.deposit.model.JournalPublicationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Creates a package, then extracts it.  Performs some basic tests on the extracted package.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsAssemblerIT extends AbstractAssemblerIT {

    private File manifest;

    private File metadata;

    @BeforeEach
    public void setUp() throws Exception {
        manifest = new File(extractedPackageDir, "manifest.txt");
        metadata = new File(extractedPackageDir, "bulk_meta.xml");
    }

    @Override
    protected Map<String, Object> getOptions() {
        return new HashMap<>() {
            {
                put(Spec.KEY, SPEC_NIHMS_NATIVE_2017_07);
                put(Archive.KEY, Archive.OPTS.TAR);
                put(Compression.KEY, Compression.OPTS.GZIP);
            }
        };
    }

    @Test
    public void testSimple() {
        assertTrue(extractedPackageDir.exists());
    }

    @Override
    protected AbstractAssembler assemblerUnderTest() {
        NihmsPackageProviderFactory packageProviderFactory = new NihmsPackageProviderFactory();
        return new NihmsAssembler(mbf, rbf, packageProviderFactory, passClient);
    }

    @Override
    protected void verifyStreamMetadata(PackageStream.Metadata metadata) {
        assertEquals(Compression.OPTS.GZIP, metadata.compression());
        assertEquals(Archive.OPTS.TAR, metadata.archive());
        assertTrue(metadata.archived());
        assertEquals(SPEC_NIHMS_NATIVE_2017_07, metadata.spec());
        assertEquals(APPLICATION_GZIP, metadata.mimeType());
    }

    /**
     * Insures that each custodial resource is included in the package, and that the required metadata files are there
     *
     * @throws Exception
     */
    @Test
    public void testBasicStructure() {
        assertTrue(manifest.exists());
        assertTrue(metadata.exists());
        assertTrue(submission.getFiles().size() > 0);
        assertEquals(1, submission.getFiles().stream()
            .filter(df -> df.getType() == DepositFileType.manuscript).count());

        Map<String, DepositFileType> custodialResourcesTypeMap = custodialResources.stream()
                                                                                   .collect(Collectors.toMap(
                                                                                       DepositFile::getName,
                                                                                       DepositFile::getType));

        // Each custodial resource is present in the package.  The tested filenames need to be remediated, in case
        // a custodial resource uses a reserved file name.
        custodialResources.forEach(custodialResource -> {
            String filename =
                org.eclipse.pass.deposit.provider.nihms.NihmsPackageProvider.getNonCollidingFilename(
                custodialResource.getName(),
                custodialResource.getType());
            assertTrue(extractedPackageDir.toPath().resolve(filename).toFile().exists());
        });

        Map<String, File> packageFiles = Arrays.stream(extractedPackageDir.listFiles())
                                               .collect(Collectors.toMap((File::getName), Function.identity()));

        // Each file in the package is accounted for as a custodial resource or as a metadata file
        // Remediated resources are detected by their file prefix
        packageFiles.keySet().stream()
                    .filter(fileName -> !fileName.equals(manifest.getName()) && !fileName.equals(metadata.getName()))
                    .forEach(fileName -> {
                        String remediatedFilename =
                            org.eclipse.pass.deposit.provider.nihms.NihmsPackageProvider
                                .getNonCollidingFilename(
                            fileName,
                            custodialResourcesTypeMap.get(fileName));

                        if (!remediatedFilename.startsWith(
                            org.eclipse.pass.deposit.provider.nihms.NihmsPackageProvider
                                .REMEDIATED_FILE_PREFIX)) {
                            assertTrue(custodialResourcesMap.containsKey(remediatedFilename));
                        } else {
                            assertTrue(custodialResourcesMap.containsKey(
                                           remediatedFilename.substring(
                                               org.eclipse.pass.deposit.provider.nihms.NihmsPackageProvider
                                                   .REMEDIATED_FILE_PREFIX.length())));
                        }
                    });

        assertTrue(packageFiles.keySet().contains(manifest.getName()));
        assertTrue(packageFiles.keySet().contains(metadata.getName()));
    }

    /**
     * Insures the manifest structure is sound, and that each file in the manifest references a custodial resource.
     * Insures there is one line in the manifest per custodial resource.
     *
     * @throws Exception
     */
    @Test
    public void testPackageManifest() throws Exception {
        int lineCount = 0;
        LineIterator lines = FileUtils.lineIterator(manifest);
        List<String> entries = new ArrayList<>();
        while (lines.hasNext()) {
            String line = lines.nextLine();
            entries.add(line);
            new ManifestLine(manifest, line, lineCount++).assertAll();
        }
        assertEquals(submission.getFiles().size() + 1, lineCount);

        //check for compliance with the NIHMS Bulk Submission Specification
        //table, figure and supplement file types must have a label
        //labels must be unique within type for all types
        Map<String, Set<String>> labels = new HashMap<>();
        for (DepositFileType fileType : DepositFileType.values()) {
            labels.put(fileType.toString(), new HashSet<>());
        }

        for (String entry : entries) {
            String[] fields = entry.split("\t");
            assertFalse(labels.get(fields[0]).contains(fields[1]));
            if (fields[0].equals("figure") || fields[0].equals("table") || fields[0].equals("supplement")) {
                assertTrue(fields[1].length() > 0);
            }
        }
    }

    @Test
    public void testPackageMetadata() throws Exception {
        Document metaDom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(metadata);
        assertNotNull(metaDom);

        // root element is <nihms-submit>
        Element root = metaDom.getDocumentElement();
        assertEquals("manuscript-submit", root.getTagName());

        // required <manuscript-title> element is present with the manuscript title as the value
        Element title = asList(root.getElementsByTagName("manuscript-title")).get(0);
        assertEquals(submission.getMetadata().getManuscriptMetadata().getTitle(), title.getTextContent());

        // Insure that only one <person> element is present in the submission metadata
        // and insure that the <person> is a PI or a Co-PI for the grant that funded the submission.

        List<Element> personElements = asList(root.getElementsByTagName("person"));
        // Assert that there is only one Person present in the metadata
        assertEquals(1, personElements.size());

        // Map persons from the metadata to Person objects
        List<Person> asPersons = personElements.stream().map(element -> {
            Person asPerson = new Person();
            asPerson.setFirstName(element.getAttribute("fname"));
            asPerson.setLastName(element.getAttribute("lname"));
            asPerson.setMiddleName(element.getAttribute("mname"));
            asPerson.setType(DepositMetadata.PERSON_TYPE.submitter);
            return asPerson;
        }).toList();

        // Insure that the Person in the metadata matches a Person on the Submission, and that the person is a
        // corresponding pi
        asPersons.stream().forEach(person -> {
            assertTrue(submission.getMetadata().getPersons().stream().anyMatch(candidate ->
                   // NIHMS metadata only use
                   // first/last/middle names, so
                   // never compare against the
                   // "full" version
                   candidate.getConstructedName()
                            .equals(person.getName()) &&
                   candidate.getType() == person.getType()));
        });

        // Assert that the DOI is present in the metadata
        assertEquals(submission.getMetadata().getArticleMetadata().getDoi().toString(), root.getAttribute("doi"));

        // Assert that the ISSNs are present in the metadata as the <issn> element
        List<Element> issns = asList(root.getElementsByTagName("issn"));
        Map<String, DepositMetadata.IssnPubType> issnPubTypes =
            submission.getMetadata().getJournalMetadata().getIssnPubTypes();
        assertEquals(issnPubTypes.size(), issns.size());
        assertTrue(issnPubTypes.size() > 0);

        issns.forEach(issn -> assertTrue(issnPubTypes.containsKey(issn.getTextContent())));
        issns.forEach(issn -> {
            DepositMetadata.IssnPubType pubType = issnPubTypes.get(issn.getTextContent());
            if (pubType.pubType == JournalPublicationType.OPUB || pubType.pubType == JournalPublicationType.EPUB) {
                assertEquals(issn.getAttribute("issn-type"), "electronic");
            } else {
                assertEquals(issn.getAttribute("issn-type"), "print");
            }
        });

    }

    /**
     * when commons-io creates an inputstream from a string, it cannot be re-read.
     *
     * @throws IOException
     */
    @Test
    public void rereadIOutilsStringInputStream() throws IOException {
        final String expected = "This is the manifest.";
        InputStream in = IOUtils.toInputStream(expected, "UTF-8");

        assertEquals(expected, IOUtils.toString(in, "UTF-8"));
        assertEquals("", IOUtils.toString(in, "UTF-8"));
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    private static class ManifestLine {
        private static final String ERR = "File %s, line %s is missing %s";
        private File manifestFile;
        private String line;
        private int lineNo;

        private ManifestLine(File manifestFile, String line, int lineNo) {
            this.manifestFile = manifestFile;
            this.line = line;
            this.lineNo = lineNo;
        }

        void assertAll() {
            assertTypeIsPresent();
            assertLabelIsPresent();
            assertFileIsPresent();
            assertNameIsValid();
        }

        void assertTypeIsPresent() {
            String[] parts = line.split("\t");
            assertFalse(isNullOrEmpty(parts[0]));
        }

        void assertLabelIsPresent() {
            String[] parts = line.split("\t");
            assertFalse(isNullOrEmpty(parts[1]));
        }

        void assertFileIsPresent() {
            String[] parts = line.split("\t");
            assertFalse(isNullOrEmpty(parts[2]));
        }

        void assertNameIsValid() {
            assertNotSame(NihmsManifestSerializer.METADATA_ENTRY_NAME, manifestFile.getName());
            assertNotSame(NihmsManifestSerializer.MANIFEST_ENTRY_NAME, manifestFile.getName());
        }
    }

}


