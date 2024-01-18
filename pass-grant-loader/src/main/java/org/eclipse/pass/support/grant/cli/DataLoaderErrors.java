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
package org.eclipse.pass.support.grant.cli;

/**
 * A class containing all error strings for errors caught by the Loader Apps
 */
class DataLoaderErrors {
    private DataLoaderErrors() {
        //never called
    }

    static String ERR_HOME_DIRECTORY_NOT_FOUND = "No home directory found for the application. Please specify a valid" +
                                                 " absolute path.";
    static String ERR_HOME_DIRECTORY_NOT_READABLE_AND_WRITABLE = "Supplied home directory must be readable" +
                                                                 " and writable by the user running this application.";
    static String ERR_REQUIRED_CONFIGURATION_FILE_MISSING = "Required file %s is missing in the specified home " +
                                                            "directory.";
    static String ERR_COULD_NOT_OPEN_CONFIGURATION_FILE = "Could not open configuration file";
    static String ERR_REQUIRED_DATA_FILE_MISSING = "Data file %s does not exist";
    static String ERR_DATA_FILE_CANNOT_READ = "Could not read data file %s";
    static String ERR_INVALID_COMMAND_LINE_TIMESTAMP = "An invalid timestamp was specified on the command line: %s. " +
                                                       "Please make sure it" +
                                                       " is of the form yyyy-mm-dd hh:mm:ss.m{mm}";
    static String ERR_INVALID_TIMESTAMP = "An invalid timestamp was found at the last line of the update timestamp " +
                                          "file. Please make sure it" +
                                          " is of the form yyyy-mm-dd hh:mm:ss.m{mm}";
    static String ERR_INVALID_COMMAND_LINE_DATE = "An invalid date was specified on the command line: %s. Please make" +
                                                  " sure it is of the form MM/dd/yyyy";
    static String ERR_COULD_NOT_APPEND_UPDATE_TIMESTAMP = "The updated succeeded, but could not append last modified " +
                                                          "date %s to update timestamp file";
    static String ERR_SQL_EXCEPTION = "An SQL error occurred querying the grant data source";
    static String ERR_MODE_NOT_VALID = "%s is not a valid mode - must be either \"grant\" or \"user\"";
    static String ERR_ACTION_NOT_VALID = "%s is not a valid action - must be either \"pull\" or \"load\"";
    static String ERR_RESULT_SET_NULL = "The result set was null - either the data pull failed, or there was an error" +
                                        " reading the result set from the data file";
}
