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
package org.eclipse.pass.support.grant.cli.jhu;

import java.util.Properties;

import org.eclipse.pass.support.grant.cli.AbstractBaseGrantLoaderApp;
import org.eclipse.pass.support.grant.data.GrantConnector;
import org.eclipse.pass.support.grant.data.PassUpdater;
import org.eclipse.pass.support.grant.data.jhu.CoeusConnector;
import org.eclipse.pass.support.grant.data.jhu.JhuPassInitUpdater;
import org.eclipse.pass.support.grant.data.jhu.JhuPassUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JHU Grant Loader App with JHU specific configuration needed for Grant Loader execution.
 */
public class JhuGrantLoaderApp extends AbstractBaseGrantLoaderApp {

    private static final Logger LOG = LoggerFactory.getLogger(JhuGrantLoaderApp.class);

    private final boolean init;

    JhuGrantLoaderApp(String startDate, String awardEndDate, String mode, String action,
                      String dataFileName, boolean init, String grant) {
        super(startDate, awardEndDate, mode, action, dataFileName, grant);
        super.setTimestamp(true);
        this.init = init;
    }

    @Override
    protected boolean checkMode(String s) {
        return (s.equals("user") || s.equals("grant") || s.equals("funder"));
    }

    @Override
    protected GrantConnector configureConnector(Properties connectionProperties) {
        return new CoeusConnector(connectionProperties);
    }

    @Override
    protected PassUpdater configureUpdater(Properties policyProperties) {
        if (init) {
            LOG.warn("**Grant Loader running in init mode**");
            return new JhuPassInitUpdater(policyProperties);
        }
        return new JhuPassUpdater(policyProperties);
    }

}
