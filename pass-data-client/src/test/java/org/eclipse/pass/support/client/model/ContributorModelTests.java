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

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createContributor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure the equals / hashcode functions work.
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class ContributorModelTests {
    @Test
    public void testContributorEqualsAndHashCode() {
        Contributor contributor1 = createContributor(TestValues.CONTRIBUTOR_ID_1, TestValues.USER_ID_1);
        Contributor contributor2 = createContributor(TestValues.CONTRIBUTOR_ID_1, TestValues.USER_ID_1);

        assertEquals(contributor1, contributor2);
        assertEquals(contributor1.hashCode(), contributor2.hashCode());

        contributor1.setFirstName("different");
        assertTrue(!contributor1.equals(contributor2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testContributorCopyConstructor()  {
        Contributor contributor = createContributor(TestValues.CONTRIBUTOR_ID_1, TestValues.USER_ID_1);
        Contributor contributorCopy = new Contributor(contributor);
        assertEquals(contributor, contributorCopy);

        String newEmail = "differentemail@differentemail.com";
        contributorCopy.setEmail(newEmail);
        assertEquals(TestValues.USER_EMAIL, contributor.getEmail());
        assertEquals(newEmail, contributorCopy.getEmail());

        contributorCopy.setUser(createUser(TestValues.USER_ID_2));
        assertEquals(TestValues.USER_ID_1, contributor.getUser().getId());
        assertEquals(TestValues.USER_ID_2, contributorCopy.getUser().getId());
    }

    private User createUser(String id) {
        User user = new User();
        user.setId(id);
        user.setFirstName(TestValues.USER_FIRST_NAME);
        user.setMiddleName(TestValues.USER_MIDDLE_NAME);
        user.setLastName(TestValues.USER_LAST_NAME);
        user.setDisplayName(TestValues.USER_DISPLAY_NAME);
        user.setEmail(TestValues.USER_EMAIL);
        user.setOrcidId(TestValues.USER_ORCID_ID);
        user.setAffiliation(TestValues.USER_AFFILIATION);

        return user;
    }
}
