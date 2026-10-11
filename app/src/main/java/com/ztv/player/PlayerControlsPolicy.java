package com.ztv.player;

import com.ztv.player.data.XtreamContentType;

public final class PlayerControlsPolicy {
    private static final int DEFAULT_TIMEOUT_MS = 3500;
    private static final int ENHANCED_TIMEOUT_MS = 5000;
    private static final int VOD_SEEK_INCREMENT_MS = 15000;

    private PlayerControlsPolicy() {
    }

    public static boolean useEnhancedControls(String mediaTypeName) {
        return XtreamContentType.MOVIE.name().equals(mediaTypeName)
                || XtreamContentType.EPISODE.name().equals(mediaTypeName);
    }

    public static int controllerShowTimeoutMs(String mediaTypeName) {
        return useEnhancedControls(mediaTypeName) ? ENHANCED_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
    }

    public static int seekBackIncrementMs() {
        return VOD_SEEK_INCREMENT_MS;
    }

    public static int seekForwardIncrementMs() {
        return VOD_SEEK_INCREMENT_MS;
    }

    public static boolean showNextEpisodeButton(String mediaTypeName, boolean hasKnownNextEpisode) {
        return XtreamContentType.EPISODE.name().equals(mediaTypeName) && hasKnownNextEpisode;
    }

    public static boolean shouldOpenLibraryOnBack(String mediaTypeName, boolean isTaskRoot) {
        if (!isTaskRoot || mediaTypeName == null) {
            return false;
        }
        try {
            XtreamContentType.valueOf(mediaTypeName);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
