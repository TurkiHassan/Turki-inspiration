package com.ztv.player.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Records recently played items (live, movies, episodes), most recent first. */
public final class HistoryStore {
    private static final String PREFS_NAME = "ztv_history";
    private static final int MAX_PER_TYPE = 40;

    private final SharedPreferences preferences;

    public HistoryStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String key(XtreamContentType type) {
        return "history_" + type.name();
    }

    public void record(XtreamMediaItem item) {
        List<XtreamMediaItem> items = list(item.type());
        List<XtreamMediaItem> kept = new ArrayList<>();
        kept.add(item);
        for (XtreamMediaItem existing : items) {
            if (existing.id() != item.id()) {
                kept.add(existing);
            }
        }
        persist(item.type(), kept);
    }

    public void remove(XtreamContentType type, int id) {
        List<XtreamMediaItem> items = list(type);
        List<XtreamMediaItem> kept = new ArrayList<>();
        for (XtreamMediaItem item : items) {
            if (item.id() != id) {
                kept.add(item);
            }
        }
        persist(type, kept);
    }

    /** Overwrites the entire history list for a type (used by settings backup/restore). */
    public void replaceAll(XtreamContentType type, List<XtreamMediaItem> items) {
        persist(type, items);
    }

    private void persist(XtreamContentType type, List<XtreamMediaItem> items) {
        List<XtreamMediaItem> kept = items.size() > MAX_PER_TYPE
                ? new ArrayList<>(items.subList(0, MAX_PER_TYPE))
                : items;
        JSONArray array = new JSONArray();
        for (XtreamMediaItem entry : kept) {
            try {
                JSONObject object = new JSONObject();
                object.put("title", entry.title());
                object.put("id", entry.id());
                object.put("categoryId", entry.categoryId());
                object.put("imageUrl", entry.imageUrl());
                object.put("detail", entry.detail());
                object.put("ext", entry.containerExtension());
                array.put(object);
            } catch (Exception ignored) {
                // Skip items that fail to serialize.
            }
        }
        preferences.edit().putString(key(type), array.toString()).apply();
    }

    public List<XtreamMediaItem> list(XtreamContentType type) {
        List<XtreamMediaItem> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(key(type), "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) {
                    continue;
                }
                items.add(new XtreamMediaItem(
                        type,
                        object.optString("title", "Untitled"),
                        object.optInt("id", -1),
                        object.optString("categoryId", ""),
                        object.optString("imageUrl", ""),
                        object.optString("detail", ""),
                        object.optString("ext", "mp4")
                ));
            }
        } catch (Exception ignored) {
            // Corrupt store: treat as empty.
        }
        return items;
    }
}
