/*
 * Copyright 2025 Johns Hopkins University
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
package org.eclipse.pass.deposit.transport;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Service
public class RepositoryConnectivityService {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryConnectivityService.class);

    private final int repoConnectTimeoutMillis;
    private final OkHttpClient httpClient;

    public RepositoryConnectivityService(
        @Value("${pass.repo.verify.connect.timeout.ms}") int repoConnectTimeoutMillis,
        @Value("${inveniordm.api.verifySslCertificate}") Boolean verifySslCertificate
    ) throws NoSuchAlgorithmException, KeyManagementException {
        this.repoConnectTimeoutMillis = repoConnectTimeoutMillis;
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(repoConnectTimeoutMillis, MILLISECONDS);
        ignoreSSLCertsForLocalTestingIfNeeded(builder, verifySslCertificate);
        httpClient = builder.build();
    }

    /**
     * Verify connection to URL and response code is less than BAD_GATEWAY (502).
     * @param url the url to verify connectivity
     * @return true if passed, false if not
     */
    public boolean verifyConnectByURL(String url) {
        Request.Builder requestBuilder =  new Request.Builder().url(url);
        Request request = requestBuilder.build();
        Call call = httpClient.newCall(request);
        try (Response response = call.execute()) {
            int responseCode = response.code();
            return responseCode < HttpStatus.BAD_GATEWAY.value();
        } catch (Exception e) {
            LOG.error("Error connecting to Transport URL.", e);
            return false;
        }
    }

    /**
     * Verify connection to host and port.
     * @param host the host
     * @param port the port
     * @return true if passed, false if not
     */
    public boolean verifyConnect(String host, int port) {
        try (Socket soc = new Socket()) {
            soc.connect(new InetSocketAddress(host, port), repoConnectTimeoutMillis);
            return true;
        } catch (IOException e) {
            LOG.error("Repository is not currently reachable. Host={}, Port={}", host, port, e);
            return false;
        }
    }

    /**
     * This is needed for local testing for repos that use self-signed certs like inveniordm.
     * <b>This should not be used in a live deployed environment, only for local testing</b>
     * @param builder a builder
     * @param verifySslCertificate verify or not
     * @throws NoSuchAlgorithmException if algo is missing
     * @throws KeyManagementException is problem with key
     */
    private void ignoreSSLCertsForLocalTestingIfNeeded(OkHttpClient.Builder builder, Boolean verifySslCertificate)
        throws NoSuchAlgorithmException, KeyManagementException {
        if (Boolean.FALSE.equals(verifySslCertificate)) {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
        }
    }
}
