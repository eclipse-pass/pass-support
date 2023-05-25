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
package org.eclipse.pass.loader.nihms.util;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.function.Predicate;

/**
 * Utility class to support directory/filepath processing
 *
 * @author Karen Hanson
 */
public class FileUtil {

    private FileUtil () {
        //never called
    }

    private static final String NIHMS_DATA_DIR_PROPKEY = "nihmsetl.data.dir";

    private static final String DEFAULT_DATA_FOLDER = "/data";

    /**
     * Gets directory that the app was run from
     *
     * @return the current directory
     */
    public static String getCurrentDirectory() {
        try {
            return new File(
                FileUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Selects the path of the config file to load properties from. Uses system property first with key provided first,
     * if none then uses environment variable, otherwise default to current folder
     * @param filepathKey the system property key to use
     * @param defaultFileName the default filename to use of the configuration file
     * @return the path to the config file
     */
    public static File getConfigFilePath(String filepathKey, String defaultFileName) {
        String configFilePath = ConfigUtil.getSystemProperty(filepathKey,
                                                             getCurrentDirectory() + "/" + defaultFileName);
        File configDirectory = new File(configFilePath);
        return configDirectory;
    }

    /**
     * Selects the directory that data will be downloaded to or read from.
     * Uses system property first, if none then use environment variable, otherwise default to ./data
     *
     * @return the path to the download directory
     */
    public static File getDataDirectory() {
        String currDirectoryPath = getCurrentDirectory();
        String dataFilePath = ConfigUtil.getSystemProperty(NIHMS_DATA_DIR_PROPKEY,
                                                           getCurrentDirectory() + "/" + DEFAULT_DATA_FOLDER);
        if (dataFilePath == null) {
            dataFilePath = currDirectoryPath + DEFAULT_DATA_FOLDER;
        }
        File downloadDirectory = new File(dataFilePath);
        return downloadDirectory;
    }

    /**
     * Retrieve a list of files in a directory, filter by directory
     *
     * @param directory the directory
     * @return the file listing
     */
    public static List<Path> getCsvFilePaths(Path directory) {
        List<Path> filepaths = null;
        try {
            filepaths = Files.list(directory)
                             .filter(FILTER_GENERAL)
                             .filter(FILTER_CSV)
                             .map(Path::toAbsolutePath)
                             .collect(toList());
        } catch (Exception ex) {
            throw new RuntimeException("A problem occurred while loading CSV file paths from " + directory.toString());
        }
        return filepaths;
    }

    /**
     * Calculate filter based on whether there is a filter system property, and whether the file is appended
     * with ".done" which signals the file was processed
     */
    private static Predicate<Path> FILTER_GENERAL = path -> {
        PathMatcher pathFilter = p -> true;
        String filterProp = ConfigUtil.getSystemProperty("filter", null);
        if (filterProp != null) {
            pathFilter = FileSystems.getDefault().getPathMatcher("glob:" + filterProp);
        }
        return pathFilter.matches(path.getFileName());
    };

    /**
     * Calculate filter based on whether there is a filter system property, and whether the file is appended
     * with ".done" which signals the file was processed
     */
    private static Predicate<Path> FILTER_CSV = path -> {
        return path.getFileName().toString().endsWith(".csv");
    };

    /**
     * Rename file to append ".done" once it has been processed
     *
     * @param path the path to rename
     */
    public static void renameToDone(Path path) {
        final File file = path.toFile();
        file.renameTo(new File(file.getAbsolutePath() + ".done"));
    }
}
