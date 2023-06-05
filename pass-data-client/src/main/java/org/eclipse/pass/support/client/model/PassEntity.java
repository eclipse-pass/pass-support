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
package org.eclipse.pass.support.client.model;

/**
 * Abstract method that all PASS model entities inherit from. All entities can include
 * a unique ID, type, and context
 *
 * @author Karen Hanson
 */

public interface PassEntity {
    /**
     * Retrieves the unique URI representing the resource.
     *
     * @return the id
     */
    String getId();

    /**
     * Sets the unique ID for an object. Note that when creating a new resource, this should be left
     * blank as the ID will be auto-generated and populated by the repository. When performing a
     * update, this ID will be used as the target resource.
     *
     * @param id the id to set
     */
    void setId(String id);
}
