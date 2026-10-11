package com.ztv.player.data;

/** Subset of the Xtream `user_info` payload the app cares about. */
public final class XtreamAccountInfo {
    private final boolean authenticated;
    private final String status;
    private final long expiryEpochSeconds;

    public XtreamAccountInfo(boolean authenticated, String status, long expiryEpochSeconds) {
        this.authenticated = authenticated;
        this.status = status == null ? "" : status;
        this.expiryEpochSeconds = expiryEpochSeconds;
    }

    public boolean authenticated() {
        return authenticated;
    }

    public String status() {
        return status;
    }

    /** Unix seconds of subscription expiry, or 0 when the line never expires / is unknown. */
    public long expiryEpochSeconds() {
        return expiryEpochSeconds;
    }

    public boolean hasExpiry() {
        return expiryEpochSeconds > 0L;
    }
}
