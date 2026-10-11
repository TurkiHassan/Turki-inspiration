package com.ztv.player.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Typed access to app preferences that don't need encryption (content/UI toggles). */
public final class SettingsStore {
    private static final String PREFS_NAME = "ztv_settings";
    private static final String KEY_RECENT_COUNT = "recent_items_count";
    private static final String KEY_DIM_UPCOMING = "dim_upcoming_program";
    private static final String KEY_STARTUP_HOME = "startup_home";
    private static final String KEY_VIDEO_PREVIEWS = "video_previews_enabled";
    private static final String KEY_AUTO_PLAY_NEXT = "auto_play_next";
    private static final String KEY_HIGH_CONTRAST_FOCUS = "high_contrast_focus";
    private static final String KEY_PLAYBACK_QUALITY = "playback_quality";
    private static final String KEY_ASPECT_RATIO = "aspect_ratio";
    private static final String KEY_HIDDEN_SECTIONS_PREFIX = "hidden_sections_";
    private static final String KEY_DETAIL_ORDER = "detail_button_order";
    private static final String KEY_DETAIL_HIDDEN = "detail_hidden_buttons";

    public static final int DEFAULT_RECENT_COUNT = 15;
    public static final String QUALITY_AUTO = "Auto";
    public static final String ASPECT_FIT = "Fit";
    public static final String BUTTON_PLAY = "PLAY";
    public static final String BUTTON_FAVORITE = "FAVORITE";
    public static final String BUTTON_EXTERNAL = "EXTERNAL";
    private static final List<String> DEFAULT_DETAIL_ORDER =
            Arrays.asList(BUTTON_PLAY, BUTTON_FAVORITE, BUTTON_EXTERNAL);

    private final SharedPreferences preferences;

    public SettingsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int recentChannelsCount() {
        return preferences.getInt(KEY_RECENT_COUNT, DEFAULT_RECENT_COUNT);
    }

    public void setRecentChannelsCount(int count) {
        preferences.edit().putInt(KEY_RECENT_COUNT, count).apply();
    }

    public boolean dimUpcomingProgram() {
        return preferences.getBoolean(KEY_DIM_UPCOMING, true);
    }

    public void setDimUpcomingProgram(boolean enabled) {
        preferences.edit().putBoolean(KEY_DIM_UPCOMING, enabled).apply();
    }

    public boolean startupHome() {
        return preferences.getBoolean(KEY_STARTUP_HOME, true);
    }

    public void setStartupHome(boolean enabled) {
        preferences.edit().putBoolean(KEY_STARTUP_HOME, enabled).apply();
    }

    public boolean videoPreviewsEnabled() {
        return preferences.getBoolean(KEY_VIDEO_PREVIEWS, true);
    }

    public void setVideoPreviewsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_VIDEO_PREVIEWS, enabled).apply();
    }

    public boolean autoPlayNextEpisode() {
        return preferences.getBoolean(KEY_AUTO_PLAY_NEXT, true);
    }

    public void setAutoPlayNextEpisode(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply();
    }

    public boolean highContrastFocus() {
        return preferences.getBoolean(KEY_HIGH_CONTRAST_FOCUS, true);
    }

    public void setHighContrastFocus(boolean enabled) {
        preferences.edit().putBoolean(KEY_HIGH_CONTRAST_FOCUS, enabled).apply();
    }

    public String playbackQuality() {
        return preferences.getString(KEY_PLAYBACK_QUALITY, QUALITY_AUTO);
    }

    public void setPlaybackQuality(String quality) {
        preferences.edit().putString(KEY_PLAYBACK_QUALITY,
                quality == null || quality.trim().isEmpty() ? QUALITY_AUTO : quality.trim()).apply();
    }

    public String aspectRatio() {
        return preferences.getString(KEY_ASPECT_RATIO, ASPECT_FIT);
    }

    public void setAspectRatio(String aspectRatio) {
        preferences.edit().putString(KEY_ASPECT_RATIO,
                aspectRatio == null || aspectRatio.trim().isEmpty() ? ASPECT_FIT : aspectRatio.trim()).apply();
    }

    public boolean isSectionHidden(XtreamContentType type, String sectionTitle) {
        return hiddenSections(type).contains(sectionTitle);
    }

    public void setSectionHidden(XtreamContentType type, String sectionTitle, boolean hidden) {
        Set<String> current = hiddenSections(type);
        if (hidden) {
            current.add(sectionTitle);
        } else {
            current.remove(sectionTitle);
        }
        setHiddenSections(type, current);
    }

    public Set<String> hiddenSections(XtreamContentType type) {
        return readStringSet(KEY_HIDDEN_SECTIONS_PREFIX + type.name());
    }

    public void setHiddenSections(XtreamContentType type, Set<String> sections) {
        writeStringSet(KEY_HIDDEN_SECTIONS_PREFIX + type.name(), sections);
    }

    public List<String> detailButtonOrder() {
        List<String> order = readStringList(KEY_DETAIL_ORDER);
        return order.isEmpty() ? new ArrayList<>(DEFAULT_DETAIL_ORDER) : order;
    }

    public void setDetailButtonOrder(List<String> order) {
        writeStringList(KEY_DETAIL_ORDER, order);
    }

    public Set<String> hiddenDetailButtons() {
        return readStringSet(KEY_DETAIL_HIDDEN);
    }

    /** PLAY can never be hidden -- it is the only way to start playback from a detail page. */
    public void setDetailButtonHidden(String buttonKey, boolean hidden) {
        if (BUTTON_PLAY.equals(buttonKey)) {
            return;
        }
        Set<String> current = hiddenDetailButtons();
        if (hidden) {
            current.add(buttonKey);
        } else {
            current.remove(buttonKey);
        }
        setHiddenDetailButtons(current);
    }

    public void setHiddenDetailButtons(Set<String> hidden) {
        Set<String> safe = new LinkedHashSet<>(hidden);
        safe.remove(BUTTON_PLAY);
        writeStringSet(KEY_DETAIL_HIDDEN, safe);
    }

    private Set<String> readStringSet(String key) {
        return new LinkedHashSet<>(readStringList(key));
    }

    private void writeStringSet(String key, Set<String> values) {
        writeStringList(key, new ArrayList<>(values));
    }

    private List<String> readStringList(String key) {
        List<String> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(key, "[]"));
            for (int index = 0; index < array.length(); index++) {
                list.add(array.getString(index));
            }
        } catch (Exception ignored) {
            // Corrupt store: treat as empty.
        }
        return list;
    }

    private void writeStringList(String key, List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        preferences.edit().putString(key, array.toString()).apply();
    }
}
