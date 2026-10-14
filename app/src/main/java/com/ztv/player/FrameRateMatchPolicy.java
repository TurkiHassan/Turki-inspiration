package com.ztv.player;

import java.util.List;

/**
 * Picks the display refresh rate that matches a video's frame rate (AFR), so 25/50 fps sports
 * plays on a 50 Hz screen and 23.976 fps films on a 23.976/47.95 Hz one instead of judder at 60 Hz.
 *
 * <p>Pure logic: the Android display glue lives in {@link DisplayModeSwitcher}.
 */
public final class FrameRateMatchPolicy {
    public static final int NO_MATCH = -1;

    /** Refresh rates closer than this to a whole multiple of the frame rate count as a match. */
    private static final float MATCH_TOLERANCE_HZ = 0.02f;
    /** Prefer refresh rates at or above this to avoid visible flicker at 23.976/24/25 Hz. */
    private static final float MIN_COMFORTABLE_REFRESH_HZ = 47.5f;
    private static final float[] STANDARD_RATES =
            {23.976f, 24f, 25f, 29.97f, 30f, 47.952f, 48f, 50f, 59.94f, 60f};
    /** A measured rate snaps to a standard one when within this fraction of it. */
    private static final float SNAP_TOLERANCE = 0.015f;

    private FrameRateMatchPolicy() {
    }

    public static final class DisplayMode {
        final int id;
        final int width;
        final int height;
        final float refreshRate;

        public DisplayMode(int id, int width, int height, float refreshRate) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.refreshRate = refreshRate;
        }
    }

    /**
     * Returns the id of the mode to switch to, keeping the current resolution, or {@link #NO_MATCH}
     * when the frame rate is unknown or no mode at that resolution matches it.
     */
    public static int pickModeId(float frameRate, List<DisplayMode> modes, DisplayMode current) {
        if (!(frameRate > 0f) || modes == null || current == null) {
            return NO_MATCH;
        }
        DisplayMode bestComfortable = null;
        DisplayMode bestLow = null;
        for (DisplayMode mode : modes) {
            if (mode.width != current.width || mode.height != current.height
                    || !isMultipleOf(mode.refreshRate, frameRate)) {
                continue;
            }
            if (mode.refreshRate >= MIN_COMFORTABLE_REFRESH_HZ) {
                if (bestComfortable == null || mode.refreshRate < bestComfortable.refreshRate) {
                    bestComfortable = mode;
                }
            } else if (bestLow == null || mode.refreshRate > bestLow.refreshRate) {
                bestLow = mode;
            }
        }
        DisplayMode best = bestComfortable != null ? bestComfortable : bestLow;
        return best == null ? NO_MATCH : best.id;
    }

    /** Snaps a measured frame rate to the nearest broadcast standard, or returns it unchanged. */
    public static float snapToStandard(float measured) {
        if (!(measured > 0f)) {
            return measured;
        }
        float best = measured;
        float bestError = Float.MAX_VALUE;
        for (float standard : STANDARD_RATES) {
            float error = Math.abs(measured - standard) / standard;
            if (error <= SNAP_TOLERANCE && error < bestError) {
                best = standard;
                bestError = error;
            }
        }
        return best;
    }

    static boolean isMultipleOf(float refreshRate, float frameRate) {
        int multiple = Math.round(refreshRate / frameRate);
        return multiple >= 1 && Math.abs(refreshRate - multiple * frameRate) <= MATCH_TOLERANCE_HZ;
    }
}
