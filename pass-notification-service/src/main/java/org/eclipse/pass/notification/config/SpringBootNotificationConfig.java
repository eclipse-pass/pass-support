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
package org.eclipse.pass.notification.config;

import java.io.IOException;

import org.eclipse.pass.support.client.PassClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Primary Spring Boot configuration class for Notification Services
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Configuration
public class SpringBootNotificationConfig {

    @Value("${pass.notification.mode}")
    private Mode notificationMode;

    @Value("${pass.notification.configuration}")
    private Resource notificationConfigResource;

    @Value("${pass.client.url}")
    private String passClientUrl;

    @Value("${pass.client.user}")
    private String passClientUser;

    @Value("${pass.client.password}")
    private String passClientPassword;

    /**
     * Configure the PassClient.
     * @return the PassClient bean
     */
    @Bean
    public PassClient passClient() {
        return PassClient.newInstance(passClientUrl, passClientUser, passClientPassword);
    }

    /**
     * Configure a Jackson module that resolves Spring property placeholders in deserialized String values.
     * Jackson 3 object mappers are immutable, so the module is contributed as a bean and is registered by Spring Boot
     * when it builds the shared mapper.
     * @param envDeserializer the String deserializer that resolves placeholders
     * @return the Jackson module
     */
    @Bean
    public SimpleModule envStringDeserializerModule(SpringEnvStringDeserializer envDeserializer) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, envDeserializer);
        return module;
    }

    /**
     * Configure the NotificationConfig.
     * @param objectMapper the object mapper
     * @return the NotificationConfig bean
     * @throws IOException thrown if IO error
     */
    @Bean
    public NotificationConfig notificationConfiguration(ObjectMapper objectMapper) throws IOException {
        NotificationConfig notificationConfig = objectMapper.readValue(notificationConfigResource.getInputStream(),
            NotificationConfig.class);
        notificationConfig.setMode(notificationMode);
        return notificationConfig;
    }

    /**
     * Configure the RecipientConfig. The appropriate RecipientConfig will be selected based on Mode.
     * @param config the notification config
     * @return the RecipientConfig bean
     */
    @Bean
    public RecipientConfig recipientConfig(NotificationConfig config) {
        return config.getRecipientConfigs().stream()
            .filter(rc -> config.getMode() == rc.getMode()).findAny()
            .orElseThrow(() ->
                new RuntimeException("Missing recipient configuration for Mode '" + config.getMode() + "'"));
    }

}
