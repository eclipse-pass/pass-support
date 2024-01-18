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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class JhuGrantLoaderManualTest {

    /**
     * This is a manual test that can run locally to test pulling the COEUS data into a file.
     * You need to set the system prop COEUS_HOME to the path of the test/resouces dir.
     * You also need the connection.properties file in test/resources which contains connection props
     * for COEUS and the Directory Service.
     * Be careful with the startDateTime to no pull too much data.  Know what the impact is on pulling
     * data before running this test.
     */
    @Disabled
    @Test
    public void testPullCoeusFile() {
        System.setProperty(
                "COEUS_HOME",
                "full_path_to/pass-support/pass-grant-loader/pass-grant-cli/src/test/resources"
        );
        String[] args = {"-a", "pull", "-s", "2023-04-01 00:00:00.000", "-z", "04/01/2023",
            "full_path_to/testresults"};
        JhuGrantLoaderCLI.main(args);
    }

}
