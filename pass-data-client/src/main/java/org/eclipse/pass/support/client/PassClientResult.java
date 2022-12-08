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

import java.util.List;

import org.eclipse.pass.support.client.model.PassEntity;

/**
 * PassClientResult represents a sublist in the list of total objects which match a selector.
 */
public class PassClientResult<T extends PassEntity> {
    private final List<T> entities;
    private final long total;

    /**
     * @param entities matching objects
     * @param total number of total matches
     */
    public PassClientResult(List<T> entities, long total) {
        this.entities = entities;
        this.total = total;
    }

    /**
     * @return The total number of matching objects or -1 if not known.
     */
    public long getTotal() {
        return total;
    }

    /**
     * @return Matching objects.
     */
    public List<T> getObjects() {
        return entities;
    }
}
