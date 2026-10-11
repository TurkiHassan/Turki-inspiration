package com.ztv.player.ui;

/** Whether an Xtream "image" field is an actual picture URL (some carry JSON or nothing). */
public final class PosterUrls {
    private static final int MAX_URL_LENGTH = 1024;

    private PosterUrls() {
    }

    public static boolean isUsable(String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim();
        return trimmed.length() <= MAX_URL_LENGTH
                && (trimmed.startsWith("http://") || trimmed.startsWith("https://"));
    }
}
