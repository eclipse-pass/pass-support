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
package org.eclipse.pass.notification.dispatch.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.StringHelpers;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.notification.model.NotificationParam;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Parameterizes Mustache templates using Handlebars.
 * <p>
 * <em>Implementation note:</em> performance could be improved by pre-compiling templates.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class HandlebarsParameterizer {

    private final Handlebars handlebars;
    private final ObjectMapper mapper;

    /**
     * Constructor.
     * @param mapper the object mapper
     */
    public HandlebarsParameterizer(ObjectMapper mapper) {
        this.mapper = mapper;
        Handlebars handlebars = new Handlebars();
        handlebars.registerHelper("eq", ConditionalHelpers.eq);
        handlebars.registerHelper("abbreviate", StringHelpers.abbreviate);
        this.handlebars = handlebars.with(EscapingStrategy.NOOP);
    }

    /**
     * Populate template stream with param values and return it as a String.
     * @param paramMap the map of params
     * @param template the template
     * @return the populated template as String
     */
    public String parameterize(Map<NotificationParam, String> paramMap, InputStream template) {

        Map<String, Object> mustacheModel = paramMap
                .entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getParamName(),
                        entry -> {
                            NotificationParam param = entry.getKey();
                            switch (param) {
                                case EVENT_METADATA, RESOURCE_METADATA -> {
                                    try {
                                        return mapper.readValue(entry.getValue(), Map.class);
                                    } catch (JacksonException e) {
                                        throw new RuntimeException(e.getMessage(), e);
                                    }
                                }
                                case LINKS -> {
                                    try {
                                        return mapper.readValue(entry.getValue(), List.class);
                                    } catch (JacksonException e) {
                                        throw new RuntimeException(e.getMessage(), e);
                                    }
                                }
                                default -> {
                                }
                            }

                            return entry.getValue();
                        }));

        String parameterizedTemplate;
        try {
            String templateString = IOUtils.toString(template, StandardCharsets.UTF_8);
            Template t = handlebars.compileInline(templateString);
            parameterizedTemplate = t.apply(mustacheModel);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return parameterizedTemplate;
    }
}
