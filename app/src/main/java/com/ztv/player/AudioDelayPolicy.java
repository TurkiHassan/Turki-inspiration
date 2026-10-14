package com.ztv.player;

/**
 * Audio delay options for streams whose sound is out of sync with the picture. A positive delay
 * plays audio later; a negative one plays it earlier.
 */
public final class AudioDelayPolicy {
    public static final String[] OPTIONS = {"-300", "-200", "-100", "0", "+100", "+200", "+300"};
    private static final int MAX_DELAY_MS = 300;
    /** Matches {@code AudioSink.CURRENT_POSITION_NOT_SET}. */
    static final long POSITION_NOT_SET = Long.MIN_VALUE;

    private AudioDelayPolicy() {
    }

    public static String label(int delayMs) {
        return delayMs > 0 ? "+" + delayMs : String.valueOf(delayMs);
    }

    /** Parses an option label, clamping to the supported range; unparseable input is 0. */
    public static int parse(String label) {
        if (label == null) {
            return 0;
        }
        try {
            int value = Integer.parseInt(label.trim().replace("+", ""));
            return Math.max(-MAX_DELAY_MS, Math.min(MAX_DELAY_MS, value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * The audio clock drives video, so reporting the audio position {@code delay} ahead makes each
     * frame show that much earlier relative to its sound, i.e. the audio is heard later.
     */
    public static long shiftPositionUs(long positionUs, long delayMs) {
        if (positionUs == POSITION_NOT_SET || delayMs == 0) {
            return positionUs;
        }
        return positionUs + delayMs * 1000L;
    }
}
