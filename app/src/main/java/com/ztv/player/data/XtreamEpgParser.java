package com.ztv.player.data;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class XtreamEpgParser {
    private XtreamEpgParser() {
    }

    public static List<XtreamEpgProgram> parseShortEpg(String json) throws JSONException {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        JSONArray listings = root.optJSONArray("epg_listings");
        List<XtreamEpgProgram> programs = new ArrayList<>();
        if (listings == null) {
            return programs;
        }
        for (int index = 0; index < listings.length(); index++) {
            JSONObject entry = listings.optJSONObject(index);
            if (entry == null) {
                continue;
            }
            programs.add(new XtreamEpgProgram(
                    decode(entry.optString("title", "")),
                    timeOf(entry.optString("start", "")),
                    timeOf(entry.optString("end", "")),
                    entry.optInt("now_playing", 0) == 1,
                    entry.optLong("start_timestamp", 0L),
                    entry.optLong("stop_timestamp", 0L)
            ));
        }
        return programs;
    }

    private static String decode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            byte[] decoded = Base64.decode(value, Base64.DEFAULT);
            return new String(decoded, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return value;
        }
    }

    /** Extracts HH:mm from a "yyyy-MM-dd HH:mm:ss" timestamp, best-effort. */
    private static String timeOf(String dateTime) {
        if (dateTime == null) {
            return "";
        }
        int space = dateTime.indexOf(' ');
        if (space >= 0 && dateTime.length() >= space + 6) {
            return dateTime.substring(space + 1, space + 6);
        }
        return dateTime;
    }
}
