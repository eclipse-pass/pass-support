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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.notification.model.NotificationParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HandlebarsParameterizerTest {

    private static final String BODY_TEMPLATE = "" +
            "Dear {{to}},\n" +
            "\n" +
            "A submission titled \"{{#resource_metadata}}{{title}}{{/resource_metadata}}\" been prepared on your " +
            "behalf by {{from}} {{#event_metadata}}{{#if comment}}with comment \"{{comment}}\"{{else}}.{{/if}} " +
            "{{/event_metadata}}\n" +
            "\n" +
            "Please review the submission at the following URL:\n" +
            "{{#each link_metadata}}{{#eq rel \"submissionReview\"}}{{href}}{{else}}{{/eq}}{{/each}}\n" +
            "https://{{app_domain}}";

    private static final String COMMENT_STRING = "How does this look?";

    private static final String EVENT_METADATA = "" +
            "{\n" +
            "  \"comment\": \"" + COMMENT_STRING + "\"\n" +
            "}";

    private static final String ARTICLE_TITLE = "Article title";

    private static final String RESOURCE_METADATA = "" +
            "{\n" +
            "  \"title\": \"" + ARTICLE_TITLE + "\"\n" +
            "}";

    private static final String SUBMISSION_REVIEW_LINK = "https://pass.jhu.edu/app/submission/abc123";

    private static final String LINK_METADATA = "" +
            "[\n" +
            "  {\n" +
            "    \"rel\": \"submissionReview\",\n" +
            "    \"href\": \"" + SUBMISSION_REVIEW_LINK + "\"\n" +
            "  }\n" +
            "]";

    private static final String FROM = "preparer@jhu.edu";

    private static final String TO = "authorized-submitter@jhu.edu";

    private Map<NotificationParam, String> paramMap;

    private HandlebarsParameterizer handlebarsParameterizer;

    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setUp() throws Exception {
        paramMap = new HashMap<>();
        paramMap.put(NotificationParam.TO, TO);
        paramMap.put(NotificationParam.FROM, FROM);
        paramMap.put(NotificationParam.RESOURCE_METADATA, RESOURCE_METADATA);
        paramMap.put(NotificationParam.EVENT_METADATA, EVENT_METADATA);
        paramMap.put(NotificationParam.LINKS, LINK_METADATA);
        paramMap.put(NotificationParam.APP_DOMAIN, "test-app-domain");
        handlebarsParameterizer = new HandlebarsParameterizer(mapper);
    }

    @Test
    public void simpleParameterization() {
        String parameterized = handlebarsParameterizer.parameterize(paramMap,
                                                      new ByteArrayInputStream(BODY_TEMPLATE.getBytes()));

        assertTrue(parameterized.contains(TO));
        assertTrue(parameterized.contains(FROM));
        assertTrue(parameterized.contains(COMMENT_STRING));
        assertTrue(parameterized.contains(SUBMISSION_REVIEW_LINK));
        assertTrue(parameterized.contains(ARTICLE_TITLE));
        assertTrue(parameterized.contains("https://test-app-domain"));
    }

    @Test
    public void urlEncoding() throws IOException {
        String href = "http://example.org?queryParam=value";
        HashMap<NotificationParam, String> paramMap = new HashMap<>() {{
                    put(NotificationParam.TO, href);
                }};
        String parameterized = handlebarsParameterizer.parameterize(paramMap,
                                                      IOUtils.toInputStream("{{to}}", "UTF-8"));

        assertEquals(href, parameterized);
    }

    @Test
    public void testAbbreviate() {
        String expectedTitle = "this is...";
        HashMap<NotificationParam, String> paramMap = new HashMap<>() {{
                    put(NotificationParam.RESOURCE_METADATA, "{ \"title\": \"this is a test for loooong\" }");
                }};
        String parameterized = handlebarsParameterizer.parameterize(paramMap,
            IOUtils.toInputStream("{{abbreviate resource_metadata.title 10}}", "UTF-8"));

        assertEquals(expectedTitle, parameterized);
    }
}