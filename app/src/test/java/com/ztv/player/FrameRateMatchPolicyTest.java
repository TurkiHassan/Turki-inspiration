package com.ztv.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class FrameRateMatchPolicyTest {
    private static final FrameRateMatchPolicy.DisplayMode UHD_60 = mode(1, 3840, 2160, 60f);
    private static final FrameRateMatchPolicy.DisplayMode UHD_5994 = mode(2, 3840, 2160, 59.94f);
    private static final FrameRateMatchPolicy.DisplayMode UHD_50 = mode(3, 3840, 2160, 50f);
    private static final FrameRateMatchPolicy.DisplayMode UHD_25 = mode(4, 3840, 2160, 25f);
    private static final FrameRateMatchPolicy.DisplayMode UHD_23976 = mode(5, 3840, 2160, 23.976f);
    private static final FrameRateMatchPolicy.DisplayMode UHD_24 = mode(6, 3840, 2160, 24f);
    private static final FrameRateMatchPolicy.DisplayMode FHD_50 = mode(7, 1920, 1080, 50f);
    private static final List<FrameRateMatchPolicy.DisplayMode> MODES =
            Arrays.asList(UHD_60, UHD_5994, UHD_50, UHD_25, UHD_23976, UHD_24, FHD_50);

    @Test
    public void sports25fps_prefers50HzOver25Hz() {
        assertEquals(3, FrameRateMatchPolicy.pickModeId(25f, MODES, UHD_60));
    }

    @Test
    public void fps50_picks50Hz() {
        assertEquals(3, FrameRateMatchPolicy.pickModeId(50f, MODES, UHD_60));
    }

    @Test
    public void ntsc2997_picks5994NotSixty() {
        assertEquals(2, FrameRateMatchPolicy.pickModeId(29.97f, MODES, UHD_60));
    }

    @Test
    public void film23976_fallsBackToLowRefreshWhenNoComfortableMultiple() {
        assertEquals(5, FrameRateMatchPolicy.pickModeId(23.976f, MODES, UHD_60));
    }

    @Test
    public void keepsCurrentResolution() {
        assertEquals(7, FrameRateMatchPolicy.pickModeId(25f, MODES, mode(8, 1920, 1080, 60f)));
    }

    @Test
    public void unknownOrUnmatchedRate_returnsNoMatch() {
        assertEquals(FrameRateMatchPolicy.NO_MATCH, FrameRateMatchPolicy.pickModeId(-1f, MODES, UHD_60));
        assertEquals(FrameRateMatchPolicy.NO_MATCH, FrameRateMatchPolicy.pickModeId(Float.NaN, MODES, UHD_60));
        assertEquals(FrameRateMatchPolicy.NO_MATCH,
                FrameRateMatchPolicy.pickModeId(25f, Arrays.asList(UHD_60, UHD_5994), UHD_60));
    }

    @Test
    public void snapToStandard_snapsNearbyAndKeepsOddRates() {
        assertEquals(25f, FrameRateMatchPolicy.snapToStandard(25.1f), 0.0001f);
        assertEquals(59.94f, FrameRateMatchPolicy.snapToStandard(59.93f), 0.0001f);
        assertEquals(60f, FrameRateMatchPolicy.snapToStandard(60.01f), 0.0001f);
        assertEquals(15f, FrameRateMatchPolicy.snapToStandard(15f), 0.0001f);
    }

    private static FrameRateMatchPolicy.DisplayMode mode(int id, int width, int height, float hz) {
        return new FrameRateMatchPolicy.DisplayMode(id, width, height, hz);
    }
}
