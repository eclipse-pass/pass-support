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

import static org.eclipse.pass.loader.nihms.util.ProcessingUtil.nullOrEmpty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.pass.client.nihms.NihmsPassClientService;
import org.eclipse.pass.entrez.PmidLookup;
import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.loader.nihms.util.FileUtil;
import org.eclipse.pass.support.client.SubmissionStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that takes a filepath, gets the csvs there, and transforms/loads the data according to a
 * list of statuses to be processed.
 *
 * @author Karen Hanson
 */
public class NihmsTransformLoadService {

    private static Logger LOG = LoggerFactory.getLogger(NihmsTransformLoadService.class);

    private NihmsPassClientService nihmsPassClient;

    private PmidLookup pmidLookup;

    private static CompletedPublicationsCache completedPubsCache;

    private SubmissionStatusService statusService;

    public NihmsTransformLoadService() {
        nihmsPassClient = new NihmsPassClientService();
        pmidLookup = new PmidLookup();
        statusService = new SubmissionStatusService();
        completedPubsCache = CompletedPublicationsCache.getInstance();
    }

    /**
     * Option to inject dependencies
     *
     * @param passClientService the NihmsPassClientService instance to use
     * @param pmidLookup        the PmidLookup instance to use
     * @param statusService     the SubmissionStatusService instance to use
     */
    public NihmsTransformLoadService(NihmsPassClientService passClientService, PmidLookup pmidLookup,
                                     SubmissionStatusService statusService) {
        this.nihmsPassClient = passClientService;
        this.pmidLookup = pmidLookup;
        this.statusService = statusService;
        completedPubsCache = CompletedPublicationsCache.getInstance();
    }

    /**
     * Goes through list of files in directory specified and processes those that have a NihmsStatus
     * that matches a row in statusesToProcess. If statuseseToProcess is null/empty, it will process all statuses
     *
     * @param statusesToProcess if null or empty, all statuses will be processed.
     */
    public void transformAndLoadFiles(Set<NihmsStatus> statusesToProcess) {
        File dataDirectory = FileUtil.getDataDirectory();
        if (dataDirectory == null) {
            throw new RuntimeException("dataDirectory cannot be empty");
        }
        if (nullOrEmpty(statusesToProcess)) {
            statusesToProcess = new HashSet<NihmsStatus>();
            statusesToProcess.addAll(EnumSet.allOf(NihmsStatus.class));
        }

        List<Path> filepaths = loadFiles(dataDirectory);

        Consumer<NihmsPublication> pubConsumer = pub -> {
            try {
                transformAndLoadNihmsPub(pub);
            } catch (IOException e) {
                LOG.error("Error transforming and loading NIHMS publication for NihmsId {}", pub.getNihmsId(), e);
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

    /**
     * Takes pub record from CSV loader, transforms it then passes transformed record to the
     * loader. Exceptions generally should not be caught here, they should be caught by CSV processor which
     * tallies the successes/failures. The only Exception caught is UpdateConflictException, which is easy to
     * recover from. On catching an UpdateConflictException, it will attempt several retries before failing and
     * moving on
     *
     * @param pub the NihmsPublication object
     */
    public void transformAndLoadNihmsPub(NihmsPublication pub) throws IOException {
        final int MAX_ATTEMPTS = 3; //applies to UpdateConflictExceptions only, which can be recovered from
        int attempt = 0;

        // if the record is compliant, let's check the cache to see if it has been processed previously
        if (pub.getNihmsStatus().equals(NihmsStatus.COMPLIANT)
            && completedPubsCache.contains(pub.getPmid(), pub.getGrantNumber())) {
            LOG.info(
                "Compliant NIHMS record with pmid {} and award number \"{}\" has been processed in a previous load",
                pub.getPmid(), pub.getGrantNumber());
            return;
        }

        while (true) {
            try {
                attempt = attempt + 1;
                NihmsPublicationToSubmission transformer = new NihmsPublicationToSubmission(nihmsPassClient,
                                                                                            pmidLookup);
                SubmissionDTO transformedRecord = transformer.transform(pub);
                if (transformedRecord.doUpdate()) {
                    SubmissionLoader loader = new SubmissionLoader(nihmsPassClient, statusService);
                    loader.load(transformedRecord);
                } else {
                    LOG.info("No update required for PMID {} with award number {}", pub.getPmid(),
                             pub.getGrantNumber());
                }

                break;
            } catch (IllegalStateException | ConcurrentModificationException | IllegalArgumentException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    LOG.warn("Update failed for PMID %s due to database conflict, attempting retry # %d", pub.getPmid(),
                             attempt);
                } else {
                    throw new RuntimeException(
                        String.format("Update could not be applied for PMID %s after %d attempts ", pub.getPmid(),
                                      MAX_ATTEMPTS), ex);
                }
            } catch (IOException e) {
                LOG.error("Error transforming or loading record for PMID {} with award number {}", pub.getPmid(),
                          pub.getGrantNumber(), e);
                throw new IOException(e);
            }
        }

        if (pub.getNihmsStatus().equals(NihmsStatus.COMPLIANT)
            && !nullOrEmpty(pub.getPmcId())) {
            //add to cache so it doesn't check it again once it has been processed and has a pmcid assigned
            completedPubsCache.add(pub.getPmid(), pub.getGrantNumber());
            LOG.debug("Added pmid {} and grant \"{}\" to cache", pub.getPmid(), pub.getGrantNumber());
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
        if (nullOrEmpty(filepaths)) {
            throw new RuntimeException(
                String.format("No file found to process at path %s", downloadDirectory.toString()));
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
