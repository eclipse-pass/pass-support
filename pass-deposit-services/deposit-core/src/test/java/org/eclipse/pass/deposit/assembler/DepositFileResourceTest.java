/*
 * Copyright 2025 Johns Hopkins University
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
package org.eclipse.pass.deposit.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.eclipse.pass.deposit.model.DepositFile;
import org.eclipse.pass.deposit.model.DepositFileType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
class DepositFileResourceTest {

    @Test
    void testDepositFileResource() throws IOException {
        // GIVEN
        DepositFile depositFile = new DepositFile();
        depositFile.setType(DepositFileType.supplement);
        depositFile.setName("test-name");
        depositFile.setLabel("test-label");
        depositFile.setLocation("/submissions/sample1/Chart.jpg");
        ClassPathResource resource = new ClassPathResource(depositFile.getLocation());

        // WHEN
        DepositFileResource depositFileResource = new DepositFileResource(depositFile, resource);

        // THEN
        assertTrue(depositFileResource.exists());
        assertTrue(depositFileResource.isReadable());
        assertFalse(depositFileResource.isOpen());
        assertTrue(depositFileResource.isFile());
        assertNotNull(depositFileResource.getURL());
        assertNotNull(depositFileResource.getURI());
        assertNotNull(depositFileResource.getFile());
        assertNotNull(depositFileResource.getInputStream());
        assertNotNull(depositFileResource.readableChannel());
        assertEquals("class path resource [submissions/sample1/Chart.jpg]", depositFileResource.getDescription());
        assertEquals("Chart.jpg", depositFileResource.getFilename());
        assertEquals("DepositFileResource{resource=class path resource [submissions/sample1/Chart.jpg], " +
            "depositFile=DepositFile{type=supplement, name='test-name', label='test-label', " +
            "location='/submissions/sample1/Chart.jpg'}}", depositFileResource.toString());
        assertEquals(199679, depositFileResource.contentLength());
        assertTrue(depositFileResource.lastModified() > 0);
    }

    @Test
    void testDepositFileResourceCreateRelative() throws IOException {
        // GIVEN
        DepositFile depositFile = new DepositFile();
        depositFile.setLocation("/submissions/sample1/Chart.jpg");
        ClassPathResource resource = new ClassPathResource(depositFile.getLocation());
        DepositFileResource depositFileResource = new DepositFileResource(depositFile, resource);

        // WHEN
        Resource relativeResource = depositFileResource.createRelative("Figure2.png");

        // THEN
        assertTrue(relativeResource.exists());
    }
}
