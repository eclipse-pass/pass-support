package org.eclipse.pass.support.client.adapter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.model.DepositStatus;

/**
 * Map type to JSON.
 */
public class DepositStatusAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(DepositStatus value) {
        return value.getValue();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public DepositStatus fromJson(String s) {
        return DepositStatus.of(s);
    }
}
