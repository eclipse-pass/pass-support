/*
 * Copyright 2024 Johns Hopkins University
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
package org.eclipse.pass.support.grant.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.eclipse.pass.support.client.PassClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Configuration
public class GrantLoaderConfig {

    @Value("${pass.client.url}")
    private String passClientUrl;

    @Value("${pass.client.user}")
    private String passClientUser;

    @Value("${pass.client.password}")
    private String passClientPassword;

    /**
     * Returns the pass client.
     * @return the pass client
     */
    @Bean
    public PassClient passClient() {
        return PassClient.newInstance(passClientUrl, passClientUser, passClientPassword);
    }

    /**
     * Returns policy properties.
     * @param policyPropResource the resource of the policy properties
     * @return the policy properties
     * @throws IOException if io exception
     */
    @Bean
    @Qualifier("policyProperties")
    public Properties policyProperties(@Value("${pass.policy.prop.path}")
                                           Resource policyPropResource) throws IOException {
        Properties properties = new Properties();
        try (InputStream resourceStream = policyPropResource.getInputStream()) {
            properties.load(resourceStream);
        }
        return properties;
    }
}
