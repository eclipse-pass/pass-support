package org.eclipse.pass.support.grant.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.eclipse.pass.support.client.PassClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrantLoaderConfig {

    @Value("${app.home}")
    private String appHome;

    @Value("${pass.client.url}")
    private String passClientUrl;

    @Value("${pass.client.user}")
    private String passClientUser;

    @Value("${pass.client.password}")
    private String passClientPassword;

    @Bean
    public PassClient passClient() {
        return PassClient.newInstance(passClientUrl, passClientUser, passClientPassword);
    }

    @Bean
    @Qualifier("policyProperties")
    public Properties policyProperties() throws IOException {
        String policyPropertiesFileName = "policy.properties";
        File policyPropertiesFile = new File(new File(appHome), policyPropertiesFileName);
        Properties properties = new Properties();
        try (InputStream resourceStream = new FileInputStream(policyPropertiesFile.getCanonicalPath())) {
            properties.load(resourceStream);
        }
        return properties;
    }
}
