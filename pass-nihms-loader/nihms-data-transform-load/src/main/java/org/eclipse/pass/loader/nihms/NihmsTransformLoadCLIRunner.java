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
package org.eclipse.pass.loader.nihms;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Component
public class NihmsTransformLoadCLIRunner implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsTransformLoadCLIRunner.class);

    /**
     * Request for help/usage documentation
     */
    @Option(name = "-h", aliases = {"-help", "--help"}, usage = "print help message")
    public boolean help = false;

    /**
     * Actively select to include non-compliant data in processing
     **/
    @Option(name = "-n", aliases = {"-noncompliant", "--noncompliant"},
        usage = "Non compliant NIHMS publication status. By default all available CSV data is processed. "
            + "If one or more status type is specified, only publications matching the selected status(es) " +
            "will be processed.")
    private boolean nonCompliant = false;

    /**
     * Actively select to include compliant data in processing
     **/
    @Option(name = "-c", aliases = {"-compliant", "--compliant"},
        usage = "Compliant NIHMS publication status. By default all available CSV data is processed. "
            + "If one or more status type is specified, only publications matching the selected status(es) " +
            "will be processed.")
    private boolean compliant = false;

    /**
     * Actively select to include in-process data in processing
     **/
    @Option(name = "-i", aliases = {"-inprocess", "--inprocess"},
        usage = "In Process NIHMS publication status. By default all available CSV data is processed. "
            + "If one or more status type is specified, only publications matching the selected status(es) " +
            "will be processed.")
    private boolean inProcess = false;

    private final NihmsTransformLoadService nihmsTransformLoadService;

    public NihmsTransformLoadCLIRunner(NihmsTransformLoadService nihmsTransformLoadService) {
        this.nihmsTransformLoadService = nihmsTransformLoadService;
    }

    @Override
    public void run(String... args) {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
            /* Handle general options such as help, version */
            if (this.help) {
                parser.printUsage(System.err);
                System.exit(0);
            }

            Set<NihmsStatus> statusesToProcess = new HashSet<>();
            //select statuses to process
            if (this.compliant) {
                statusesToProcess.add(NihmsStatus.COMPLIANT);
            }
            if (this.nonCompliant) {
                statusesToProcess.add(NihmsStatus.NON_COMPLIANT);
            }
            if (this.inProcess) {
                statusesToProcess.add(NihmsStatus.IN_PROCESS);
            }

            /* Run the package generation application proper */
            nihmsTransformLoadService.transformAndLoadFiles(statusesToProcess);

        } catch (CmdLineException e) {
            parser.printUsage(System.err);
            System.exit(1);
        } catch (Exception e) {
            LOG.error("Error running Nihms Transform and Load", e);
            System.exit(1);
        }
    }
}
