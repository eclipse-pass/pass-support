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
package org.eclipse.pass.deposit.transport.inveniordm;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;

import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONObject;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.eclipse.pass.deposit.assembler.DepositFileResource;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.provider.inveniordm.InvenioRdmMetadataMapper;
import org.eclipse.pass.deposit.transport.TransportResponse;
import org.eclipse.pass.deposit.transport.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
class InvenioRdmSession implements TransportSession {
    private static final Logger LOG = LoggerFactory.getLogger(InvenioRdmSession.class);

    private final InvenioRdmMetadataMapper invenioRdmMetadataMapper;
    private final RestClient restClient;
    private final RetryTemplate retryTemplate;

    InvenioRdmSession(String baseServerUrl, String apiToken, Boolean verifySslCertificate) {
        this.invenioRdmMetadataMapper = new InvenioRdmMetadataMapper();
        HttpClientConnectionManager connectionManager = buildConnectionManager(verifySslCertificate);
        final CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .disableCookieManagement()
            .build();
        this.restClient = RestClient.builder()
            .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
            .baseUrl(baseServerUrl)
            .defaultHeader("Authorization", "Bearer " + apiToken)
            .build();
        this.retryTemplate = RetryTemplate.builder()
            .maxAttempts(5)
            .fixedBackoff(3000)
            .retryOn(HttpServerErrorException.class)
            .build();
    }

    @Override
    public TransportResponse send(PackageStream packageStream, Map<String, String> metadata) {
        try {
            DepositSubmission depositSubmission = packageStream.getDepositSubmission();
            LOG.warn("Processing InvenioRDM Deposit for Submission: {}", depositSubmission.getId());
            deleteDraftRecordIfNeeded(depositSubmission);
            JSONObject recordBody = invenioRdmMetadataMapper.toInvenioMetadata(depositSubmission);
            String recordId = createRecordDraft(recordBody.toJSONString());
            uploadFiles(packageStream, recordId);
            String accessUrl = publishRecord(recordId);
            LOG.warn("Completed InvenioRDM Deposit for Submission: {}, accessUrl: {}",
                depositSubmission.getId(), accessUrl);
            return new InvenioRdmResponse(true, accessUrl);
        } catch (Exception e) {
            LOG.error("Error depositing into InvenioRDM", e);
            return new InvenioRdmResponse(false, null, e);
        }
    }

    @Override
    public boolean closed() {
        return true;
    }

    @Override
    public void close() throws Exception {
        // no-op resources are closed with try-with-resources
    }

    private HttpClientConnectionManager buildConnectionManager(Boolean verifySslCertificate) {
        try {
            PoolingHttpClientConnectionManagerBuilder connMgrBuilder =
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofMinutes(1))
                        .build())
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setSocketTimeout(Timeout.ofMinutes(1))
                        .setConnectTimeout(Timeout.ofMinutes(1))
                        .setTimeToLive(TimeValue.ofMinutes(10))
                        .build());
            // This is needed because localhost invenioRdm runs with self-signed cert
            if (Boolean.FALSE.equals(verifySslCertificate)) {
                final TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
                final SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();
                final SSLConnectionSocketFactory sslConnectionSocketFactory =
                    new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
                connMgrBuilder.setSSLSocketFactory(sslConnectionSocketFactory);
            }
            return connMgrBuilder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteDraftRecordIfNeeded(DepositSubmission depositSubmission) {
        String title = depositSubmission.getSubmissionMeta().get("title").getAsString();
        String searchResponse = restClient.get()
            .uri("/user/records?q=metadata.title:\"{title}\"", title)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String.class);
        List<Map<String, ?>> foundRecords = JsonPath.parse(searchResponse).read("$.hits.hits[*]");
        if (!foundRecords.isEmpty()) {
            deleteDraftRecord(foundRecords, title);
        }
    }

    private void deleteDraftRecord(List<Map<String, ?>> foundRecords, String title) {
        if (foundRecords.size() > 1) {
            throw new RuntimeException("Found more than one match in invenioRDM for title=" + title + ", aborting");
        }
        Map<String, ?> record = foundRecords.get(0);
        String recordId = record.get("id").toString();
        String isPublished = record.get("is_published").toString();
        if (isPublished.equals("true")) {
            throw new RuntimeException("Found published record match in invenioRDM for title=" + title + ", aborting");
        }
        LOG.warn("Deleting existing invenioRDM draft record: {}", recordId);
        restClient.delete()
            .uri("/records/{recordId}/draft", recordId)
            .retrieve()
            .body(String.class);
    }

    private String createRecordDraft(String recordBodyJson) {
        String recordResponse = restClient.post()
            .uri("/records")
            .contentType(MediaType.APPLICATION_JSON)
            .body(recordBodyJson)
            .retrieve()
            .body(String.class);
        return JsonPath.parse(recordResponse).read("$.id");
    }

    private String publishRecord(String recordId) {
        // This call is wrapped in a retry template because of this inveniordm bug for the publish action:
        // https://github.com/inveniosoftware/invenio-rdm-records/issues/809
        return retryTemplate.execute(ctx -> {
            String publishResponse = restClient.post()
                .uri("/records/{recordId}/draft/actions/publish", recordId)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
            return JsonPath.parse(publishResponse).read("$.links.self_html");
        });
    }

    private void uploadFiles(PackageStream packageStream, String recordId) {
        List<DepositFileResource> depositFileResources = packageStream.getCustodialContent();
        depositFileResources.forEach(depositFileResource -> {
            String fileName = UriUtils.encodePathSegment(depositFileResource.getDepositFile().getName(),
                StandardCharsets.UTF_8);
            createDraftFile(recordId, fileName);
            updateDraftFileContent(depositFileResource.getResource(), recordId, fileName);
            commitDraftFile(recordId, fileName);
        });
    }

    private void createDraftFile(String recordId, String fileName) {
        restClient.post()
            .uri("/records/{recordId}/draft/files", recordId)
            .contentType(MediaType.APPLICATION_JSON)
            .body("[{\"key\": \"" + fileName + "\"}]")
            .retrieve()
            .body(String.class);
    }

    private void updateDraftFileContent(Resource resource, String recordId, String fileName) {
        restClient.put()
            .uri("/records/{recordId}/draft/files/{fileName}/content", recordId, fileName)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
            .retrieve()
            .body(String.class);
    }

    private void commitDraftFile(String recordId, String fileName) {
        restClient.post()
            .uri("/records/{recordId}/draft/files/{fileName}/commit", recordId, fileName)
            .retrieve()
            .body(String.class);
    }
}
