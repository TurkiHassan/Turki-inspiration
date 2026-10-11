package com.ztv.player;

import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.data.WatchProgressStore;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SeriesResumeResolver {
    private SeriesResumeResolver() {
    }

    public static XtreamMediaItem findResumeEpisode(
            List<XtreamMediaItem> savedProgressItems,
            List<XtreamMediaItem> seriesEpisodes
    ) {
        if (savedProgressItems == null || seriesEpisodes == null || seriesEpisodes.isEmpty()) {
            return null;
        }
        Set<Integer> episodeIds = new HashSet<>();
        for (XtreamMediaItem episode : seriesEpisodes) {
            if (episode.type() == XtreamContentType.EPISODE) {
                episodeIds.add(episode.id());
            }
        }
        for (XtreamMediaItem savedItem : savedProgressItems) {
            if (savedItem.type() == XtreamContentType.EPISODE && episodeIds.contains(savedItem.id())) {
                return savedItem;
            }
        }
        return null;
    }

    public static XtreamMediaItem findLastWatchedEpisode(
            Map<Integer, WatchProgressStore.EntrySnapshot> savedEntries,
            List<XtreamMediaItem> seriesEpisodes
    ) {
        if (savedEntries == null || savedEntries.isEmpty() || seriesEpisodes == null || seriesEpisodes.isEmpty()) {
            return null;
        }
        XtreamMediaItem lastWatched = null;
        long newestUpdate = -1L;
        for (XtreamMediaItem episode : seriesEpisodes) {
            if (episode.type() != XtreamContentType.EPISODE) {
                continue;
            }
            WatchProgressStore.EntrySnapshot snapshot = savedEntries.get(episode.id());
            if (snapshot != null && snapshot.updatedAtMs() >= newestUpdate) {
                newestUpdate = snapshot.updatedAtMs();
                lastWatched = episode;
            }
        }
        return lastWatched;
    }

    public static int findSeasonIndex(List<String> seasons, XtreamMediaItem episode) {
        if (seasons == null || seasons.isEmpty() || episode == null) {
            return 0;
        }
        for (int index = 0; index < seasons.size(); index++) {
            if (seasons.get(index).equals(episode.categoryId())) {
                return index;
            }
        }
        return 0;
    }

    public static int findEpisodeIndex(List<XtreamMediaItem> episodes, XtreamMediaItem episode) {
        if (episodes == null || episode == null) {
            return -1;
        }
        for (int index = 0; index < episodes.size(); index++) {
            if (episodes.get(index).id() == episode.id()) {
                return index;
            }
        }
        return -1;
    }
}
