/*
 * Copyright 2023 Johns Hopkins University
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
package org.eclipse.pass.loader.nihms.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Karen Hanson
 */
public class ConfigUtil {

    private ConfigUtil() {
        //never called
    }

    private static final String NIHMS_REPOSITORY_URI_KEY = "nihmsetl.repository.id";
    private static final String NIHMS_REPOSITORY_URI_DEFAULT = "1";

    /**
     * Retrieve property from a system property or environment variable or set to default
     * <p>
     * Given a string as a system property name (and a default) will:
     * </p>
     * <ol>
     * <li>Look up the system property with the matching name, and return it if defined</li>
     * <li>Try to match an environment variable matching the key transformed TO_UPPER_CASE with periods substituted
     * with underscores</li>
     * <li>Use the default of none others match</li>
     * </ol>
     *
     * @param key          - property/variable name in property-normal form (period separators, ideally all lowercas)
     * @param defaultValue default value for the key if it does not exist as a system property or environment variable
     * @return the property value
     */
    public static String getSystemProperty(final String key, final String defaultValue) {
        return System.getProperty(key, System.getenv().getOrDefault(toEnvName(key), defaultValue));
    }

    static String toEnvName(String name) {
        return name.toUpperCase().replace('.', '_');
    }

    /**
     * Retrieves the NIHMS Repository URI based on property key
     *
     * @return the NIHMS repository URI
     */
    public static String getNihmsRepositoryUri() {
        return ConfigUtil.getSystemProperty(NIHMS_REPOSITORY_URI_KEY, NIHMS_REPOSITORY_URI_DEFAULT);
    }

    /**
     * This method processes a plain text properties file and returns a {@code Properties} object
     *
     * @param propertiesFile - the properties {@code File} to be read
     * @return the Properties object derived from the supplied {@code File}
     */
    public static Properties loadProperties(File propertiesFile) {
        Properties properties = new Properties();
        String resource;
        try {
            resource = propertiesFile.getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException("Could not open configuration file, check configuration path.", e);
        }
        try (InputStream resourceStream = new FileInputStream(resource)) {
            properties.load(resourceStream);
        } catch (IOException e) {
            throw new RuntimeException("Could not open configuration file, check configuration path.", e);
        }
        return properties;
    }

}
