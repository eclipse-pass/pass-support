package org.eclipse.pass.support.client.adapter;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.model.EventType;

/**
 * Map type to JSON.
 */
public class EventTypeAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(EventType value) {
        return value.getValue();
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public EventType fromJson(String s) {
        return EventType.of(s);
    }
}
