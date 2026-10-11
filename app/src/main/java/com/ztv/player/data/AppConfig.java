package com.ztv.player.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AppConfig {
    /**
     * Built-in Xtream servers in priority order: the sign-in screen's pre-filled default, with
     * failover between entries. Once a viewer signs in with their own server
     * ({@link ServerConfigStore#setUserHost}), only that server is used.
     */
    private static final List<String> XTREAM_HOSTS = Collections.unmodifiableList(Arrays.asList(
            // Intentionally empty in public builds: each viewer enters their own server on the
            // sign-in screen. Add a default host here for a private, pre-configured build.
    ));

    public static List<String> xtreamHosts() {
        return XTREAM_HOSTS;
    }

    private AppConfig() {
    }
}
