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
    private static final String KEY_MATCH_FRAME_RATE = "match_frame_rate";
    private static final String KEY_STAY_NEAR_LIVE = "stay_near_live";
    private static final String KEY_TUNNELED_PLAYBACK = "tunneled_playback";
    private static final String KEY_LIVE_BUFFER = "live_buffer";
    private static final String KEY_AUDIO_DECODER = "audio_decoder";
    private static final String KEY_AUDIO_DELAY_MS = "audio_delay_ms";
    private static final String KEY_VOICE_LANGUAGE = "voice_language";
    private static final String KEY_HIDDEN_SECTIONS_PREFIX = "hidden_sections_";
    private static final String KEY_DETAIL_ORDER = "detail_button_order";
    private static final String KEY_DETAIL_HIDDEN = "detail_hidden_buttons";

    public static final int DEFAULT_RECENT_COUNT = 15;
    public static final String QUALITY_AUTO = "Auto";
    public static final String ASPECT_FIT = "Fit";
    public static final String LIVE_BUFFER_NORMAL = "Normal";
    public static final String LIVE_BUFFER_LARGE = "Large";
    public static final String AUDIO_DECODER_DEVICE = "Device";
    public static final String AUDIO_DECODER_FFMPEG = "FFmpeg";
    public static final String VOICE_DEVICE = "device";
    public static final String VOICE_ARABIC = "ar-SA";
    public static final String VOICE_ENGLISH = "en-US";
    public static final String VOICE_BILINGUAL = "ar-SA+en-US";
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

    /** Off by default: switching the display mode blanks some TVs for a second or two. */
    public boolean matchFrameRate() {
        return preferences.getBoolean(KEY_MATCH_FRAME_RATE, false);
    }

    public void setMatchFrameRate(boolean enabled) {
        preferences.edit().putBoolean(KEY_MATCH_FRAME_RATE, enabled).apply();
    }

    public boolean stayNearLive() {
        return preferences.getBoolean(KEY_STAY_NEAR_LIVE, true);
    }

    public void setStayNearLive(boolean enabled) {
        preferences.edit().putBoolean(KEY_STAY_NEAR_LIVE, enabled).apply();
    }

    /** Off by default: some TV boxes show a black screen or lose audio with tunneling. */
    public boolean tunneledPlayback() {
        return preferences.getBoolean(KEY_TUNNELED_PLAYBACK, false);
    }

    public void setTunneledPlayback(boolean enabled) {
        preferences.edit().putBoolean(KEY_TUNNELED_PLAYBACK, enabled).apply();
    }

    public String liveBuffer() {
        return LIVE_BUFFER_LARGE.equals(preferences.getString(KEY_LIVE_BUFFER, LIVE_BUFFER_NORMAL))
                ? LIVE_BUFFER_LARGE : LIVE_BUFFER_NORMAL;
    }

    public void setLiveBuffer(String size) {
        preferences.edit().putString(KEY_LIVE_BUFFER,
                LIVE_BUFFER_LARGE.equals(size) ? LIVE_BUFFER_LARGE : LIVE_BUFFER_NORMAL).apply();
    }

    /** Arabic and English together (the default), one of them, or the TV's own language. */
    public String voiceLanguage() {
        return knownVoiceLanguage(preferences.getString(KEY_VOICE_LANGUAGE, VOICE_BILINGUAL));
    }

    public void setVoiceLanguage(String language) {
        preferences.edit().putString(KEY_VOICE_LANGUAGE, knownVoiceLanguage(language)).apply();
    }

    private static String knownVoiceLanguage(String value) {
        return VOICE_DEVICE.equals(value) || VOICE_ARABIC.equals(value) || VOICE_ENGLISH.equals(value)
                ? value : VOICE_BILINGUAL;
    }

    public String audioDecoder() {
        return AUDIO_DECODER_FFMPEG.equals(preferences.getString(KEY_AUDIO_DECODER, AUDIO_DECODER_DEVICE))
                ? AUDIO_DECODER_FFMPEG : AUDIO_DECODER_DEVICE;
    }

    public void setAudioDecoder(String decoder) {
        preferences.edit().putString(KEY_AUDIO_DECODER,
                AUDIO_DECODER_FFMPEG.equals(decoder) ? AUDIO_DECODER_FFMPEG : AUDIO_DECODER_DEVICE).apply();
    }

    public int audioDelayMs() {
        return preferences.getInt(KEY_AUDIO_DELAY_MS, 0);
    }

    public void setAudioDelayMs(int delayMs) {
        preferences.edit().putInt(KEY_AUDIO_DELAY_MS, delayMs).apply();
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
