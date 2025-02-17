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
package org.eclipse.pass.deposit.support.dspace;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jayway.jsonpath.JsonPath;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.eclipse.pass.deposit.assembler.DepositFileResource;
import org.eclipse.pass.deposit.transport.RepositoryConnectivityService;
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
import org.springframework.web.client.RestClientResponseException;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ConditionalOnProperty(name = "dspace.api.url")
@Service
public class DSpaceDepositService {
    private static final Logger LOG = LoggerFactory.getLogger(DSpaceDepositService.class);
    private static final String X_XSRF_TOKEN = "X-XSRF-TOKEN";
    private static final String COOKIE = "Cookie";
    private static final String DSPACE_XSRF_COOKIE = "DSPACE-XSRF-COOKIE=";
    private static final String AUTHORIZATION = "Authorization";

    private final RestClient restClient;

    @Value("${dspace.api.url}")
    private String dspaceApiUrl;

    @Value("${dspace.website.url}")
    private String dspaceWebsiteUrl;

    @Value("${dspace.user}")
    private String dspaceUsername;

    @Value("${dspace.password}")
    private String dspacePassword;

    @Value("${dspace.collection.handle}")
    private String dspaceCollectionHandle;

    private final RepositoryConnectivityService repositoryConnectivityService;

    public record AuthContext(String xsrfToken, String authToken){}

    public DSpaceDepositService(@Value("${dspace.api.url}") String dspaceApiUrl,
            RepositoryConnectivityService repositoryConnectivityService) {
        this.repositoryConnectivityService = repositoryConnectivityService;

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
            .baseUrl(dspaceApiUrl)
            .build();
    }

    /**
     * Authenticate with the repository. This should be called as appropriate per the repository's API
     * authentication docs.
     * @return an AuthContext containing authToken and xsrfToken
     */
    public AuthContext authenticate() {
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
            .header(X_XSRF_TOKEN, xsrfToken)
            .header(COOKIE, DSPACE_XSRF_COOKIE + xsrfToken)
            .body(bodyPair)
            .retrieve()
            .toBodilessEntity();
        String authToken = getAuthHeaderValue(authResponse, AUTHORIZATION);

        return new AuthContext(xsrfToken, authToken);
    }

    /**
     * Deletes the deposit in the remote repository.
     * @param deposit contains deposit info to do the delete
     */
    public void deleteDeposit(Deposit deposit, AuthContext authContext) {
        LOG.warn("Deleting Test Deposit In Dspace (PASS Deposit ID={})", deposit.getId());
        URI accessUrl = Objects.nonNull(deposit.getRepositoryCopy())
                ? deposit.getRepositoryCopy().getAccessUrl()
                : null;
        LOG.warn("Deposit accessUrl={}", accessUrl);
        String itemUuid = parseItemUuid(accessUrl);
        if (StringUtils.isNotEmpty(itemUuid)) {
            LOG.warn("Processing item UUID={}", itemUuid);
            List<String> bundleUuidArray = findBundleUuids(itemUuid, authContext);
            bundleUuidArray.forEach(bundleUuid -> deleteBundle(bundleUuid, authContext));
            deleteItem(itemUuid, authContext);
            LOG.warn("Deleted Test Deposit In Dspace (PASS Deposit ID={})", deposit.getId());
        } else {
            LOG.error("Deposit has invalid accessUrl (PASS Deposit ID={}), nothing deleted", deposit.getId());
        }
    }

    private String getAuthHeaderValue(ResponseEntity<Void> response, String header) {
        List<String> values = response.getHeaders().get(header);
        if (Objects.isNull(values) || values.isEmpty()) {
            throw new RuntimeException("Auth Header not found: " + header);
        }
        return values.get(0);
    }

    private String parseItemUuid(URI accessUrl) {
        if (Objects.isNull(accessUrl)) {
            return "";
        }
        String path = accessUrl.getPath();
        String mark = "/items/";
        int start = path.indexOf(mark);
        return start == -1 ? "" : path.substring(start + mark.length());
    }

    public String createAccessUrlFromItemUuid(String itemUuid) {
        return dspaceWebsiteUrl + "/items/" + itemUuid;
    }

    private  List<String> findBundleUuids(String itemUuid, AuthContext authContext) {
        LOG.warn("Search Dspace for item bundles with item UUID={}",  itemUuid);
        try {
            String bundlesResponse = restClient.get()
                .uri("/core/items/{itemUuid}/bundles", itemUuid)
                .accept(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION, authContext.authToken())
                .retrieve()
                .body(String.class);
            return JsonPath.parse(bundlesResponse).read("$..bundles[*].uuid");
        } catch (RestClientResponseException clientResponseException) {
            if (clientResponseException.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOG.error("Delete Bundle UUIDs Not Found: {}", itemUuid);
                return List.of();
            }
            throw clientResponseException;
        }
    }

