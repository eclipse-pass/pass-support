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

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.deposit.assembler.SizedStream;
import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.JournalPublicationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * XML serialization of our NihmsMetadata to conform with the bulk submission
 * dtd
 *
 * @author Jim Martino (jrm@jhu.edu)
 */
public class NihmsMetadataSerializer implements StreamingSerializer {
    private static final Logger LOG = LoggerFactory.getLogger(NihmsMetadataSerializer.class);

    private DepositMetadata metadata;

    public NihmsMetadataSerializer(DepositMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public SizedStream serialize() {
        try {
            Document doc = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();

            write_metadata(doc);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(os));

            return NihmsAssemblerUtil.asSizedStream(os);
        } catch (ParserConfigurationException | TransformerException e) {
            throw new RuntimeException("Failed to serialize nihms metadata", e);
        }
    }

    private void add_text_element(Document doc, Element parent, String name, String text, String attr,
            String attr_value) {
        if (text != null) {
            Element el = doc.createElement(name);
            parent.appendChild(el);
            el.setTextContent(text);

            if (attr != null && attr_value != null) {
                el.setAttribute(attr, attr_value);
            }
        }
    }

    private void add_text_element(Document doc, Element parent, String name, String text) {
        add_text_element(doc, parent, name, text, null, null);
    }

    private void write_metadata(Document doc) {
        Element root = doc.createElement("manuscript-submit");
        doc.appendChild(root);

        addManuscriptMetadata(root);
        addArticleMetadata(root);
        addJournalMetadata(doc, root);
        addManuscriptTitle(doc, root);
        addContacts(doc, root);
        addGrants(doc, root);
    }

    private void addManuscriptMetadata(Element root) {
        DepositMetadata.Manuscript manuscript = metadata.getManuscriptMetadata();

        if (StringUtils.isNotBlank(manuscript.getNihmsId())) {
            root.setAttribute("manuscript-id", manuscript.getNihmsId());
        }
    }

    private void addArticleMetadata(Element root) {
        DepositMetadata.Article article = metadata.getArticleMetadata();

        if (article != null && metadata.getArticleMetadata().getEmbargoLiftDate() != null) {
            LocalDate lift = article.getEmbargoLiftDate().toLocalDate();
            LocalDate now = LocalDate.now();

            if (lift.isAfter(now)) {
                long months = Period.between(LocalDate.now(), lift).toTotalMonths();

                // The max embargo time is 12 months
                if (months > 12) {
                    months = 12;
                }

                root.setAttribute("embargo-months", "" + months);
            }
        }

        if (article != null && article.getDoi() != null) {
            // DOI may not include UTI's scheme or host, only path
            String path = article.getDoi().getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            root.setAttribute("doi", path);
        }
    }
    private void addJournalMetadata(Document doc, Element root) {
        DepositMetadata.Journal journal = metadata.getJournalMetadata();

        // There is an optional agency attribute.
        // Should only be used for non-NIH funders when grant information is also given

        if (journal != null) {
            Element journal_meta = doc.createElement("journal-meta");
            root.appendChild(journal_meta);

            add_text_element(doc, journal_meta, "nlm-ta", journal.getJournalId());

            // If the IssnPubType is incomplete (either the pubType or issn is null or
            // empty), we should omit it from the metadata, per NIH's requirements.
            // See https://github.com/OA-PASS/metadata-schemas/pull/28 and
            // https://github.com/OA-PASS/jhu-package-providers/issues/16
            journal.getIssnPubTypes().values().forEach(issnPubType -> {
                if (issnPubType.pubType == null || StringUtils.isBlank(issnPubType.issn)) {
                    LOG.debug("Discarding incomplete ISSN: {}", issnPubType);
                    return;
                }

                if (issnPubType.pubType == JournalPublicationType.PPUB) {
                    add_text_element(doc, journal_meta, "issn", issnPubType.issn, "issn-type", "print");
                } else if (issnPubType.pubType == JournalPublicationType.EPUB
                        || issnPubType.pubType == JournalPublicationType.OPUB) {
                    add_text_element(doc, journal_meta, "issn", issnPubType.issn, "issn-type", "electronic");
                }
            });

            add_text_element(doc, journal_meta, "journal-title", journal.getJournalTitle());
        }

    }
    private void addManuscriptTitle(Document doc, Element root) {
        DepositMetadata.Manuscript manuscript = metadata.getManuscriptMetadata();

        if (manuscript != null && StringUtils.isNotBlank(manuscript.title)) {
            add_text_element(doc, root, "manuscript-title", manuscript.title);
        }

    }

