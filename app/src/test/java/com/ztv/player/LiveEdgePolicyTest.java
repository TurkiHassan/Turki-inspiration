package com.ztv.player;

import static org.junit.Assert.assertEquals;

import com.ztv.player.data.XtreamContentType;

import org.junit.Test;

public class LiveEdgePolicyTest {
    private static final float NORMAL = LiveEdgePolicy.NORMAL_SPEED;
    private static final float FAST = LiveEdgePolicy.CATCH_UP_SPEED;

    @Test
    public void smallBuffer_playsAtNormalSpeed() {
        assertEquals(NORMAL, LiveEdgePolicy.nextSpeed(true, 1000, NORMAL), 0f);
        assertEquals(NORMAL, LiveEdgePolicy.nextSpeed(true, 3500, NORMAL), 0f);
    }

    @Test
    public void largeBufferAfterStall_catchesUp() {
        assertEquals(FAST, LiveEdgePolicy.nextSpeed(true, 6000, NORMAL), 0f);
    }

    @Test
    public void keepsCatchingUpUntilBufferDrainsToStopThreshold() {
        assertEquals(FAST, LiveEdgePolicy.nextSpeed(true, 3000, FAST), 0f);
        assertEquals(NORMAL, LiveEdgePolicy.nextSpeed(true, 2000, FAST), 0f);
    }

    @Test
    public void pausedOrBuffering_returnsToNormalSpeed() {
        assertEquals(NORMAL, LiveEdgePolicy.nextSpeed(false, 8000, FAST), 0f);
    }

    @Test
    public void stopThresholdFollowsBufferSetting() {
        long normal = LiveEdgePolicy.stopThresholdMs(PlaybackBufferPolicy.configFor(XtreamContentType.LIVE.name(), false));
        long large = LiveEdgePolicy.stopThresholdMs(PlaybackBufferPolicy.configFor(XtreamContentType.LIVE.name(), true));
        assertEquals(LiveEdgePolicy.CATCH_UP_STOP_MS, normal);
        assertEquals(5000, large);
    }

    @Test
    public void largeBuffer_doesNotCatchUpWithinItsNormalCushion() {
        assertEquals(NORMAL, LiveEdgePolicy.nextSpeed(true, 6000, NORMAL, 5000), 0f);
        assertEquals(FAST, LiveEdgePolicy.nextSpeed(true, 7500, NORMAL, 5000), 0f);
        assertEquals(NORMAL, LiveEdgePolicy.nextSpeed(true, 5000, FAST, 5000), 0f);
    }
}
