package com.ztv.player.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * Kids mode state on this device: the parent PIN (salted SHA-256, with a lockout after
 * repeated mistakes), the allow-list and limits, and today's watch time.
 */
public final class KidsStore {
    public static final int MIN_PIN_LENGTH = 4;
    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCKOUT_MS = 60_000L;

    private static final String PREFS_NAME = "ztv_kids";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_FAILED = "pin_failed";
    private static final String KEY_LOCKED_UNTIL = "pin_locked_until";
    private static final String KEY_CATEGORIES = "categories";
    private static final String KEY_ALLOWED = "allowed_items";
    private static final String KEY_BLOCKED = "blocked_items";
    private static final String KEY_DAILY_MINUTES = "daily_minutes";
    private static final String KEY_BEDTIME = "bedtime";
    private static final String KEY_BEDTIME_START = "bedtime_start";
    private static final String KEY_BEDTIME_END = "bedtime_end";
    private static final String KEY_USAGE_DAY = "usage_day";
    private static final String KEY_USAGE_MS = "usage_ms";
    private static final String KEY_BONUS_DAY = "bonus_day";
    private static final String KEY_BONUS_MINUTES = "bonus_minutes";
    private static final String KEY_BEDTIME_PASS_DAY = "bedtime_pass_day";

    public static final int DEFAULT_BEDTIME_START = 21 * 60;
    public static final int DEFAULT_BEDTIME_END = 7 * 60;

    private final SharedPreferences preferences;
    private KidsPolicy policy;

    public KidsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Mode ───────────────────────────────────────────────────────────────

    public boolean isActive() {
        return hasPin() && preferences.getBoolean(KEY_ACTIVE, false);
    }

    public void setActive(boolean active) {
        preferences.edit().putBoolean(KEY_ACTIVE, active && hasPin()).apply();
    }

    public boolean autoStart() {
        return preferences.getBoolean(KEY_AUTO_START, false);
    }

