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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests loads in some test data from spreadsheets and verifies it all loaded in as expected
 *
 * @author Karen Hanson
 */
@Disabled("https://github.com/eclipse-pass/main/issues/679")
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

        createGrant("P30 DDDDDD");
        createGrant("UL1 JJJJJJ");
        createGrant("R01 BBBBBB");
        createGrant("N01 IIIIII");
        createGrant("T32 KKKKKK");
        createGrant("P30 KKKKKK");
        createGrant("P20 HHHHHH");
        createGrant("T32 LLLLLL");
        createGrant("R01 YYYYYY");
        createGrant("P30 AAAAAA");
        createGrant("R01 WWWWWW");
        createGrant("R01 FFFFFF");
        createGrant("R01 HHHHHH");
        createGrant("T32 MMMMMM");
        createGrant("F31 CCCCCC");
        createGrant("T32 NNNNNN");
        createGrant("T32 XXXXXX");
        createGrant("R01 GGGGGG");
        createGrant("R01 OOOOOO");
        createGrant("T32 JJJJJJ");
        createGrant("U01 LLLLLL");
        createGrant("TL1 OOOOOO");
        createGrant("K23 MMMMMM");
        createGrant("P30 ZZZZZZ");
        createGrant("R01 EEEEEE");
        createGrant("P60 EEEEEE");
        createGrant("U01 AAAAAA");
        createGrant("T32 GGGGGG");
        createGrant("T32 PPPPPP");
        createGrant("R01 PPPPPP");
        createGrant("R01 QQQQQQ");
        createGrant("T32 RRRRRR");
        createGrant("P50 UUUUUU");
        createGrant("R01 CCCCCC");
        createGrant("N01 TTTTTT");
        createGrant("P50 CCCCCC");
        createGrant("R01 DDDDDD");
        createGrant("P30 VVVVVV");
        createGrant("K24 SSSSSS");
        createGrant("R01 RRRRRR");
        createGrant("U01 BBBBBB");
        createGrant("K23 BBBBBB");
        createGrant("U54 EB007958");
        createGrant("T32 HD094687");
        createGrant("R01 HL153178");
        createGrant("R21 NS127076");
        createGrant("R01 HL139543");
        createGrant("U54 CA268083");
        createGrant("UL1 TR003098");
        createGrant("R01 AI145435");
        createGrant("T32 HD094687");
        createGrant("R01 CA204345");
        createGrant("T32 GM066691");
        createGrant("R01 AG054004");
        createGrant("T32 MH109436");
        createGrant("UM1 AI068613");
        createGrant("R01 CA121113");
        createGrant("R01 NS082338");
        createGrant("N01 HC095168");
        createGrant("K23 DK124515");
        createGrant("U01 HL156056");
        createGrant("R01 HD086026");

        String checkableAwardNumber = "R01 AAAAAA";
        String checkableGrantId = createGrant(checkableAwardNumber);

        grantSelector.setFilter(RSQL.equals("awardNumber", checkableAwardNumber));
        String testGrantId = passClient.streamObjects(grantSelector).findFirst().get().getId().toString();
        assertEquals(checkableGrantId, testGrantId);

    }

}
