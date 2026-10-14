package com.ztv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ztv.player.data.XtreamContentType;

import org.junit.Test;

public class PlaybackBufferPolicyTest {
    @Test
    public void liveStreams_useShortStartupBuffers() {
        PlaybackBufferPolicy.BufferConfig config =
                PlaybackBufferPolicy.configFor(XtreamContentType.LIVE.name());

        assertEquals(2000, config.minBufferMs());
        assertEquals(8000, config.maxBufferMs());
        assertEquals(500, config.bufferForPlaybackMs());
        assertEquals(1000, config.bufferForPlaybackAfterRebufferMs());
        assertEquals(0, config.backBufferMs());
        assertTrue(config.prioritizeTimeOverSizeThresholds());
        assertFalse(config.retainBackBufferFromKeyframe());
    }

    @Test
    public void vodStreams_startFastButKeepUsefulBackBuffer() {
        PlaybackBufferPolicy.BufferConfig config =
                PlaybackBufferPolicy.configFor(XtreamContentType.MOVIE.name());

        assertEquals(20000, config.minBufferMs());
        assertEquals(60000, config.maxBufferMs());
        assertEquals(1000, config.bufferForPlaybackMs());
        assertEquals(2500, config.bufferForPlaybackAfterRebufferMs());
        assertEquals(30000, config.backBufferMs());
        assertTrue(config.prioritizeTimeOverSizeThresholds());
        assertTrue(config.retainBackBufferFromKeyframe());
    }

    @Test
    public void episodes_useVodBuffering() {
        PlaybackBufferPolicy.BufferConfig config =
                PlaybackBufferPolicy.configFor(XtreamContentType.EPISODE.name());

        assertEquals(20000, config.minBufferMs());
        assertEquals(60000, config.maxBufferMs());
    }

    @Test
    public void largeLiveBuffer_startsLaterAndHoldsMore() {
        PlaybackBufferPolicy.BufferConfig config =
                PlaybackBufferPolicy.configFor(XtreamContentType.LIVE.name(), true);

        assertEquals(6000, config.minBufferMs());
        assertEquals(20000, config.maxBufferMs());
        assertEquals(2500, config.bufferForPlaybackMs());
        assertEquals(4000, config.bufferForPlaybackAfterRebufferMs());
        assertEquals(0, config.backBufferMs());
    }

    @Test
    public void largeLiveBuffer_doesNotAffectVod() {
        assertEquals(PlaybackBufferPolicy.configFor(XtreamContentType.MOVIE.name()),
                PlaybackBufferPolicy.configFor(XtreamContentType.MOVIE.name(), true));
    }
}
