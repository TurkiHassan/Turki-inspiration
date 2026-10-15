package com.ztv.player;

import com.ztv.player.data.WatchProgressStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.ztv.player.ui.Tr;

/** Builds the hub's "Continue watching" rail and the copy that goes with resume points. */
public final class ContinueWatchingPolicy {
    private ContinueWatchingPolicy() {
    }

    /** Unfinished movies and episodes together, most recently watched first. */
    public static List<WatchProgressStore.EntrySnapshot> rail(
            Collection<WatchProgressStore.EntrySnapshot> movies,
            Collection<WatchProgressStore.EntrySnapshot> episodes,
            int limit) {
        List<WatchProgressStore.EntrySnapshot> merged = new ArrayList<>();
        addInProgress(merged, movies);
        addInProgress(merged, episodes);
        merged.sort((left, right) -> Long.compare(right.updatedAtMs(), left.updatedAtMs()));
        return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
    }

    private static void addInProgress(List<WatchProgressStore.EntrySnapshot> target,
                                      Collection<WatchProgressStore.EntrySnapshot> source) {
        if (source == null) {
            return;
        }
        for (WatchProgressStore.EntrySnapshot snapshot : source) {
            if (snapshot != null && snapshot.inProgress()) {
                target.add(snapshot);
            }
        }
    }

    /** "1h 12m left", "8m left", or "" when the duration is unknown. */
    public static String timeLeft(long positionMs, long durationMs) {
        if (durationMs <= 0L || positionMs >= durationMs) {
            return "";
        }
        long minutes = Math.max(1L, (durationMs - Math.max(0L, positionMs) + 59_999L) / 60_000L);
        if (minutes >= 60L) {
            long hours = minutes / 60L;
            long rest = minutes % 60L;
            return rest == 0L ? Tr.t(hours + "h left", "باقي " + hours + " س")
                    : Tr.t(hours + "h " + rest + "m left", "باقي " + hours + " س " + rest + " د");
        }
        return Tr.t(minutes + "m left", "باقي " + minutes + " د");
    }

    /** Label for the detail page's primary button. */
    public static String resumeLabel(long positionMs, long durationMs) {
        if (positionMs <= 0L) {
            return Tr.t("Play", "تشغيل");
        }
        String left = timeLeft(positionMs, durationMs);
        return left.isEmpty() ? Tr.t("Resume", "استئناف") : Tr.t("Resume", "استئناف") + "  ·  " + left;
    }
}
