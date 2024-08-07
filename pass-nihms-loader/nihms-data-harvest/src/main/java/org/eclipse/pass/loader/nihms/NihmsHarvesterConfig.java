/*
 * Copyright 2024 Johns Hopkins University
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

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Russ Poetker (rpoetker1@jh.edu)
 */
@Configuration
public class NihmsHarvesterConfig {

    @Value("${nihmsetl.http.connect-timeout-ms}")
    private Long nihmsConnectTimeoutMs;

    @Value("${nihmsetl.http.read-timeout-ms}")
    private Long nihmsReadTimeoutMs;

    /**
     * The OkHttpClient that has the connection properties set from the application.properties
     *
     * @return OkHttpClient
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(nihmsConnectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(nihmsReadTimeoutMs, TimeUnit.MILLISECONDS)
            .build();
    }
}
