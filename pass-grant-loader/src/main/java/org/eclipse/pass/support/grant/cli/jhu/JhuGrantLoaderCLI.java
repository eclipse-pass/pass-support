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

import org.eclipse.pass.support.grant.cli.AbstractBaseGrantLoaderApp;
import org.eclipse.pass.support.grant.cli.AbstractGrantLoaderCLI;

/**
 * This is the JHU Main class to run the grant loader.
 *
 * @author jrm@jhu.edu
 */
public class JhuGrantLoaderCLI extends AbstractGrantLoaderCLI {

    /**
     * The main method which parses the command line arguments and options then runs the JHU grant loader.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        final JhuGrantLoaderCLI jhuGrantLoaderCLI = new JhuGrantLoaderCLI();
        jhuGrantLoaderCLI.runGrantLoader(args);
    }

    @Override
    protected AbstractBaseGrantLoaderApp getGrantLoaderApp(String dataFileName) {
        return new JhuGrantLoaderApp(startDate, awardEndDate, mode, action, dataFileName,
            init, grant);
    }
}
