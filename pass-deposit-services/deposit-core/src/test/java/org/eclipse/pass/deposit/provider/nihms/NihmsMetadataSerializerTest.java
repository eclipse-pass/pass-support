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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static java.util.stream.StreamSupport.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;

import com.github.jknack.handlebars.internal.Files;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.deposit.assembler.SizedStream;
import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.JournalPublicationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlunit.validation.Languages;
import org.xmlunit.validation.ValidationResult;
import org.xmlunit.validation.Validator;

/**
 * This is a test for the metadata serializer. for now we just validate against the bulk submission dtd
 *
 * @author Jim Martino (jrm@jhu.edu)
 */
public class NihmsMetadataSerializerTest {

    @TempDir
    private Path tempDir;

    private static NihmsMetadataSerializer underTest;
    private static HashMap<String, Object> packageOptions;
    private static final DepositMetadata metadata = new DepositMetadata();

    @BeforeEach
    public void setup() throws Exception {
        //set up metadata snd its fields;
        DepositMetadata.Journal journal = new DepositMetadata.Journal();
        DepositMetadata.Manuscript manuscript = new DepositMetadata.Manuscript();
        DepositMetadata.Article article = new DepositMetadata.Article();
        List<DepositMetadata.Person> personList = new ArrayList<>();
        List<DepositMetadata.Grant> grantList = new ArrayList<>();

        //populate journal metadata
        journal.setJournalId("FJ001");
        journal.setJournalTitle("Dairy Cow Monthly");
        journal.setIssnPubTypes(new HashMap<String, DepositMetadata.IssnPubType>() {
            {
                put("8765-4321", new DepositMetadata.IssnPubType("8765-4321", JournalPublicationType.EPUB));
                // OPUB publication type should be expressed as an EPUB in the resulting metadata per the NIHMS
                // bulk submission metadata requirements
                put("1234-5678", new DepositMetadata.IssnPubType("1234-5678", JournalPublicationType.OPUB));
                put("0000-0000", new DepositMetadata.IssnPubType("0000-0000", JournalPublicationType.PPUB));
            }
        });

        //populate manuscript metadata
        manuscript.setManuscriptUrl(new URL("http://farm.com/Cows"));
        manuscript.setNihmsId("00001");
        manuscript.setPublisherPdf(true);
        manuscript.setShowPublisherPdf(false);
        manuscript.setTitle("Manuscript Title");

        // populate article metadata
        article.setDoi(URI.create("10.1234/smh0000001"));
        article.setEmbargoLiftDate(ZonedDateTime.now().plusYears(100));

        //populate persons
        DepositMetadata.Person person1 = new DepositMetadata.Person();
        person1.setType(DepositMetadata.PERSON_TYPE.author);
        person1.setEmail("person@farm.com");
        person1.setFirstName("Bessie");
        person1.setLastName("Cow");
        person1.setMiddleName("The");
        personList.add(person1);

        // Enter the first person twice, as both an author and a PI
        DepositMetadata.Person person1a = new DepositMetadata.Person(person1);
        person1a.setType(DepositMetadata.PERSON_TYPE.pi);
        personList.add(person1a);

        DepositMetadata.Person person2 = new DepositMetadata.Person();
        person2.setType(DepositMetadata.PERSON_TYPE.submitter);
        person2.setEmail("person@farm.com");
        person2.setFirstName("Elsie");
        person2.setLastName("Cow");
        person2.setMiddleName("The");
        personList.add(person2);

        DepositMetadata.Person person3 = new DepositMetadata.Person();
        person3.setType(DepositMetadata.PERSON_TYPE.author);
        person3.setEmail("person@farm.com");
        person3.setFirstName("Mark");
        person3.setLastName("Cow");
        person3.setMiddleName("The");
        personList.add(person3);

        DepositMetadata.Person person4 = new DepositMetadata.Person();
        person4.setType(DepositMetadata.PERSON_TYPE.copi);
        person4.setEmail("person@farm.com");
        person4.setFirstName("John");
        person4.setLastName("Cow");
        person4.setMiddleName("The");
        personList.add(person4);

        DepositMetadata.Grant grant1 = new DepositMetadata.Grant();
        grant1.setGrantId("R0123456789");
        grant1.setFunder("FOGARTY INTERNATIONAL CENTER");
        grant1.setFunderLocalKey("johnshopkins.edu:funder:300484");
        grant1.setGrantPi(person1);
        grantList.add(grant1);

        DepositMetadata.Grant grant2 = new DepositMetadata.Grant();
        grant2.setGrantId("R0123456000");
        grant2.setFunder("CENTERS FOR DISEASE CONTROL");
        grant2.setFunderLocalKey("johnshopkins.edu:funder:300293");
        grant2.setGrantPi(person2);
        grantList.add(grant2);

        DepositMetadata.Grant grant3 = new DepositMetadata.Grant();
        grant2.setGrantId("R0123456897");
        grant2.setFunder("MYRIAD GENETICS INC");
        grant2.setFunderLocalKey("johnshopkins.edu:funder:301885");
        grant2.setGrantPi(person2);
        grantList.add(grant2);

        metadata.setJournalMetadata(journal);
        metadata.setManuscriptMetadata(manuscript);
        metadata.setPersons(personList);
        metadata.setGrantsMetadata(grantList);

        Map<String, String> funderMapping =
                Map.of("johnshopkins.edu:funder:300293", "cdc",
                        "johnshopkins.edu:funder:300484", "nih");
        packageOptions = new HashMap<>();
        packageOptions.put(NihmsPackageProvider.FUNDER_MAPPING, funderMapping);

        underTest = new NihmsMetadataSerializer(metadata, packageOptions);
    }

