package com.ztv.player.data;

import android.content.Context;

import com.ztv.player.AudioDelayPolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Bundles favorites, history, watch progress, and settings into one JSON file for backup/restore. */
public final class BackupManager {
    private static final int BACKUP_VERSION = 1;

    private final FavoritesStore favoritesStore;
    private final HistoryStore historyStore;
    private final WatchProgressStore progressStore;
    private final SettingsStore settingsStore;

    public BackupManager(Context context) {
        favoritesStore = new FavoritesStore(context);
        historyStore = new HistoryStore(context);
        progressStore = new WatchProgressStore(context);
        settingsStore = new SettingsStore(context);
    }

    public String export() throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", BACKUP_VERSION);

        JSONObject favorites = new JSONObject();
        JSONObject history = new JSONObject();
        for (XtreamContentType type : XtreamContentType.values()) {
            favorites.put(type.name(), itemsToJson(favoritesStore.list(type)));
            history.put(type.name(), itemsToJson(historyStore.list(type)));
        }
        root.put("favorites", favorites);
        root.put("history", history);

        JSONArray progress = new JSONArray();
        for (JSONObject entry : progressStore.entries()) {
            progress.put(entry);
        }
        root.put("progress", progress);

        JSONObject settings = new JSONObject();
        settings.put("startupHome", settingsStore.startupHome());
        settings.put("videoPreviewsEnabled", settingsStore.videoPreviewsEnabled());
        settings.put("autoPlayNextEpisode", settingsStore.autoPlayNextEpisode());
        settings.put("highContrastFocus", settingsStore.highContrastFocus());
        settings.put("playbackQuality", settingsStore.playbackQuality());
        settings.put("aspectRatio", settingsStore.aspectRatio());
        settings.put("matchFrameRate", settingsStore.matchFrameRate());
        settings.put("stayNearLive", settingsStore.stayNearLive());
        settings.put("tunneledPlayback", settingsStore.tunneledPlayback());
        settings.put("liveBuffer", settingsStore.liveBuffer());
        settings.put("audioDecoder", settingsStore.audioDecoder());
        settings.put("audioDelayMs", settingsStore.audioDelayMs());
        settings.put("recentChannelsCount", settingsStore.recentChannelsCount());
        settings.put("dimUpcomingProgram", settingsStore.dimUpcomingProgram());
        settings.put("detailButtonOrder", new JSONArray(settingsStore.detailButtonOrder()));
        settings.put("hiddenDetailButtons", new JSONArray(settingsStore.hiddenDetailButtons()));
        JSONObject hiddenSections = new JSONObject();
        for (XtreamContentType type : XtreamContentType.values()) {
            hiddenSections.put(type.name(), new JSONArray(settingsStore.hiddenSections(type)));
        }
        settings.put("hiddenSections", hiddenSections);
        root.put("settings", settings);

        return root.toString(2);
    }

    /** Restores from a JSON bundle produced by {@link #export()}. Overwrites current data. */
    public void restoreFrom(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.optInt("version", -1) != BACKUP_VERSION) {
            throw new IllegalArgumentException("Unsupported backup version");
        }

        JSONObject favorites = root.optJSONObject("favorites");
        JSONObject history = root.optJSONObject("history");
        for (XtreamContentType type : XtreamContentType.values()) {
            if (favorites != null) {
                favoritesStore.replaceAll(type, itemsFromJson(type, favorites.optJSONArray(type.name())));
            }
            if (history != null) {
                historyStore.replaceAll(type, itemsFromJson(type, history.optJSONArray(type.name())));
            }
        }

        JSONArray progress = root.optJSONArray("progress");
        if (progress != null) {
            List<JSONObject> entries = new ArrayList<>();
            for (int index = 0; index < progress.length(); index++) {
                JSONObject entry = progress.optJSONObject(index);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            progressStore.replaceAll(entries);
        }

        JSONObject settings = root.optJSONObject("settings");
        if (settings != null) {
            settingsStore.setStartupHome(settings.optBoolean("startupHome", true));
            settingsStore.setVideoPreviewsEnabled(settings.optBoolean("videoPreviewsEnabled", false));
            settingsStore.setAutoPlayNextEpisode(settings.optBoolean("autoPlayNextEpisode", true));
            settingsStore.setHighContrastFocus(settings.optBoolean("highContrastFocus", true));
            settingsStore.setPlaybackQuality(settings.optString("playbackQuality", SettingsStore.QUALITY_AUTO));
            settingsStore.setAspectRatio(settings.optString("aspectRatio", SettingsStore.ASPECT_FIT));
            settingsStore.setMatchFrameRate(settings.optBoolean("matchFrameRate", false));
            settingsStore.setStayNearLive(settings.optBoolean("stayNearLive", true));
            settingsStore.setTunneledPlayback(settings.optBoolean("tunneledPlayback", false));
            settingsStore.setLiveBuffer(settings.optString("liveBuffer", SettingsStore.LIVE_BUFFER_NORMAL));
            settingsStore.setAudioDecoder(settings.optString("audioDecoder", SettingsStore.AUDIO_DECODER_DEVICE));
            settingsStore.setAudioDelayMs(AudioDelayPolicy.parse(String.valueOf(settings.optInt("audioDelayMs", 0))));
            settingsStore.setRecentChannelsCount(settings.optInt("recentChannelsCount", SettingsStore.DEFAULT_RECENT_COUNT));
            settingsStore.setDimUpcomingProgram(settings.optBoolean("dimUpcomingProgram", true));
            settingsStore.setDetailButtonOrder(toStringList(settings.optJSONArray("detailButtonOrder")));
            settingsStore.setHiddenDetailButtons(toStringSet(settings.optJSONArray("hiddenDetailButtons")));
            JSONObject hiddenSections = settings.optJSONObject("hiddenSections");
            if (hiddenSections != null) {
                for (XtreamContentType type : XtreamContentType.values()) {
                    settingsStore.setHiddenSections(type, toStringSet(hiddenSections.optJSONArray(type.name())));
                }
            }
        }
    }

    private static JSONArray itemsToJson(List<XtreamMediaItem> items) throws Exception {
        JSONArray array = new JSONArray();
        for (XtreamMediaItem item : items) {
            JSONObject object = new JSONObject();
            object.put("title", item.title());
            object.put("id", item.id());
            object.put("categoryId", item.categoryId());
            object.put("imageUrl", item.imageUrl());
            object.put("detail", item.detail());
            object.put("ext", item.containerExtension());
            array.put(object);
        }
        return array;
    }

    private static List<XtreamMediaItem> itemsFromJson(XtreamContentType type, JSONArray array) {
        List<XtreamMediaItem> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
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
        return items;
    }

    private static List<String> toStringList(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int index = 0; index < array.length(); index++) {
            String value = array.optString(index, null);
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    private static Set<String> toStringSet(JSONArray array) {
        return new LinkedHashSet<>(toStringList(array));
    }
}
