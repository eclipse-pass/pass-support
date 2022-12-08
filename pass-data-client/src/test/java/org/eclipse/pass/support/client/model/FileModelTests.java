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

import static org.eclipse.pass.support.client.model.support.TestObjectCreator.createSubmission;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.eclipse.pass.support.client.model.support.TestValues;
import org.junit.jupiter.api.Test;

/**
 * These tests do a simple check to ensure the equals / hashcode functions work.
 *
 * @author Karen Hanson
 * @author Jim Martino
 */
public class FileModelTests {
    @Test
    public void testFileEqualsAndHashCode()  {

        File file1 = createFile();
        File file2 = createFile();

        assertEquals(file1, file2);
        assertEquals(file1.hashCode(), file2.hashCode());

        file1.setDescription("different");
        assertTrue(!file1.equals(file2));
    }

    /**
     * Test copy constructor creates a valid duplicate that is not the same object
     */
    @Test
    public void testFileCopyConstructor()  {
        File file = createFile();
        File fileCopy = new File(file);
        assertEquals(file, fileCopy);

        fileCopy.setFileRole(FileRole.SUPPLEMENTAL);
        assertEquals(FileRole.of(TestValues.FILE_ROLE), file.getFileRole());
        assertEquals(FileRole.SUPPLEMENTAL, fileCopy.getFileRole());

        String newMimeType = "text/html";
        fileCopy.setMimeType(newMimeType);
        assertEquals(TestValues.FILE_MIMETYPE, file.getMimeType());
        assertEquals(newMimeType, fileCopy.getMimeType());
    }

    private File createFile()  {
        File file = new File();
        file.setId(TestValues.FILE_ID_1);
        file.setName(TestValues.FILE_NAME);
        file.setUri(URI.create(TestValues.FILE_URI));
        file.setDescription(TestValues.FILE_DESCRIPTION);
        file.setFileRole(FileRole.of(TestValues.FILE_ROLE));
        file.setMimeType(TestValues.FILE_MIMETYPE);
        file.setSubmission(createSubmission(TestValues.SUBMISSION_ID_1));

        return file;
    }

}
