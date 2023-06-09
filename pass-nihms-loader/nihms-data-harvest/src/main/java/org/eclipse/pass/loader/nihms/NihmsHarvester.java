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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.loader.nihms.util.FileUtil;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Karen Hanson
 */
public class NihmsHarvester {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsHarvester.class);

    /**
     * Directory to download files to
     */
    private Path downloadDirectoryPath;

    private UrlBuilder urlBuilder;

    private OkHttpClient okHttp;

    /**
     * Initiate harvester with required properties
     */
    public NihmsHarvester() {
        this.downloadDirectoryPath = FileUtil.getDataDirectory().toPath();
        this.urlBuilder = new UrlBuilder();

        if (downloadDirectoryPath == null) {
            throw new RuntimeException("The harvester's downloadDirectory cannot be empty");
        }

        //if download directory doesn't already exist attempt to make it
        if (!Files.isDirectory(downloadDirectoryPath)) {
            LOG.warn("Download directory does not exist at path provided. A new directory will be created at path: {}",
                     downloadDirectoryPath);
            try {
                FileUtils.forceMkdir(downloadDirectoryPath.toFile());
            } catch (IOException e) {
                throw new RuntimeException("A new download directory could not be created at path: " +
                                           downloadDirectoryPath + ". Please provide a valid path for the downloads",
                                           e);
            }
        }

        okHttp = new OkHttpClient.Builder()
            .connectTimeout(NihmsHarvesterConfig.getHttpConnectTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(NihmsHarvesterConfig.getHttpReadTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();
    }

    /**
     * Retrieve files from NIHMS based on status list and startDate provided
     *
     * @param statusesToDownload list of {@code NihmsStatus} types to download from the NIHMS website
     * @param startDate          formatted as {@code yyyy-mm}, can be null to default to 1 year prior to harvest date
     */
    public void harvest(Set<NihmsStatus> statusesToDownload, String startDate) {
        if (CollectionUtils.isEmpty(statusesToDownload)) {
            throw new RuntimeException("statusesToDownload list cannot be empty");
        }
        if (!validStartDate(startDate)) {
            throw new RuntimeException(
                String.format("The startDate %s is not valid. The date must be formatted as mm-yyyy", startDate));
        }

        try {
            LOG.info("Writing files to: {}", downloadDirectoryPath.toString());

            Map<String, String> params = new HashMap<>();

            if (StringUtils.isNotEmpty(startDate)) {
                startDate = startDate.replace("-", "/");
                LOG.info("Filtering with Start Date " + startDate);
                params.put("pdf", startDate);
            }

            if (statusesToDownload.contains(NihmsStatus.COMPLIANT)) {
                LOG.info("Goto {} list", NihmsStatus.COMPLIANT);
                File file = newFile(NihmsStatus.COMPLIANT);
                URL url = urlBuilder.compliantUrl(params);

                download(url, file, NihmsStatus.COMPLIANT);
            }

            if (statusesToDownload.contains(NihmsStatus.NON_COMPLIANT)) {
                LOG.info("Goto {} list", NihmsStatus.NON_COMPLIANT);
                File file = newFile(NihmsStatus.NON_COMPLIANT);
                URL url = urlBuilder.nonCompliantUrl(params);
                download(url, file, NihmsStatus.NON_COMPLIANT);
            }

            if (statusesToDownload.contains(NihmsStatus.IN_PROCESS)) {
                LOG.info("Goto {} list", NihmsStatus.IN_PROCESS);
                File file = newFile(NihmsStatus.IN_PROCESS);
                URL url = urlBuilder.inProcessUrl(params);
                download(url, file, NihmsStatus.IN_PROCESS);
            }

        } catch (Exception ex) {
            throw new RuntimeException("An error occurred while downloading the NIHMS files.", ex);
        }
    }

    private void download(URL url, File outputFile, NihmsStatus status) throws IOException, InterruptedException {
        LOG.debug("Retrieving: {}", url);
        try (Response res = okHttp
            .newCall(new Request.Builder()
                         .get()
                         .url(url)
                         .build())
            .execute();
             FileOutputStream out = new FileOutputStream(outputFile)) {

            if (!res.isSuccessful()) {
                throw new RuntimeException(String.format("Error retrieving %s (HTTP status: %s): %s",
                                                         url, res.code(), res.message()));
            }

            IOUtils.copy(res.body().byteStream(), out);
            LOG.info("Downloaded and saved {} publications as file {}", status, outputFile);
            Thread.sleep(2000);
        }
    }

    /**
     * null or empty are OK for start date, but a badly formatted date that does not have the format mm-yyyy should
     * return false
     *
     * @param startDate true if valid start date (empty or formatted mm-yyyy)
     * @return true if valid start date (empty or formatted mm-yyyy)
     */
    public static boolean validStartDate(String startDate) {
        return (StringUtils.isEmpty(startDate) || startDate.matches("^(0?[1-9]|1[012])-(\\d{4})$"));
    }

    private File newFile(NihmsStatus status) {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyyMMddHHmmss");
        String timeStamp = fmt.print(new DateTime());
        String newFilePath = downloadDirectoryPath.toString() + "/" + status.toString() + "_nihmspubs_"
                             + timeStamp + ".csv";
        return new File(newFilePath);
    }

}
