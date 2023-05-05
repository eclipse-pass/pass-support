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
package org.eclipse.pass.loader.nihms.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URI;
import java.util.Set;

import org.eclipse.pass.loader.nihms.cli.NihmsTransformLoadApp;
import org.eclipse.pass.model.Grant;
import org.eclipse.pass.model.Publication;
import org.eclipse.pass.model.RepositoryCopy;
import org.eclipse.pass.model.Submission;
import org.junit.Before;
import org.junit.Test;

/**
 * Smoke tests loads in some test data from spreadsheets and verifies it all loaded in as expected
 *
 * @author Karen Hanson
 */
public class TransformAndLoadSmokeIT extends NihmsSubmissionEtlITBase {

    @Before
    public void setup() throws Exception {
        preLoadGrants();
    }

    /**
     * Retrieves csv files from data folder and processes the rows. This test
     * verifies that the data looks as expected after import
     *
     * @throws Exception
     */
    @Test
    public void smokeTestLoadAndTransform() throws Exception {

        NihmsTransformLoadApp app = new NihmsTransformLoadApp(null);
        app.run();

        //now that it has run lets do some basic tallys to make sure they are as expected:

        //make sure RepositoryCopies are all in before moving on so we can be sure the counts are done.
        attempt(RETRIES, () -> {
            final Set<URI> repoCopies = client.findAllByAttribute(RepositoryCopy.class, "@type", "RepositoryCopy");
            assertEquals(26, repoCopies.size());
        });

        attempt(RETRIES, () -> {
            Set<URI> publications = client.findAllByAttribute(Publication.class, "@type", "Publication");
            assertEquals(37, publications.size());
        });

        attempt(RETRIES, () -> {
            Set<URI> submissions = client.findAllByAttribute(Submission.class, "@type", "Submission");
            assertEquals(45, submissions.size());
        });

        //reset file names:
        File downloadDir = new File(path);
        resetPaths(downloadDir);

    }

    private void resetPaths(File folder) {
        try {
            File[] listOfFiles = folder.listFiles();
            for (File filepath : listOfFiles) {
                if (filepath.getAbsolutePath().endsWith(".done")) {
                    String fp = filepath.getAbsolutePath();
                    filepath.renameTo(new File(fp.substring(0, fp.length() - 5)));
                }
            }
        } catch (Exception ex) {
            fail(
                "There was a problem resetting the file names to remove '.done'. File names will need to be manually " +
                "reset before testing again");
        }
    }

    private void preLoadGrants() throws Exception {
        createGrant("P30 DDDDDD", "http://test:8080/fcrepo/rest/users/1");
        createGrant("UL1 JJJJJJ", "http://test:8080/fcrepo/rest/users/2");
        createGrant("R01 BBBBBB", "http://test:8080/fcrepo/rest/users/3");
        createGrant("N01 IIIIII", "http://test:8080/fcrepo/rest/users/4");
        createGrant("T32 KKKKKK", "http://test:8080/fcrepo/rest/users/5");
        createGrant("P30 KKKKKK", "http://test:8080/fcrepo/rest/users/6");
        createGrant("P20 HHHHHH", "http://test:8080/fcrepo/rest/users/7");
        createGrant("T32 LLLLLL", "http://test:8080/fcrepo/rest/users/8");
        createGrant("R01 YYYYYY", "http://test:8080/fcrepo/rest/users/9");
        createGrant("P30 AAAAAA", "http://test:8080/fcrepo/rest/users/10");
        createGrant("R01 WWWWWW", "http://test:8080/fcrepo/rest/users/11");
        createGrant("R01 FFFFFF", "http://test:8080/fcrepo/rest/users/12");
        createGrant("R01 HHHHHH", "http://test:8080/fcrepo/rest/users/12");
        createGrant("T32 MMMMMM", "http://test:8080/fcrepo/rest/users/13");
        createGrant("F31 CCCCCC", "http://test:8080/fcrepo/rest/users/14");
        createGrant("T32 NNNNNN", "http://test:8080/fcrepo/rest/users/15");
        createGrant("T32 XXXXXX", "http://test:8080/fcrepo/rest/users/16");
        createGrant("R01 GGGGGG", "http://test:8080/fcrepo/rest/users/17");
        createGrant("R01 OOOOOO", "http://test:8080/fcrepo/rest/users/18");
        createGrant("T32 JJJJJJ", "http://test:8080/fcrepo/rest/users/19");
        createGrant("U01 LLLLLL", "http://test:8080/fcrepo/rest/users/20");
        createGrant("TL1 OOOOOO", "http://test:8080/fcrepo/rest/users/21");
        createGrant("K23 MMMMMM", "http://test:8080/fcrepo/rest/users/22");
        createGrant("P30 ZZZZZZ", "http://test:8080/fcrepo/rest/users/23");
        createGrant("R01 EEEEEE", "http://test:8080/fcrepo/rest/users/24");
        createGrant("P60 EEEEEE", "http://test:8080/fcrepo/rest/users/25");
        createGrant("U01 AAAAAA", "http://test:8080/fcrepo/rest/users/26");
        createGrant("T32 GGGGGG", "http://test:8080/fcrepo/rest/users/27");
        createGrant("T32 PPPPPP", "http://test:8080/fcrepo/rest/users/28");
        createGrant("R01 PPPPPP", "http://test:8080/fcrepo/rest/users/29");
        createGrant("R01 QQQQQQ", "http://test:8080/fcrepo/rest/users/29");
        createGrant("T32 RRRRRR", "http://test:8080/fcrepo/rest/users/29");
        createGrant("P50 UUUUUU", "http://test:8080/fcrepo/rest/users/30");
        createGrant("R01 CCCCCC", "http://test:8080/fcrepo/rest/users/31");
        createGrant("N01 TTTTTT", "http://test:8080/fcrepo/rest/users/32");
        createGrant("P50 CCCCCC", "http://test:8080/fcrepo/rest/users/33");
        createGrant("R01 DDDDDD", "http://test:8080/fcrepo/rest/users/33");
        createGrant("P30 VVVVVV", "http://test:8080/fcrepo/rest/users/34");
        createGrant("K24 SSSSSS", "http://test:8080/fcrepo/rest/users/35");
        createGrant("R01 RRRRRR", "http://test:8080/fcrepo/rest/users/35");
        createGrant("U01 BBBBBB", "http://test:8080/fcrepo/rest/users/36");
        createGrant("K23 BBBBBB", "http://test:8080/fcrepo/rest/users/37");

        String checkableAwardNumber = "R01 AAAAAA";
        URI checkableGrantUri = createGrant(checkableAwardNumber, "http://test:8080/fcrepo/rest/users/38");

        attempt(RETRIES, () -> {
            final URI uri = client.findByAttribute(Grant.class, "awardNumber", checkableAwardNumber);
            assertEquals(checkableGrantUri, uri);
        });

    }

}
