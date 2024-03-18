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
import java.io.IOException;
import java.nio.file.Path;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.loader.nihms.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service that takes a filepath, gets the csvs there, and transforms/loads the data according to a
 * list of statuses to be processed.
 *
 * @author Karen Hanson
 */
@Service
public class NihmsTransformLoadService {

    private static Logger LOG = LoggerFactory.getLogger(NihmsTransformLoadService.class);

    @Value("${nihmsetl.data.dir}")
    private String downloadDirectory;

    private final NihmsPublicationToSubmission nihmsPublicationToSubmission;
    private final SubmissionLoader submissionLoader;
    private final CompletedPublicationsCache completedPublicationsCache;

    public NihmsTransformLoadService(SubmissionLoader submissionLoader,
                                     NihmsPublicationToSubmission nihmsPublicationToSubmission,
                                     CompletedPublicationsCache completedPublicationsCache) {
        this.submissionLoader = submissionLoader;
        this.nihmsPublicationToSubmission = nihmsPublicationToSubmission;
        this.completedPublicationsCache = completedPublicationsCache;
    }

    /**
     * Goes through list of files in directory specified and processes those that have a NihmsStatus
     * that matches a row in statusesToProcess. If statuseseToProcess is null/empty, it will process all statuses
     *
     * @param statusesToProcess if null or empty, all statuses will be processed.
     */
    public void transformAndLoadFiles(Set<NihmsStatus> statusesToProcess) {
        File dataDirectory = new File(downloadDirectory);
        if (dataDirectory == null) {
            throw new RuntimeException("dataDirectory cannot be empty");
        }
        if (CollectionUtils.isEmpty(statusesToProcess)) {
            statusesToProcess = new HashSet<>();
            statusesToProcess.addAll(EnumSet.allOf(NihmsStatus.class));
        }

        List<Path> filepaths = loadFiles(dataDirectory);

        Consumer<NihmsPublication> pubConsumer = pub -> {
            try {
                transformAndLoadNihmsPub(pub);
            } catch (IOException e) {
                LOG.error("Error transforming and loading NIHMS publication for NIHMS ID {}", pub.getRawNihmsId(), e);
                throw new RuntimeException(e);
            }
        };
        int count = 0;
        for (Path path : filepaths) {
            NihmsStatus nihmsStatus = nihmsStatus(path);
            if (statusesToProcess.contains(nihmsStatus)) {
                NihmsCsvProcessor processor = new NihmsCsvProcessor(path, nihmsStatus);
                processor.processCsv(pubConsumer);
                FileUtil.renameToDone(path);
                count = count + 1;
            }
        }
        if (count > 0) {
            LOG.info("Transform and load complete. Processed {} files", count);
        } else {
            LOG.info("Transform and load complete. No files matched the statuses provided");
        }
    }

    void transformAndLoadNihmsPub(NihmsPublication pub) throws IOException {
        final int MAX_ATTEMPTS = 3; //applies to UpdateConflictExceptions only, which can be recovered from
        int attempt = 0;

        // if the record is compliant, let's check the cache to see if it has been processed previously
        if (pub.getNihmsStatus().equals(NihmsStatus.COMPLIANT)
            && completedPublicationsCache.contains(pub.getPmid(), pub.getGrantNumber())) {
            LOG.info("Compliant NIHMS record with PMID {}, NIHMS ID {}, and award number {} has been processed " +
                    "in a previous load", pub.getPmid(), pub.getRawNihmsId(), pub.getGrantNumber());
            return;
        }

        while (true) {
            try {
                attempt = attempt + 1;
                SubmissionDTO transformedRecord = nihmsPublicationToSubmission.transform(pub);
                if (transformedRecord.doUpdate()) {
                    submissionLoader.load(transformedRecord);
                } else {
                    LOG.info("No update required for PMID {}, NIHMS ID {}, and award number {}", pub.getPmid(),
                        pub.getRawNihmsId(), pub.getGrantNumber());
                }

                break;
            } catch (IllegalStateException | ConcurrentModificationException | IllegalArgumentException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    LOG.warn("Update failed for PMID {} and NIHMS ID {} due to database conflict, " +
                            "attempting retry # {}", pub.getPmid(), pub.getRawNihmsId(), attempt);
                    LOG.warn("Error message: {}", ex.getMessage());
                } else {
                    throw new RuntimeException(
                        String.format("Update could not be applied for PMID %s and NIHMS ID %s after %d attempts ",
                            pub.getPmid(), pub.getRawNihmsId(), MAX_ATTEMPTS), ex);
                }
            } catch (IOException e) {
                LOG.error("Error transforming or loading record for PMID {} and NIHMS ID {} with award number {}",
                    pub.getPmid(), pub.getRawNihmsId(), pub.getGrantNumber(), e);
                throw new IOException(e);
            }
        }

        if (pub.getNihmsStatus().equals(NihmsStatus.COMPLIANT)
            && StringUtils.isNotEmpty(pub.getPmcId())) {
            //add to cache so it doesn't check it again once it has been processed and has a pmcid assigned
            completedPublicationsCache.add(pub.getPmid(), pub.getGrantNumber());
            LOG.debug("Added PMID {} and grant \"{}\" to cache", pub.getPmid(), pub.getGrantNumber());
        }
    }

    /**
     * Checks directory provided and attempts to load a list of files to process
     *
     * @param downloadDirectory
     */
    private List<Path> loadFiles(File downloadDirectory) {
        List<Path> filepaths = null;
        try {
            filepaths = FileUtil.getCsvFilePaths(downloadDirectory.toPath());
        } catch (Exception e) {
            throw new RuntimeException(
                String.format("A problem occurred while loading file paths from %s", downloadDirectory.toString()), e);
        }
        if (CollectionUtils.isEmpty(filepaths)) {
            throw new RuntimeException(
                String.format("No file found to process at path %s", downloadDirectory));
        }
        return filepaths;
    }

    /**
     * Cycles through Submission status types, and matches it to the filepath to determine
     * the status of the rows in the CSV file. If no match is found, an exception is thrown.
     *
     * @param path
     * @return
     */
    private static NihmsStatus nihmsStatus(Path path) {
        String filename = path.getFileName().toString();

        for (NihmsStatus status : NihmsStatus.values()) {
            if (filename.startsWith(status.toString())) {
                return status;
            }
        }
        throw new RuntimeException(
            "Could not determine the Status of the publications being imported." +
            " Please ensure filenames are prefixed according to the Submission status.");
    }

}
