package com.ztv.player.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HostFailoverPolicy {
    private static final int MEDIA3_IO_ERROR_START = 2000;
    private static final int MEDIA3_IO_ERROR_END = 3000;

    private HostFailoverPolicy() {
    }

    public static List<String> orderedHosts(List<String> configuredHosts, String preferredHost) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (configuredHosts != null) {
            for (String host : configuredHosts) {
                String value = XtreamUrlFactory.normalizeHost(host);
                if (!value.isEmpty()) {
                    normalized.add(value);
                }
            }
        }

        List<String> result = new ArrayList<>(normalized.size());
        String preferred = XtreamUrlFactory.normalizeHost(preferredHost);
        if (normalized.contains(preferred)) {
            result.add(preferred);
        }
        for (String host : normalized) {
            if (!host.equals(preferred)) {
                result.add(host);
            }
        }
        return result;
    }

    public static String nextUntriedHost(List<String> orderedHosts, Set<String> attemptedHosts) {
        Set<String> attempted = new LinkedHashSet<>();
        if (attemptedHosts != null) {
            for (String host : attemptedHosts) {
                attempted.add(XtreamUrlFactory.normalizeHost(host));
            }
        }
        if (orderedHosts != null) {
            for (String host : orderedHosts) {
                String normalized = XtreamUrlFactory.normalizeHost(host);
                if (!normalized.isEmpty() && !attempted.contains(normalized)) {
                    return normalized;
                }
            }
        }
        return "";
    }

    public static boolean shouldFailOverHttpStatus(int code) {
        return code == 408 || code == 429 || code >= 500;
    }

    public static boolean shouldFailOverPlaybackError(int media3ErrorCode) {
        return media3ErrorCode >= MEDIA3_IO_ERROR_START && media3ErrorCode < MEDIA3_IO_ERROR_END;
    }
}
