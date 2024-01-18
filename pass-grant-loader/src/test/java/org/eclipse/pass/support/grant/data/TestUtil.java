package org.eclipse.pass.support.grant.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TestUtil {

    private TestUtil () {}

    public static Properties loaderPolicyProperties() throws IOException {
        File policyPropertiesFile = new File(
            TestUtil.class.getClassLoader().getResource("policy.properties").getFile());
        Properties policyProperties = new Properties();
        try (InputStream resourceStream = new FileInputStream(policyPropertiesFile)) {
            policyProperties.load(resourceStream);
        }
        return policyProperties;
    }
}
