package com.ztv.player.data;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class EpgNowNextTest {
    private static XtreamEpgProgram program(String title, long start, long end) {
        return new XtreamEpgProgram(title, "", "", false, start, end);
    }

    @Test
    public void picksProgrammeByTimestamps() {
        List<XtreamEpgProgram> programs = Arrays.asList(
                program("Past", 1000, 2000), program("Now", 2000, 3000), program("Next", 3000, 4000));
        assertEquals(1, EpgNowNext.nowIndex(programs, 2500));
    }

    @Test
    public void fallsBackToNowPlayingFlagThenFirst() {
        List<XtreamEpgProgram> flagged = Arrays.asList(
                new XtreamEpgProgram("A", "", "", false), new XtreamEpgProgram("B", "", "", true));
        assertEquals(1, EpgNowNext.nowIndex(flagged, 5000));
        List<XtreamEpgProgram> plain = Arrays.asList(
                new XtreamEpgProgram("A", "", "", false), new XtreamEpgProgram("B", "", "", false));
        assertEquals(0, EpgNowNext.nowIndex(plain, 5000));
        assertEquals(-1, EpgNowNext.nowIndex(Collections.emptyList(), 5000));
    }

    @Test
    public void progressAndMinutesLeft() {
        XtreamEpgProgram show = program("Show", 10_000, 13_600);
        assertEquals(25, EpgNowNext.progressPercent(show, 10_900));
        assertEquals(45, EpgNowNext.minutesLeft(show, 10_900));
        assertEquals(100, EpgNowNext.progressPercent(show, 99_999));
        assertEquals(-1, EpgNowNext.progressPercent(new XtreamEpgProgram("x", "", "", true), 10));
        assertEquals(-1, EpgNowNext.minutesLeft(show, 13_600));
    }
}
