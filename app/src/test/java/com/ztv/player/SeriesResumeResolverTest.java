package com.ztv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.ztv.player.data.WatchProgressStore;
import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SeriesResumeResolverTest {
    @Test
    public void findResumeEpisode_returnsMostRecentProgressItemThatBelongsToSeries() {
        XtreamMediaItem episodeOne = episode("Episode 1", 101);
        XtreamMediaItem episodeTwo = episode("Episode 2", 102);
        XtreamMediaItem otherSeriesEpisode = episode("Other Show", 201);

        XtreamMediaItem result = SeriesResumeResolver.findResumeEpisode(
                Arrays.asList(otherSeriesEpisode, episodeTwo, episodeOne),
                Arrays.asList(episodeOne, episodeTwo)
        );

        assertEquals(episodeTwo.id(), result.id());
        assertEquals(episodeTwo.title(), result.title());
    }

    @Test
    public void findResumeEpisode_returnsNullWhenSeriesHasNoSavedProgress() {
        XtreamMediaItem episodeOne = episode("Episode 1", 101);
        XtreamMediaItem otherSeriesEpisode = episode("Other Show", 201);

        assertNull(SeriesResumeResolver.findResumeEpisode(
                Collections.singletonList(otherSeriesEpisode),
                Collections.singletonList(episodeOne)
        ));
    }

    @Test
    public void findLastWatchedEpisode_returnsMostRecentlyUpdatedSeriesEpisode() {
        XtreamMediaItem episodeOne = episode("Episode 1", 101);
        XtreamMediaItem episodeTwo = episode("Episode 2", 102);
        XtreamMediaItem episodeThree = episode("Episode 3", 103);

        Map<Integer, WatchProgressStore.EntrySnapshot> snapshots = new LinkedHashMap<>();
        snapshots.put(episodeOne.id(), snapshot(episodeOne, 25, false, 1000L));
        snapshots.put(episodeThree.id(), snapshot(episodeThree, 100, true, 3000L));
        snapshots.put(999, snapshot(episode("Other Show", 999), 80, false, 5000L));

        XtreamMediaItem result = SeriesResumeResolver.findLastWatchedEpisode(
                snapshots,
                Arrays.asList(episodeOne, episodeTwo, episodeThree)
        );

        assertEquals(episodeThree.id(), result.id());
    }

    @Test
    public void findSeasonIndex_returnsMatchingEpisodeSeason() {
        XtreamMediaItem seasonTwoEpisode = new XtreamMediaItem(
                XtreamContentType.EPISODE, "Episode 6", 106, "2", "", "", "mp4");

        assertEquals(1, SeriesResumeResolver.findSeasonIndex(
                Arrays.asList("1", "2", "3"),
                seasonTwoEpisode
        ));
    }

    @Test
    public void findEpisodeIndex_returnsMinusOneWhenEpisodeMissing() {
        XtreamMediaItem episodeOne = episode("Episode 1", 101);
        XtreamMediaItem episodeTwo = episode("Episode 2", 102);

        assertEquals(-1, SeriesResumeResolver.findEpisodeIndex(
                Collections.singletonList(episodeOne),
                episodeTwo
        ));
    }

    private static XtreamMediaItem episode(String title, int id) {
        return new XtreamMediaItem(XtreamContentType.EPISODE, title, id, "1", "", "", "mp4");
    }

    private static WatchProgressStore.EntrySnapshot snapshot(
            XtreamMediaItem item,
            int percent,
            boolean completed,
            long updatedAtMs
    ) {
        long duration = 100L;
        long position = completed ? duration : percent;
        return new WatchProgressStore.EntrySnapshot(item, position, duration, completed, updatedAtMs);
    }
}
