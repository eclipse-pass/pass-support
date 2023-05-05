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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/**
 * Utility to poll a directory to check a file is downloaded. Once the file appears it
 * sends the new filepath back
 *
 * @author Karen Hanson
 */
public class FileWatcher {

    private FileWatcher () {
        //never called
    }

    /**
     * time to wait for file to appear
     */
    private static final Integer TIMEOUT = 90;

    /**
     * Polls a directory until it finds a file matching the criteria, passes back the file name.
     *
     * @param directory          directory to poll
     * @param matchPrefix        the filename prefix
     * @param matchFileExtension the filename extension
     * @return the matching file
     */
    public static File getNewFile(Path directory, String matchPrefix, String matchFileExtension) {

        try {
            long startTime = System.currentTimeMillis();
            long currentTime = (System.currentTimeMillis() - startTime) / 1000;

            do {
                Optional<File> mostRecentFile = Arrays
                    .stream(directory.toFile().listFiles())
                    .filter(f -> (
                        f.isFile()
                        && f.getName().startsWith(matchPrefix)
                        && f.getName().endsWith(matchFileExtension)))
                    .max(
                        (f1, f2) -> Long.compare(f1.lastModified(),
                                                 f2.lastModified()));
                Thread.sleep(1000);
                if (mostRecentFile.isPresent()) {
                    //a file has appeared, but make sure it is finished downloading by checking for .part file
                    String partFile = mostRecentFile.get().getAbsolutePath() + ".part";
                    if (!(new File(partFile).exists())) {
                        return mostRecentFile.get();
                    }
                }
                currentTime = (System.currentTimeMillis() - startTime) / 1000;
            } while (currentTime <= TIMEOUT);

        } catch (InterruptedException ie) {
            throw new RuntimeException("Process was interrupted while waiting for file to download", ie);
        } catch (Exception ex) {
            throw new RuntimeException("A problem occurred while waiting for file to download", ex);
        }
        //if didn't return a value by now, something went wrong.
        throw new RuntimeException("Download operation timed out. Expected file was not downloaded");
    }
}
