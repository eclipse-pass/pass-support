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

import static java.util.Collections.singletonList;
import static org.eclipse.pass.deposit.provider.nihms.NihmsAssembler.SPEC_NIHMS_NATIVE_2022_05;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.pass.deposit.assembler.AbstractAssembler;
import org.eclipse.pass.deposit.assembler.AbstractThreadedAssemblyIT;
import org.eclipse.pass.deposit.assembler.PackageOptions;
import org.eclipse.pass.deposit.assembler.PackageVerifier;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsThreadedAssemblyIT extends AbstractThreadedAssemblyIT {

    @Override
    protected AbstractAssembler assemblerUnderTest() {
        NihmsPackageProviderFactory ppf = new NihmsPackageProviderFactory();
        return new NihmsAssembler(mbf, rbf, ppf, passClient);
    }

    @Override
    protected Map<String, Object> packageOptions() {
        Map<String, String> funderMapping =
                Map.of("johnshopkins.edu:funder:300293", "cdc",
                        "johnshopkins.edu:funder:300484", "nih");
        return new HashMap<>() {
            {
                put(PackageOptions.Spec.KEY, SPEC_NIHMS_NATIVE_2022_05);
                put(PackageOptions.Archive.KEY, PackageOptions.Archive.OPTS.TAR);
                put(PackageOptions.Compression.KEY, PackageOptions.Compression.OPTS.GZIP);
                put(PackageOptions.Checksum.KEY, singletonList(PackageOptions.Checksum.OPTS.SHA256));
                put(NihmsPackageProvider.FUNDER_MAPPING, funderMapping);
            }
        };
    }

    @Override
    protected PackageVerifier packageVerifier() {
        return new NihmsPackageVerifier();
    }

}
