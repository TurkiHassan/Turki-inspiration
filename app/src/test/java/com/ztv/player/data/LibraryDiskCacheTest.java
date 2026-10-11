package com.ztv.player.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LibraryDiskCacheTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writeThenReadLiveLibrary_preservesSectionAndItemOrder() throws Exception {
        LibraryDiskCache cache = new LibraryDiskCache(temporaryFolder.newFolder("cache"));
        List<XtreamMediaSection> sections = Arrays.asList(
                new XtreamMediaSection("Sports", Arrays.asList(
                        new XtreamMediaItem(XtreamContentType.LIVE, "Channel A", 100, "1", "https://img/a.png", "A detail", "ts"),
                        new XtreamMediaItem(XtreamContentType.LIVE, "Channel B", 200, "1", "", "", "ts")
                )),
                new XtreamMediaSection("News", Arrays.asList(
                        new XtreamMediaItem(XtreamContentType.LIVE, "Channel C", 300, "2", "https://img/c.png", "", "ts")
                ))
        );

        cache.writeLive("demo-user", sections);

        List<XtreamMediaSection> restored = cache.readLive("demo-user");
        assertEquals(2, restored.size());
        assertEquals("Sports", restored.get(0).title());
        assertEquals("Channel A", restored.get(0).items().get(0).title());
        assertEquals(100, restored.get(0).items().get(0).id());
        assertEquals("Channel B", restored.get(0).items().get(1).title());
        assertEquals("News", restored.get(1).title());
        assertEquals("Channel C", restored.get(1).items().get(0).title());
    }

    @Test
    public void writeThenReadLibrary_preservesRequestedContentType() throws Exception {
        LibraryDiskCache cache = new LibraryDiskCache(temporaryFolder.newFolder("cache"));
        List<XtreamMediaSection> sections = Arrays.asList(
                new XtreamMediaSection("Foreign Shows 2026", Arrays.asList(
                        new XtreamMediaItem(XtreamContentType.SERIES, "Show A", 700, "series-cat", "https://img/show.png", "A show", "mp4")
                )),
                new XtreamMediaSection("Movies", Arrays.asList(
                        new XtreamMediaItem(XtreamContentType.SERIES, "Show B", 701, "series-cat", "", "", "mp4")
                ))
        );

        cache.write("demo-user", XtreamContentType.SERIES, sections);

        List<XtreamMediaSection> restored = cache.read("demo-user", XtreamContentType.SERIES);
        assertEquals(2, restored.size());
        assertEquals("Foreign Shows 2026", restored.get(0).title());
        assertEquals(XtreamContentType.SERIES, restored.get(0).items().get(0).type());
        assertEquals("Show A", restored.get(0).items().get(0).title());
        assertEquals("series-cat", restored.get(0).items().get(0).categoryId());
    }

    @Test
    public void writeLiveLibrary_doesNotStoreCredentialsOrRawUsernameInCacheFile() throws Exception {
        File cacheDir = temporaryFolder.newFolder("cache");
        LibraryDiskCache cache = new LibraryDiskCache(cacheDir);

        cache.writeLive("demo-user", Arrays.asList(
                new XtreamMediaSection("Sports", Arrays.asList(
                        new XtreamMediaItem(XtreamContentType.LIVE, "Channel A", 100, "1", "", "", "ts")
                ))
        ));

        File[] files = cacheDir.listFiles();
        assertTrue(files != null && files.length == 1);
        String fileName = files[0].getName();
        String content = new String(Files.readAllBytes(files[0].toPath()), StandardCharsets.UTF_8);

        assertTrue(fileName.startsWith("live_library_"));
        assertFalse(fileName.contains("demo-user"));
        assertFalse(content.contains("demo-user"));
        assertFalse(content.contains("password"));
    }

    @Test
    public void readLiveLibrary_readsFilesWrittenByOldOrgJsonFormat() throws Exception {
        // Cache files written before the streaming rewrite (compact org.json output)
        // must stay readable so users don't lose their instant-boot cache on update.
        File cacheDir = temporaryFolder.newFolder("cache");
        LibraryDiskCache cache = new LibraryDiskCache(cacheDir);
        String legacyJson = "{\"sections\":[{\"title\":\"Sports\",\"items\":["
                + "{\"title\":\"Channel A\",\"id\":100,\"categoryId\":\"1\","
                + "\"imageUrl\":\"https://img/a.png\",\"detail\":\"A detail\",\"ext\":\"ts\"}]}]}";
        File legacyFile = new File(cacheDir, cache.liveFileNameForTesting("demo-user"));
        Files.write(legacyFile.toPath(), legacyJson.getBytes(StandardCharsets.UTF_8));

        List<XtreamMediaSection> restored = cache.readLive("demo-user");

        assertEquals(1, restored.size());
        assertEquals("Sports", restored.get(0).title());
        assertEquals("Channel A", restored.get(0).items().get(0).title());
        assertEquals(100, restored.get(0).items().get(0).id());
        assertEquals("https://img/a.png", restored.get(0).items().get(0).imageUrl());
    }

    @Test
    public void readLiveLibrary_returnsEmptyListForCorruptJson() throws Exception {
        File cacheDir = temporaryFolder.newFolder("cache");
        LibraryDiskCache cache = new LibraryDiskCache(cacheDir);
        File corruptFile = new File(cacheDir, cache.liveFileNameForTesting("demo-user"));
        Files.write(corruptFile.toPath(), "{not-json".getBytes(StandardCharsets.UTF_8));

        List<XtreamMediaSection> restored = cache.readLive("demo-user");

        assertTrue(restored.isEmpty());
    }

    @Test
    public void writeThenRead_keepsRatingAndAddedStamp() throws Exception {
        LibraryDiskCache cache = new LibraryDiskCache(temporaryFolder.newFolder("rated"));
        List<XtreamMediaSection> sections = Arrays.asList(new XtreamMediaSection("Films", Arrays.asList(
                new XtreamMediaItem(XtreamContentType.MOVIE, "Film", 5, "1", "", "", "mp4", "8.2", 1712000000L))));

        cache.write("demo-user", XtreamContentType.MOVIE, sections);

        XtreamMediaItem restored = cache.read("demo-user", XtreamContentType.MOVIE).get(0).items().get(0);
        assertEquals("8.2", restored.rating());
        assertEquals(1712000000L, restored.addedAt());
    }
}
