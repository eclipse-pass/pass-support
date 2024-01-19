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
package org.eclipse.pass.support.grant.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.csv.CSVRecord;
import org.eclipse.pass.support.grant.data.file.GrantIngestCsvHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ExtendWith(MockitoExtension.class)
public class GrantIngestRecordTest {

    @Test
    void testParse() {
        // GIVEN
        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.get(GrantIngestCsvHeaders.GRANT_NUMBER)).thenReturn("test-grant-number");
        when(csvRecord.get(GrantIngestCsvHeaders.GRANT_TITLE)).thenReturn("test-title");
        when(csvRecord.get(GrantIngestCsvHeaders.AWARD_NUMBER)).thenReturn("test-award-number");
        when(csvRecord.get(GrantIngestCsvHeaders.AWARD_STATUS)).thenReturn("test-award-status");
        when(csvRecord.get(GrantIngestCsvHeaders.AWARD_DATE)).thenReturn("test-award-date");
        when(csvRecord.get(GrantIngestCsvHeaders.AWARD_START)).thenReturn("test-award-start");
        when(csvRecord.get(GrantIngestCsvHeaders.AWARD_END)).thenReturn("test-award-end");
        when(csvRecord.get(GrantIngestCsvHeaders.PRIMARY_FUNDER_NAME)).thenReturn("test-primary-name");
        when(csvRecord.get(GrantIngestCsvHeaders.PRIMARY_FUNDER_CODE)).thenReturn("test-primary-code");
        when(csvRecord.get(GrantIngestCsvHeaders.DIRECT_FUNDER_NAME)).thenReturn("test-direct-name");
        when(csvRecord.get(GrantIngestCsvHeaders.DIRECT_FUNDER_CODE)).thenReturn("test-direct-code");
        when(csvRecord.get(GrantIngestCsvHeaders.PI_FIRST_NAME)).thenReturn("test-pi-fn");
        when(csvRecord.get(GrantIngestCsvHeaders.PI_MIDDLE_NAME)).thenReturn("test-pi-mn");
        when(csvRecord.get(GrantIngestCsvHeaders.PI_LAST_NAME)).thenReturn("test-pi-ln");
        when(csvRecord.get(GrantIngestCsvHeaders.PI_EMAIL)).thenReturn("test-pi-email");
        when(csvRecord.get(GrantIngestCsvHeaders.PI_INSTITUTIONAL_ID)).thenReturn("test-pi-inst-id");
        when(csvRecord.get(GrantIngestCsvHeaders.PI_EMPLOYEE_ID)).thenReturn("test-pi-emp-id");
        when(csvRecord.get(GrantIngestCsvHeaders.PI_ROLE)).thenReturn("test-pi-role");
        when(csvRecord.get(GrantIngestCsvHeaders.UPDATE_TIMESTAMP)).thenReturn("test-update-ts");

        // WHEN
        GrantIngestRecord grantIngestRecord = GrantIngestRecord.parse(csvRecord);

        // THEN
        assertEquals("test-grant-number", grantIngestRecord.getGrantNumber());
        assertEquals("test-title", grantIngestRecord.getGrantTitle());
        assertEquals("test-award-number", grantIngestRecord.getAwardNumber());
        assertEquals("test-award-status", grantIngestRecord.getAwardStatus());
        assertEquals("test-award-date", grantIngestRecord.getAwardDate());
        assertEquals("test-award-start", grantIngestRecord.getAwardStart());
        assertEquals("test-award-end", grantIngestRecord.getAwardEnd());
        assertEquals("test-primary-name", grantIngestRecord.getPrimaryFunderName());
        assertEquals("test-primary-code", grantIngestRecord.getPrimaryFunderCode());
        assertEquals("test-direct-name", grantIngestRecord.getDirectFunderName());
        assertEquals("test-direct-code", grantIngestRecord.getDirectFunderCode());
        assertEquals("test-pi-fn", grantIngestRecord.getPiFirstName());
        assertEquals("test-pi-mn", grantIngestRecord.getPiMiddleName());
        assertEquals("test-pi-ln", grantIngestRecord.getPiLastName());
        assertEquals("test-pi-email", grantIngestRecord.getPiEmail());
        assertEquals("test-pi-inst-id", grantIngestRecord.getPiInstitutionalId());
        assertEquals("test-pi-emp-id", grantIngestRecord.getPiEmployeeId());
        assertEquals("test-pi-role", grantIngestRecord.getPiRole());
        assertEquals("test-update-ts", grantIngestRecord.getUpdateTimeStamp());
    }
}
