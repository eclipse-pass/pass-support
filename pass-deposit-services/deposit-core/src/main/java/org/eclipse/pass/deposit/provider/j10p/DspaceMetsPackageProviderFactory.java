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
package org.eclipse.pass.deposit.provider.j10p;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
class DspaceMetsPackageProviderFactory {

    private DspaceMetadataDomWriterFactory domWriterFactory;

    @Autowired
    public DspaceMetsPackageProviderFactory(DspaceMetadataDomWriterFactory domWriterFactory) {
        this.domWriterFactory = domWriterFactory;
    }

    DspaceMetsPackageProvider newInstance() {
        return new DspaceMetsPackageProvider(domWriterFactory);
    }

}
