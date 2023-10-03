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

import java.util.Properties;

import org.eclipse.pass.support.grant.data.CoeusConnector;
import org.eclipse.pass.support.grant.data.GrantConnector;
import org.eclipse.pass.support.grant.data.JhuPassInitUpdater;
import org.eclipse.pass.support.grant.data.JhuPassUpdater;
import org.eclipse.pass.support.grant.data.PassUpdater;

class JhuGrantLoaderApp extends BaseGrantLoaderApp {

    boolean init;

    JhuGrantLoaderApp(String startDate, String awardEndDate, boolean email, String mode, String action,
                      String dataFileName, boolean init, String grant) {
        super(startDate, awardEndDate, email, mode, action, dataFileName, grant);
        super.setTimestamp(true);
        this.init = init;
    }

    @Override
    boolean checkMode(String s) {
        return (s.equals("user") || s.equals("grant") || s.equals("funder"));
    }

    @Override
    GrantConnector configureConnector(Properties connectionProperties, Properties policyProperties) {
        return new CoeusConnector(connectionProperties, policyProperties);
    }

    @Override
    PassUpdater configureUpdater() {
        if (init) {
            return new JhuPassInitUpdater();
        }
        return new JhuPassUpdater();
    }

}
