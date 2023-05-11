package org.eclipse.pass.loader.nihms.cli;

import java.util.EnumSet;
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
public class NihmsHarvesterCLI {

    //java -Dnihmsetl.harvester.configfile="/pass/config/config.properties" -jar example.jar -c -startdate 12-2008

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
    @Option(name = "-p", aliases = {"-inprocess", "--inprocess"},
            usage = "In Process NIHMS publication status. By default all available CSV data is processed. "
                    + "If one or more status type is specified, only publications matching the selected status(es) " +
                    "will be processed.")
    private boolean inProcess = false;

    /**
     * The start date from which to load .
     */
    @Option(name = "-s", aliases = {"-startDate", "--startDate"},
            usage = "DateTime to start the query against NIHMS data. This will cause "
                    + "a return of all records published since the date provided. Syntax must be mm-yyyy. This value " +
                    "will override the "
                    + "NIHMS system default which is one year before the current month")
    private String startDate = "";

    public static void main(String[] args) {

        final NihmsHarvesterCLI application = new NihmsHarvesterCLI();
        CmdLineParser parser = new CmdLineParser(application);

        try {

            parser.parseArgument(args);
            /* Handle general options such as help, version */
            if (application.help) {
                parser.printUsage(System.err);
                System.err.println();
                System.exit(0);
            }

            Set<NihmsStatus> statusesToProcess = new HashSet<NihmsStatus>();
            String startDateFilter = application.startDate;

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
            if (statusesToProcess.size() == 0) {
                statusesToProcess.addAll(EnumSet.allOf(NihmsStatus.class));
            }

            /* Run the package generation application proper */
            NihmsHarvesterApp app = new NihmsHarvesterApp(statusesToProcess, startDateFilter);
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
