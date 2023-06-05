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
package org.eclipse.pass.support.client.model;

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createGrant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure the the equals / hashcode functions work.
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class GrantModelTests {
    @Test
    public void testGrantEqualsAndHashCode()  {

        Grant grant1 = createGrant(TestValues.GRANT_ID_1);
        Grant grant2 = createGrant(TestValues.GRANT_ID_1);

        assertEquals(grant1, grant2);
        assertEquals(grant1.hashCode(), grant2.hashCode());

        grant1.setAwardNumber("different");
        assertTrue(!grant1.equals(grant2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testGrantCopyConstructor()  {
        Grant grant = createGrant(TestValues.GRANT_ID_1);
        Grant grantCopy = new Grant(grant);
        assertEquals(grant, grantCopy);

        String newLocalKey = "different:key";
        grantCopy.setLocalKey(newLocalKey);
        assertEquals(TestValues.GRANT_LOCALKEY, grant.getLocalKey());
        assertEquals(newLocalKey, grantCopy.getLocalKey());

        ZonedDateTime zdt = ZonedDateTime.parse(TestValues.GRANT_AWARD_DATE_STR_1);
        ZonedDateTime newAwardDate = ZonedDateTime.parse(TestValues.GRANT_AWARD_DATE_STR_2);
        grantCopy.setAwardDate(newAwardDate);
        assertEquals(zdt, grant.getAwardDate());
        assertEquals(newAwardDate, grantCopy.getAwardDate());
    }
}
