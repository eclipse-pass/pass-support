package org.eclipse.pass.support.grant.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoeusConnectorIT {

    private CoeusConnector connector;

    private File policyPropertiesFile = new File(
            getClass().getClassLoader().getResource("policy.properties").getFile());
    private Properties policyProperties = new Properties();
    @BeforeEach
    public void setup() throws Exception {

        try (InputStream resourceStream = new FileInputStream(policyPropertiesFile)) {
            policyProperties.load(resourceStream);
        }
        connector = new CoeusConnector(
                null, policyProperties);
    }
    @Test
    public void testGrantRetrieveShouldNormalizeAwardNumber() throws SQLException, IOException, ClassNotFoundException {
        String query = connector.buildQueryString("2023-06-01 06:00:00.0", "07/20/2023", "grant", null);
        List<Map<String, String>> results = connector.retrieveUpdates(query, "grant");
        //loop through result and check that all award numbers are normalized
        for (Map<String, String> result : results) {
            String awardNumber = result.get(CoeusFieldNames.C_GRANT_AWARD_NUMBER);
            assertTrue(awardNumber.matches("^[A-Z0-9]{3}\\s[A-Z0-9]{8}"));
        }
    }
}
