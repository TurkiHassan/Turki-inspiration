package com.ztv.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AudioDelayPolicyTest {
    @Test
    public void labelsRoundTrip() {
        for (String option : AudioDelayPolicy.OPTIONS) {
            assertEquals(option, AudioDelayPolicy.label(AudioDelayPolicy.parse(option)));
        }
    }

    @Test
    public void parseClampsAndRejectsGarbage() {
        assertEquals(300, AudioDelayPolicy.parse("+900"));
        assertEquals(-300, AudioDelayPolicy.parse("-900"));
        assertEquals(0, AudioDelayPolicy.parse("abc"));
        assertEquals(0, AudioDelayPolicy.parse(null));
    }

    @Test
    public void positiveDelayMovesClockAhead() {
        assertEquals(1_200_000L, AudioDelayPolicy.shiftPositionUs(1_000_000L, 200));
        assertEquals(900_000L, AudioDelayPolicy.shiftPositionUs(1_000_000L, -100));
    }

    @Test
    public void unsetPositionPassesThrough() {
        assertEquals(AudioDelayPolicy.POSITION_NOT_SET,
                AudioDelayPolicy.shiftPositionUs(AudioDelayPolicy.POSITION_NOT_SET, 200));
    }
}
