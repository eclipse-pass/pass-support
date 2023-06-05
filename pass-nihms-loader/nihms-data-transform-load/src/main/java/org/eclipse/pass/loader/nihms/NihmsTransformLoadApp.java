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
package org.eclipse.pass.loader.nihms;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Set;

import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.loader.nihms.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates loading of csv files and passing into load and transform routine
 *
 * @author Karen Hanson
 */
public class NihmsTransformLoadApp {

    private static Logger LOG = LoggerFactory.getLogger(NihmsTransformLoadApp.class);

    private static final String NIHMS_CONFIG_FILEPATH_PROPKEY = "nihmsetl.loader.configfile";
    private static final String DEFAULT_CONFIG_FILENAME = "nihms-loader.properties";

    /**
     * These are the only system properties that can be loaded in from the properties file.
     * Existing values will not be overwritten, these will just be added if missing.
     */
    private static final String[] SYSTEM_PROPERTIES = {"pass.core.user", "pass.core.password",
                                                       "pass.core.url", "nihmsetl.data.dir", "nihmsetl.pmcurl.template",
                                                       "nihmsetl.loader.cachepath"};

    private Set<NihmsStatus> statusesToProcess;

    /**
     * Constructor for the NihmsTransformLoadApp
     * @param statusesToProcess the statuses to process
     * @see NihmsStatus
     */
    public NihmsTransformLoadApp(Set<NihmsStatus> statusesToProcess) {
        this.statusesToProcess = statusesToProcess;
    }

    /**
     * Run the transform and load process
     */
    public void run() {

        //properties and downloads folders will default to current folder and default name provided if properties not
        // configured
        File configFile = FileUtil.getConfigFilePath(NIHMS_CONFIG_FILEPATH_PROPKEY, DEFAULT_CONFIG_FILENAME);

        if (Files.exists(configFile.toPath()) && configFile.canRead()) {
            LOG.info("Config file found at path {}, loading in properties", configFile.getAbsolutePath());
            Properties properties = ConfigUtil.loadProperties(configFile);
            //other properties should be system properties, per java-fedora-client needs.
            //add new system properties if we have any
            for (String key : SYSTEM_PROPERTIES) {
                String value = properties.getProperty(key);
                if (value != null) {
                    System.setProperty(key, value);
                }
            }
        } else {
            LOG.warn(
                "Could not find a readable config file at path {}, will use current system and environment variables " +
                "for configuration. "
                + "To use a config file, create a file named \"{}\" in the app's folder or provide a valid path "
                + "using the \"nihms.config.filepath\" environment variable.", configFile.getAbsolutePath(),
                DEFAULT_CONFIG_FILENAME);
        }

        if (LOG.isDebugEnabled()) {
            StringBuilder props = new StringBuilder("\n"
                                                    + "--------------------------------------------------------------\n"
                                                    + "*                         PROPERTIES                         *\n"
                                                    +
                                                    "--------------------------------------------------------------\n");

            props.append(NIHMS_CONFIG_FILEPATH_PROPKEY + ": " + configFile.toString());

            for (String key : SYSTEM_PROPERTIES) {
                props.append(key + ": " + ConfigUtil.getSystemProperty(key, "{uses_default}"));
            }
            props.append("--------------------------------------------------------------\n");
            LOG.debug(props.toString());
        }

        NihmsTransformLoadService service = new NihmsTransformLoadService();
        service.transformAndLoadFiles(statusesToProcess);

    }

}
