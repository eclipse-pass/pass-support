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

package org.eclipse.pass.deposit.messaging.config.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PropertyResolvingDeserializerTest extends AbstractJacksonMappingTest {

    @Test
    public void noPropertyResolutionTest() throws Exception {
        RepositoryConfig config = repositoriesMapper.readValue(RepositoryConfigMappingTest.SWORD_REPOSITORY_JSON,
            RepositoryConfig.class);
        assertEquals(SwordV2Binding.PROTO, config.getTransportConfig().getProtocolBinding().getProtocol());
        SwordV2Binding swordV2Binding = (SwordV2Binding) config.getTransportConfig().getProtocolBinding();
        assertTrue(swordV2Binding.getDefaultCollectionUrl().contains("http://localhost:8181"));
    }

    @Test
    public void resolvePropertiesTest() throws Exception {
        RepositoryConfig config = repositoriesMapper.readValue(RepositoryConfigMappingTest.SWORD_REPOSITORY_JSON,
            RepositoryConfig.class);
        assertTrue(config.getTransportConfig().getProtocolBinding().getProtocol().equals(SwordV2Binding.PROTO));
        SwordV2Binding swordV2Binding = (SwordV2Binding) config.getTransportConfig().getProtocolBinding();
        assertFalse(swordV2Binding.getDefaultCollectionUrl().contains("${dspace.host}"));
    }
}
