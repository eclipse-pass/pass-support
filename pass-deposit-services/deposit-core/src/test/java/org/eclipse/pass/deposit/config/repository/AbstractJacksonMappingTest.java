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

package org.eclipse.pass.deposit.config.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.pass.deposit.DepositApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = DepositApp.class)
@TestPropertySource(
    locations = "/test-application.properties",
    properties = {
        "dspace.server=test-dspace-host:8000",
        "pmc.ftp.host=test-ftp-host",
        "pmc.ftp.port=test-ftp-port",
    })
public abstract class AbstractJacksonMappingTest {

    @Autowired
    protected ObjectMapper repositoriesMapper;

    protected <T> void assertRoundTrip(T instance, Class<T> type) throws IOException {
        assertEquals(instance, repositoriesMapper.readValue(repositoriesMapper.writeValueAsString(instance), type));
    }
}
