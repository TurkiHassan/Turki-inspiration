package com.ztv.player.data;

/** A single EPG programme entry (from Xtream `get_short_epg`). */
public final class XtreamEpgProgram {
    private final String title;
    private final String start;
    private final String end;
    private final boolean nowPlaying;
    private final long startEpochSeconds;
    private final long endEpochSeconds;

    public XtreamEpgProgram(String title, String start, String end, boolean nowPlaying) {
        this(title, start, end, nowPlaying, 0L, 0L);
    }

    public XtreamEpgProgram(String title, String start, String end, boolean nowPlaying,
                            long startEpochSeconds, long endEpochSeconds) {
        this.title = title == null ? "" : title;
        this.start = start == null ? "" : start;
        this.end = end == null ? "" : end;
        this.nowPlaying = nowPlaying;
        this.startEpochSeconds = Math.max(0L, startEpochSeconds);
        this.endEpochSeconds = Math.max(0L, endEpochSeconds);
    }

    public String title() {
        return title;
    }

    /** Time as HH:mm, best-effort. */
    public String start() {
        return start;
    }

    public String end() {
        return end;
    }

    public boolean nowPlaying() {
        return nowPlaying;
    }

    /** Unix start time in seconds, or 0 when the server did not send one. */
    public long startEpochSeconds() {
        return startEpochSeconds;
    }

    public long endEpochSeconds() {
        return endEpochSeconds;
    }

    public boolean hasTimestamps() {
        return startEpochSeconds > 0L && endEpochSeconds > startEpochSeconds;
    }
}
