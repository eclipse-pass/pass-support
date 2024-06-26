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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Karen Hanson
 */
@ExtendWith({MockitoExtension.class})
public class FileUtilTest {
    private File createdFolder;
    private String testFilePath;

    private static final String DATA_DIR_KEY = "nihmsetl.data.dir";
    private static final String NIHMS_CONFIG_FILEPATH_KEY = "nihmsetl.harvester.configfile";

    @BeforeEach
    public void createFolder(@TempDir Path tempDir) throws Exception {
        createdFolder = tempDir.toFile();
        testFilePath = createdFolder.getAbsolutePath() + "test.properties";
    }

    @AfterEach
    public void clearProps() throws IOException {
        System.clearProperty("filter");
        System.clearProperty(NIHMS_CONFIG_FILEPATH_KEY);
        System.clearProperty(DATA_DIR_KEY);
        if (createdFolder.exists()) {
            deleteFilesInDirectory(createdFolder);
            createdFolder.delete();
        }
        assertFalse(createdFolder.exists());
    }

    /**
     * Confirms that getFilePaths will retrieve 3 csv files in target directory
     *
     * @throws IOException if file cannot be created
     */
    @Test
    public void testGetFilePaths3csv() throws IOException {
        File file1 = File.createTempFile("file1", ".csv", createdFolder);
        File file2 = File.createTempFile("file2", ".csv", createdFolder);
        File file3 = File.createTempFile("file3", ".csv", createdFolder);
        List<Path> paths = FileUtil.getCsvFilePaths(createdFolder.toPath());
        assertTrue(paths.contains(file1.toPath()));
        assertTrue(paths.contains(file2.toPath()));
        assertTrue(paths.contains(file3.toPath()));
    }

    /**
     * Verifies that getFilePaths ignores .docx, and .done, and gathers only .csv
     *
     * @throws IOException if file cannot be created
     */
    @Test
    public void testGetFilePaths3csv1doc() throws IOException {
        File file1 = File.createTempFile("file1", ".csv", createdFolder);
        File.createTempFile("file2", ".csv.done", createdFolder);
        File file2 = File.createTempFile("file3", ".csv", createdFolder);
        File.createTempFile("file4", ".docx", createdFolder);
        List<Path> paths = FileUtil.getCsvFilePaths(createdFolder.toPath());
        assertTrue(paths.contains(file1.toPath()));
        assertTrue(paths.contains(file2.toPath()));
        assertEquals(2, paths.size());
        paths = null;
    }

    /**
     * Verifies that getFilePaths ignores .docx, and .done, and gathers only .csv
     *
     * @throws IOException if file cannot be created
     */
    @Test
    public void testGetFilePathsFilterFileName() throws IOException {
        File file1 = File.createTempFile("file1-02-08-2018-", ".csv", createdFolder);
        File.createTempFile("file2-02-08-2018-", ".docx", createdFolder);
        File file2 = File.createTempFile("file3-02-08-2018-", ".csv", createdFolder);
        File.createTempFile("file4-02-09-2018-", ".csv", createdFolder);
        File.createTempFile("file5-02-09-2018-", ".csv", createdFolder);
        System.setProperty("filter", "*02-08-2018*");
        List<Path> paths = FileUtil.getCsvFilePaths(createdFolder.toPath());
        assertTrue(paths.contains(file1.toPath()));
        assertTrue(paths.contains(file2.toPath()));
        assertEquals(2, paths.size());
    }

    /**
     * Confirms that if you rename a file to append ".done", the new file exists, while the old is gone.
     *
     * @throws IOException if there is an error creating the file
     */
    @Test
    public void renameToDone() throws IOException {
        File file1 = File.createTempFile("file1-02-08-2018-", ".csv", createdFolder);
        FileUtil.renameToDone(file1.toPath());
        String newFileName = file1.getAbsolutePath().toString() + ".done";
        Path path = FileSystems.getDefault().getPath(newFileName);
        assertTrue(path.toFile().exists());
        assertFalse(file1.exists());

    }

    /**
     * Remove files within the specified directory
     *
     * @param directory the directory to remove files from
     */
    private void deleteFilesInDirectory(File directory) {
        String[] entries = directory.list();
        for (String s : entries) {
            File currentFile = new File(directory.getPath(), s);
            currentFile.delete();
        }
    }

}
