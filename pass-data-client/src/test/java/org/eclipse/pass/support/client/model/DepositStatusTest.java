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
package org.eclipse.pass.support.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class DepositStatusTest {
    private static Stream<Arguments> provideStatuses() {
        return Stream.of(
            Arguments.of(DepositStatus.ACCEPTED, true),
            Arguments.of(DepositStatus.REJECTED, true),
            Arguments.of(DepositStatus.FAILED, true),
            Arguments.of(DepositStatus.SUBMITTED, false),
            Arguments.of(DepositStatus.RETRY, false),
            Arguments.of(null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStatuses")
    void testTerminalStatus(DepositStatus depositStatus, boolean expectedTerminalStatus) {
        boolean terminalStatus = DepositStatus.isTerminalStatus(depositStatus);
        assertEquals(expectedTerminalStatus, terminalStatus);
    }
}
