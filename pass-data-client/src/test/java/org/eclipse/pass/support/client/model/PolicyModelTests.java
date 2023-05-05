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

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createPolicy;
import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure the the equals / hashcode functions work
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class PolicyModelTests {
    @Test
    public void testPolicyEqualsAndHashCode()  {
        Policy policy1 = createPolicy(TestValues.POLICY_ID_1);
        Policy policy2 = createPolicy(TestValues.POLICY_ID_1);

        assertEquals(policy1, policy2);
        assertEquals(policy1.hashCode(), policy2.hashCode());

        policy1.setPolicyUrl(URI.create("https://somethingdifferent.test"));
        assertTrue(!policy1.equals(policy2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testPolicyCopyConstructor()  {
        Policy policy = createPolicy(TestValues.POLICY_ID_1);
        List<Repository> repositoriesOrig =
            new ArrayList<Repository>(Arrays.asList(createRepository(TestValues.REPOSITORY_ID_1),
                                             createRepository(TestValues.REPOSITORY_ID_2)));
        policy.setRepositories(repositoriesOrig);

        Policy policyCopy = new Policy(policy);
        assertEquals(policy, policyCopy);

        URI newInstitution = URI.create("different:institution");
        policyCopy.setInstitution(newInstitution);
        assertEquals(URI.create(TestValues.INSTITUTION_ID_1), policy.getInstitution());
        assertEquals(newInstitution, policyCopy.getInstitution());

        List<Repository> repositoriesNew =
            new ArrayList<Repository>(Arrays.asList(createRepository(TestValues.REPOSITORY_ID_2)));
        policyCopy.setRepositories(repositoriesNew);
        assertEquals(repositoriesOrig, policy.getRepositories());
        assertEquals(repositoriesNew, policyCopy.getRepositories());
    }
}
