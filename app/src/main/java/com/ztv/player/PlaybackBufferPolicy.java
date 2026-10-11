package com.ztv.player;

import com.ztv.player.data.XtreamContentType;

public final class PlaybackBufferPolicy {
    private static final BufferConfig LIVE = new BufferConfig(
            2000,
            8000,
            500,
            1000,
            0,
            true,
            false
    );

    private static final BufferConfig VOD = new BufferConfig(
            20000,
            60000,
            1000,
            2500,
            30000,
            true,
            true
    );

    private PlaybackBufferPolicy() {
    }

    public static BufferConfig configFor(String mediaTypeName) {
        if (XtreamContentType.LIVE.name().equals(mediaTypeName)) {
            return LIVE;
        }
        return VOD;
    }

    public static final class BufferConfig {
        private final int minBufferMs;
        private final int maxBufferMs;
        private final int bufferForPlaybackMs;
        private final int bufferForPlaybackAfterRebufferMs;
        private final int backBufferMs;
        private final boolean prioritizeTimeOverSizeThresholds;
        private final boolean retainBackBufferFromKeyframe;

        private BufferConfig(
                int minBufferMs,
                int maxBufferMs,
                int bufferForPlaybackMs,
                int bufferForPlaybackAfterRebufferMs,
                int backBufferMs,
                boolean prioritizeTimeOverSizeThresholds,
                boolean retainBackBufferFromKeyframe
        ) {
            this.minBufferMs = minBufferMs;
            this.maxBufferMs = maxBufferMs;
            this.bufferForPlaybackMs = bufferForPlaybackMs;
            this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
            this.backBufferMs = backBufferMs;
            this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds;
            this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe;
        }

        public int minBufferMs() {
            return minBufferMs;
        }

        public int maxBufferMs() {
            return maxBufferMs;
        }

        public int bufferForPlaybackMs() {
            return bufferForPlaybackMs;
        }

        public int bufferForPlaybackAfterRebufferMs() {
            return bufferForPlaybackAfterRebufferMs;
        }

        public int backBufferMs() {
            return backBufferMs;
        }

        public boolean prioritizeTimeOverSizeThresholds() {
            return prioritizeTimeOverSizeThresholds;
        }

        public boolean retainBackBufferFromKeyframe() {
            return retainBackBufferFromKeyframe;
        }
    }
}