    public void setAutoStart(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_START, enabled).apply();
    }

    // ─── PIN ────────────────────────────────────────────────────────────────

    public boolean hasPin() {
        return preferences.contains(KEY_PIN_HASH);
    }

    public void setPin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltText = Base64.encodeToString(salt, Base64.NO_WRAP);
        preferences.edit()
                .putString(KEY_PIN_SALT, saltText)
                .putString(KEY_PIN_HASH, hash(saltText, pin))
                .putInt(KEY_FAILED, 0)
                .putLong(KEY_LOCKED_UNTIL, 0)
                .apply();
    }

    /** Milliseconds until another attempt is accepted after too many wrong PINs. */
    public long lockoutRemainingMs(long now) {
        return Math.max(0, preferences.getLong(KEY_LOCKED_UNTIL, 0) - now);
    }

    /** True when {@code pin} matches; a wrong PIN counts toward the lockout. */
    public boolean checkPin(String pin, long now) {
        if (!hasPin() || lockoutRemainingMs(now) > 0) {
            return false;
        }
        String expected = preferences.getString(KEY_PIN_HASH, "");
        boolean ok = MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                hash(preferences.getString(KEY_PIN_SALT, ""), pin).getBytes(StandardCharsets.UTF_8));
        if (ok) {
            preferences.edit().putInt(KEY_FAILED, 0).apply();
            return true;
        }
        int failed = preferences.getInt(KEY_FAILED, 0) + 1;
        SharedPreferences.Editor editor = preferences.edit().putInt(KEY_FAILED, failed);
        if (failed >= MAX_ATTEMPTS) {
            editor.putInt(KEY_FAILED, 0).putLong(KEY_LOCKED_UNTIL, now + LOCKOUT_MS);
        }
        editor.apply();
        return false;
    }

    static String hash(String salt, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ─── Allow-list and limits ──────────────────────────────────────────────

    public KidsPolicy policy() {
        if (policy == null) {
            policy = new KidsPolicy(set(KEY_CATEGORIES), set(KEY_ALLOWED), set(KEY_BLOCKED),
                    preferences.getInt(KEY_DAILY_MINUTES, KidsPolicy.UNLIMITED),
                    preferences.getBoolean(KEY_BEDTIME, false),
                    preferences.getInt(KEY_BEDTIME_START, DEFAULT_BEDTIME_START),
                    preferences.getInt(KEY_BEDTIME_END, DEFAULT_BEDTIME_END));
        }
        return policy;
    }

    /**
     * Shows or hides one item. Inside an allowed category hiding blocks it; outside one,
     * showing allows it on its own.
     */
    public void setItemVisible(String itemKey, boolean categoryAllowed, boolean visible) {
        Set<String> allowed = set(KEY_ALLOWED);
        Set<String> blocked = set(KEY_BLOCKED);
        allowed.remove(itemKey);
        blocked.remove(itemKey);
        if (categoryAllowed && !visible) {
            blocked.add(itemKey);
        } else if (!categoryAllowed && visible) {
            allowed.add(itemKey);
        }
        preferences.edit().putStringSet(KEY_ALLOWED, allowed).putStringSet(KEY_BLOCKED, blocked).apply();
        policy = null;
    }

    /** Allows or hides a whole category and drops the per-item exceptions inside it. */
    public void setCategory(String categoryKey, boolean allowed, Iterable<String> itemKeys) {
        Set<String> categories = set(KEY_CATEGORIES);
        Set<String> allowedItems = set(KEY_ALLOWED);
        Set<String> blocked = set(KEY_BLOCKED);
        if (allowed) {
            categories.add(categoryKey);
        } else {
            categories.remove(categoryKey);
        }
        for (String key : itemKeys) {
            allowedItems.remove(key);
            blocked.remove(key);
        }
        preferences.edit().putStringSet(KEY_CATEGORIES, categories)
                .putStringSet(KEY_ALLOWED, allowedItems).putStringSet(KEY_BLOCKED, blocked).apply();
        policy = null;
    }

    public void setDailyMinutes(int minutes) {
        preferences.edit().putInt(KEY_DAILY_MINUTES, Math.max(0, minutes)).apply();
        policy = null;
    }

    public void setBedtime(boolean enabled, int start, int end) {
        preferences.edit().putBoolean(KEY_BEDTIME, enabled)
                .putInt(KEY_BEDTIME_START, start).putInt(KEY_BEDTIME_END, end).apply();
        policy = null;
    }

    private Set<String> set(String key) {
        return new HashSet<>(preferences.getStringSet(key, new HashSet<>()));
    }

    // ─── Watch time ─────────────────────────────────────────────────────────

    public static int dayKey(Calendar now) {
        return now.get(Calendar.YEAR) * 10000 + (now.get(Calendar.MONTH) + 1) * 100 + now.get(Calendar.DAY_OF_MONTH);
    }

    public static int minuteOfDay(Calendar now) {
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    public long watchedTodayMs(int day) {
        return preferences.getInt(KEY_USAGE_DAY, 0) == day ? preferences.getLong(KEY_USAGE_MS, 0) : 0;
    }

    public void addWatched(int day, long ms) {
        if (ms <= 0) {
            return;
        }
        preferences.edit().putInt(KEY_USAGE_DAY, day).putLong(KEY_USAGE_MS, watchedTodayMs(day) + ms).apply();
    }

    /** Extra minutes a parent granted for today on the lock screen. */
    public int bonusMinutes(int day) {
        return preferences.getInt(KEY_BONUS_DAY, 0) == day ? preferences.getInt(KEY_BONUS_MINUTES, 0) : 0;
    }

    public void addBonusMinutes(int day, int minutes) {
        preferences.edit().putInt(KEY_BONUS_DAY, day).putInt(KEY_BONUS_MINUTES, bonusMinutes(day) + minutes).apply();
    }

    /** A parent let the child watch through tonight's bedtime once. */
    public void passBedtime(Calendar now) {
        preferences.edit().putInt(KEY_BEDTIME_PASS_DAY, policy().bedtimeNight(now)).apply();
    }

    /** Why a child can't watch right now, or null when watching is fine. */
    public KidsPolicy.Block block(Calendar now) {
        if (!isActive()) {
            return null;
        }
        KidsPolicy current = policy();
        int day = dayKey(now);
        return current.block(now, watchedTodayMs(day), bonusMinutes(day), preferences.getInt(KEY_BEDTIME_PASS_DAY, 0));
    }

    /** Milliseconds of watching left today, or {@link Long#MAX_VALUE} without a limit. */
    public long remainingMs(Calendar now) {
        if (!isActive()) {
            return Long.MAX_VALUE;
        }
        int day = dayKey(now);
        return policy().remainingMs(watchedTodayMs(day), bonusMinutes(day));
    }
}
