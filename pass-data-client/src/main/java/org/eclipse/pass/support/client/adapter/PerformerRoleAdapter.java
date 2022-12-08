package org.eclipse.pass.support.client.adapter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.model.PerformerRole;

/**
 * Map type to JSON.
 */
public class PerformerRoleAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(PerformerRole value) {
        return value.getValue();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public PerformerRole fromJson(String s) {
        return PerformerRole.of(s);
    }
}
