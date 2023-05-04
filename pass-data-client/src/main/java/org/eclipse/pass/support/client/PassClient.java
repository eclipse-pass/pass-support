/*
 * Copyright 2022 Johns Hopkins University
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
package org.eclipse.pass.support.client;

import java.io.IOException;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.pass.support.client.model.PassEntity;

/**
 * PassClient provides CRUD operations on objects in a running PASS system.
 * A Java representation of the PASS data model is provided.
 */
public interface PassClient {
    /**
     * Create a new PassClient configured by using system properties:
     * pass.core.url, pass.core.user, and pass.core.password.
     *
     * @return new PassClient
     */
    public static PassClient newInstance() {
        String url = System.getProperty("pass.core.url");
        String user = System.getProperty("pass.core.user");
        String pass = System.getProperty("pass.core.password");

        if (url == null) {
            throw new RuntimeException("Missing required system property: pass.core.url");
        }

        return new JsonApiPassClient(url, user, pass);
    }

    /**
     * Create a new PassClient.
     *
     * @param baseUrl base url of PASS API
     * @return new PassClient
     */
    public static PassClient newInstance(String baseUrl) {
        return new JsonApiPassClient(baseUrl);
    }

    /**
     * Create a PassClient which uses HTTP basic auth.
     *
     * @param baseUrl  base url of PASS API
     * @param user user to connect as
     * @param pass password of user
     * @return new PassClient
     */
    public static PassClient newInstance(String baseUrl, String user, String pass) {
        return new JsonApiPassClient(baseUrl, user, pass);
    }

    /**
     * Create a new object.
     * The id of the object must be null and will be set by the method.
     *
     * @param <T> type of the object
     * @param obj object to persist
     * @throws IOException if operation fails
     */
    <T extends PassEntity> void createObject(T obj) throws IOException;

    /**
     * Update an existing object. Note that a relationship cannot be removed by
     * setting it to null.
     *
     * @param <T> type of the object
     * @param obj object to update
     * @throws IOException if operation fails
     */
    <T extends PassEntity> void updateObject(T obj) throws IOException;

    /**
     * Retrieve object with the given type and id from the repository. Targets of
     * relationships may optionally be included in the response. If they are not included,
     * the target object will have its identifier set, but nothing else.
     *
     * @param <T> type of the object
     * @param type Class of the object
     * @param id identifier of the object
     * @param include Array of relationship names whose targets will be included in response
     * @return persisted object or null if it does not exist
     * @throws IOException if operation fails
     */
    <T extends PassEntity> T getObject(Class<T> type, String id, String... include) throws IOException;

    /**
     * Retrieve object with the type of and id of the argument object.
     * This can be useful when only the type and id are known.
     *
     * @param <T> type of the object
     * @param obj type and id of object to retrieve
     * @param include relationships who
     * @return persisted object or null if it does not exist
     * @throws IOException if operation fails
     */
    @SuppressWarnings("unchecked")
    default <T extends PassEntity> T getObject(T obj, String... include) throws IOException {
        return (T) getObject(obj.getClass(), obj.getId(), include);
    }

    /**
     * Delete object with the given type and id.
     *
     * @param <T> type of the object
     * @param type type of the object
     * @param id identifier of the object
     * @throws IOException if operation fails
     */
    <T extends PassEntity> void deleteObject(Class<T> type, String id) throws IOException;

    /**
     * Delete an object.
     *
     * @param <T> type of the object
     * @param obj object to delete
     * @throws IOException if operation fails
     */
    default <T extends PassEntity> void deleteObject(T obj) throws IOException {
        deleteObject(obj.getClass(), obj.getId());
    }

    /**
     * Select objects from the repository matching the selector.
     *
     * @param <T> type of the object
     * @param selector which objects to retrieve
     * @return matching objects
     * @throws IOException if operation fails
     */
    <T extends PassEntity> PassClientResult<T> selectObjects(PassClientSelector<T> selector) throws IOException;

    /**
     * Stream all objects in the repository matching the selector starting from the selector offset.
     *
     * @param <T> type of the object
     * @param selector which objects to retrieve
     * @return Stream matching objects
     * @throws IOException if operation fails
     */
    default <T extends PassEntity> Stream<T> streamObjects(PassClientSelector<T> selector) throws IOException {
        Spliterator<T> iter = new Spliterator<T>() {
            PassClientResult<T> result = selectObjects(selector);
            int next = 0;

            @Override
            public int characteristics() {
                return NONNULL | CONCURRENT;
            }

            @Override
            public long estimateSize() {
                return result.getTotal();
            }

            @Override
            public boolean tryAdvance(Consumer<? super T> consumer) {
                if (next == result.getObjects().size()) {
                    try {
                        selector.setOffset(selector.getOffset() + selector.getLimit());
                        result = selectObjects(selector);
                        next = 0;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (result.getObjects().size() == 0) {
                        return false;
                    }
                }

                consumer.accept(result.getObjects().get(next++));
                return true;
            }

            @Override
            public Spliterator<T> trySplit() {
                return null;
            }
        };

        return StreamSupport.stream(iter, false);
    }
}