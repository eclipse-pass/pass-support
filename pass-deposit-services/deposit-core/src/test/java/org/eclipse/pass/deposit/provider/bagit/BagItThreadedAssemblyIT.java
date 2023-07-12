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
package org.eclipse.pass.deposit.provider.bagit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.pass.deposit.assembler.AbstractThreadedAssemblyIT;
import org.eclipse.pass.deposit.assembler.Assembler;
import org.eclipse.pass.deposit.assembler.PackageOptions;
import org.eclipse.pass.deposit.assembler.PackageVerifier;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class BagItThreadedAssemblyIT extends AbstractThreadedAssemblyIT {

    @Override
    protected Assembler assemblerUnderTest() {
        return new BagItAssembler(mbf, rbf, passClient);
    }

    @Override
    protected Map<String, Object> packageOptions() {
        return new HashMap<>() {
            {
                put(PackageOptions.Spec.KEY, "BagIt 1.0");
                put(PackageOptions.Archive.KEY, PackageOptions.Archive.OPTS.ZIP);
                put(PackageOptions.Compression.KEY, PackageOptions.Compression.OPTS.NONE);
                put(PackageOptions.Checksum.KEY, Arrays.asList(PackageOptions.Checksum.OPTS.SHA512));
                put(BagItPackageProvider.BAGINFO_TEMPLATE, "/bag-info.hbm");
            }
        };
    }

    @Override
    protected PackageVerifier packageVerifier() {
        return new BagItPackageVerifier();
    }
}