    @Test
    public void testSerializedMetadataValidity() throws Exception {
        SizedStream sizedStream = underTest.serialize();
        byte[] buffer = new byte[sizedStream.getInputStream().available()];
        sizedStream.getInputStream().read(buffer);
        sizedStream.getInputStream().close();

        java.io.File targetFile = tempDir.resolve("MetadataSerializerTest.xml").toFile();

        OutputStream os = new FileOutputStream(targetFile);

        os.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n".getBytes());
        os.write("<!DOCTYPE manuscript-submit SYSTEM \"bulksubmission.dtd\">\n".getBytes());
        os.write(buffer);
        os.close();

        Validator v = Validator.forLanguage(Languages.XML_DTD_NS_URI);
        StreamSource dtd = new StreamSource(getClass().getResourceAsStream("bulksubmission.dtd"));
        dtd.setSystemId(getClass().getResource("bulksubmission.dtd").toURI().toString());
        v.setSchemaSource(dtd);
        StreamSource s = new StreamSource(targetFile);
        ValidationResult r = v.validateInstance(s);

        if (!r.isValid()) {
            System.err.println(Files.read(targetFile, UTF_8));

            r.getProblems().forEach(p -> {
                System.err.println(p);
            });
        }

        assertTrue(r.isValid());
    }

    @Test
    // DOI must be in "raw" format (no leading http://domain/ or https://domain/)
    public void testSerializedMetadataDoi() throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        String path = "10.1234/smh0000001";
        SizedStream sizedStream;
        InputStream is;
        Node node;
        String doi;

        metadata.getArticleMetadata().setDoi(URI.create(path));
        sizedStream = underTest.serialize();
        is = sizedStream.getInputStream();
        node = builder.parse(is).getDocumentElement();
        doi = node.getAttributes().getNamedItem("doi").getTextContent();
        is.close();
        assertTrue(doi.contentEquals(path));

