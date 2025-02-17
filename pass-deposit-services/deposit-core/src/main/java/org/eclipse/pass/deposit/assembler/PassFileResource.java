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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.eclipse.pass.support.client.PassClient;
import org.springframework.core.io.AbstractResource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class PassFileResource extends AbstractResource {

    private final PassClient passClient;
    private final String passFileId;
    private final String filename;

    public PassFileResource(PassClient passClient, String passFileId, String filename) {
        this.passClient = passClient;
        this.passFileId = passFileId;
        this.filename = filename;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return passClient.downloadFile(passFileId);
    }

    @Override
    public byte[] getContentAsByteArray() throws IOException {
        return super.getContentAsByteArray();
    }

    @Override
    public String getContentAsString(Charset charset) throws IOException {
        return super.getContentAsString(charset);
    }

    @Override
    public String getDescription() {
        return "PassFileResource File ID: " + passFileId;
    }

    @Override
    public String getFilename() {
        return filename;
    }
}
