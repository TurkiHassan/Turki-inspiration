package com.ztv.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FrameRateEstimatorTest {
    @Test
    public void measures25fps() {
        assertEquals(25f, feed(new FrameRateEstimator(), 0, 40_000, 200), 0.0001f);
    }

    @Test
    public void measures5994fpsFrom90kHzTimestamps() {
        FrameRateEstimator estimator = new FrameRateEstimator();
        float result = -1f;
        for (int frame = 0; frame < 200 && result < 0f; frame++) {
            long ticks = Math.round(frame * 90_000.0 / 59.94);
            result = estimator.onFrame(ticks * 1_000_000L / 90_000L);
        }
        assertEquals(59.94f, result, 0.0001f);
    }

    @Test
    public void restartsAfterTimestampJump() {
        FrameRateEstimator estimator = new FrameRateEstimator();
        feed(estimator, 0, 40_000, 10);
        assertEquals(-1f, estimator.onFrame(10_000_000L), 0f);
        assertEquals(50f, feed(estimator, 10_020_000L, 20_000, FrameRateEstimator.SAMPLE_FRAMES), 0.0001f);
    }

    @Test
    public void notReadyBeforeEnoughFrames() {
        assertEquals(-1f, feed(new FrameRateEstimator(), 0, 40_000, 5), 0f);
    }

    private static float feed(FrameRateEstimator estimator, long startUs, long stepUs, int frames) {
        float result = -1f;
        for (int frame = 0; frame < frames; frame++) {
            result = estimator.onFrame(startUs + frame * stepUs);
        }
        return result;
    }
}
