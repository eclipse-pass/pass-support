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

package org.eclipse.pass.deposit.config.spring;

import org.eclipse.pass.deposit.config.repository.SpringEnvironmentDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class RepositoriesFactoryBeanConfig {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoriesFactoryBeanConfig.class);

    @Bean
    public JsonMapper repositoriesMapper(Environment env) {
        SimpleModule module = new SimpleModule();
        SpringEnvironmentDeserializer envDeserializer = new SpringEnvironmentDeserializer(env);
        module.addDeserializer(String.class, envDeserializer);
        // Jackson 3 mappers are immutable, so modules are added with the builder. The Jackson 2 defaults keep the
        // behavior of the previous "new ObjectMapper()".
        return JsonMapper.builder()
            .configureForJackson2()
            .addModule(module)
            .build();
    }

    @Bean(name = "repositories")
    public RepositoriesFactory repositoriesFactory(@Value("${pass.deposit.repository.configuration}")
                                                       Resource configResource,
                                                   ObjectMapper repositoriesMapper) {
        LOG.trace("Resolving repository configuration resource from '{}'", configResource);
        RepositoriesFactory factory = new RepositoriesFactory(configResource, repositoriesMapper);
        return factory;
    }

}
