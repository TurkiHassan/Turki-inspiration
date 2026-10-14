package com.ztv.player;

/**
 * Keeps live channels close to the live edge after a stall.
 *
 * <p>Xtream live channels are continuous MPEG-TS streams with no live window, so ExoPlayer cannot
 * tell how far behind live it is. The video buffered ahead of the playhead is the delay a stall
 * left behind: the provider kept sending while playback waited. Playing slightly faster while that
 * buffer is large drains it, bringing the viewer back near live without a visible jump.
 */
public final class LiveEdgePolicy {
    public static final float NORMAL_SPEED = 1.0f;
    /** Fast enough to drain a few seconds in about a minute, slow enough to go unnoticed. */
    public static final float CATCH_UP_SPEED = 1.05f;
    /** Default stop threshold, matching the normal live buffer. */
    static final long CATCH_UP_STOP_MS = 2000;
    /** Start catching up once the buffer is this far above the stop threshold. */
    static final long CATCH_UP_START_MARGIN_MS = 2000;
    /** Headroom kept above the rebuffer target so catching up never causes a stall. */
    private static final long STOP_HEADROOM_MS = 1000;

    private LiveEdgePolicy() {
    }

    /**
     * The buffer level to drain down to for a live buffer config. A larger buffer setting keeps a
     * larger cushion, otherwise catching up would undo the extra stability the user asked for.
     */
    public static long stopThresholdMs(PlaybackBufferPolicy.BufferConfig config) {
        return config.bufferForPlaybackAfterRebufferMs() + STOP_HEADROOM_MS;
    }

    /** Returns the playback speed to use given the current buffer ahead and speed. */
    public static float nextSpeed(boolean playing, long bufferedAheadMs, float currentSpeed) {
        return nextSpeed(playing, bufferedAheadMs, currentSpeed, CATCH_UP_STOP_MS);
    }

    public static float nextSpeed(boolean playing, long bufferedAheadMs, float currentSpeed, long stopMs) {
        if (!playing) {
            return NORMAL_SPEED;
        }
        boolean catchingUp = currentSpeed > NORMAL_SPEED;
        if (bufferedAheadMs > stopMs + CATCH_UP_START_MARGIN_MS) {
            return CATCH_UP_SPEED;
        }
        if (catchingUp && bufferedAheadMs > stopMs) {
            return CATCH_UP_SPEED;
        }
        return NORMAL_SPEED;
    }
}
