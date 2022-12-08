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

import org.eclipse.pass.support.client.model.PassEntity;

/**
 * PassClientSelector is used to select objects in the repository.
 * See https://elide.io/pages/guide/v6/10-jsonapi.html for information on the
 * sort, filter, and include syntax.
 * A given number of matches in the total result list starting at the given offset are returned.
 */
public class PassClientSelector<T extends PassEntity> {
    private static final int DEFAULT_LIMIT = 500;

    private int offset;
    private int limit;
    private Class<T> type;
    private String sorting;
    private String filter;
    private String[] include;

    /**
     * Match all objects of the given type.
     *
     * @param type of object to match
     */
    public PassClientSelector(Class<T> type) {
        this(type, 0, DEFAULT_LIMIT, null, null);
    }

    /**
     * Match objects in the repository.
     *
     * @param type Match objects of this type
     * @param offset Return objects starting at this location in the list of total results
     * @param limit Return at most this many matching objects
     * @param filter Return objects which match this RSQL filter or null for no filter
     * @param sorting Sort objects in this fashion or null for no sorting
     * @param include Also retrieve targets of these relationships
     */
    public PassClientSelector(Class<T> type, int offset, int limit, String filter, String sorting, String... include) {
        this.offset = offset;
        this.limit = limit;
        this.type = type;
        this.filter = filter;
        this.sorting = sorting;
        this.include = include;
    }

    /**
     * @return offset into list of total matches
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @param offset to set
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * @return max number of matches to return
     */
    public int getLimit() {
        return limit;
    }

    /**
     * @param limit to set
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * @return type of objects to match
     */
    public Class<? extends PassEntity> getType() {
        return type;
    }

    /**
     * @param type to set
     */
    public void setType(Class<T> type) {
        this.type = type;
    }

    /**
     * @return how results are sorted
     */
    public String getSorting() {
        return sorting;
    }

    /**
     * @param sorting to set
     */
    public void setSorting(String sorting) {
        this.sorting = sorting;
    }

    /**
     * @return RSQL expression to filter matches
     */
    public String getFilter() {
        return filter;
    }

    /**
     * @param filter to set
     */
    public void setFilter(String filter) {
        this.filter = filter;
    }

    /**
     * @return relationship of matches whose target should be returned
     */
    public String[] getInclude() {
        return include;
    }

    /**
     * @param include to set
     */
    public void setInclude(String... include) {
        this.include = include;
    }
}
