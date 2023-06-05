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

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createPublication;
import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createRepository;
import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createRepositoryCopy;
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
public class RepositoryCopyModelTests {
    @Test
    public void testRepositoryCopyEqualsAndHashCode()  {
        RepositoryCopy repoCopy1 = createRepositoryCopy(TestValues.REPOSITORYCOPY_ID_1);
        RepositoryCopy repoCopy2 = createRepositoryCopy(TestValues.REPOSITORYCOPY_ID_1);

        assertEquals(repoCopy1, repoCopy2);
        assertEquals(repoCopy1.hashCode(), repoCopy2.hashCode());

        repoCopy1.setRepository(createRepository(TestValues.REPOSITORY_ID_2));
        assertTrue(!repoCopy1.equals(repoCopy2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testRepositoryCopyCopyConstructor()  {
        RepositoryCopy repositoryCopy = createRepositoryCopy(TestValues.REPOSITORYCOPY_ID_1);
        List<String> externalIds = new ArrayList<String>(
            Arrays.asList(TestValues.REPOSITORYCOPY_EXTERNALID_1, TestValues.REPOSITORYCOPY_EXTERNALID_2));
        repositoryCopy.setExternalIds(externalIds);
        RepositoryCopy repositoryCopyCopy = new RepositoryCopy(repositoryCopy);
        assertEquals(repositoryCopy, repositoryCopyCopy);

        Publication newPublication = createPublication(TestValues.PUBLICATION_ID_1);
        repositoryCopyCopy.setPublication(newPublication);
        assertEquals(createPublication(TestValues.PUBLICATION_ID_1), repositoryCopy.getPublication());
        assertEquals(newPublication, repositoryCopyCopy.getPublication());

        List<String> externalIdsNew = new ArrayList<String>(Arrays.asList(TestValues.REPOSITORYCOPY_EXTERNALID_2));
        repositoryCopyCopy.setExternalIds(externalIdsNew);
        assertEquals(externalIds, repositoryCopy.getExternalIds());
        assertEquals(externalIdsNew, repositoryCopyCopy.getExternalIds());
    }
}
