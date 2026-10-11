package com.ztv.player.workspace;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.LruCache;

import com.ztv.player.data.LibraryDiskCache;
import com.ztv.player.data.XtreamAccountInfo;
import com.ztv.player.data.XtreamApiClient;
import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamCredentials;
import com.ztv.player.data.XtreamEpgProgram;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.data.XtreamMediaSection;
import com.ztv.player.data.XtreamMovieInfo;
import com.ztv.player.data.XtreamSeriesDetail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The workspace's single source of catalog data: memory -> disk -> network, in that order
 * (the paint-from-disk-first pipeline the Mi Box needs), plus small caches for the detail
 * and EPG requests IPTVnator fires while browsing.
 */
public final class Catalog {
    private static final String TAG = "ZTV";
    private static final long MEMORY_TTL_MS = 10 * 60 * 1000;

    public interface Result<T> {
        void onResult(T value);
    }

    public interface LibraryListener {
        /** Called once from disk (if cached) and again when the network copy lands. */
        void onLibrary(XtreamContentType type, List<XtreamMediaSection> sections, boolean fromNetwork);

        void onLibraryError(XtreamContentType type, Exception error);
    }

    private final Activity activity;
    private final XtreamApiClient api;
    private final LibraryDiskCache diskCache;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService diskWriter = Executors.newSingleThreadExecutor();
    private final ExecutorService diskReader = Executors.newFixedThreadPool(2);
    private final Map<XtreamContentType, List<XtreamMediaSection>> memory = new EnumMap<>(XtreamContentType.class);
    private final Map<XtreamContentType, Long> memoryTime = new EnumMap<>(XtreamContentType.class);
    private final Map<XtreamContentType, List<LibraryListener>> waiting = new EnumMap<>(XtreamContentType.class);
    private final Set<XtreamContentType> networkInFlight = new HashSet<>();
    private final List<LibraryListener> globalListeners = new ArrayList<>();
    private final LruCache<Integer, List<XtreamEpgProgram>> epgCache = new LruCache<>(400);
    private final Map<Integer, Long> epgTime = new HashMap<>();
    private final Map<Integer, List<Result<List<XtreamEpgProgram>>>> epgWaiting = new HashMap<>();
    private final LruCache<Integer, XtreamMovieInfo> movieInfoCache = new LruCache<>(64);
    private final LruCache<Integer, XtreamSeriesDetail> seriesCache = new LruCache<>(24);
    private XtreamCredentials credentials;
    private XtreamAccountInfo accountInfo;
    private boolean shutdown;
    private final long startedAt = SystemClock.elapsedRealtime();

    public Catalog(Activity activity, XtreamApiClient api, LibraryDiskCache diskCache) {
        this.activity = activity;
        this.api = api;
        this.diskCache = diskCache;
    }

    public void setCredentials(XtreamCredentials credentials) {
        boolean changed = this.credentials == null || credentials == null
                || !this.credentials.username().equals(credentials.username())
                || !this.credentials.host().equals(credentials.host());
        this.credentials = credentials;
        if (changed) {
            memory.clear();
            memoryTime.clear();
            epgCache.evictAll();
            movieInfoCache.evictAll();
            seriesCache.evictAll();
            accountInfo = null;
        }
    }

    public XtreamCredentials credentials() {
        return credentials;
    }

    public void addListener(LibraryListener listener) {
        globalListeners.add(listener);
    }

    public void removeListener(LibraryListener listener) {
        globalListeners.remove(listener);
    }

    /** Sections in memory right now (possibly stale), or null. */
    public List<XtreamMediaSection> cached(XtreamContentType type) {
        return memory.get(type);
    }

    public boolean isLoaded(XtreamContentType type) {
        return memory.containsKey(type);
    }

    /**
     * Delivers the library to {@code listener}: synchronously from memory when present,
     * otherwise from disk, then (silently) the fresh network copy.
     */
    public void load(XtreamContentType type, boolean forceNetwork, LibraryListener listener) {
        if (credentials == null) {
            return;
        }
        List<XtreamMediaSection> inMemory = memory.get(type);
        Long at = memoryTime.get(type);
        boolean fresh = at != null && SystemClock.elapsedRealtime() - at < MEMORY_TTL_MS;
        if (inMemory != null && !forceNetwork) {
            if (listener != null) {
                listener.onLibrary(type, inMemory, false);
            }
            if (!fresh) {
                fetchNetwork(type, null);
            }
            return;
        }
        if (forceNetwork) {
            fetchNetwork(type, listener);
            return;
        }
        String username = credentials.username();
        readDisk(() -> {
            List<XtreamMediaSection> disk = diskCache.read(username, type);
            ui.post(() -> {
                if (shutdown || activity.isDestroyed()) {
                    return;
                }
                if (!disk.isEmpty()) {
                    if (!memory.containsKey(type)) {
                        memory.put(type, disk);
                        memoryTime.put(type, 0L);
                    }
                    Log.d(TAG, "perf " + type + "_disk_painted elapsedMs=" + (SystemClock.elapsedRealtime() - startedAt));
                    if (listener != null) {
                        listener.onLibrary(type, memory.get(type), false);
                    }
                    fetchNetwork(type, listener);
                } else {
                    fetchNetwork(type, listener);
                }
            });
        });
    }

