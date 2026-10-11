package com.ztv.player.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persists favorite movies/series/channels as lightweight item snapshots. */
public final class FavoritesStore {
    private static final String PREFS_NAME = "ztv_favorites";

    private final SharedPreferences preferences;

    public FavoritesStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String key(XtreamContentType type) {
        return "fav_" + type.name();
    }

    public boolean isFavorite(XtreamContentType type, int id) {
        for (XtreamMediaItem item : list(type)) {
            if (item.id() == id) {
                return true;
            }
        }
        return false;
    }

    /** Adds or removes the item; returns the new favorite state. */
    public boolean toggle(XtreamMediaItem item) {
        if (isFavorite(item.type(), item.id())) {
            remove(item.type(), item.id());
            return false;
        }
        add(item);
        return true;
    }

    public void add(XtreamMediaItem item) {
        List<XtreamMediaItem> items = list(item.type());
        for (XtreamMediaItem existing : items) {
            if (existing.id() == item.id()) {
                return;
            }
        }
        items.add(0, item);
        save(item.type(), items);
    }

    public void remove(XtreamContentType type, int id) {
        List<XtreamMediaItem> items = list(type);
        List<XtreamMediaItem> kept = new ArrayList<>();
        for (XtreamMediaItem item : items) {
            if (item.id() != id) {
                kept.add(item);
            }
        }
        save(type, kept);
    }

    /** Overwrites the entire favorites list for a type (used by settings backup/restore). */
    public void replaceAll(XtreamContentType type, List<XtreamMediaItem> items) {
        save(type, items);
    }

    /** All favorite ids of a type in one parse -- for badging long grids without a lookup per cell. */
    public java.util.Set<Integer> idSet(XtreamContentType type) {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (XtreamMediaItem item : list(type)) {
            ids.add(item.id());
        }
        return ids;
    }

    public List<XtreamMediaItem> list(XtreamContentType type) {
        List<XtreamMediaItem> items = new ArrayList<>();
        String raw = preferences.getString(key(type), "[]");
        try {
            JSONArray array = new JSONArray(raw);
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

    private void save(XtreamContentType type, List<XtreamMediaItem> items) {
        JSONArray array = new JSONArray();
        for (XtreamMediaItem item : items) {
            try {
                JSONObject object = new JSONObject();
                object.put("title", item.title());
                object.put("id", item.id());
                object.put("categoryId", item.categoryId());
                object.put("imageUrl", item.imageUrl());
                object.put("detail", item.detail());
                object.put("ext", item.containerExtension());
                array.put(object);
            } catch (Exception ignored) {
                // Skip items that fail to serialize.
            }
        }
        preferences.edit().putString(key(type), array.toString()).apply();
    }
}
