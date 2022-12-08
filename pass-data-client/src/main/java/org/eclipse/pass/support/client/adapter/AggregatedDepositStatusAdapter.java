package org.eclipse.pass.support.client.adapter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.model.AggregatedDepositStatus;

/**
 * Map type to JSON.
 */
public class AggregatedDepositStatusAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(AggregatedDepositStatus value) {
        return value.getValue();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public AggregatedDepositStatus fromJson(String s) {
        return AggregatedDepositStatus.of(s);
    }
}
