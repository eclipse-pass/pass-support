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
package org.eclipse.pass.deposit.assembler;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.pass.support.client.PassClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class PassFileResource extends FileSystemResource {
    private static final Logger LOG = LoggerFactory.getLogger(PassFileResource.class);

    private PassClient passClient;
    private String passFileId;

    public PassFileResource(PassClient passClient, String passFileId) {
        super("test");
        this.passClient = passClient;
        this.passFileId = passFileId;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        // TODO need to implement real call to pass client to get file inputstream
        return null;
    }

}
