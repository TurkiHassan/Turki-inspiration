package com.ztv.player.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.List;

/**
 * The Xtream server the app talks to. A viewer sets it on the sign-in screen; once set, it is the
 * ONLY host used (no automatic fallback). Until then the built-in {@link AppConfig} list applies,
 * with failover between its entries.
 */
public final class ServerConfigStore implements HostProvider {
    private static final String PREFS_NAME = "ztv_server";
    private static final String KEY_HOST_OVERRIDE = "host_override";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_FAILED_COUNT = "pin_failed_count";
    private static final String KEY_PIN_LAST_FAILED_AT = "pin_last_failed_at";
    private static final String KEY_ACTIVE_HOST = "active_owner_host";
    private static final String KEY_USER_HOST = "user_host";

    private final SharedPreferences preferences;

    public ServerConfigStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        removeLegacyUserControls();
    }

    public String effectiveHost() {
        List<String> hosts = orderedHosts();
        return hosts.isEmpty() ? "" : hosts.get(0);
    }

    @Override
    public List<String> orderedHosts() {
        String userHost = userHost();
        if (!userHost.isEmpty()) {
            return Collections.singletonList(userHost);
        }
        return HostFailoverPolicy.orderedHosts(
                AppConfig.xtreamHosts(),
                preferences.getString(KEY_ACTIVE_HOST, ""));
    }

    /** The viewer's own server, or "" while the built-in list is in use. */
    public String userHost() {
        return ServerAddressPolicy.normalize(preferences.getString(KEY_USER_HOST, ""));
    }

    public boolean hasUserHost() {
        return !userHost().isEmpty();
    }

    /** Saves the viewer's server (normalized); returns true when it differs from the previous host. */
    public boolean setUserHost(String host) {
        String normalized = ServerAddressPolicy.normalize(host);
        if (normalized.isEmpty()) {
            return false;
        }
        boolean changed = !normalized.equals(effectiveHost());
        preferences.edit().putString(KEY_USER_HOST, normalized).apply();
        return changed;
    }

    @Override
    public void markHealthy(String host) {
        if (hasUserHost()) {
            return; // a single viewer-chosen host: nothing to rank
        }
        String normalized = XtreamUrlFactory.normalizeHost(host);
        if (!HostFailoverPolicy.orderedHosts(AppConfig.xtreamHosts(), normalized).contains(normalized)) {
            return;
        }
        preferences.edit().putString(KEY_ACTIVE_HOST, normalized).apply();
    }

    public int hostCount() {
        if (hasUserHost()) {
            return 1;
        }
        return HostFailoverPolicy.orderedHosts(AppConfig.xtreamHosts(), "").size();
    }

    public boolean hasBackupHosts() {
        return hostCount() > 1;
    }

    public int activeHostPosition() {
        if (hasUserHost()) {
            return 1;
        }
        String active = effectiveHost();
        List<String> configured = HostFailoverPolicy.orderedHosts(AppConfig.xtreamHosts(), "");
        int index = configured.indexOf(active);
        return index < 0 ? 0 : index + 1;
    }

    private void removeLegacyUserControls() {
        if (preferences.contains(KEY_HOST_OVERRIDE)
                || preferences.contains(KEY_PIN_HASH)
                || preferences.contains(KEY_PIN_FAILED_COUNT)
                || preferences.contains(KEY_PIN_LAST_FAILED_AT)) {
            preferences.edit()
                    .remove(KEY_HOST_OVERRIDE)
                    .remove(KEY_PIN_HASH)
                    .remove(KEY_PIN_FAILED_COUNT)
                    .remove(KEY_PIN_LAST_FAILED_AT)
                    .apply();
        }
    }
}
