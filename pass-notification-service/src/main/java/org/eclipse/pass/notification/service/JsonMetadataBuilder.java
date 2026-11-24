/*
 * Copyright 2023 Johns Hopkins University
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
package org.eclipse.pass.notification.service;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.springframework.util.ReflectionUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class JsonMetadataBuilder {

    private JsonMetadataBuilder() {}

    /**
     * Build and return resource metadata for Submission.
     * @param submission the submission
     * @param mapper the object mapper
     * @return resource metadata as String
     */
    public static String resourceMetadata(Submission submission, ObjectMapper mapper) {
        String metadata = submission.getMetadata();
        if (metadata == null || metadata.trim().length() == 0) {
            return "{}";
        }
        JsonNode metadataNode;
        try {
            metadataNode = mapper.readTree(metadata);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }

        ObjectNode resourceMetadata = mapper.createObjectNode();
        resourceMetadata.put("title", field("title", metadataNode).orElse(""));
        resourceMetadata.put("journal-title", field("journal-title", metadataNode).orElse(""));
        resourceMetadata.put("volume", field("volume", metadataNode).orElse(""));
        resourceMetadata.put("issue", field("issue", metadataNode).orElse(""));
        resourceMetadata.put("abstract", field("abstract", metadataNode).orElse(""));
        resourceMetadata.put("doi", field("doi", metadataNode).orElse(""));
        resourceMetadata.put("publisher", field("publisher", metadataNode).orElse(""));
        resourceMetadata.put("authors", field("authors", metadataNode).orElse(""));
        return resourceMetadata.toString();
    }

    /**
     * Build and return resource metadata for SubmissionEvent.
     * @param event the submission event
     * @param mapper the object mapper
     * @return resource metadata as String
     */
    public static String eventMetadata(SubmissionEvent event, ObjectMapper mapper) {
        ObjectNode eventMetadata = mapper.createObjectNode();
        eventMetadata.put("id", field("id", event).orElse(""));
        eventMetadata.put("comment", field("comment", event).orElse(""));
        eventMetadata.put("performedDate", field("performedDate", event).orElse(""));
        eventMetadata.put("performedBy", field("performedBy", event).orElse(""));
        eventMetadata.put("performerRole", field("performerRole", event).orElse(""));
        eventMetadata.put("performedDate", field("performedDate", event).orElse(""));
        eventMetadata.put("eventType", field("eventType", event).orElse(""));
        return eventMetadata.toString();
    }

    private static Optional<String> field(String fieldname, JsonNode metadata) {
        Optional<JsonNode> node = Optional.ofNullable(metadata.findValue(fieldname));
        if (node.isPresent() && node.get().isArray()) {
            return node.map(Objects::toString);
        }
        return node.map(JsonMetadataBuilder::asText);
    }

    // Jackson 3 JsonNode#asString() throws for container nodes and returns the default for null nodes, whereas
    // Jackson 2 JsonNode#asText() returned "" for containers and "null" for null nodes.
    private static String asText(JsonNode node) {
        return node.isNull() ? "null" : node.asString("");
    }

    private static Optional<String> field(String fieldname, SubmissionEvent event) {
        Optional<Field> field = Optional.ofNullable(ReflectionUtils.findField(SubmissionEvent.class, fieldname));
        return field
            .map(f -> {
                ReflectionUtils.makeAccessible(f);
                return f;
            })
            .map(f -> ReflectionUtils.getField(f, event))
            .map(Object::toString);
    }

}
