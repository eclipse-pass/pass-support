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
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.deposit.service.SubmissionReader;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Deposit;
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
    @Autowired private SubmissionReader submissionReader;

    public Submission readSubmissionJsonAndAddToPass(InputStream is, List<PassEntity> entities) throws IOException {
        entities.clear();
        Submission submissionFromJson = createSubmissionFromJson(is, entities);
        Submission submission = passClient.getObject(Submission.class, submissionFromJson.getId());
        if (Objects.nonNull(submission)) {
            resetSubmissionStatuses(submission, submissionFromJson);
            return submissionReader.readPassSubmission(submission.getId(), entities);
        }
        Submission createdSubmission = createEntitiesInPass(entities);
        return submissionReader.readPassSubmission(createdSubmission.getId(), entities);
    }

    public void deleteDepositsInPass() throws IOException {
        PassClientSelector<Deposit> depositSelector = new PassClientSelector<>(Deposit.class);
        depositSelector.setInclude("repositoryCopy");
        PassClientResult<Deposit> resultDeposits = passClient.selectObjects(depositSelector);
        resultDeposits.getObjects().forEach(deposit -> {
            try {
                passClient.deleteObject(deposit);
                if (Objects.nonNull(deposit.getRepositoryCopy())) {
                    passClient.deleteObject(deposit.getRepositoryCopy());
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void resetSubmissionStatuses(Submission submission, Submission submissionFromJson) throws IOException {
        submission.setSubmissionStatus(submissionFromJson.getSubmissionStatus());
        submission.setAggregatedDepositStatus(submissionFromJson.getAggregatedDepositStatus());
        passClient.updateObject(submission);
    }

    @SuppressWarnings("unchecked")
    public Submission createSubmissionFromJson(InputStream is, List<PassEntity> entities) {
        Submission submission = null;
        try {
            // Read JSON stream that defines the sample repo data
            String contentString = IOUtils.toString(is, Charset.defaultCharset());
            JsonArray entitiesJson = JsonParser.parseString(contentString).getAsJsonArray();

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
            throw new RuntimeException(e);
        }
    }

    private Submission createEntitiesInPass(List<PassEntity> entities) {
        entities.forEach(entity -> {
            try {
                passClient.createObject(entity);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        return entities.stream().filter(Submission.class::isInstance)
            .map(Submission.class::cast)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Submission not found"));
    }
}
