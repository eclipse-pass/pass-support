/*
 * Copyright 2026 Johns Hopkins University
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
package org.eclipse.pass.loader.nihms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.model.Grant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class GrantAwardSourceTest {

    private static Grant grant(String awardNumber) {
        Grant grant = new Grant();
        grant.setAwardNumber(awardNumber);
        return grant;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFindNihAwards() throws IOException {
        PassClient passClient = mock(PassClient.class);
        when(passClient.streamObjects(any(PassClientSelector.class))).thenReturn(Stream.of(
            grant("R01 EB019135"), grant("5R01EB019135-02"), grant("NSF-12345"), grant(null),
            grant("K23HL153888")));

        Set<NihAward> awards = new GrantAwardSource(passClient, "").findNihAwards();

        assertEquals(Set.of(new NihAward("R01", "EB", "019135"), new NihAward("K23", "HL", "153888")), awards);
        ArgumentCaptor<PassClientSelector<Grant>> selector = ArgumentCaptor.forClass(PassClientSelector.class);
        verify(passClient).streamObjects(selector.capture());
        assertNull(selector.getValue().getFilter());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFindNihAwardsWithFilter() throws IOException {
        PassClient passClient = mock(PassClient.class);
        when(passClient.streamObjects(any(PassClientSelector.class))).thenReturn(Stream.of());

        new GrantAwardSource(passClient, "awardStatus==ACTIVE").findNihAwards();

        ArgumentCaptor<PassClientSelector<Grant>> selector = ArgumentCaptor.forClass(PassClientSelector.class);
        verify(passClient).streamObjects(selector.capture());
        assertEquals("awardStatus==ACTIVE", selector.getValue().getFilter());
    }
}
