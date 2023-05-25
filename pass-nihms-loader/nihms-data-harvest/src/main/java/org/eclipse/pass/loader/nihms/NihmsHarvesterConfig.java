/*
 * Copyright 2023 Johns Hopkins University
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
package org.eclipse.pass.loader.nihms;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.pass.loader.nihms.util.ConfigUtil;

/**
 * Holds information and methods required to configure the NIHMS harvester tool.
 *
 * @author Karen Hanson
 */
public class NihmsHarvesterConfig {

    private NihmsHarvesterConfig () {
        //never called
    }

    /**
     * The API host prefix to use for the NIHMS data harvester
     */
    public static final String NIHMS_ETL_PROPERTY_PREFIX = "nihmsetl.";

    /**
     * The API host to use for the NIHMS data harvester
     */
    private static final String API_HOST_KEY = NIHMS_ETL_PROPERTY_PREFIX + "api.host";

    /**
     * Default API host to use for the NIHMS data harvester
     */
    private static final String DEFAULT_API_HOST = "www.ncbi.nlm.nih.gov";

    /**
     * The API scheme (http or https), default is https
     */
    private static final String API_SCHEME_KEY = NIHMS_ETL_PROPERTY_PREFIX + "api.scheme";

    /**
     * Default API scheme (https)
     */
    private static final String DEFAULT_API_SCHEME = "https";

    /**
     * The API path, default is /pmc/utils/pacm/
     */
    private static final String API_PATH_KEY = NIHMS_ETL_PROPERTY_PREFIX + "api.path";

    /**
     * Default API path (/pmc/utils/pacm/)
     */
    private static final String DEFAULT_API_PATH = "/pmc/utils/pacm/";

    /**
     * The API URL parameter prefix
     */
    static final String API_URL_PARAM_PREFIX = NIHMS_ETL_PROPERTY_PREFIX + "api.url.param.";

    /**
     * Read Timeout key for HTTP requests
     */
    private static final String HTTP_READ_TIMEOUT_KEY = NIHMS_ETL_PROPERTY_PREFIX + "http.read-timeout-ms";

    /**
     * Default read timeout for HTTP requests
     */
    private static final String DEFAULT_HTTP_READ_TIMEOUT = "10000";

    /**
     * Connect Timeout key for HTTP requests
     */
    private static final String HTTP_CONNECT_TIMEOUT_KEY = NIHMS_ETL_PROPERTY_PREFIX + "http.connect-timeout-ms";

    /**
     * Default connect timeout for HTTP requests
     */
    private static final String DEFAULT_HTTP_CONNECT_TIMEOUT = "10000";

    /**
     * Get the API host to use for the NIHMS data harvester
     * @return The value of the API host to use for the NIHMS data harvester
     */
    public static String getApiHost() {
        return ConfigUtil.getSystemProperty(API_HOST_KEY, DEFAULT_API_HOST);
    }

    /**
     * Get the API scheme (http or https), default is https
     * @return the API scheme (http or https)
     */
    public static String getApiScheme() {
        return ConfigUtil.getSystemProperty(API_SCHEME_KEY, DEFAULT_API_SCHEME);
    }

    /**
     * Get the API path, default is /pmc/utils/pacm/
     * @return the API path
     */
    public static String getApiPath() {
        return ConfigUtil.getSystemProperty(API_PATH_KEY, DEFAULT_API_PATH);
    }

    /**
     * Get the API URL parameters
     * @return the API URL parameters
     */
    public static Map<String, String> getApiUrlParams() {
        return System.getProperties()
                     .entrySet()
                     .stream()
                     .filter((entry) -> ((String) entry.getKey()).startsWith(API_URL_PARAM_PREFIX))
                     .collect(HashMap::new,
                              (map, entry) -> map.put(
                                  ((String) entry.getKey()).substring(API_URL_PARAM_PREFIX.length()),
                                  (String) entry.getValue()),
                              (map1, map2) -> map2.putAll(map1));
    }

    /**
     * Get the HTTP connect timeout in milliseconds
     * @return the HTTP connect timeout in milliseconds
     */
    public static long getHttpConnectTimeoutMs() {
        return Long.valueOf(ConfigUtil.getSystemProperty(HTTP_CONNECT_TIMEOUT_KEY, DEFAULT_HTTP_CONNECT_TIMEOUT));
    }

    /**
     * Get the HTTP read timeout in milliseconds
     * @return the HTTP read timeout in milliseconds
     */
    public static long getHttpReadTimeoutMs() {
        return Long.valueOf(ConfigUtil.getSystemProperty(HTTP_READ_TIMEOUT_KEY, DEFAULT_HTTP_READ_TIMEOUT));
    }
}
