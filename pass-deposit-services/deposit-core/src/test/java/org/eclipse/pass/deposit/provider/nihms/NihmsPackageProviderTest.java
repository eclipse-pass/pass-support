/*
 * Copyright 2019 Johns Hopkins University
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
package org.eclipse.pass.deposit.provider.nihms;

import static org.eclipse.pass.deposit.provider.nihms.NihmsPackageProvider.getNonCollidingFilename;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.pass.deposit.model.DepositFileType;
import org.junit.jupiter.api.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsPackageProviderTest {

    @Test
    public void nonCollidingFilename() throws Exception {
        String nameIn;
        String nameOut;

        nameIn = "test.txt";
        nameOut = getNonCollidingFilename(nameIn, DepositFileType.supplement);
        assertTrue(nameIn.contentEquals(nameOut));

        nameIn = "manifest.txt";
        nameOut = getNonCollidingFilename(nameIn, DepositFileType.supplement);
        assertFalse(nameIn.contentEquals(nameOut));

        nameIn = "bulk_meta.xml";
        nameOut = getNonCollidingFilename(nameIn, DepositFileType.supplement);
        assertFalse(nameIn.contentEquals(nameOut));

        nameIn = "bulk_meta.xml";
        nameOut = getNonCollidingFilename(nameIn, DepositFileType.bulksub_meta_xml);
        assertTrue(nameIn.contentEquals(nameOut));
    }

}