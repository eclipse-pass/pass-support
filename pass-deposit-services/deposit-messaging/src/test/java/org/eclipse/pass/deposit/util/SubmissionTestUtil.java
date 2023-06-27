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
package org.eclipse.pass.deposit.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.deposit.builder.DepositSubmissionMapper;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.File;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class SubmissionTestUtil {

    @Autowired private PassClient passClient;
    @Autowired private DepositSubmissionMapper depositSubmissionMapper;

    public DepositSubmission asDepositSubmission(String submissionJsonName) throws IOException {
        InputStream inputStream = ResourceTestUtil.readSubmissionJson(submissionJsonName);
        List<PassEntity> entities = new LinkedList<>();
        Submission submissionEntity = readSubmissionJsonAndAddToPass(inputStream, entities);
        return depositSubmissionMapper.createDepositSubmission(submissionEntity, entities);
    }

    public Submission readSubmissionJsonAndAddToPass(InputStream is, List<PassEntity> entities) {
        entities.clear();
        createSubmissionFromJson(is, entities);
        return createEntitiesInPass(entities);
    }

    public Submission createSubmissionFromJson(InputStream is, List<PassEntity> entities) {
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

    private Submission createEntitiesInPass(List<PassEntity> entities) {

        entities.forEach(entity -> {
            try {
                passClient.createObject(entity);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        return entities.stream().filter(entity -> entity instanceof Submission)
            .map(passEntity -> (Submission) passEntity)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Submission not found"));
    }

    public static Collection<URI> getDepositUris(Submission submission, PassClient passClient) {
        return getIncomingUris(submission, passClient, Deposit.class);
    }

    public static Collection<URI> getFileUris(Submission submission, PassClient passClient) {
        return getIncomingUris(submission, passClient, File.class);
    }

    private static Collection<URI> getIncomingUris(Submission submission, PassClient passClient,
                                                   Class<? extends PassEntity> incomingResourceClass) {
        // TODO Deposit service port pending
//        Map<String, Collection<URI>> incoming = passClient.getIncoming(submission.getId());
//        if (!incoming.containsKey("submission")) {
//            return Collections.emptySet();
//        }
//
//        return incoming.get("submission").stream().filter(uri -> {
//            try {
//                passClient.readResource(uri, incomingResourceClass);
//                return true;
//            } catch (Exception e) {
//                return false;
//            }
//        }).collect(toSet());
        return null;
    }
}
