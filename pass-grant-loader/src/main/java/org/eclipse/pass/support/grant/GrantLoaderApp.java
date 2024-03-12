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
package org.eclipse.pass.support.grant;

import static java.lang.String.format;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_ACTION_NOT_VALID;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_COULD_NOT_APPEND_UPDATE_TIMESTAMP;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_COULD_NOT_OPEN_CONFIGURATION_FILE;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_DATA_FILE_CANNOT_READ;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_HOME_DIRECTORY_NOT_FOUND;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_HOME_DIRECTORY_NOT_READABLE_AND_WRITABLE;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_INVALID_COMMAND_LINE_DATE;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_INVALID_COMMAND_LINE_TIMESTAMP;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_INVALID_TIMESTAMP;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_MODE_NOT_VALID;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_REQUIRED_CONFIGURATION_FILE_MISSING;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_REQUIRED_DATA_FILE_MISSING;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_RESULT_SET_NULL;
import static org.eclipse.pass.support.grant.DataLoaderErrors.ERR_SQL_EXCEPTION;
import static org.eclipse.pass.support.grant.data.DateTimeUtil.verifyDate;
import static org.eclipse.pass.support.grant.data.DateTimeUtil.verifyDateTimeFormat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import org.eclipse.pass.support.grant.data.GrantConnector;
import org.eclipse.pass.support.grant.data.GrantIngestRecord;
import org.eclipse.pass.support.grant.data.PassUpdater;
import org.eclipse.pass.support.grant.data.file.GrantDataCsvFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This class does the orchestration for the pulling of grant and user data. The basic steps are to read in all of the
 * configuration files needed by the various classes; call the GrantLoader to pull in all
 * of the grants or users updated since the timestamp at the end of the updated timestamps file;
 * use the PassLoader to take {@code List} representing the {@code ResultSet} to push this data into our PASS instance
 * via the java pass client.
 * <p>
 * A large percentage of the code here is handling exceptional paths, as this is intended to be run in an automated
 * fashion, so care must be taken to log errors, report them to STDOUT.
 *
 * @author jrm@jhu.edu
 */
@Component
public class GrantLoaderApp {
    private static final Logger LOG = LoggerFactory.getLogger(GrantLoaderApp.class);

    @Value("${app.home}")
    private String appHome;

    private File updateTimestampsFile;
    private final String updateTimestampsFileName;

    private final GrantConnector grantConnector;
    private final PassUpdater passUpdater;

    /**
     * Constructor.
     * @param grantConnector the grant connector
     * @param passUpdater the pass update
     */
    public GrantLoaderApp(GrantConnector grantConnector,
                          PassUpdater passUpdater) {
        this.grantConnector = grantConnector;
        this.passUpdater = passUpdater;
        this.updateTimestampsFileName = "grant_update_timestamps";
    }

