/*
 *
 *  * Copyright 2018 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package org.eclipse.pass.notification.dispatch.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author rpoetke1@jh.edu
 */
public class SpringUriTemplateResolverTest {

    private SpringUriTemplateResolver springUriTemplateResolver;

    @BeforeEach
    public void setUp() throws Exception {
        springUriTemplateResolver = new SpringUriTemplateResolver();
    }

    @Test
    public void resolveClasspathUrl() {
        String packageName = this.getClass().getPackage().getName().replace('.', '/');
        assertNotNull(springUriTemplateResolver.resolve(null, "classpath:" + packageName + "/" + "templateToResolve"));
    }

    @Disabled("flaky test using example.com")
    @Test
    public void resolveHttpUrl() {
        assertNotNull(springUriTemplateResolver.resolve(null, "http://example.org"));
    }

    @Disabled("flaky test using example.com")
    @Test
    public void resolveHttpsUrl() {
        assertNotNull(springUriTemplateResolver.resolve(null, "https://example.org"));
    }

    @Test
    public void resolveFileUrl() {
        URL u = this.getClass().getResource("templateToResolve");
        assertEquals("file", u.getProtocol());
        assertNotNull(springUriTemplateResolver.resolve(null, u.toString()));
    }

    @Test
    public void resolveLocalFile() {
        URL u = this.getClass().getResource("templateToResolve");
        assertEquals("file", u.getProtocol());
        assertNotNull(springUriTemplateResolver.resolve(null, u.getPath()));
    }

    @Test
    public void resolveUnsupportedProtocol() throws MalformedURLException {
        assertNull(springUriTemplateResolver.resolve(null,
                new URL("jar:file:/path/to/file.jar!/path/to/resource").toString()));
    }
}