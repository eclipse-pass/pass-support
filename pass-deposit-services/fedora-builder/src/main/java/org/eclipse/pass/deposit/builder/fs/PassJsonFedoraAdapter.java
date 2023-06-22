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

package org.eclipse.pass.deposit.builder.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.io.IOUtils;
import org.eclipse.deposit.util.spring.EncodingClassPathResource;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.File;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * Converts and transports PassEntity data between local JSON files, indexed lists and Fedora repositories.
 * The functionality supports:
 * 1. Creating DepositSubmission data from resources on a Fedora server.
 * 2. Creating DepositSubmission data from a local JSON file containing PassEntity data.
 * 3. Downloading a JSON snapshot of Fedora resources rooted at a specified Submission resource.
 * 4. Uploading JSON PassEntity data to a Fedora repository to create test data or migrate repository contents.
 *
 * It might make sense to migrate this functionality to the pass-json-adapter module.
 *
 * @author Ben Trumbore (wbt3@cornell.edu)
 */
@Component
public class PassJsonFedoraAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(PassJsonFedoraAdapter.class);

    private final PassClient passClient;

    public PassJsonFedoraAdapter(PassClient passClient) {
        this.passClient = passClient;
    }

    /**
     * Extract PassEntity data from a JSON input stream and fill a collection of PassEntity objects.
     *
     * @param is       the input stream carrying the JSON data.
     * @param entities the map that will contain the parsed PassEntity objects, indexed by their IDs.
     * @return the PassEntity Submission object that is the root of the data tree.
     */
    public Submission jsonToPass(InputStream is, List<PassEntity> entities) {
        Submission submission = null;
        try {
            // Read JSON stream that defines the sample repo data
            String contentString = IOUtils.toString(is, Charset.defaultCharset());
            JsonArray entitiesJson = new JsonParser().parse(contentString).getAsJsonArray();

            // Add all the PassEntity objects to the map and remember the Submission object
            ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.registerModule(new JavaTimeModule());
            for (JsonElement entityJson : entitiesJson) {
                // What is the entity type?
                JsonElement typeName = entityJson.getAsJsonObject().get("@type");
                String typeStr = "org.eclipse.pass.support.client.model." + typeName.getAsString();
                Class<org.eclipse.pass.support.client.model.PassEntity> type =
                    (Class<org.eclipse.pass.support.client.model.PassEntity>) Class.forName(typeStr);

                // Create and save the PassEntity object
                byte[] entityJsonBytes = entityJson.toString().getBytes();
                try {
                    PassEntity entity = objectMapper.readValue(entityJsonBytes, type);
                    entities.add(entity);
                    if (entity instanceof Submission) {
                        submission = (Submission) entity;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to adapt the following JSON to a " + type.getName() + ": " +
                        entityJson, e);
                }
            }
            return submission;

        } catch (Exception e) {
            // TODO re-throw?
            System.out.println("Error building Submission from stream.");
            e.printStackTrace();
        }

        entities.clear();
        return null;
    }

    /***
     * Serializes a collection of PassEntity objects into an output stream as JSON.
     * @param entities the map that contains the PassEntity objects to serialize.
     * @param os the output stream carrying the JSON representing the PassEntity objects.
     */
    public void passToJson(HashMap<URI, PassEntity> entities, OutputStream os) throws JsonProcessingException {
        ArrayList<URI> printedUris = new ArrayList<>();
        PrintWriter pw = new PrintWriter(os);
        ObjectMapper objectMapper = new ObjectMapper();

        boolean first = true;
        pw.println("[");

        for (URI uri : entities.keySet()) {
            PassEntity entity = entities.get(uri);
            byte[] text = objectMapper.writeValueAsBytes(entity);
            // Make sure each resource is only printed once
            if (!printedUris.contains(uri)) {
                if (!first) {
                    pw.println(",");
                } else {
                    first = false;
                }
                pw.print(new String(text));
                printedUris.add(uri);
            }
        }

        pw.println("\n]");
        pw.close();
    }

    // Creates a list of URIs that are the updated counterparts to a provided list of "old" URIs.
    private ArrayList<URI> getUpdatedUris(HashMap<URI, URI> uriMap, List<URI> oldUris) {
        ArrayList<URI> newUris = new ArrayList<>();
        for (URI oldUri : oldUris) {
            newUris.add(uriMap.get(oldUri));
        }
        return newUris;
    }

    /***
     * Uploads a collection of PassEntity objects to a Fedora server as new resources.
     *
     * The collection must contain exactly one Submission entity.  All other entities
     * referenced by this Submission (and further entities referenced by them) must
     * be present in the collection.  All entities must have unique IDs.
     *
     * The target Fedora server is specified with the pass.fedora.baseurl system property,
     * which defaults to http://localhost:8080/ (trailing slash is needed).
     * Credentials on the server are specified with the
     * pass.fedora.user and pass.fedora.password system properties.
     *
     * @param entities the PassEntity objects to upload.  Keys are updated to be URIs on the Fedora server.
     * @return the newly created Submission resource from Fedora.
     */
    private Submission passToFcrepo(List<PassEntity> entities) {

        entities.forEach(entity -> {
            try {
                passClient.createObject(entity);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // TODO Deposit service port pending
//        // Upload the File binary content to the Submission, and update the File.uri field
//        Submission repoSubmission = (Submission) entities.get(submissionUri);
//        entities.values().stream().filter(e -> e instanceof File)
//                .forEach(f -> uploadBinaryToSubmission(repoSubmission, (File) f, client));

        return entities.stream().filter(entity -> entity instanceof Submission)
            .map(passEntity -> (Submission) passEntity)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Submission not found"));
    }

    /**
     * Resolves the content referenced by {@link File#getUri()}, uploads the binary to Fedora, and then updates the
     * {@code File uri} with the location of the binary in the repository.
     *
     * @param s      the Submission resource that the binary File content will be subordinate to
     * @param f      a File entity that may have a URI that links to binary content
     * @param client client used to update the File URI in the repository
     */
    // TODO Deposit service port pending
//    private void uploadBinaryToSubmission(Submission s, File f, PassClient client) {
//        // attempt to upload binary content to fedora as a child resource of the Submission
//
//        // If the file has no URI, there's nothing for us to do.
//        if (f.getUri() == null) {
//            return;
//        }
//
//        String contentUri = f.getUri().toString();
//
//        Resource contentResource = null;
//        if (contentUri.startsWith("http") || contentUri.startsWith("file:")) {
//            try {
//                contentResource = new UrlResource(f.getUri());
//            } catch (MalformedURLException e) {
//                throw new RuntimeException(e.getMessage(), e);
//            }
//        }
//
//        if (contentUri.startsWith("classpath*:")) {
//            contentResource = new ClassPathResource(
//                contentUri.substring("classpath*:".length()), this.getClass().getClassLoader());
//        }
//
//        if (contentUri.startsWith("classpath:")) {
//            contentResource = new ClassPathResource(contentUri.substring("classpath:".length()));
//        }
//
//        if (contentUri.startsWith(EncodingClassPathResource.RESOURCE_KEY)) {
//            contentResource = new EncodingClassPathResource(contentUri.substring(
//                EncodingClassPathResource.RESOURCE_KEY.length()));
//        }
//
//        if (contentResource == null) {
//            return;
//        }
//
//        HashMap<String, String> params = new HashMap<>();
//
//        if (f.getName() != null) {
//            params.put("filename", f.getName());
//        }
//
//        if (f.getMimeType() != null) {
//            params.put("content-type", f.getMimeType());
//        }
//
//        try (InputStream in = contentResource.getInputStream()) {
//            URI binaryUri = client.upload(s.getId(), in, params);
//            f.setUri(binaryUri);
//            f.setSubmission(s.getId());
//            client.updateResource(f);
//            LOG.trace("Uploaded binary {} for {} to {}.  Updating File 'uri' field to {} from {}",
//                      contentUri, f.getId(), s.getId(), binaryUri, contentUri);
//        } catch (Exception e) {
//            throw new RuntimeException("Error uploading resource " + contentResource + " to " + f.getId() +
//                                       ": " + e.getMessage(), e);
//        }
//    }

    // If not already added to the entity list, process the Funder at the provide URI
    // and do the same for its referenced Policy.
//    private void funderFcrepoToPass(HashMap<URI, PassEntity> entities, PassClient client, URI funderURI) {
//        // Make sure each funder and policy is only added once.
//        if (!entities.containsKey(funderURI)) {
//            Funder funder = client.readResource(funderURI, Funder.class);
//            entities.put(funderURI, funder);
//            if (!entities.containsKey(funder.getPolicy()) && funder.getPolicy() != null) {
//                Policy policy = client.readResource(funder.getPolicy(), Policy.class);
//                entities.put(funder.getPolicy(), policy);
//                // Ignore the repositories listed for the policy - they are added from the Submission's list.
//            }
//        }
//    }

    /***
     * Downloads a tree of resources, rooted at a Submission, from a Fedora server.
     *
     * The target Fedora server is specified with the pass.fedora.baseurl system property,
     * which defaults to http://localhost:8080/ (trailing slash is needed).
     * Credentials on the server are specified with the
     * pass.fedora.user and pass.fedora.password system properties.
     *
     * @param submissionId the URI of the root Submission resource to download.
     * @param entities the collection of PassEntity objects that is created.
     * @return the Submission entity that corresponds to the provided URI.
     */
    public Submission fcrepoToPass(String submissionId, List<PassEntity> entities) throws IOException {

        Submission submission = passClient.getObject(Submission.class, submissionId, "publication",
            "repositories", "submitter", "preparers", "grants", "effectivePolicies");

        List<Grant> populatedGrants = submission.getGrants().stream()
            .map(grant -> {
                try {
                    return passClient.getObject(grant, "primaryFunder", "directFunder", "pi", "coPis");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        submission.setGrants(populatedGrants);
//
//        // Add File resources that reference this Submission to the entity list.
//        Map<String, Collection<URI>> incomingLinks = client.getIncoming(submissionUri);
//        Collection<URI> uris = incomingLinks.get(Submission.class.getSimpleName().toLowerCase());
//        if (uris != null) {
//            for (URI uri : uris) {
//                try {
//                    File file = client.readResource(uri, File.class);
//                    entities.put(uri, file);
//                } catch (RuntimeException e) {
//                    // Ignore non-File entities, which throw invalid type exceptions.
//                    boolean tolerate = false;
//                    Throwable cause = e.getCause();
//                    while (cause != null) {
//                        if (cause instanceof InvalidTypeIdException) {
//                            tolerate = true;
//                            break;
//                        }
//                        cause = cause.getCause();
//                    }
//                    if (!tolerate) {
//                        // There was some other kind of exception
//                        throw e;
//                    }
//                }
//            }
//        }
//
//        return submission;
        return submission;
    }

    /***
     * Upload JSON PassEntity data to a Fedora repository.
     *
     * The JSON must contain exactly one Submission entity.  All other entities
     * referenced by this Submission (and further entities referenced by them) must
     * be present in the JSON.  All entities must have unique IDs.
     *
     * The target Fedora server is specified with the pass.fedora.baseurl system property.
     * Credentials on the server are specified with the
     * pass.fedora.user and pass.fedora.password system properties.
     *
     * @param is the stream containing the JSON data.
     * @param entities a map which will be filled with all uploaded PassEntities.
     * @return the root Submission resource on the Fedora server.
     */
    public Submission jsonToFcrepo(InputStream is, List<PassEntity> entities) throws IOException {
        entities.clear();
        jsonToPass(is, entities);
        return passToFcrepo(entities);
    }

    /***
     * Remove the provided set of PassEntity resources from the Fedora server.
     *
     * @param entities the PASS entities to be deleted
     */
    // TODO Deposit service port pending
//    public void deleteFromFcrepo(HashMap<URI, PassEntity> entities) {
//        PassClient client = PassClientFactory.getPassClient();
//        for (URI key : entities.keySet()) {
//            PassEntity entity = entities.get(key);
//            client.deleteResource(entity.getId());
//        }
//    }
}