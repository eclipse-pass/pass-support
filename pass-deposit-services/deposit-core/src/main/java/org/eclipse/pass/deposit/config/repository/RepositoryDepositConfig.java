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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class RepositoryDepositConfig {

    @JsonProperty("mapping")
    private StatusMapping statusMapping;

    public StatusMapping getStatusMapping() {
        return statusMapping;
    }

    public void setStatusMapping(StatusMapping statusMapping) {
        this.statusMapping = statusMapping;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RepositoryDepositConfig that = (RepositoryDepositConfig) o;

        return statusMapping != null ? statusMapping.equals(that.statusMapping) : that.statusMapping == null;
    }

    @Override
    public int hashCode() {
        return 31 * (statusMapping != null ? statusMapping.hashCode() : 0);
    }

    @Override
    public String toString() {
        return "RepositoryDepositConfig{statusMapping=" + statusMapping + "}";
    }
}
