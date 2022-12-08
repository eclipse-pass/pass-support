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

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createRepository;
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
public class RepositoryModelTests {
    @Test
    public void testRepositoryEqualsAndHashCode()  {
        Repository repository1 = createRepository(TestValues.REPOSITORY_ID_1);
        Repository repository2 = createRepository(TestValues.REPOSITORY_ID_1);

        assertEquals(repository1, repository2);
        assertEquals(repository1.hashCode(), repository2.hashCode());

        repository1.setName("different");
        assertTrue(!repository1.equals(repository2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testRepositoryCopyConstructor()  {
        Repository repository = createRepository(TestValues.REPOSITORY_ID_1);
        Repository repositoryCopy = new Repository(repository);
        assertEquals(repository, repositoryCopy);

        String newAgreementText = "new agreement text";
        repositoryCopy.setAgreementText(newAgreementText);
        assertEquals(TestValues.REPOSITORY_AGREEMENTTEXT, repository.getAgreementText());
        assertEquals(newAgreementText, repositoryCopy.getAgreementText());

        repositoryCopy.setIntegrationType(IntegrationType.ONE_WAY);
        assertEquals(IntegrationType.of(TestValues.REPOSITORY_INTEGRATION_TYPE), repository.getIntegrationType());
        assertEquals(IntegrationType.ONE_WAY, repositoryCopy.getIntegrationType());
    }
}
