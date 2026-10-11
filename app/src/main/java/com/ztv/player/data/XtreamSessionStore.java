package com.ztv.player.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class XtreamSessionStore {
    private static final String TAG = "ZTV";
    private static final String PREFS_NAME = "ztv_session";
    private static final String SECURE_PREFS_NAME = "ztv_session_secure";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    private final SharedPreferences preferences;
    private final ServerConfigStore serverConfigStore;

    public XtreamSessionStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = createPreferences(appContext);
        serverConfigStore = new ServerConfigStore(appContext);
    }

    private static SharedPreferences createPreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception exception) {
            // Some devices (older TV boxes) have an unreliable keystore. Fall back to
            // plain preferences so the app stays usable instead of crashing on launch.
            Log.w(TAG, "Encrypted storage unavailable, falling back to plain preferences", exception);
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public XtreamCredentials load() {
        try {
            return new XtreamCredentials(
                    serverConfigStore.effectiveHost(),
                    preferences.getString(KEY_USERNAME, ""),
                    preferences.getString(KEY_PASSWORD, "")
            );
        } catch (Exception exception) {
            // A device keystore hiccup can make previously-saved encrypted values
            // undecryptable; treat that as "no saved session" instead of crashing,
            // and wipe the unreadable entries so this doesn't repeat every launch.
            Log.w(TAG, "Saved session unreadable, clearing it", exception);
            preferences.edit().clear().apply();
            return new XtreamCredentials(serverConfigStore.effectiveHost(), "", "");
        }
    }

    public void save(XtreamCredentials credentials) {
        preferences.edit()
                .putString(KEY_USERNAME, credentials.username())
                .putString(KEY_PASSWORD, credentials.password())
                .apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
