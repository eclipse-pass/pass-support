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

package org.eclipse.pass.deposit.config.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Repositories {

    private final Map<String, RepositoryConfig> repositoryConfigs = new HashMap<>();

    public void addRepositoryConfig(String key, RepositoryConfig config) {
        String lowerKey = key.toLowerCase();
        repositoryConfigs.put(lowerKey, config);
    }

    public RepositoryConfig getConfig(String key) {
        String lowerKey = key.toLowerCase();
        return repositoryConfigs.get(lowerKey);
    }

    public Collection<RepositoryConfig> getAllConfigs() {
        return repositoryConfigs.values();
    }

}
