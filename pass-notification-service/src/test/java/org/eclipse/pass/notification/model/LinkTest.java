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
package org.eclipse.pass.notification.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;

import org.eclipse.pass.notification.AbstractNotificationSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class LinkTest extends AbstractNotificationSpringTest {

    private static final String LINK_JSON = "" +
            "{\n" +
            "    \"rel\": \"submissionResource\",\n" +
            "    \"href\": \"https://pass.jhu.edu/fcrepo/rest/submissions/abc123\"\n" +
            "  }";

    private static final String LINKS_JSON = "" +
            "[\n" +
            "  {\n" +
            "    \"rel\": \"approval-requested-newuser\",\n" +
            "    \"href\": \"https://pass.jhu.edu/app/user/invite?token=abc123\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"rel\": \"submissionResource\",\n" +
            "    \"href\": \"https://pass.jhu.edu/fcrepo/rest/submissions/abc123\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"rel\": \"eventResource\",\n" +
            "    \"href\": \"https://pass.jhu.edu/fcrepo/rest/events/xyz789\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"rel\": \"submissionReview\",\n" +
            "    \"href\": \"https://pass.jhu.edu/app/submission/abc123\"\n" +
            "  }\n" +
            "]";

    @Autowired private ObjectMapper objectMapper;

    @Test
    public void parseLinkFromJson() throws IOException {
        Link link = objectMapper.readValue(LINK_JSON, Link.class);

        assertEquals("submissionResource", link.getRel());
        assertEquals(URI.create("https://pass.jhu.edu/fcrepo/rest/submissions/abc123"), link.getHref());
    }

    @Test
    public void parseLinksFromJson() throws IOException {
        Link[] links = objectMapper.readValue(LINKS_JSON, Link[].class);
        assertEquals(4, links.length);
    }

}