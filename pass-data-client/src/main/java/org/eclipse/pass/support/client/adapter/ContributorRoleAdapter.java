package org.eclipse.pass.support.client.adapter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.model.ContributorRole;

/**
 * Map type to JSON.
 */
public class ContributorRoleAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(ContributorRole value) {
        return value.getValue();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public ContributorRole fromJson(String s) {
        return ContributorRole.of(s);
    }
}
