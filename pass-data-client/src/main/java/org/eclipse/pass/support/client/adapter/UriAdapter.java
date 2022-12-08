package org.eclipse.pass.support.client.adapter;

import java.net.URI;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;

/**
 * Map type to JSON.
 */
public class UriAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(URI value) {
        return value.toString();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public URI fromJson(String s) {
        return URI.create(s);
    }
}
