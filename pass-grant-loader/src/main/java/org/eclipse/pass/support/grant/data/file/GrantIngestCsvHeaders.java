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
package org.eclipse.pass.support.grant.data.file;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public enum GrantIngestCsvHeaders {
    GRANT_NUMBER,
    GRANT_TITLE,
    AWARD_NUMBER,
    AWARD_STATUS,
    AWARD_DATE,
    AWARD_START,
    AWARD_END,
    PRIMARY_FUNDER_NAME,
    PRIMARY_FUNDER_CODE,
    DIRECT_FUNDER_NAME,
    DIRECT_FUNDER_CODE,
    PI_FIRST_NAME,
    PI_MIDDLE_NAME,
    PI_LAST_NAME,
    PI_EMAIL,
    PI_INSTITUTIONAL_ID,
    PI_EMPLOYEE_ID,
    PI_ROLE,
    UPDATE_TIMESTAMP
}
