package com.ztv.player.data;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;

/** Finds the HTTP status behind a playback failure, when the provider answered at all. */
public final class HttpStatusResolver {
    private static final int MAX_CAUSE_DEPTH = 12;

    private HttpStatusResolver() {
    }

    /** @return the provider response code, or {@code 0} when the failure never reached the server. */
    public static int responseCode(PlaybackException error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) cause).responseCode;
            }
            cause = cause.getCause();
        }
        return 0;
    }
}