    private void addContacts(Document doc, Element root) {
        List<DepositMetadata.Person> persons = metadata.getPersons();

        if (persons != null && persons.size() > 0) {
            Element contacts = doc.createElement("contacts");
            root.appendChild(contacts);

            for (DepositMetadata.Person person : persons) {
                // There should be exactly one corresponding PI per deposit.
                if (person.getType() == DepositMetadata.PERSON_TYPE.submitter) {
                    Element p = doc.createElement("person");
                    contacts.appendChild(p);

                    if (StringUtils.isNotBlank(person.getFirstName())) {
                        p.setAttribute("fname", person.getFirstName());
                    } else {
                        if (StringUtils.isNotBlank(person.getFullName())) {
                            p.setAttribute("fname", person.getFullName().split("\\s")[0]);
                        }
                    }
                    if (StringUtils.isNotBlank(person.getMiddleName())) {
                        p.setAttribute("mname", person.getMiddleName());
                    }
                    if (StringUtils.isNotBlank(person.getLastName())) {
                        p.setAttribute("lname", person.getLastName());
                    } else {
                        if (StringUtils.isNotBlank(person.getFullName())) {
                            String[] split = person.getFullName().split("\\s");
                            if (split.length > 2) {
                                // middle name is present
                                p.setAttribute("lname",
                                        String.join(" ", Arrays.copyOfRange(split, 2, split.length)));
                            } else {
                                p.setAttribute("lname", split[1]);
                            }
                        }
                    }
                    if (StringUtils.isNotBlank(person.getEmail())) {
                        p.setAttribute("email", person.getEmail());
                    }

                    p.setAttribute("person-type", "author");
                }
            }
        }
    }

    private void addGrants(Document doc, Element root) {
        List<DepositMetadata.Grant> grantsList = metadata.getGrantsMetadata();

        if (grantsList != null && !grantsList.isEmpty()) {
            //TODO: add check, if funder is not in the list of accepted funders then do not create grant element
            //TODO: at least 1 accepted funder for a grant is required to create parent element grants
            Element grantsElement = doc.createElement("grants");
            root.appendChild(grantsElement);

            for (DepositMetadata.Grant grant : grantsList) {
                if (StringUtils.isNotBlank(grant.getFunder())) {
                    Element grantElement = doc.createElement("grant");
                    grantsElement.appendChild(grantElement);
                    grantElement.setAttribute("funder", grant.getFunder());

                    if (StringUtils.isNotBlank(grant.getGrantId())) {
                        grantElement.setAttribute("id", grant.getGrantId());
                    }


                    DepositMetadata.Person pi = grant.getGrantPi();
                    if (pi != null) {
                        Element piElement = doc.createElement("PI");
                        grantElement.appendChild(piElement);

                        if (StringUtils.isNotBlank(pi.getFirstName())) {
                            piElement.setAttribute("fname", pi.getFirstName());
                        }
                        if (StringUtils.isNotBlank(pi.getLastName())) {
                            piElement.setAttribute("lname", pi.getLastName());
                        }
                        if (StringUtils.isNotBlank(pi.getEmail())) {
                            piElement.setAttribute("email", pi.getEmail());
                        }
                    }
                }
            }
        }
    }
}
