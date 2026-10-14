package com.ztv.player;

import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;

/** Applies the user's audio delay by offsetting the audio clock that video syncs to. */
final class AudioDelaySink extends ForwardingAudioSink {
    private final long delayMs;

    AudioDelaySink(AudioSink sink, long delayMs) {
        super(sink);
        this.delayMs = delayMs;
    }

    @Override
    public long getCurrentPositionUs(boolean sourceEnded) {
        return AudioDelayPolicy.shiftPositionUs(super.getCurrentPositionUs(sourceEnded), delayMs);
    }
}
