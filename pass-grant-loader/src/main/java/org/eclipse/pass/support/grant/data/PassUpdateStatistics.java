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

import static java.lang.String.format;

/**
 * A utility class to collect and disseminate statistics related to an update
 */
class PassUpdateStatistics {

    private int grantsUpdated = 0;
    private int fundersUpdated = 0;
    private int usersUpdated = 0;
    private int grantsCreated = 0;
    private int fundersCreated = 0;
    private int usersCreated = 0;
    private int pisAdded = 0;
    private int coPisAdded = 0;
    private String latestUpdateString = "";
    private String report = "";

    private String type;

    String getReport() {
        return report;
    }

    void setReport(int resultSetSize, int size) {
        StringBuilder sb = new StringBuilder();

        switch (type) {
            case "grant":
                sb.append(format("%s grant records processed; the most recent update in this batch has timestamp %s",
                                 resultSetSize, latestUpdateString));
                sb.append("\n");
                sb.append(format("%s Pis and %s Co-Pis were processed on %s grants", pisAdded, coPisAdded, size));
                sb.append("\n\n");
                sb.append("Pass Activity");
                sb.append("\n\n");
                sb.append(format("%s Grants were created; %s Grants were updated", grantsCreated, grantsUpdated));
                sb.append("\n");
                sb.append(format("%s Users were created; %s Users were updated", usersCreated, usersUpdated));
                sb.append("\n");
                sb.append(format("%s Funders were created; %s Funders were updated", fundersCreated, fundersUpdated));
                sb.append("\n");
                break;
            case "user":
                sb.append(format("%s user records processed; the most recent update in this batch has timestamp %s",
                                 resultSetSize, latestUpdateString));
                sb.append("\n");
                sb.append("Pass Activity");
                sb.append("\n\n");
                sb.append(format("%s Users were created; %s Users were updated", usersCreated, usersUpdated));
                sb.append("\n");
                break;
            case "funder":
                sb.append(format("%s funder records processed",
                                 resultSetSize));
                sb.append("\n\n");
                sb.append(format("%s Funders were created; %s Funders were updated", fundersCreated, fundersUpdated));
                sb.append("\n");
                break;
            default:
                break;
        }
        this.report = sb.toString();
    }

    void reset() {
        grantsUpdated = 0;
        fundersUpdated = 0;
        usersUpdated = 0;
        grantsCreated = 0;
        fundersCreated = 0;
        usersCreated = 0;
        pisAdded = 0;
        coPisAdded = 0;
        latestUpdateString = "";
        report = "";
    }

    int getGrantsUpdated() {
        return grantsUpdated;
    }

    void addGrantsUpdated() {
        grantsUpdated++;
    }

    int getFundersUpdated() {
        return fundersUpdated;
    }

    void addFundersUpdated() {
        fundersUpdated++;
    }

    int getUsersUpdated() {
        return usersUpdated;
    }

    void addUsersUpdated() {
        usersUpdated++;
    }

    int getGrantsCreated() {
        return grantsCreated;
    }

    void addGrantsCreated() {
        grantsCreated++;
    }

    int getFundersCreated() {
        return fundersCreated;
    }

    void addFundersCreated() {
        fundersCreated++;
    }

    int getUsersCreated() {
        return usersCreated;
    }

    void addUsersCreated() {
        usersCreated++;
    }

    int getPisAdded() {
        return pisAdded;
    }

    void addPi() {
        pisAdded++;
    }

    int getCoPisAdded() {
        return coPisAdded;
    }

    void addCoPi() {
        coPisAdded++;
    }

    String getLatestUpdateString() {
        return latestUpdateString;
    }

    void setLatestUpdateString(String latestUpdateString) {
        this.latestUpdateString = latestUpdateString;
    }

    void setType(String type) {
        this.type = type;
    }

}

