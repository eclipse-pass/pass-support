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
package org.eclipse.pass.deposit.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

/**
 * Provides access to test resources found on the classpath.
 *
 * @author Russ Poetker (rpoetke@jhu.edu)
 */
public class ResourceTestUtil {

    private ResourceTestUtil() {}

    public static InputStream readSubmissionJson(String submissionJsonName) throws IOException {
        return new ClassPathResource("/submissions/" + submissionJsonName + ".json").getInputStream();
    }

    /**
     * Locates a test resource on the classpath by its name.
     * Caller is responsible for closing the returned stream.
     *
     * @param resourceName the classpath resource name
     * @param baseClass    the base class to scan from
     * @return the input stream
     */
    public static InputStream findByName(String resourceName, Class<?> baseClass) throws IOException {
        return new ClassPathResource(resourceName, baseClass).getInputStream();
    }

    public static String findByNameAsString(String resourceName, Class<?> baseClass) {
        Resource resource = new ClassPathResource(resourceName, baseClass);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
