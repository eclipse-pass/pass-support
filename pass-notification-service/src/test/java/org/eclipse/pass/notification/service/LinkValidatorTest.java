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
package org.eclipse.pass.notification.service;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.pass.notification.config.LinkValidationRule;
import org.eclipse.pass.notification.config.NotificationConfig;
import org.eclipse.pass.notification.model.Link;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * @author apb@jhu.edu
 */
public class LinkValidatorTest {

    private static final String REL = "myRel";
    private static final String EXPECTED_BASEURI = "http://example.org";
    private static final URI GOOD_URI = URI.create("http://example.org/whatever");
    private static final URI BAD_URI = URI.create("http://ruminant.moo/whatever");

    private Link goodLink;
    private Link badLink;
    private LinkValidationRule rule;

    private NotificationConfig config;
    private Set<LinkValidationRule> rules;

    @BeforeEach
    public void setUp() {
        goodLink = new Link(GOOD_URI, REL);
        badLink = new Link(BAD_URI, REL);

        rule = new LinkValidationRule();
        rule.setRels(List.of(REL));
        rule.setRequiredBaseURI(EXPECTED_BASEURI);

        rules = new HashSet<>();

        config = new NotificationConfig();
        config.setLinkValidatorConfig(rules);
    }

    // Make sure a simple rule passing/failing on baseuri does the right the right thing
    @Test
    public void simpleRuleTest() {
        rules.add(rule);
        final LinkValidator toTest = new LinkValidator(config, JsonMapper.builder().build());
        assertTrue(toTest.test(goodLink));
        assertFalse(toTest.test(badLink));
    }

    // If the config says to throw an exception on on invalid links, verify an exception is thrown.
    @Test
    public void throwExceptionIfRequestedTest() {
        rule.setThrowExceptionOnFailure(true);
        rules.add(rule);
        final LinkValidator toTest = new LinkValidator(config, JsonMapper.builder().build());
        Exception ex = assertThrows(Exception.class, () -> {
            toTest.test(badLink);
        });
        assertTrue(ex.getMessage().contains(badLink.toString()));
        assertTrue(ex.getMessage().contains(EXPECTED_BASEURI));
    }

    // If there are no rules defined at all, all links shall pass.
    @Test
    public void noRulesTest() {
        rules.clear();

        final LinkValidator toTest = new LinkValidator(config, JsonMapper.builder().build());
        assertTrue(toTest.test(badLink));
        assertTrue(toTest.test(badLink));
    }

    // Make sure validation rule matching picks up the appropriate rules, and ignores the others.
    @Test
    public void ruleMatchingTest() {

        final String DIFFERENT_REL = "otherRel";

        final LinkValidationRule DIFFERENT_RULE = new LinkValidationRule();
        DIFFERENT_RULE.setRels(asList(DIFFERENT_REL));
        DIFFERENT_RULE.setRequiredBaseURI(BAD_URI.toString());

        rules.addAll(asList(rule, DIFFERENT_RULE));

        final LinkValidator toTest = new LinkValidator(config, JsonMapper.builder().build());
        assertTrue(toTest.test(goodLink));
        assertFalse(toTest.test(badLink));

        goodLink.setRel(DIFFERENT_REL);
        badLink.setRel(DIFFERENT_REL);

        assertFalse(toTest.test(goodLink));
        assertTrue(toTest.test(badLink));
    }

    // Verify that a rule is applied if it matches _any_ rels in a _list_ of rels.
    @Test
    public void relMatchingTest() {
        final String OTHER_REL_MATCHING = "otherRel";
        final String OTHER_REL_NON_MATCHING = "otherRelNonMatching";

        rule.setRels(asList(REL, OTHER_REL_MATCHING));

        rules.add(rule);

        final LinkValidator toTest = new LinkValidator(config, JsonMapper.builder().build());
        assertFalse(toTest.test(badLink));

        badLink.setRel(OTHER_REL_NON_MATCHING);
        assertTrue(toTest.test(badLink));

        badLink.setRel(OTHER_REL_MATCHING);
        assertFalse(toTest.test(badLink));
    }

    // Make sure a glob matches everything
    @Test
    public void globMatchingTest() {
        final String OTHER_REL_MATCHING = "otherRel";
        final String OTHER_REL_NON_MATCHING = "otherRelNonMatching";

        rule.setRels(asList("*"));

        rules.add(rule);

        final LinkValidator toTest = new LinkValidator(config, JsonMapper.builder().build());
        assertFalse(toTest.test(badLink));

        badLink.setRel(OTHER_REL_NON_MATCHING);
        assertFalse(toTest.test(badLink));

        badLink.setRel(OTHER_REL_MATCHING);
        assertFalse(toTest.test(badLink));
    }

    // Null base URI in a rule simply means it won't be tested.
    @Test
    public void nullBaseUriTest() {
        rule.setRequiredBaseURI(null);
        rules.add(rule);

        final LinkValidator toTest = new LinkValidator(config, JsonMapper.builder().build());
        assertTrue(toTest.test(goodLink));
        assertTrue(toTest.test(badLink));
    }

}