        metadata.getArticleMetadata().setDoi(URI.create("http://dx.doi.org/" + path));
        sizedStream = underTest.serialize();
        is = sizedStream.getInputStream();
        node = builder.parse(is).getDocumentElement();
        doi = node.getAttributes().getNamedItem("doi").getTextContent();
        is.close();
        assertTrue(doi.contentEquals(path));
    }

    /**
     * Test that the number of grants and their associated information is valid when serialized
    */
    @Test
    public void testSerializedMetaDataGrants() throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        SizedStream sizedStream;
        InputStream is;

        List<String> validAwardNumbers = new ArrayList<>(List.of("R0123456789", "R0123456000"));
        List<String> validFunders = new ArrayList<>(List.of("nih"));
        List<String> validPiFname = new ArrayList<>(List.of("Bessie", "Elsie"));
        List<String> validPiLname = new ArrayList<>(List.of("Cow"));
        List<String> validPiEmail = new ArrayList<>(List.of("person@farm.com"));

        sizedStream = underTest.serialize();
        is = sizedStream.getInputStream();
        Document document = builder.parse(is);
        NodeList grantNodes = document.getElementsByTagName("grants");

        Node grantNode = grantNodes.item(0);
        NodeList grantNodeChildList = grantNode.getChildNodes();

        for (int i = 0; i < grantNodeChildList.getLength(); i++) {
            Node grantChildNode = grantNodeChildList.item(i);

            if (grantChildNode.getNodeType() == Node.ELEMENT_NODE) {
                Element grantElement = (Element) grantChildNode;

                // Extracting grant attributes from the XML doc
                String grantId = grantElement.getAttribute("id");
                String funder = grantElement.getAttribute("funder");

                assertTrue(validAwardNumbers.contains(grantId));
                assertTrue(validFunders.contains(funder));

                // Extracting PI information from the XML doc
                NodeList piNodes = grantElement.getElementsByTagName("PI");
                for (int k = 0; k < piNodes.getLength(); k++) {
                    Element piElement = (Element) piNodes.item(k);
                    String piFname = piElement.getAttribute("fname");
                    String piLname = piElement.getAttribute("lname");
                    String piEmail = piElement.getAttribute("email");

                    assertTrue(validPiFname.contains(piFname));
                    assertTrue(validPiLname.contains(piLname));
                    assertTrue(validPiEmail.contains(piEmail));
                }
            }
        }
    }

    /**
     * A complete IssnPubType (having a non null publication type and issn value) should be serialized to the metadata.
     */
    @Test
    public void completeIssnPubtype() throws IOException, ParserConfigurationException, SAXException {
        DepositMetadata metadata = new DepositMetadata();
        DepositMetadata.Journal journalMd = new DepositMetadata.Journal();
        String expectedIssn = "foo";
        String expectedPubType = "electronic";

        DepositMetadata.IssnPubType issn = new DepositMetadata.IssnPubType(expectedIssn, JournalPublicationType.OPUB);
        journalMd.setIssnPubTypes(new HashMap<>() {
            {
                put(issn.issn, issn);
            }
        });

        metadata.setJournalMetadata(journalMd);

        underTest = new NihmsMetadataSerializer(metadata, packageOptions);

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Node doc = builder.parse(underTest.serialize().getInputStream());
        NodeList issnNodes = ((Document) doc).getElementsByTagName("issn");

        assertEquals(1, issnNodes.getLength());

        Node actualIssn = asStream(issnNodes)
            .filter(node -> expectedIssn.equals(node.getFirstChild().getNodeValue()))
            .findAny()
            .orElseThrow(() ->
                             new RuntimeException(
                                 "Missing expected <issn> element for " + expectedIssn + " and " + expectedPubType));

        assertEquals(expectedPubType, actualIssn.getAttributes().getNamedItem("issn-type").getNodeValue());
    }

    /**
     * IssnPubType instances that are incomplete (one of the fields is null or empty) should not be serialized
     * https://github.com/OA-PASS/jhu-package-providers/issues/16
     */
    @Test
    public void incompleteIssnPubtypeNullIssn() throws IOException {
        DepositMetadata metadata = new DepositMetadata();
        DepositMetadata.Journal journalMd = new DepositMetadata.Journal();
        DepositMetadata.IssnPubType nullIssn = new DepositMetadata.IssnPubType(null, JournalPublicationType.OPUB);
        journalMd.setIssnPubTypes(new HashMap<>() {
            {
                put(nullIssn.issn, nullIssn);
            }
        });

        metadata.setJournalMetadata(journalMd);

        underTest = new NihmsMetadataSerializer(metadata, packageOptions);

        assertFalse(IOUtils.toString(underTest.serialize().getInputStream(), UTF_8).contains("issn"));
    }

    /**
     * IssnPubType instances that are incomplete (one of the fields is null or empty) should not be serialized
     * https://github.com/OA-PASS/jhu-package-providers/issues/16
     */
    @Test
    public void incompleteIssnPubtypeNullPubType() throws IOException {
        DepositMetadata metadata = new DepositMetadata();
        DepositMetadata.Journal journalMd = new DepositMetadata.Journal();
        DepositMetadata.IssnPubType nullPubType = new DepositMetadata.IssnPubType("foo", null);
        journalMd.setIssnPubTypes(new HashMap<String, DepositMetadata.IssnPubType>() {
            {
                put(nullPubType.issn, nullPubType);
            }
        });

        metadata.setJournalMetadata(journalMd);

        underTest = new NihmsMetadataSerializer(metadata, packageOptions);

        assertFalse(IOUtils.toString(underTest.serialize().getInputStream(), UTF_8).contains("issn"));
    }

    /**
     * If the User is missing a first name or last name, it should be parsed from the full name instead.  The middle
     * name is excluded if it is present in the full name.  Parsing is not exact, but will prevent NPEs and provide
     * a non-null value in the metadata, which is necessary for the package to pass validaton.
     */
    @Test
    public void missingUserFirstNameAndLastName() throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        // Override the first and last name of the Person with null, and then set the full name to expected values
        DepositMetadata.Person submitter =
            metadata.getPersons()
                    .stream()
                    .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.submitter)
                    .findAny()
                    .orElseThrow(() -> new RuntimeException("Missing Submitter for submission."));

        String expectedFirstName = "MooFirstName";
        String expectedLastName = "CowLastName";
        submitter.setFullName(String.format("%s %s %s", expectedFirstName, "SpotsMiddleName", expectedLastName));
        submitter.setFirstName(null);
        submitter.setLastName(null);

        Element doc = builder.parse(underTest.serialize().getInputStream()).getDocumentElement();

        Node foundPerson = asStream(doc.getElementsByTagName("person"))
            .filter(node -> node.getAttributes().getNamedItem("email").getTextContent().equals(submitter.email))
            .findAny()
            .orElseThrow(() -> new RuntimeException("Missing submitter person in serialized metadata"));

        assertEquals(expectedFirstName, foundPerson.getAttributes().getNamedItem("fname").getTextContent());
        assertEquals(expectedLastName, foundPerson.getAttributes().getNamedItem("lname").getTextContent());
    }

    private static Stream<Node> asStream(NodeList nodeList) {
        int characteristics = SIZED | ORDERED;
        Stream<Node> nodeStream = stream(new AbstractSpliterator<Node>(nodeList.getLength(), characteristics) {
            int index = 0;

            @Override
            public boolean tryAdvance(Consumer<? super Node> action) {
                if (nodeList.getLength() == index) {
                    return false;
                }

                action.accept(nodeList.item(index++));

                return true;
            }
        }, false);

        return nodeStream;
    }

}