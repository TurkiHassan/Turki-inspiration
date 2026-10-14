package com.ztv.player.data;

/**
 * Decides how a failed playback attempt should be handled.
 *
 * <p>A provider-side rejection — the panel answered 4xx for this exact stream — is not worth
 * retrying: the same URL keeps failing and a fast retry burst can even trip provider connection
 * limits. Transient failures (network, 408, 429, 5xx) may still be retried or failed over.
 */
public final class PlaybackFailurePolicy {

    private PlaybackFailurePolicy() {
    }

    /** True when the provider answered for this exact item and refused to serve it. */
    public static boolean isProviderRejection(int httpStatus) {
        if (httpStatus < 400 || httpStatus > 499) {
            return false;
        }
        return httpStatus != 408 && httpStatus != 429;
    }

    public static boolean shouldRetry(boolean providerRejection, int retryCount, int maxRetries) {
        return !providerRejection && retryCount < maxRetries;
    }

    public static boolean shouldFailOver(boolean providerRejection, int media3ErrorCode) {
        return !providerRejection && HostFailoverPolicy.shouldFailOverPlaybackError(media3ErrorCode);
    }
}
