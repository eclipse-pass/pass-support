package org.eclipse.pass.support.client.adapter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.model.CopyStatus;

/**
 * Map type to JSON.
 */
public class CopyStatusAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(CopyStatus value) {
        return value.getValue();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public CopyStatus fromJson(String s) {
        return CopyStatus.of(s);
    }
}
