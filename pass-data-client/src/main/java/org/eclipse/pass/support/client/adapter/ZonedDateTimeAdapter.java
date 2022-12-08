package org.eclipse.pass.support.client.adapter;

import java.time.ZonedDateTime;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.eclipse.pass.support.client.Util;

/**
 * Map type to JSON.
 */
public class ZonedDateTimeAdapter {
    /**
     * @param value to convert
     * @return JSON value
     */
    @ToJson
    public String toJson(ZonedDateTime value) {
        return value.format(Util.dateTimeFormatter());
    }

    /**
     * @param s to parse
     * @return type value
     */
    @FromJson
    public ZonedDateTime fromJson(String s) {
        return ZonedDateTime.parse(s, Util.dateTimeFormatter());
    }
}