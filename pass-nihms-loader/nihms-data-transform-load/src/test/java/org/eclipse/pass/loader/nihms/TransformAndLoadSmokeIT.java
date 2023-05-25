package org.eclipse.pass.loader.nihms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests loads in some test data from spreadsheets and verifies it all loaded in as expected
 *
 * @author Karen Hanson
 */
public class TransformAndLoadSmokeIT extends NihmsSubmissionEtlITBase {

    @BeforeEach
    public void setup() throws Exception {
        preLoadGrants();
    }

    /**
     * Retrieves csv files from data folder and processes the rows. This test
     * verifies that the data looks as expected after import
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void smokeTestLoadAndTransform() throws Exception {

        NihmsTransformLoadApp app = new NihmsTransformLoadApp(null);
        app.run();
        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        PassClientSelector<Publication> publicationSelector = new PassClientSelector<>(Publication.class);
        PassClientSelector<Submission> submissionSelector = new PassClientSelector<>(Submission.class);

        //now that it has run lets do some basic tallys to make sure they are as expected:

        //make sure RepositoryCopies are all in before moving on so we can be sure the counts are done.
        attempt(RETRIES, () -> {
            final List<RepositoryCopy> repoCopies;
            repoCopySelector.setFilter(RSQL.equals("@type", "RepositoryCopy"));
            try {
                repoCopies = passClient.selectObjects(repoCopySelector).getObjects();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertEquals(26, repoCopies.size());
        });

        attempt(RETRIES, () -> {
            final List<Publication> publications;
            publicationSelector.setFilter(RSQL.equals("@type", "Publication"));
            try {
                publications = passClient.selectObjects(publicationSelector).getObjects();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertEquals(37, publications.size());
        });

        attempt(RETRIES, () -> {
            final List<Submission> submissions;
            submissionSelector.setFilter(RSQL.equals("@type", "Submission"));
            try {
                submissions = passClient.selectObjects(submissionSelector).getObjects();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
        PassClientSelector<Grant> grantSelector = new PassClientSelector(Grant.class);

        createGrant("P30 DDDDDD", "1");
        createGrant("UL1 JJJJJJ", "2");
        createGrant("R01 BBBBBB", "3");
        createGrant("N01 IIIIII", "4");
        createGrant("T32 KKKKKK", "5");
        createGrant("P30 KKKKKK", "6");
        createGrant("P20 HHHHHH", "7");
        createGrant("T32 LLLLLL", "8");
        createGrant("R01 YYYYYY", "9");
        createGrant("P30 AAAAAA", "10");
        createGrant("R01 WWWWWW", "11");
        createGrant("R01 FFFFFF", "12");
        createGrant("R01 HHHHHH", "12");
        createGrant("T32 MMMMMM", "13");
        createGrant("F31 CCCCCC", "14");
        createGrant("T32 NNNNNN", "15");
        createGrant("T32 XXXXXX", "16");
        createGrant("R01 GGGGGG", "17");
        createGrant("R01 OOOOOO", "18");
        createGrant("T32 JJJJJJ", "19");
        createGrant("U01 LLLLLL", "20");
        createGrant("TL1 OOOOOO", "21");
        createGrant("K23 MMMMMM", "22");
        createGrant("P30 ZZZZZZ", "23");
        createGrant("R01 EEEEEE", "24");
        createGrant("P60 EEEEEE", "25");
        createGrant("U01 AAAAAA", "26");
        createGrant("T32 GGGGGG", "27");
        createGrant("T32 PPPPPP", "28");
        createGrant("R01 PPPPPP", "29");
        createGrant("R01 QQQQQQ", "29");
        createGrant("T32 RRRRRR", "29");
        createGrant("P50 UUUUUU", "30");
        createGrant("R01 CCCCCC", "31");
        createGrant("N01 TTTTTT", "32");
        createGrant("P50 CCCCCC", "33");
        createGrant("R01 DDDDDD", "33");
        createGrant("P30 VVVVVV", "34");
        createGrant("K24 SSSSSS", "35");
        createGrant("R01 RRRRRR", "35");
        createGrant("U01 BBBBBB", "36");
        createGrant("K23 BBBBBB", "37");

        String checkableAwardNumber = "R01 AAAAAA";
        String checkableGrantUri = createGrant(checkableAwardNumber, "38");

        attempt(RETRIES, () -> {
            grantSelector.setFilter(RSQL.equals("awardNumber", checkableAwardNumber));
            final String testGrantId;
            try {
                testGrantId = passClient.selectObjects(grantSelector).getObjects().get(0).getId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertEquals(checkableGrantUri, testGrantId);
        });

    }

}
