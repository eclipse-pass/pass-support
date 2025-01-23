/*
 * Copyright 2025 Johns Hopkins University
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
package org.eclipse.pass.deposit.transport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.eclipse.pass.deposit.DepositApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@SpringBootTest(classes = DepositApp.class)
@TestPropertySource(
    locations = "/test-application.properties",
    properties = {
        "pass.repo.verify.connect.timeout.ms=2000"
    })
@WireMockTest(httpPort = 9030)
class RepositoryConnectivityServiceTest {

    @Autowired private RepositoryConnectivityService repositoryConnectivityService;

    @Test
    void verifyConnectByURL_Success() {
        // GIVEN
        stubFor(get("/test/connection").willReturn(ok()));

        // WHEN
        boolean result = repositoryConnectivityService.verifyConnectByURL("http://localhost:9030/test/connection");

        // THEN
        assertTrue(result);
    }

    @Test
    void verifyConnectByURL_Fail() {
        // GIVEN
        stubFor(get("/test/connection").willReturn(ok()));

        // WHEN
        boolean result = repositoryConnectivityService.verifyConnectByURL("http://localhost:8030/test/connection");

        // THEN
        assertFalse(result);
    }

    @Test
    void verifyConnectByURL_Success400Response() {
        // GIVEN
        stubFor(get("/test/connection").willReturn(badRequest()));

        // WHEN
        boolean result = repositoryConnectivityService.verifyConnectByURL("http://localhost:9030/test/connection");

        // THEN
        assertTrue(result);
    }

    @Test
    void verifyConnectByURL_Fail500Response() {
        // GIVEN
        stubFor(get("/test/connection").willReturn(aResponse().withStatus(500)));

        // WHEN
        boolean result = repositoryConnectivityService.verifyConnectByURL("http://localhost:9030/test/connection");

        // THEN
        assertFalse(result);
    }

    @Test
    void verifyConnect_Success() {
        // WHEN
        boolean result = repositoryConnectivityService.verifyConnect("localhost", 9030);

        // THEN
        assertTrue(result);
    }

    @Test
    void verifyConnect_Failure() {
        // WHEN
        boolean result = repositoryConnectivityService.verifyConnect("localhost", 8030);

        // THEN
        assertFalse(result);
    }

}
