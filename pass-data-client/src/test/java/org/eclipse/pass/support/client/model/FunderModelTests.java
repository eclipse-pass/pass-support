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

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createFunder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure the the equals / hashcode functions work.
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class FunderModelTests {
    @Test
    public void testFunderEqualsAndHashCode()  {
        Funder funder1 = createFunder(TestValues.FUNDER_ID_1);
        Funder funder2 = createFunder(TestValues.FUNDER_ID_1);

        assertEquals(funder1, funder2);
        assertEquals(funder1.hashCode(), funder2.hashCode());

        funder1.setName("different");
        assertTrue(!funder1.equals(funder2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testFunderCopyConstructor()  {
        Funder funder = createFunder(TestValues.FUNDER_ID_1);
        Funder funderCopy = new Funder(funder);
        assertEquals(funder, funderCopy);

        String newLocalKey = "different:key";
        funderCopy.setLocalKey(newLocalKey);
        assertEquals(TestValues.FUNDER_LOCALKEY, funder.getLocalKey());
        assertEquals(newLocalKey, funderCopy.getLocalKey());

        URI newUrl = URI.create("different:url");
        funderCopy.setUrl(newUrl);
        assertEquals(URI.create(TestValues.FUNDER_URL), funder.getUrl());
        assertEquals(newUrl, funderCopy.getUrl());
    }
}
