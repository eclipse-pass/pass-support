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
package org.eclipse.pass.deposit.messaging.service;

import static org.eclipse.pass.deposit.messaging.service.DepositUtil.UNKNOWN_DATETIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import javax.jms.Session;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jms.JmsProperties;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositUtilTest {
    @Test
    public void testContains() throws Exception {
        assertTrue(DepositUtil.csvStringContains("event", "event"));
        assertTrue(DepositUtil.csvStringContains("event", "foo, event"));
        assertFalse(DepositUtil.csvStringContains("event", "foo"));
        assertFalse(DepositUtil.csvStringContains("event", "foo, bar"));
        assertFalse(DepositUtil.csvStringContains("event", ""));
        assertFalse(DepositUtil.csvStringContains("event", null));
    }

    @Test
    public void parseTimestamp() throws Exception {
        String dateTime = DepositUtil.parseDateTime(Instant.now().toEpochMilli());
        assertNotNull(dateTime);
        assertFalse(UNKNOWN_DATETIME.equals(dateTime));
    }

    @Test
    public void parseNegativeTimestamp() throws Exception {
        assertEquals(UNKNOWN_DATETIME, DepositUtil.parseDateTime(-1));
    }

    @Test
    public void asAcknowledgeModeValid() throws Exception {
        assertEquals(JmsProperties.AcknowledgeMode.AUTO, DepositUtil.asAcknowledgeMode(Session.AUTO_ACKNOWLEDGE));
        assertEquals(JmsProperties.AcknowledgeMode.CLIENT, DepositUtil.asAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE));
        assertEquals(JmsProperties.AcknowledgeMode.DUPS_OK, DepositUtil.asAcknowledgeMode(Session.DUPS_OK_ACKNOWLEDGE));
    }

    public void asAcknowledgeModeInvalid() throws Exception {
        assertThrows(RuntimeException.class, () -> {
            DepositUtil.asAcknowledgeMode(-1);
        });
    }

    @Test
    public void parseAckMode() throws Exception {
        Session session = mock(Session.class);
        when(session.getAcknowledgeMode())
            .thenReturn(Session.AUTO_ACKNOWLEDGE)
            .thenReturn(Session.CLIENT_ACKNOWLEDGE)
            .thenReturn(Session.DUPS_OK_ACKNOWLEDGE)
            .thenReturn(-1);

        assertEquals("AUTO", DepositUtil.parseAckMode(session, null, null));
        assertEquals("CLIENT", DepositUtil.parseAckMode(session, null, null));
        assertEquals("DUPS_OK", DepositUtil.parseAckMode(session, null, null));
        assertEquals("UNKNOWN", DepositUtil.parseAckMode(session, null, null));
    }
}