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

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure the equals / hashcode functions work.
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class UserModelTest {
    @Test
    public void testUserEqualsAndHashCode()  {

        User user1 = createUser(TestValues.USER_ID_1);
        User user2 = createUser(TestValues.USER_ID_1);

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());

        user1.setUsername("different");
        assertTrue(!user1.equals(user2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testUserCopyConstructor()  {
        User user = createUser(TestValues.USER_ID_1);
        List<UserRole> rolesOrig = new ArrayList<UserRole>(Arrays.asList(UserRole.ADMIN));
        user.setRoles(rolesOrig);

        User userCopy = new User(user);
        assertEquals(user, userCopy);

        String newOrcidId = "https://orcid.org/0000-new-orcid-id";
        userCopy.setOrcidId(newOrcidId);
        assertEquals(TestValues.USER_ORCID_ID, user.getOrcidId());
        assertEquals(newOrcidId, userCopy.getOrcidId());

        List<UserRole> rolesNew = new ArrayList<>(Arrays.asList(UserRole.ADMIN, UserRole.SUBMITTER));
        userCopy.setRoles(rolesNew);
        assertEquals(rolesOrig, user.getRoles());
        assertEquals(rolesNew, userCopy.getRoles());
    }
}
