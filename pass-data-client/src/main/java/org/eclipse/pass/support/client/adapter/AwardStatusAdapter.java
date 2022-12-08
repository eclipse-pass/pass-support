package org.eclipse.pass.support.client.adapter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.model.AwardStatus;

/**
 * Map type to JSON.
 */
public class AwardStatusAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(AwardStatus value) {
        return value.getValue();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public AwardStatus fromJson(String s) {
        return AwardStatus.of(s);
    }
}
