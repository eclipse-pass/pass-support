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
package org.eclipse.pass.support.grant.data;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * This interface defines methods for connecting to a grant datasource for us with PASS
 */
public interface GrantConnector {

    /**
     * This method retrieves the data from a data source. The format is a List of Maps - one List element for each
     * grant or user record.
     *
     * @param startDate - the date of the earliest record we wish to get on this pull
     * @param awardEndDate - the end date of the award
     * @param mode      - indicates whether the data pull is for grants, or users
     * @param grant      - a grant number
     * @throws SQLException           if there is an SQL exception
     */
    List<Map<String, String>> retrieveUpdates(String startDate, String awardEndDate, String mode, String grant) throws
        SQLException;

}
