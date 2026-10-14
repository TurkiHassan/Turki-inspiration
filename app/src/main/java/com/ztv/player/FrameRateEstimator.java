package com.ztv.player;

/**
 * Measures a stream's frame rate from rendered frame timestamps. Live MPEG-TS streams usually
 * do not declare a frame rate, so the player has to work it out from the frames themselves.
 */
public final class FrameRateEstimator {
    /** Frames to collect before deciding; about one second of video at 50 fps. */
    static final int SAMPLE_FRAMES = 48;
    /** A gap this far from the first one means a jump (seek, dropped frames); start over. */
    private static final float MAX_DEVIATION = 0.25f;

    private long firstTimeUs = Long.MIN_VALUE;
    private long lastTimeUs = Long.MIN_VALUE;
    private long referenceDeltaUs;
    private int frames;
    private float result = -1f;

    /** Feeds one frame; returns the snapped frame rate once known, otherwise -1. */
    public float onFrame(long presentationTimeUs) {
        if (result > 0f) {
            return result;
        }
        if (lastTimeUs == Long.MIN_VALUE) {
            start(presentationTimeUs);
            return -1f;
        }
        long delta = presentationTimeUs - lastTimeUs;
        if (delta <= 0) {
            start(presentationTimeUs);
            return -1f;
        }
        if (referenceDeltaUs == 0) {
            referenceDeltaUs = delta;
        } else if (Math.abs(delta - referenceDeltaUs) > referenceDeltaUs * MAX_DEVIATION) {
            start(presentationTimeUs);
            return -1f;
        }
        lastTimeUs = presentationTimeUs;
        frames++;
        if (frames >= SAMPLE_FRAMES) {
            float averageDeltaUs = (float) (lastTimeUs - firstTimeUs) / frames;
            result = FrameRateMatchPolicy.snapToStandard(1_000_000f / averageDeltaUs);
        }
        return result;
    }

    public void reset() {
        lastTimeUs = Long.MIN_VALUE;
        result = -1f;
        referenceDeltaUs = 0;
        frames = 0;
    }

    private void start(long presentationTimeUs) {
        firstTimeUs = presentationTimeUs;
        lastTimeUs = presentationTimeUs;
        referenceDeltaUs = 0;
        frames = 0;
    }
}
