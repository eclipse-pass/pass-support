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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Component
public class NihmsHarvesterDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsHarvesterDownloader.class);

    private final Path downloadDirectoryPath;
    private final OkHttpClient okHttp;

    /**
     * Initiate harvester with required properties
     */
    public NihmsHarvesterDownloader(OkHttpClient okHttpClient,
                                    @Value("${nihmsetl.data.dir}") String downloadDirectory) {
        this.okHttp = okHttpClient;
        this.downloadDirectoryPath = Path.of(downloadDirectory);

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
    }

    public void download(URL url, NihmsStatus status) throws IOException, InterruptedException {
        File outputFile = newFile(status);
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

    private File newFile(NihmsStatus status) {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyyMMddHHmmss");
        String timeStamp = fmt.print(new DateTime());
        String newFilePath = downloadDirectoryPath.toString() + "/" + status.toString() + "_nihmspubs_"
                             + timeStamp + ".csv";
        return new File(newFilePath);
    }

}
