package com.ztv.player.workspace;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DetailFormattingTest {
    @Test
    public void formatDuration_matchesIptvnatorChips() {
        assertEquals("1 h 54 min", DetailPage.formatDuration("01:54:00"));
        assertEquals("44 min", DetailPage.formatDuration("00:44:10"));
        assertEquals("2 h 4 min", DetailPage.formatDuration("124"));
        assertEquals("44 min/ep", DetailPage.formatDuration("44 min/ep"));
        assertEquals("", DetailPage.formatDuration(""));
        assertEquals("", DetailPage.formatDuration("00:00:00"));
    }

    @Test
    public void yearOf_findsTheYearAnywhere() {
        assertEquals("2026", DetailPage.yearOf("2026-04-24"));
        assertEquals("1999", DetailPage.yearOf("released 12/03/1999"));
        assertEquals("", DetailPage.yearOf("soon"));
    }

    @Test
    public void normalizeRating_hidesZero() {
        assertEquals("8.2", DetailPage.normalizeRating("8.2"));
        assertEquals("7.0", DetailPage.normalizeRating("7"));
        assertEquals("", DetailPage.normalizeRating("0"));
        assertEquals("", DetailPage.normalizeRating("n/a"));
    }
}