    private void fetchNetwork(XtreamContentType type, LibraryListener listener) {
        if (listener != null) {
            List<LibraryListener> list = waiting.get(type);
            if (list == null) {
                list = new ArrayList<>();
                waiting.put(type, list);
            }
            if (!list.contains(listener)) {
                list.add(listener);
            }
        }
        if (networkInFlight.contains(type) || credentials == null) {
            return;
        }
        networkInFlight.add(type);
        XtreamCredentials requestCredentials = credentials;
        api.fetchLibrary(requestCredentials, type, new XtreamApiClient.Callback<List<XtreamMediaSection>>() {
            @Override
            public void onSuccess(List<XtreamMediaSection> result) {
                networkInFlight.remove(type);
                if (shutdown || activity.isDestroyed() || requestCredentials != credentials) {
                    return;
                }
                memory.put(type, result);
                memoryTime.put(type, SystemClock.elapsedRealtime());
                writeDisk(() -> diskCache.write(requestCredentials.username(), type, result));
                Log.d(TAG, "perf " + type + "_network_loaded elapsedMs=" + (SystemClock.elapsedRealtime() - startedAt));
                List<LibraryListener> listeners = waiting.remove(type);
                if (listeners != null) {
                    for (LibraryListener l : listeners) {
                        l.onLibrary(type, result, true);
                    }
                }
                for (LibraryListener l : new ArrayList<>(globalListeners)) {
                    l.onLibrary(type, result, true);
                }
            }

            @Override
            public void onError(Exception exception) {
                networkInFlight.remove(type);
                if (shutdown || activity.isDestroyed()) {
                    return;
                }
                Log.w(TAG, "Unable to load " + type, exception);
                List<LibraryListener> listeners = waiting.remove(type);
                if (listeners != null) {
                    for (LibraryListener l : listeners) {
                        l.onLibraryError(type, exception);
                    }
                }
            }
        });
    }

    /** Warms the other catalogs after the visible one painted (disk first, then network). */
    public void prefetch(XtreamContentType... types) {
        ui.postDelayed(() -> {
            for (XtreamContentType type : types) {
                if (!memory.containsKey(type)) {
                    load(type, false, null);
                }
            }
        }, 1200L);
    }

    public void refreshAll() {
        for (XtreamContentType type : new XtreamContentType[]{
                XtreamContentType.MOVIE, XtreamContentType.LIVE, XtreamContentType.SERIES}) {
            fetchNetwork(type, null);
        }
    }

    // ─── Derived views ──────────────────────────────────────────────────────

