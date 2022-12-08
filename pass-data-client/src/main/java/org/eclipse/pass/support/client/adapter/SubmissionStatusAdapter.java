package org.eclipse.pass.support.client.adapter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.model.SubmissionStatus;

/**
 * Map type to JSON.
 */
public class SubmissionStatusAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(SubmissionStatus value) {
        return value.getValue();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public SubmissionStatus fromJson(String s) {
        return SubmissionStatus.of(s);
    }
}
