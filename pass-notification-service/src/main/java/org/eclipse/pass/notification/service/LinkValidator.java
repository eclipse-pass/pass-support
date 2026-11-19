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

import static java.lang.String.format;

import java.util.Collection;
import java.util.function.Predicate;

import org.eclipse.pass.notification.config.LinkValidationRule;
import org.eclipse.pass.notification.config.NotificationConfig;
import org.eclipse.pass.notification.model.Link;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @author apb@jhu.edu
 */
@Component
public class LinkValidator implements Predicate<Link> {

    private final Collection<LinkValidationRule> rules;
    private final ObjectMapper objectMapper;

    /**
     * Constructor.
     * @param config the notification config
     * @param objectMapper the object mapper
     */
    public LinkValidator(NotificationConfig config,
                         ObjectMapper objectMapper) {
        this.rules = config.getLinkValidatorConfig();
        this.objectMapper = objectMapper;
    }

    /**
     * Test a link against the set of link validation configs.
     * <p>
     * Any rules that pertain to the matching link relation will be applied. Rules that contain a "*" will match all
     * links
     * </p>
     *
     * @param link Link to test.
     */
    @Override
    public boolean test(Link link) {
        return rules.stream()
                .filter(rule -> rule.getRels().contains(link.getRel()) || rule.getRels().contains("*"))
                .allMatch(rule -> apply(rule, link));
    }

    private boolean apply(LinkValidationRule cfg, Link link) {
        if (cfg.getRequiredBaseURI() != null) {
            final boolean isValid = link.getHref().toString().startsWith(cfg.getRequiredBaseURI());

            if (cfg.isThrowExceptionOnFailure() && !isValid) {
                try {
                    throw new RuntimeException(format("Invalid link %s violates rule %s", link, objectMapper
                            .writerWithDefaultPrettyPrinter().writeValueAsString(cfg)));
                } catch (final JacksonException e) {
                    // Should never happen. This is a fallback in case deserializing the rule in JSON in the error
                    // message fails.
                    throw new RuntimeException("Invalid link " + link);
                }
            } else {
                return isValid;
            }
        } else {
            return true;
        }
    }

}
