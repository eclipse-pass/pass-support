package org.eclipse.pass.support.client.adapter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.model.IntegrationType;

/**
 * Map type to JSON.
 */
public class IntegrationTypeAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(IntegrationType value) {
        return value.getValue();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public IntegrationType fromJson(String s) {
        return IntegrationType.of(s);
    }
}