    public static List<XtreamMediaItem> allItems(List<XtreamMediaSection> sections) {
        List<XtreamMediaItem> items = new ArrayList<>();
        if (sections == null) {
            return items;
        }
        Set<Integer> seen = new HashSet<>();
        for (XtreamMediaSection section : sections) {
            for (XtreamMediaItem item : section.items()) {
                if (seen.add(item.id())) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** Newest first by the panel's `added` stamp (IPTVnator "Date Added (Latest First)"). */
    public static List<XtreamMediaItem> latest(List<XtreamMediaItem> items, int limit) {
        List<XtreamMediaItem> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> Long.compare(b.addedAt(), a.addedAt()));
        if (limit > 0 && sorted.size() > limit) {
            return new ArrayList<>(sorted.subList(0, limit));
        }
        return sorted;
    }

    public int count(XtreamContentType type) {
        List<XtreamMediaSection> sections = memory.get(type);
        return sections == null ? 0 : allItems(sections).size();
    }

    // ─── Details ────────────────────────────────────────────────────────────

    public void movieInfo(int vodId, Result<XtreamMovieInfo> result) {
        XtreamMovieInfo cached = movieInfoCache.get(vodId);
        if (cached != null) {
            result.onResult(cached);
            return;
        }
        if (credentials == null) {
            return;
        }
        api.fetchMovieInfo(credentials, vodId, new XtreamApiClient.Callback<XtreamMovieInfo>() {
            @Override
            public void onSuccess(XtreamMovieInfo info) {
                movieInfoCache.put(vodId, info);
                if (!shutdown) {
                    result.onResult(info);
                }
            }

            @Override
            public void onError(Exception exception) {
                if (!shutdown) {
                    result.onResult(null);
                }
            }
        });
    }

    public void seriesDetail(int seriesId, Result<XtreamSeriesDetail> result) {
        XtreamSeriesDetail cached = seriesCache.get(seriesId);
        if (cached != null) {
            result.onResult(cached);
            return;
        }
        if (credentials == null) {
            return;
        }
        api.fetchSeriesDetail(credentials, seriesId, new XtreamApiClient.Callback<XtreamSeriesDetail>() {
            @Override
            public void onSuccess(XtreamSeriesDetail detail) {
                seriesCache.put(seriesId, detail);
                if (!shutdown) {
                    result.onResult(detail);
                }
            }

            @Override
            public void onError(Exception exception) {
                if (!shutdown) {
                    result.onResult(null);
                }
            }
        });
    }

    public XtreamAccountInfo accountInfo() {
        return accountInfo;
    }

    public void loadAccountInfo(Result<XtreamAccountInfo> result) {
        if (credentials == null) {
            return;
        }
        if (accountInfo != null) {
            result.onResult(accountInfo);
            return;
        }
        api.fetchAccountInfo(credentials, new XtreamApiClient.Callback<XtreamAccountInfo>() {
            @Override
            public void onSuccess(XtreamAccountInfo info) {
                accountInfo = info;
                if (!shutdown) {
                    result.onResult(info);
                }
            }

            @Override
            public void onError(Exception exception) {
                if (!shutdown) {
                    result.onResult(null);
                }
            }
        });
    }

    // ─── EPG ────────────────────────────────────────────────────────────────

    public List<XtreamEpgProgram> cachedEpg(int streamId) {
        Long at = epgTime.get(streamId);
        if (at == null || SystemClock.elapsedRealtime() - at > 5 * 60 * 1000) {
            return null;
        }
        return epgCache.get(streamId);
    }

    /** Short EPG for one channel; concurrent requests for the same channel share one call. */
    public void epg(int streamId, Result<List<XtreamEpgProgram>> result) {
        List<XtreamEpgProgram> cached = cachedEpg(streamId);
        if (cached != null) {
            result.onResult(cached);
            return;
        }
        if (credentials == null) {
            return;
        }
        List<Result<List<XtreamEpgProgram>>> queue = epgWaiting.get(streamId);
        if (queue != null) {
            queue.add(result);
            return;
        }
        queue = new ArrayList<>();
        queue.add(result);
        epgWaiting.put(streamId, queue);
        api.fetchShortEpg(credentials, streamId, new XtreamApiClient.Callback<List<XtreamEpgProgram>>() {
            @Override
            public void onSuccess(List<XtreamEpgProgram> programs) {
                deliver(programs);
            }

            @Override
            public void onError(Exception exception) {
                deliver(Collections.emptyList());
            }

            private void deliver(List<XtreamEpgProgram> programs) {
                epgCache.put(streamId, programs);
                epgTime.put(streamId, SystemClock.elapsedRealtime());
                List<Result<List<XtreamEpgProgram>>> waiters = epgWaiting.remove(streamId);
                if (shutdown || waiters == null) {
                    return;
                }
                for (Result<List<XtreamEpgProgram>> waiter : waiters) {
                    waiter.onResult(programs);
                }
            }
        });
    }

    // ─── Executors ──────────────────────────────────────────────────────────

    private void readDisk(Runnable task) {
        if (shutdown || diskReader.isShutdown()) {
            return;
        }
        try {
            diskReader.execute(task);
        } catch (RejectedExecutionException ignored) {
            // Torn down mid-flight.
        }
    }

    private void writeDisk(Runnable task) {
        if (shutdown || diskWriter.isShutdown()) {
            return;
        }
        try {
            diskWriter.execute(task);
        } catch (RejectedExecutionException ignored) {
            // Torn down mid-flight.
        }
    }

    public void clearDisk() {
        writeDisk(diskCache::clearAll);
        memory.clear();
        memoryTime.clear();
    }

    public void shutdown() {
        shutdown = true;
        ui.removeCallbacksAndMessages(null);
        diskWriter.shutdown();
        diskReader.shutdownNow();
    }
}
