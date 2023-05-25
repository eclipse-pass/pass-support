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
import java.util.Collections;
import java.util.Map;

import com.damnhandy.uri.template.UriTemplate;

/**
 * The URLBuilder class is responsible for building NIHMS Loader URLs.
 */
public class UrlBuilder {

    private static final String TEMPLATE = "{scheme}://{host}{+path}{+type}{?params*}";

    /**
     * Get url for the type Compliant without any parameters
     * @return the URL without any parameters
     */
    public URL compliantUrl() {
        return urlFor(UrlType.COMPLIANT, Collections.emptyMap());
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
     * Get the url for the type Non-Compliant without any parameters
     * @return the URL without any parameters
     */
    public URL nonCompliantUrl() {
        return urlFor(UrlType.NON_COMPLIANT, Collections.emptyMap());
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
     * Get the url for the type In-Process without any parameters
     * @return the URL without any parameters
     */
    public URL inProcessUrl() {
        return urlFor(UrlType.IN_PROCESS, Collections.emptyMap());
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
            Map<String, String> mergedParams = NihmsHarvesterConfig.getApiUrlParams();
            mergedParams.putAll(params);

            return new URL(UriTemplate.fromTemplate(TEMPLATE)
                                      .set("scheme", NihmsHarvesterConfig.getApiScheme())
                                      .set("host", NihmsHarvesterConfig.getApiHost())
                                      .set("path", NihmsHarvesterConfig.getApiPath())
                                      .set("type", type.getCode())
                                      .set("params", mergedParams)
                                      .expand());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
