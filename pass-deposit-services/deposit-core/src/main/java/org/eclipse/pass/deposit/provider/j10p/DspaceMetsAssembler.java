/*
 * Copyright 2018 Johns Hopkins University
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

package org.eclipse.pass.deposit.provider.j10p;

import static org.eclipse.pass.deposit.assembler.AssemblerSupport.buildMetadata;

import java.util.List;
import java.util.Map;

import org.eclipse.pass.deposit.assembler.AbstractAssembler;
import org.eclipse.pass.deposit.assembler.ArchivingPackageStream;
import org.eclipse.pass.deposit.assembler.DepositFileResource;
import org.eclipse.pass.deposit.assembler.MetadataBuilder;
import org.eclipse.pass.deposit.assembler.MetadataBuilderFactory;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.assembler.ResourceBuilderFactory;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.support.client.PassClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DspaceMetsAssembler extends AbstractAssembler {
    /**
     * Package specification URI identifying a DSpace METS SIP.
     */
    public static final String SPEC_DSPACE_METS = "http://purl.org/net/sword/package/METSDSpaceSIP";

    private final DspaceMetsPackageProviderFactory packageProviderFactory;

    @Autowired
    public DspaceMetsAssembler(MetadataBuilderFactory mbf,
                               ResourceBuilderFactory rbf,
                               DspaceMetsPackageProviderFactory packageProviderFactory,
                               PassClient passClient) {
        super(mbf, rbf, passClient);
        this.packageProviderFactory = packageProviderFactory;
    }

    @Override
    protected PackageStream createPackageStream(DepositSubmission submission,
                                                List<DepositFileResource> custodialResources,
                                                MetadataBuilder mb, ResourceBuilderFactory rbf,
                                                Map<String, Object> options) {
        buildMetadata(mb, options);
        DspaceMetsPackageProvider packageProvider = this.packageProviderFactory.newInstance();
        return new ArchivingPackageStream(submission, custodialResources, mb, rbf, options, packageProvider);
    }

}
