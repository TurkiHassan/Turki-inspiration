package com.ztv.player;

import static org.junit.Assert.assertEquals;

import com.ztv.player.data.WatchProgressStore;
import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ContinueWatchingPolicyTest {
    private static WatchProgressStore.EntrySnapshot snapshot(XtreamContentType type, int id, long position,
                                                             boolean completed, long updatedAt) {
        XtreamMediaItem item = new XtreamMediaItem(type, "T" + id, id, "", "", "", "mp4");
        return new WatchProgressStore.EntrySnapshot(item, position, 100_000L, completed, updatedAt);
    }

    @Test
    public void mergesTypesNewestFirstAndDropsFinished() {
        List<WatchProgressStore.EntrySnapshot> rail = ContinueWatchingPolicy.rail(
                Arrays.asList(snapshot(XtreamContentType.MOVIE, 1, 500, false, 10),
                        snapshot(XtreamContentType.MOVIE, 2, 500, true, 99)),
                Arrays.asList(snapshot(XtreamContentType.EPISODE, 3, 500, false, 30),
                        snapshot(XtreamContentType.EPISODE, 4, 0, false, 50)),
                10);
        assertEquals(2, rail.size());
        assertEquals(3, rail.get(0).item().id());
        assertEquals(1, rail.get(1).item().id());
    }

    @Test
    public void capsTheRail() {
        List<WatchProgressStore.EntrySnapshot> rail = ContinueWatchingPolicy.rail(
                Arrays.asList(snapshot(XtreamContentType.MOVIE, 1, 5, false, 1),
                        snapshot(XtreamContentType.MOVIE, 2, 5, false, 2),
                        snapshot(XtreamContentType.MOVIE, 3, 5, false, 3)),
                null, 2);
        assertEquals(2, rail.size());
        assertEquals(3, rail.get(0).item().id());
    }

    @Test
    public void formatsTimeLeft() {
        assertEquals("8m left", ContinueWatchingPolicy.timeLeft(52 * 60_000L, 60 * 60_000L));
        assertEquals("1h 12m left", ContinueWatchingPolicy.timeLeft(0L, 72 * 60_000L));
        assertEquals("2h left", ContinueWatchingPolicy.timeLeft(0L, 120 * 60_000L));
        assertEquals("", ContinueWatchingPolicy.timeLeft(10L, 0L));
        assertEquals("Play", ContinueWatchingPolicy.resumeLabel(0L, 1000L));
        assertEquals("Resume", ContinueWatchingPolicy.resumeLabel(500L, 0L));
        assertEquals("Resume  ·  1m left", ContinueWatchingPolicy.resumeLabel(30_000L, 60_000L));
    }
}
