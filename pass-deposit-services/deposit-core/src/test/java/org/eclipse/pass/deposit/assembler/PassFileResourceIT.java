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
package org.eclipse.pass.deposit.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.eclipse.pass.deposit.AbstractDepositSubmissionIT;
import org.eclipse.pass.support.client.model.File;
import org.junit.jupiter.api.Test;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class PassFileResourceIT extends AbstractDepositSubmissionIT {

    @Test
    void testGetInputStream() throws IOException {
        // GIVEN
        File file = new File();
        String expectedData = "What's in a name?";
        file.setName("rose.txt");
        URI fileUri = passClient.uploadBinary(file.getName(), expectedData.getBytes(StandardCharsets.UTF_8));
        file.setUri(fileUri);
        passClient.createObject(file);

        PassFileResource passFileResource = new PassFileResource(passClient, file.getId());

        // WHEN
        InputStream inputStream = passFileResource.getInputStream();

        // THEN
        String actualData = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(expectedData, actualData);
    }
}
