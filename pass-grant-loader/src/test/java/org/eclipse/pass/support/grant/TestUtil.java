package org.eclipse.pass.support.grant;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.eclipse.pass.support.grant.data.GrantIngestRecord;

public class TestUtil {

    public final static String[] grantAwardNumber = {"B10000000", "B10000001", "B10000002", "B10000003", "B10000004"};
    public final static String[] grantLocalKey =
        {"10000001", "10000001", "10000001", "10000002", "10000003"}; //all the same, different from other ITs tho
    public final static String[] grantProjectName =
        {"Stupendous \"Research Project\" I", "Stupendous Research Project II", "Stupendous Research ProjectIII",
            "Stupendous Research ProjectIV", "Stupendous Research ProjectV"};
    public final static String[] grantAwardDate = {"01/01/1999", "01/01/2001", "01/01/2003", "01/01/2004",
        "01/01/2005"};
    //these appear to ge the same for all awards
    public final static String[] grantStartDate =
        {"2000-07-01 00:00:00", "2000-07-01 00:00:00", "2000-07-01 00:00:00", "2000-07-01 00:00:00",
            "2000-07-01 00:00:00"};
    //these seem to be the same for all awards
    public final static String[] grantEndDate =
        {"2004-06-30 00:00:00", "2004-06-30 00:00:00", "2004-06-30 00:00:00", "2004-06-30 00:00:00",
            "2004-06-30 00:00:00"};
    public final static String[] grantUpdateTimestamp =
        {"2006-03-11 00:00:00.0", "2010-04-05 00:00:00.0", "2015-11-11 00:00:00.0", "2016-11-11 00:00:00.0",
            "2016-12-11 00:00:00.0"};
    public final static String[] userEmployeeId = {"31000000", "31000001", "31000002", "31000003", "31000004"};
    public final static String[] userInstitutionalId = {"arecko1", "sclass1", "jgunn1", "jdoe1", "jdoe2"};
    public final static String[] userFirstName = {"Amanda", "Skip", "Janie", "John", "James"};
    public final static String[] userMiddleName = {"Bea", "Avery", "Gotta", "Nobody", ""};
    public final static String[] userLastName = {"Reckondwith", "Class", "Gunn", "Doe1", "Doe2"};
    public final static String[] userEmail = {"arecko1@jhu.edu", "sclass1@jhu.edu", "jgunn1@jhu.edu", "jdoe1@jhu.edu",
        "jdoe2@jhu.edu"};

    private TestUtil () {}

    public static Properties loaderPolicyProperties() throws IOException {
        File policyPropertiesFile = new File(
            TestUtil.class.getClassLoader().getResource("policy.properties").getFile());
        Properties policyProperties = new Properties();
        try (InputStream resourceStream = new FileInputStream(policyPropertiesFile)) {
            policyProperties.load(resourceStream);
        }
        return policyProperties;
    }

    public static GrantIngestRecord makeGrantIngestRecord(int iteration, int user, String abbrRole) {
        GrantIngestRecord grantIngestRecord = new GrantIngestRecord();
        grantIngestRecord.setAwardNumber(grantAwardNumber[iteration]);
        grantIngestRecord.setAwardStatus("Active");
        grantIngestRecord.setGrantNumber(grantLocalKey[iteration]);
        grantIngestRecord.setGrantTitle(grantProjectName[iteration]);
        grantIngestRecord.setAwardDate(grantAwardDate[iteration]);
        grantIngestRecord.setAwardStart(grantStartDate[iteration]);
        grantIngestRecord.setAwardEnd(grantEndDate[iteration]);

        grantIngestRecord.setDirectFunderCode("20000000");
        grantIngestRecord.setDirectFunderName("Enormous State University");
        grantIngestRecord.setPrimaryFunderCode("20000001");
        grantIngestRecord.setPrimaryFunderName("J L Gotrocks Foundation");

        grantIngestRecord.setPiFirstName(userFirstName[user]);
        grantIngestRecord.setPiMiddleName(userMiddleName[user]);
        grantIngestRecord.setPiLastName(userLastName[user]);
        grantIngestRecord.setPiEmail(userEmail[user]);
        grantIngestRecord.setPiInstitutionalId(userInstitutionalId[user]);
        grantIngestRecord.setPiEmployeeId(userEmployeeId[user]);

        grantIngestRecord.setUpdateTimeStamp(grantUpdateTimestamp[iteration]);
        grantIngestRecord.setPiRole(abbrRole);

        return grantIngestRecord;
    }
}
