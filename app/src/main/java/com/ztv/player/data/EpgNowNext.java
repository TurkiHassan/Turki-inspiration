package com.ztv.player.data;

import java.util.List;

/** Picks the programme on air from a short-EPG list and how far through it we are. */
public final class EpgNowNext {
    private EpgNowNext() {
    }

    /** Index of the programme on air at {@code nowEpochSeconds}, or -1 for an empty list.
     *  Timestamps win when the server sends them; otherwise the now_playing flag; else the first entry. */
    public static int nowIndex(List<XtreamEpgProgram> programs, long nowEpochSeconds) {
        if (programs == null || programs.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < programs.size(); index++) {
            XtreamEpgProgram program = programs.get(index);
            if (program.hasTimestamps()
                    && program.startEpochSeconds() <= nowEpochSeconds
                    && nowEpochSeconds < program.endEpochSeconds()) {
                return index;
            }
        }
        for (int index = 0; index < programs.size(); index++) {
            if (programs.get(index).nowPlaying()) {
                return index;
            }
        }
        return 0;
    }

    /** 0-100 elapsed share of the programme, or -1 when its times are unknown. */
    public static int progressPercent(XtreamEpgProgram program, long nowEpochSeconds) {
        if (program == null || !program.hasTimestamps()) {
            return -1;
        }
        long length = program.endEpochSeconds() - program.startEpochSeconds();
        long elapsed = nowEpochSeconds - program.startEpochSeconds();
        return (int) Math.max(0L, Math.min(100L, elapsed * 100L / length));
    }

    /** Whole minutes left in the programme, or -1 when unknown. */
    public static long minutesLeft(XtreamEpgProgram program, long nowEpochSeconds) {
        if (program == null || !program.hasTimestamps() || nowEpochSeconds >= program.endEpochSeconds()) {
            return -1L;
        }
        return Math.max(1L, (program.endEpochSeconds() - nowEpochSeconds + 59L) / 60L);
    }
}
