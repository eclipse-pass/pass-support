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
package org.eclipse.pass.deposit.support.deploymenttest;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jayway.jsonpath.JsonPath;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.eclipse.pass.support.client.model.Deposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ConditionalOnProperty(name = "pass.test.data.job.enabled")
@Service
public class DspaceDepositService {
    private static final Logger LOG = LoggerFactory.getLogger(DspaceDepositService.class);

    private final RestClient restClient;

    @Value("${dspace.user}")
    private String dspaceUsername;

    @Value("${dspace.password}")
    private String dspacePassword;

    @Value("${dspace.server}")
    private String dspaceServer;

    @Value("${dspace.server.api.protocol}")
    private String dspaceApiProtocol;

    protected record AuthContext(String xsrfToken, String authToken){}

    public DspaceDepositService(@Value("${dspace.server.api.protocol}") String dspaceApiProtocol,
                                @Value("${dspace.server}") String dspaceServer,
                                @Value("${dspace.server.api.path}") String dspaceApiPath) {
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(Timeout.ofMinutes(1))
                .build())
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(Timeout.ofMinutes(1))
                .setConnectTimeout(Timeout.ofMinutes(1))
                .setTimeToLive(TimeValue.ofMinutes(10))
                .build())
            .build();
        final CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .disableCookieManagement()
            .build();
        this.restClient = RestClient.builder()
            .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
            .baseUrl(dspaceApiProtocol + "://" + dspaceServer + dspaceApiPath)
            .build();
    }

    /**
     * Authenticate with the repository. This should be called as appropriate per the repository's API
     * authentication docs.
     * @return an AuthContext containing authToken and xsrfToken
     */
    AuthContext authenticate() {
        // Using exchange is needed for this call because dspace returns 404, but the response headers has the
        // csrf token header DSPACE-XSRF-TOKEN
        ResponseEntity<Void> csrfResponse = restClient.get()
            .uri("/security/csrf")
            .exchange((request, response) -> new ResponseEntity<>(null, response.getHeaders(), HttpStatus.OK));
        String xsrfToken = getAuthHeaderValue(csrfResponse, "DSPACE-XSRF-TOKEN");

        MultiValueMap<String, String> bodyPair = new LinkedMultiValueMap<>();
        bodyPair.add("user",  dspaceUsername);
        bodyPair.add("password",  dspacePassword);
        ResponseEntity<Void> authResponse = restClient.post()
            .uri("/authn/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header("X-XSRF-TOKEN", xsrfToken)
            .header("Cookie", "DSPACE-XSRF-COOKIE=" + xsrfToken)
            .body(bodyPair)
            .retrieve()
            .toBodilessEntity();
        String authToken = getAuthHeaderValue(authResponse, "Authorization");

        return new AuthContext(xsrfToken, authToken);
    }

    /**
     * Deletes the deposit in the remote repository.
     * @param deposit contains deposit info to do the delete
     */
    void deleteDeposit(Deposit deposit, AuthContext authContext) {
        LOG.warn("Deleting Test Deposit In Dspace (PASS Deposit ID={})", deposit.getId());
        URI accessUrl = deposit.getRepositoryCopy().getAccessUrl();
        LOG.warn("Deposit accessUrl={}", accessUrl);
        if (Objects.nonNull(accessUrl)) {
            String handleValue = parseHandleFilter(accessUrl);
            String submissionMetadata = deposit.getSubmission().getMetadata();
            String submissionTitle = JsonPath.parse(submissionMetadata).read("$.title");
            String itemUuid = findItemUuid(handleValue, authContext, submissionTitle);
            if (Objects.nonNull(itemUuid)) {
                LOG.warn("Processing item UUID={}", itemUuid);
                List<String> bundleUuidArray = findBundleUuids(itemUuid, authContext);
                bundleUuidArray.forEach(bundleUuid -> deleteBundle(bundleUuid, authContext));
                deleteItem(itemUuid, authContext);
                LOG.warn("Deleted Test Deposit In Dspace (PASS Deposit ID={})", deposit.getId());
            } else {
                LOG.error("Did not find item in Dspace with handle={}, nothing deleted", handleValue);
            }
        } else {
            LOG.error("Deposit has no accessUrl (PASS Deposit ID={}), nothing deleted", deposit.getId());
        }
    }

    private String getAuthHeaderValue(ResponseEntity<Void> response, String header) {
        List<String> values = response.getHeaders().get(header);
        if (Objects.isNull(values) || values.isEmpty()) {
            throw new RuntimeException("Auth Header not found: " + header);
        }
        return values.get(0);
    }

    private String parseHandleFilter(URI accessUrl) {
        String handleDelim = dspaceApiProtocol + "://" + dspaceServer + "/handle/";
        String[] handleTokens = accessUrl.toString().split(handleDelim);
        if (handleTokens.length != 2) {
            throw new RuntimeException("Unable to determine dspace item handle for " + accessUrl);
        }
        return handleTokens[1];
    }

    private String findItemUuid(String handleValue, AuthContext authContext, String submissionTitle) {
        LOG.warn("Search Dspace for item with handle={}", handleValue);
        String searchResponse = restClient.get()
            .uri("/discover/search/objects?query=handle:{handleValue}&dsoType=item", handleValue)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", authContext.authToken())
            .retrieve()
            .body(String.class);
        List<Map<String, ?>> searchArray = JsonPath.parse(searchResponse).read("$..indexableObject[?(@.handle)]");
        if (searchArray.size() == 1) {
            Map<String, ?> itemMap = searchArray.get(0);
            String itemName = itemMap.get("name").toString();
            String itemHandle = itemMap.get("handle").toString();
            String itemUuid = itemMap.get("uuid").toString();
            LOG.warn("Found item UUID={} with handle={} and name={}", itemUuid, itemHandle, itemName);
            if (handleValue.equals(itemHandle) && itemName.equals(submissionTitle)) {
                return itemUuid;
            } else {
                throw new RuntimeException(
                    String.format("Item handle and name don't match [expected handle=%s, " +
                            "name=%s/actual handle=%s and name=%s]",
                    handleValue, submissionTitle, itemHandle, itemName));
            }
        }
        return null;
    }

    private  List<String> findBundleUuids(String itemUuid, AuthContext authContext) {
        LOG.warn("Search Dspace for item bundles with item UUID={}",  itemUuid);
        String bundlesResponse = restClient.get()
            .uri("/core/items/{itemUuid}/bundles", itemUuid)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", authContext.authToken())
            .retrieve()
            .body(String.class);
        return JsonPath.parse(bundlesResponse).read("$..bundles[*].uuid");
    }

    private void deleteBundle(String bundleUuid, AuthContext authContext) {
        LOG.warn("Deleting bundle UUID={}", bundleUuid);
        restClient.delete()
            .uri("/core/bundles/{bundleUuid}", bundleUuid)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", authContext.authToken())
            .header("X-XSRF-TOKEN", authContext.xsrfToken())
            .header("Cookie", "DSPACE-XSRF-COOKIE=" + authContext.xsrfToken())
            .retrieve()
            .toBodilessEntity();
        LOG.warn("Deleted bundle UUID={}", bundleUuid);
    }

    private void deleteItem(String itemUuid, AuthContext authContext) {
        LOG.warn("Deleting item UUID={}", itemUuid);
        restClient.delete()
            .uri("/core/items/{itemUuid}", itemUuid)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", authContext.authToken())
            .header("X-XSRF-TOKEN", authContext.xsrfToken())
            .header("Cookie", "DSPACE-XSRF-COOKIE=" + authContext.xsrfToken())
            .retrieve()
            .toBodilessEntity();
        LOG.warn("Deleted item UUID={}", itemUuid);
    }
}
