/*
 *
 *  * Copyright 2023 Johns Hopkins University
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
package org.eclipse.pass.loader.nihms;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.damnhandy.uri.template.UriTemplate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The URLBuilder class is responsible for building NIHMS Loader URLs.
 */
@Component
public class UrlBuilder {

    private static final String API_URL_PARAM_PREFIX = "nihmsetl.api.url.param.";
    private static final String TEMPLATE = "{scheme}://{host}{+path}{+type}{?params*}";
    private static final List<String> nihmsParams = List.of("format", "inst", "ipf", "api-token", "pdf", "pdt");

    @Value("${nihmsetl.api.scheme}")
    private String apiScheme;

    @Value("${nihmsetl.api.host}")
    private String apiHost;

    @Value("${nihmsetl.api.path}")
    private String apiPath;

    private final Environment environment;

    /**
     * Constructor for the UrlBuilder. Sets environment either profiles or properties using SpringBoot Configuration.
     *
     * @param environment enables the accessing of configuration properties in the UrlBuilder, such as the api schema,
     *                    api host, and api path.
     */
    public UrlBuilder(Environment environment) {
        this.environment = environment;
    }

    /**
     * Get the url for the type Compliant with the provided parameters
     * @param params parameters for the URL
     * @return the Compliant URL with the provided parameters
     */
    public URL compliantUrl(Map<String, String> params) {
        return urlFor(UrlType.COMPLIANT, params);
    }

    /**
     * Get the url for the type Non-Compliant with the provided parameters
     * @param params parameters for the URL
     * @return the Non-Compliant URL with the provided parameters
     */
    public URL nonCompliantUrl(Map<String, String> params) {
        return urlFor(UrlType.NON_COMPLIANT, params);
    }

    /**
     * Get the url for the type In-Process with the provided parameters
     * @param params parameters for the URL
     * @return the In-Process URL with the provided parameters
     */
    public URL inProcessUrl(Map<String, String> params) {
        return urlFor(UrlType.IN_PROCESS, params);
    }

    private URL urlFor(UrlType type, Map<String, String> params) {
        try {
            Map<String, String> mergedParams = getApiUrlParams();
            mergedParams.putAll(params);

            return new URL(UriTemplate.fromTemplate(TEMPLATE)
                                      .set("scheme", apiScheme)
                                      .set("host", apiHost)
                                      .set("path", apiPath)
                                      .set("type", type.getCode())
                                      .set("params", mergedParams)
                                      .expand());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Map<String, String> getApiUrlParams() {
        Map<String, String> params = new HashMap<>();
        nihmsParams.forEach(paramKey -> {
            String value = environment.getProperty(API_URL_PARAM_PREFIX + paramKey);
            if (StringUtils.isNotBlank(value)) {
                params.put(paramKey, value);
            }
        });
        return params;
    }

}
