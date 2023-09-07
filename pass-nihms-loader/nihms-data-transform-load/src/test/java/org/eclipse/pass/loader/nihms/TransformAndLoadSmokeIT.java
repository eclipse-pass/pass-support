package org.eclipse.pass.loader.nihms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.List;

import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.User;
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
        repoCopySelector.setFilter(RSQL.notEquals("id", "-1"));
        List<RepositoryCopy> repositoryCopies = passClient.selectObjects(repoCopySelector).getObjects();
        assertEquals(26, repositoryCopies.size());

        publicationSelector.setFilter(RSQL.notEquals("id", "-1"));
        List<Publication> publications = passClient.selectObjects(publicationSelector).getObjects();
        assertEquals(37, publications.size());

        submissionSelector.setFilter(RSQL.notEquals("id", "-1"));
        List<Submission> submissions = passClient.selectObjects(submissionSelector).getObjects();
        assertEquals(37, submissions.size());

        //reset file names:
        File downloadDir = new File(path);
        resetPaths(downloadDir);
    }

    private void resetPaths(File folder) {
        try {
            File[] listOfFiles = folder.listFiles();
            assertNotNull(listOfFiles);
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
        PassClientSelector<Grant> grantSelector = new PassClientSelector<>(Grant.class);

        User user = new User();
        passClient.createObject(user);

        createGrant("P30 DDDDDD", user);
        createGrant("UL1 JJJJJJ", user);
        createGrant("R01 BBBBBB", user);
        createGrant("N01 IIIIII", user);
        createGrant("T32 KKKKKK", user);
        createGrant("P30 KKKKKK", user);
        createGrant("P20 HHHHHH", user);
        createGrant("T32 LLLLLL", user);
        createGrant("R01 YYYYYY", user);
        createGrant("P30 AAAAAA", user);
        createGrant("R01 WWWWWW", user);
        createGrant("R01 FFFFFF", user);
        createGrant("R01 HHHHHH", user);
        createGrant("T32 MMMMMM", user);
        createGrant("F31 CCCCCC", user);
        createGrant("T32 NNNNNN", user);
        createGrant("R01 GGGGGG", user);
        createGrant("T32 NNNNNN", user);
        createGrant("R01 OOOOOO", user);
        createGrant("T32 JJJJJJ", user);
        createGrant("U01 LLLLLL", user);
        createGrant("TL1 OOOOOO", user);
        createGrant("K23 MMMMMM", user);
        createGrant("P30 ZZZZZZ", user);
        createGrant("R01 EEEEEE", user);
        createGrant("P60 EEEEEE", user);
        createGrant("U01 AAAAAA", user);
        createGrant("T32 GGGGGG", user);
        createGrant("T32 PPPPPP", user);
        createGrant("R01 PPPPPP", user);
        createGrant("R01 QQQQQQ", user);
        createGrant("T32 RRRRRR", user);
        createGrant("P50 UUUUUU", user);
        createGrant("R01 CCCCCC", user);
        createGrant("N01 TTTTTT", user);
        createGrant("P50 CCCCCC", user);
        createGrant("R01 DDDDDD", user);
        createGrant("P30 VVVVVV", user);
        createGrant("K24 SSSSSS", user);
        createGrant("R01 RRRRRR", user);
        createGrant("U01 BBBBBB", user);
        createGrant("K23 BBBBBB", user);
        createGrant("T32 XXXXXX", user);
        createGrant("U54 EB007958", user);
        createGrant("T32 HD094687", user);
        createGrant("R01 HL153178", user);
        createGrant("R21 NS127076", user);
        createGrant("R01 HL139543", user);
        createGrant("U54 CA268083", user);
        createGrant("UL1 TR003098", user);
        createGrant("R01 AI145435", user);
        createGrant("T32 HD094687", user);
        createGrant("R01 CA204345", user);
        createGrant("T32 GM066691", user);
        createGrant("R01 AG054004", user);
        createGrant("T32 MH109436", user);
        createGrant("UM1 AI068613", user);
        createGrant("R01 CA121113", user);
        createGrant("R01 NS082338", user);
        createGrant("N01 HC095168", user);
        createGrant("K23 DK124515", user);
        createGrant("U01 HL156056", user);
        createGrant("R01 HD086026", user);

        String checkableAwardNumber = "R01 AAAAAA";
        String checkableGrantId = createGrant(checkableAwardNumber, user);

        grantSelector.setFilter(RSQL.equals("awardNumber", checkableAwardNumber));
        String testGrantId = passClient.streamObjects(grantSelector).findFirst().orElseThrow().getId();
        assertEquals(checkableGrantId, testGrantId);

    }

}
