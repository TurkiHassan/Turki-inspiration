package com.ztv.player.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Remembers playback position per movie/episode for "Continue watching". */
public final class WatchProgressStore {
    private static final String PREFS_NAME = "ztv_progress";
    private static final String KEY = "progress";
    private static final int MAX_ENTRIES = 40;

    private final SharedPreferences preferences;

    public WatchProgressStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String id(XtreamContentType type, int itemId) {
        return type.name() + "_" + itemId;
    }

    public long positionMs(XtreamContentType type, int itemId) {
        EntrySnapshot snapshot = snapshot(type, itemId);
        if (snapshot == null || snapshot.completed()) {
            return 0L;
        }
        return snapshot.positionMs();
    }

    /** Upserts progress, moving the item to the front (most recent). */
    public void save(XtreamMediaItem item, long positionMs, long durationMs) {
        upsert(item, positionMs, durationMs, false);
    }

    /** Marks a title as finished without leaving it in "Continue watching". */
    public void markCompleted(XtreamMediaItem item, long durationMs) {
        upsert(item, durationMs, durationMs, true);
    }

    private void upsert(XtreamMediaItem item, long positionMs, long durationMs, boolean completed) {
        List<JSONObject> entries = entries();
        String key = id(item.type(), item.id());
        List<JSONObject> kept = new ArrayList<>();
        for (JSONObject entry : entries) {
            if (!key.equals(entry.optString("key"))) {
                kept.add(entry);
            }
        }
        try {
            JSONObject object = new JSONObject();
            object.put("key", key);
            object.put("type", item.type().name());
            object.put("title", item.title());
            object.put("id", item.id());
            object.put("categoryId", item.categoryId());
            object.put("imageUrl", item.imageUrl());
            object.put("detail", item.detail());
            object.put("ext", item.containerExtension());
            object.put("position", positionMs);
            object.put("duration", durationMs);
            object.put("completed", completed);
            object.put("updatedAt", System.currentTimeMillis());
            kept.add(0, object);
        } catch (Exception ignored) {
            return;
        }
        while (kept.size() > MAX_ENTRIES) {
            kept.remove(kept.size() - 1);
        }
        persist(kept);
    }

    public void remove(XtreamContentType type, int itemId) {
        String key = id(type, itemId);
        List<JSONObject> kept = new ArrayList<>();
        for (JSONObject entry : entries()) {
            if (!key.equals(entry.optString("key"))) {
                kept.add(entry);
            }
        }
        persist(kept);
    }

    /** Items of the given type that have saved progress, most recent first. */
    public List<XtreamMediaItem> list(XtreamContentType type) {
        List<XtreamMediaItem> items = new ArrayList<>();
        for (JSONObject entry : entries()) {
            EntrySnapshot snapshot = snapshotOf(entry);
            if (snapshot == null
                    || snapshot.item().type() != type
                    || snapshot.completed()
                    || snapshot.positionMs() <= 0L) {
                continue;
            }
            items.add(snapshot.item());
        }
        return items;
    }

    public EntrySnapshot snapshot(XtreamContentType type, int itemId) {
        String key = id(type, itemId);
        for (JSONObject entry : entries()) {
            if (key.equals(entry.optString("key"))) {
                EntrySnapshot snapshot = snapshotOf(entry);
                if (snapshot != null && snapshot.item().type() == type) {
                    return snapshot;
                }
                return null;
            }
        }
        return null;
    }

    public Map<Integer, EntrySnapshot> snapshotsFor(XtreamContentType type) {
        Map<Integer, EntrySnapshot> snapshots = new LinkedHashMap<>();
        for (JSONObject entry : entries()) {
            EntrySnapshot snapshot = snapshotOf(entry);
            if (snapshot != null && snapshot.item().type() == type) {
                snapshots.put(snapshot.item().id(), snapshot);
            }
        }
        return snapshots;
    }

    /** Overwrites all saved playback positions (used by settings backup/restore). */
    public void replaceAll(List<JSONObject> newEntries) {
        List<JSONObject> capped = newEntries.size() > MAX_ENTRIES
                ? new ArrayList<>(newEntries.subList(0, MAX_ENTRIES))
                : newEntries;
        persist(capped);
    }

    /** Raw stored entries (key/type/title/.../position/duration) -- used by settings backup/restore. */
    public List<JSONObject> entries() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY, "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object != null) {
                    list.add(object);
                }
            }
        } catch (Exception ignored) {
            // Corrupt store: treat as empty.
        }
        return list;
    }

    private void persist(List<JSONObject> entries) {
        JSONArray array = new JSONArray();
        for (JSONObject entry : entries) {
            array.put(entry);
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }

    private static EntrySnapshot snapshotOf(JSONObject entry) {
        XtreamContentType type;
        try {
            type = XtreamContentType.valueOf(entry.optString("type"));
        } catch (Exception ignored) {
            return null;
        }
        XtreamMediaItem item = new XtreamMediaItem(
                type,
                entry.optString("title", "Untitled"),
                entry.optInt("id", -1),
                entry.optString("categoryId", ""),
                entry.optString("imageUrl", ""),
                entry.optString("detail", ""),
                entry.optString("ext", "mp4")
        );
        return new EntrySnapshot(
                item,
                entry.optLong("position", 0L),
                entry.optLong("duration", 0L),
                entry.optBoolean("completed", false),
                entry.optLong("updatedAt", 0L)
        );
    }

    public static final class EntrySnapshot {
        private final XtreamMediaItem item;
        private final long positionMs;
        private final long durationMs;
        private final boolean completed;
        private final long updatedAtMs;

        public EntrySnapshot(XtreamMediaItem item, long positionMs, long durationMs,
                             boolean completed, long updatedAtMs) {
            this.item = item;
            this.positionMs = Math.max(positionMs, 0L);
            this.durationMs = Math.max(durationMs, 0L);
            this.completed = completed;
            this.updatedAtMs = Math.max(updatedAtMs, 0L);
        }

        public XtreamMediaItem item() {
            return item;
        }

        public long positionMs() {
            return positionMs;
        }

        public long durationMs() {
            return durationMs;
        }

        public boolean completed() {
            return completed;
        }

        public long updatedAtMs() {
            return updatedAtMs;
        }

        public int progressPercent() {
            if (durationMs <= 0L) {
                return completed ? 100 : 0;
            }
            long bounded = Math.min(durationMs, positionMs);
            return (int) Math.max(0L, Math.min(100L, (bounded * 100L) / durationMs));
        }

        public boolean inProgress() {
            return !completed && positionMs > 0L;
        }
    }
}