    /**
     * Run the grant loader.
     * @param startDate the start date
     * @param awardEndDate the award end date
     * @param mode the mode
     * @param action the action
     * @param dataFileName the data file name
     * @param grant the grant id
     * @throws PassCliException
     */
    public void run(String startDate, String awardEndDate, String mode, String action,
                    String dataFileName, String grant) throws PassCliException {

        File dataFile = new File(dataFileName);

        //check that we have a good value for mode
        if (!checkMode(mode)) {
            throw processException(format(ERR_MODE_NOT_VALID, mode), null);
        }

        //check that we have a good value for action
        if (!action.equals("") && !action.equals("pull") && !action.equals("load")) {
            throw processException(format(ERR_ACTION_NOT_VALID, action), null);
        }

        //first check that we have the required files
        File appHomeFile = new File(appHome);
        if (!appHomeFile.exists()) {
            throw processException(ERR_HOME_DIRECTORY_NOT_FOUND, null);
        }
        if (!appHomeFile.canRead() || !appHomeFile.canWrite()) {
            throw processException(ERR_HOME_DIRECTORY_NOT_READABLE_AND_WRITABLE, null);
        }

        updateTimestampsFile = new File(appHome, updateTimestampsFileName);

        //check suitability of our input file
        if (action.equals("load") || action.equals("pull")) {
            if (!dataFile.exists()) {
                throw processException(format(ERR_REQUIRED_DATA_FILE_MISSING, dataFileName), null);
            } else if (!dataFile.canRead()) {
                throw processException(format(ERR_DATA_FILE_CANNOT_READ, dataFileName), null);
            } else if (action.equals("pull") && !dataFile.canWrite()) {
                throw processException(format(ERR_DATA_FILE_CANNOT_READ, dataFileName), null);
            }
        }

        List<GrantIngestRecord> resultSet;

        //now do things;
        if (!action.equals("load")) { //action includes a pull - need to build a result set
            //establish the start dateTime - it is either given as an option, or it is
            //the last entry in the update_timestamps file

            if (mode.equals("grant") || mode.equals("user")) { //these aren't used for "funder"
                if (startDate != null) {
                    if (!startDate.isEmpty()) {
                        if (!verifyDateTimeFormat(startDate)) {
                            throw processException(format(ERR_INVALID_COMMAND_LINE_TIMESTAMP, startDate), null);
                        }
                    } else {
                        startDate = getLatestTimestamp();
                        if (!verifyDateTimeFormat(startDate)) {
                            throw processException(format(ERR_INVALID_TIMESTAMP, startDate), null);
                        }
                    }
                }
                if (awardEndDate != null) {
                    if (!verifyDate(awardEndDate)) {
                        throw processException(format(ERR_INVALID_COMMAND_LINE_DATE, awardEndDate), null);
                    }
                }
            }

            try {
                resultSet = grantConnector.retrieveUpdates(startDate, awardEndDate, mode, grant);
            } catch (SQLException e) {
                throw processException(ERR_SQL_EXCEPTION, e);
            } catch (RuntimeException e) {
                throw processException("Runtime Exception", e);
            }
        } else { //just doing a PASS load, must have results set in the data file
            try {
                resultSet = GrantDataCsvFileUtils.readGrantIngestCsv(dataFile);
            } catch (IOException ex) {
                throw processException("Error loading CSV data file", ex);
            }
        }

        if (resultSet == null) { //this shouldn't happen
            throw processException(ERR_RESULT_SET_NULL, null);
        }

        //update PASS if required
        if (!action.equals("pull")) {
            try {
                passUpdater.updatePass(resultSet, mode);
            } catch (RuntimeException e) {
                throw processException("Runtime Exception", e);
            }

            //apparently the hard part has succeeded, let's write the timestamp to our update timestamps file
            String updateTimestamp = passUpdater.getLatestUpdate();
            if (verifyDateTimeFormat(updateTimestamp)) {
                try {
                    appendLineToFile(updateTimestampsFile, passUpdater.getLatestUpdate());
                } catch (IOException e) {
                    throw processException(
                        format(ERR_COULD_NOT_APPEND_UPDATE_TIMESTAMP, passUpdater.getLatestUpdate()), null);
                }
            }
            //now everything succeeded - log this result
            String message = passUpdater.getReport();
            LOG.warn(message);
            if (!passUpdater.getIngestRecordErrors().isEmpty()) {
                throw processException("!!There were record data errors during load!!", null);
            }
        } else { //don't need to update, just write the result set out to the data file
            try {
                GrantDataCsvFileUtils.writeGrantIngestCsv(resultSet, dataFile.toPath());
            } catch (IOException ex) {
                throw processException("Error writing CSV data file", ex);
            }
            //do some notification
            int size = resultSet.size();
            StringBuilder sb = new StringBuilder();
            sb.append("Wrote result set for ");
            sb.append(size);
            sb.append(" ");
            sb.append(mode);
            sb.append(" record");
            sb.append((size == 1 ? "" : "s")); //handle plural correctly
            sb.append(" into file ");
            sb.append(dataFileName);
            sb.append("\n");
            String message = sb.toString();
            LOG.warn(message);
        }
    }

    /**
     * Ths method returns  a string representing the timestamp on the last line of the updated timestamps file
     *
     * @return the timestamp string
     * @throws PassCliException if the updated timestamps file could not be accessed
     */
    private String getLatestTimestamp() throws PassCliException {
        String lastLine = "";
        if (!updateTimestampsFile.exists()) {
            throw processException(format(ERR_REQUIRED_CONFIGURATION_FILE_MISSING, updateTimestampsFileName), null);
        } else {
            try (BufferedReader br = new BufferedReader(new FileReader(updateTimestampsFile))) {
                String readLine;
                while ((readLine = br.readLine()) != null) {
                    lastLine = readLine;
                }
            } catch (IOException e) {
                throw processException(ERR_COULD_NOT_OPEN_CONFIGURATION_FILE, e);
            }
            lastLine = lastLine.replaceAll("[\\r\\n]", "");
        }
        return lastLine;
    }

    /**
     * This method appends the timestamp representing the latest update timestamp of all of the {@code Grant}s being
     * processed
     * in this running of the loader
     *
     * @param file         - the {@code File} to write to
     * @param updateString - the timestamp string to append to the {@code File}
     * @throws IOException if the append fails
     */
    private void appendLineToFile(File file, String updateString) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(
            new FileOutputStream(file.getCanonicalPath(), true), StandardCharsets.UTF_8);
        BufferedWriter fbw = new BufferedWriter(writer);
        fbw.write(updateString);
        fbw.newLine();
        fbw.close();
    }

    /**
     * This method logs the supplied message and exception, reports the {@code Exception} to STDOUT
     *
     * @param message - the error message
     * @param e       - the Exception
     * @return = the {@code PassCliException} wrapper
     */
    private PassCliException processException(String message, Exception e) {
        PassCliException clie;
        if (e != null) {
            clie = new PassCliException(message, e);
            LOG.error(message, e);
        } else {
            clie = new PassCliException(message);
            LOG.error(message);
        }
        return clie;
    }

    /**
     * This method determines which objects may be updated - override in child classes
     *
     * @param s the string for the mode
     * @return whether we support this mode
     */
    protected boolean checkMode(String s) {
        return (s.equals("user") || s.equals("grant") || s.equals("funder"));
    }

}
