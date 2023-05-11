package org.eclipse.pass.loader.nihms.cli;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * NIHMS Submission Loader CLI
 *
 * @author Karen Hanson
 */
public class NihmsTransformLoadCLI {

    /**
     *
     * General Options
     */

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

    public static void main(String[] args) {

        final NihmsTransformLoadCLI application = new NihmsTransformLoadCLI();
        CmdLineParser parser = new CmdLineParser(application);

        Set<NihmsStatus> statusesToProcess = new HashSet<NihmsStatus>();

        try {

            parser.parseArgument(args);
            /* Handle general options such as help, version */
            if (application.help) {
                parser.printUsage(System.err);
                System.err.println();
                System.exit(0);
            }

            //select statuses to process
            if (application.compliant) {
                statusesToProcess.add(NihmsStatus.COMPLIANT);
            }
            if (application.nonCompliant) {
                statusesToProcess.add(NihmsStatus.NON_COMPLIANT);
            }
            if (application.inProcess) {
                statusesToProcess.add(NihmsStatus.IN_PROCESS);
            }

            /* Run the package generation application proper */
            NihmsTransformLoadApp app = new NihmsTransformLoadApp(statusesToProcess);
            app.run();
            System.exit((0));

        } catch (CmdLineException e) {
            /**
             * This is an error in command line args, just print out usage data
             * and description of the error.
             */
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            System.err.println();
            System.exit(1);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(1);

        }
    }
}
