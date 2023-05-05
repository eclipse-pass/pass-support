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
package org.eclipse.pass.loader.nihms.cli;

import static java.util.Objects.nonNull;
import static org.eclipse.pass.loader.nihms.NihmsHarvesterConfig.NIHMS_ETL_PROPERTY_PREFIX;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;

import org.eclipse.pass.loader.nihms.NihmsHarvester;
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
public class NihmsHarvesterApp {

    private static Logger LOG = LoggerFactory.getLogger(NihmsHarvesterApp.class);

    private static final String NIHMS_CONFIG_FILEPATH_PROPKEY = "nihmsetl.harvester.configfile";
    private static final String DEFAULT_CONFIG_FILENAME = "nihms-harvest.properties";

    private String startDate;

    private Set<NihmsStatus> statusesToProcess;

    /**
     * Initiate harvester app with statuses to process and a startDate for the NIHMS data to report from.
     *
     * @param statusesToProcess list of NihmsStatuses to process
     * @param startDate         null to use NIHMS system default - 1 year prior to download date
     */
    public NihmsHarvesterApp(Set<NihmsStatus> statusesToProcess, String startDate) {
        this.statusesToProcess = statusesToProcess;
        this.startDate = startDate;
    }

    /**
     * Gathers the properties for the harvester and calls harvest method
     */
    public void run() {
        //properties and downloads folders will default to current folder and default name provided if properties not
        // configured
        File configFile = FileUtil.getConfigFilePath(NIHMS_CONFIG_FILEPATH_PROPKEY, DEFAULT_CONFIG_FILENAME);
        LOG.debug("NIHMS Harvester app is checking for config file at path {}", configFile.toString());

        if (Files.exists(configFile.toPath()) && configFile.canRead()) {
            LOG.info("Config file found at path {}, loading in properties", configFile.getAbsolutePath());
            //other properties should be system properties, per java-fedora-client needs.
            //add new system properties if we have any
            ConfigUtil.loadProperties(configFile)
                      .entrySet()
                      .stream()
                      .filter(entry -> ((String) entry.getKey()).startsWith(NIHMS_ETL_PROPERTY_PREFIX))
                      .filter(entry -> nonNull(entry.getValue()))
                      .forEach(entry -> {
                          LOG.debug("Setting property '{}' = '{}'", entry.getKey(), entry.getValue());
                          System.setProperty((String) entry.getKey(), (String) entry.getValue());
                      });
        } else {
            LOG.warn(
                "Could not find a readable config file at path {}, will use current system and environment variables " +
                "for configuration. "
                + "To use a config file, create a file named \"{}\" in the app's folder or provide a valid path "
                + "using the \"{}\" environment variable.", configFile.getAbsolutePath(), DEFAULT_CONFIG_FILENAME,
                NIHMS_CONFIG_FILEPATH_PROPKEY);
        }

        if (LOG.isDebugEnabled()) {
            StringBuilder props = new StringBuilder("\n"
                                                    + "--------------------------------------------------------------\n"
                                                    + "*                         PROPERTIES                         *\n"
                                                    +
                                                    "--------------------------------------------------------------\n");

            props.append(String.format("  %s: %s\n", NIHMS_CONFIG_FILEPATH_PROPKEY, configFile.toString()));

            ConfigUtil.loadProperties(configFile)
                      .entrySet()
                      .stream()
                      .filter(entry -> ((String) entry.getKey()).startsWith(NIHMS_ETL_PROPERTY_PREFIX))
                      .filter(entry -> nonNull(entry.getValue()))
                      .forEach(entry -> {
                          props.append(String.format("  %s: %s\n",
                                                     entry.getKey(),
                                                     ConfigUtil.getSystemProperty((String) entry.getKey(),
                                                                                  "{uses_default}")));
                      });

            props.append("--------------------------------------------------------------\n");
            LOG.debug(props.toString());
        }

        NihmsHarvester harvester = new NihmsHarvester();
        harvester.harvest(statusesToProcess, startDate);
    }

}