    private void deleteBundle(String bundleUuid, AuthContext authContext) {
        LOG.warn("Deleting bundle UUID={}", bundleUuid);
        restClient.delete()
            .uri("/core/bundles/{bundleUuid}", bundleUuid)
            .accept(MediaType.APPLICATION_JSON)
            .header(AUTHORIZATION, authContext.authToken())
            .header(X_XSRF_TOKEN, authContext.xsrfToken())
            .header(COOKIE, DSPACE_XSRF_COOKIE + authContext.xsrfToken())
            .retrieve()
            .toBodilessEntity();
        LOG.warn("Deleted bundle UUID={}", bundleUuid);
    }

    private void deleteItem(String itemUuid, AuthContext authContext) {
        LOG.warn("Deleting item UUID={}", itemUuid);
        try {
            restClient.delete()
                .uri("/core/items/{itemUuid}", itemUuid)
                .accept(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION, authContext.authToken())
                .header(X_XSRF_TOKEN, authContext.xsrfToken())
                .header(COOKIE, DSPACE_XSRF_COOKIE + authContext.xsrfToken())
                .retrieve()
                .toBodilessEntity();
            LOG.warn("Deleted item UUID={}", itemUuid);
        } catch (RestClientResponseException clientResponseException) {
            if (clientResponseException.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOG.error("Delete Item Not Found: {}", itemUuid);
                return;
            }
            throw clientResponseException;
        }
    }

    public String getUuidForHandle(String handle, AuthContext authContext) {
        LOG.debug("Search Dspace for object with handle={}", handle);

        String searchResponse = restClient.get()
            .uri("/discover/search/objects?query=handle:{handleValue}", handle)
            .accept(MediaType.APPLICATION_JSON)
            .header(AUTHORIZATION, authContext.authToken())
            .retrieve()
            .body(String.class);

        List<Map<String, ?>> searchArray = JsonPath.parse(searchResponse).read("$..indexableObject[?(@.handle)]");

        if (searchArray.size() == 1) {
            Map<String, ?> itemMap = searchArray.get(0);
            String uuid = itemMap.get("uuid").toString();

            LOG.debug("Found object UUID={} with handle={}", uuid, handle);

            return uuid;
        }

        throw new RuntimeException("Unable to find object with handle: " + handle);
    }

    public String createWorkspaceItem(List<DepositFileResource> files, AuthContext authContext) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        for (DepositFileResource res: files) {
            body.add("file", res.getResource());
        }

        String uuid = getUuidForHandle(dspaceCollectionHandle, authContext);

        return restClient.post()
            .uri("/submission/workspaceitems?owningCollection={collectionUuid}", uuid)
            .header(AUTHORIZATION, authContext.authToken())
            .header(X_XSRF_TOKEN, authContext.xsrfToken())
            .header(COOKIE, DSPACE_XSRF_COOKIE + authContext.xsrfToken())
            .body(body)
            .retrieve().body(String.class);
    }

    public void patchWorkspaceItem(int workspaceItemId, String patch, AuthContext authContext) {
        restClient.patch()
            .uri("/submission/workspaceitems/{workspaceItemId}", workspaceItemId)
            .contentType(MediaType.APPLICATION_JSON)
            .header(AUTHORIZATION, authContext.authToken())
            .header(X_XSRF_TOKEN, authContext.xsrfToken())
            .header(COOKIE, DSPACE_XSRF_COOKIE + authContext.xsrfToken())
            .body(patch)
            .retrieve().toBodilessEntity();
    }

    public void createWorkflowItem(int workspaceItemId, AuthContext authContext) {
        String workspaceItemUrl = dspaceApiUrl + "/submission/workspaceitems/" + workspaceItemId;

        restClient.post()
            .uri("/workflow/workflowitems")
            .header("Content-Type", "text/uri-list")
            .header(AUTHORIZATION, authContext.authToken())
            .header(X_XSRF_TOKEN, authContext.xsrfToken())
            .header(COOKIE, DSPACE_XSRF_COOKIE + authContext.xsrfToken())
            .body(workspaceItemUrl)
            .retrieve().toBodilessEntity();
    }

    public boolean verifyConnectivity() {
        // The base API URL is a valid service endpoint
        return repositoryConnectivityService.verifyConnectByURL(dspaceApiUrl);
    }
}
