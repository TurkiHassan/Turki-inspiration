package com.ztv.player.data;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class LibraryDiskCache {
    private static final String LIVE_PREFIX = "live_library_";
    private static final String MOVIE_PREFIX = "movie_library_";
    private static final String SERIES_PREFIX = "series_library_";
    private static final String EXTENSION = ".json";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final File cacheDir;

    public LibraryDiskCache(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public List<XtreamMediaSection> readLive(String username) {
        return read(username, XtreamContentType.LIVE);
    }

    /** Stable, non-reversible key used in cache file names; safe to keep in plain prefs. */
    public static String cacheKeyFor(String username) {
        return stableHash(username);
    }

    public List<XtreamMediaSection> read(String username, XtreamContentType type) {
        return readByKey(stableHash(username), type);
    }

    /** Same as {@link #read} but takes a precomputed cache key, so the (slow) encrypted
     *  session store doesn't have to be opened first. */
    public List<XtreamMediaSection> readByKey(String cacheKey, XtreamContentType type) {
        File file = new File(cacheDir, prefix(type) + cacheKey + EXTENSION);
        if (!file.isFile()) {
            return new ArrayList<>();
        }
        // Stream-parse straight from the file: for a 20k-item catalog this avoids a
        // multi-megabyte String plus a full org.json tree, which dominated boot time.
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new BufferedInputStream(new FileInputStream(file), 64 * 1024), StandardCharsets.UTF_8))) {
            return sectionsFromJson(type, reader);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    /** Drops every cached catalog -- used when the server changes, so another server's
     *  catalog never shows up under the same username. */
    public void clearAll() {
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(LIVE_PREFIX) || name.startsWith(MOVIE_PREFIX) || name.startsWith(SERIES_PREFIX)) {
                file.delete();
            }
        }
    }

    public void writeLive(String username, List<XtreamMediaSection> sections) {
        write(username, XtreamContentType.LIVE, sections);
    }

    public void write(String username, XtreamContentType type, List<XtreamMediaSection> sections) {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return;
        }
        File file = cacheFile(username, type);
        File temp = new File(cacheDir, file.getName() + ".tmp");
        if (!writeTo(temp, sections)) {
            temp.delete();
            return;
        }
        if (!temp.renameTo(file)) {
            writeTo(file, sections);
            temp.delete();
        }
    }

    private static boolean writeTo(File target, List<XtreamMediaSection> sections) {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(
                new BufferedOutputStream(new FileOutputStream(target), 64 * 1024), StandardCharsets.UTF_8))) {
            writeSections(writer, sections);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    String liveFileNameForTesting(String username) {
        return fileNameForTesting(username, XtreamContentType.LIVE);
    }

    String fileNameForTesting(String username, XtreamContentType type) {
        return prefix(type) + stableHash(username) + EXTENSION;
    }

    private File cacheFile(String username, XtreamContentType type) {
        return new File(cacheDir, fileNameForTesting(username, type));
    }

    private static String prefix(XtreamContentType type) {
        if (type == XtreamContentType.MOVIE) {
            return MOVIE_PREFIX;
        }
        if (type == XtreamContentType.SERIES) {
            return SERIES_PREFIX;
        }
        return LIVE_PREFIX;
    }

    private static void writeSections(JsonWriter writer, List<XtreamMediaSection> sections) throws IOException {
        writer.beginObject();
        writer.name("sections");
        writer.beginArray();
        for (XtreamMediaSection section : sections) {
            writer.beginObject();
            writer.name("title").value(section.title());
            writer.name("items");
            writer.beginArray();
            for (XtreamMediaItem item : section.items()) {
                writer.beginObject();
                writer.name("title").value(item.title());
                writer.name("id").value(item.id());
                writer.name("categoryId").value(item.categoryId());
                writer.name("imageUrl").value(item.imageUrl());
                writer.name("detail").value(item.detail());
                writer.name("ext").value(item.containerExtension());
                writer.name("rating").value(item.rating());
                writer.name("added").value(item.addedAt());
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
        }
        writer.endArray();
        writer.endObject();
    }

    private static List<XtreamMediaSection> sectionsFromJson(XtreamContentType type, JsonReader reader) throws IOException {
        List<XtreamMediaSection> sections = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            if ("sections".equals(reader.nextName())) {
                reader.beginArray();
                while (reader.hasNext()) {
                    XtreamMediaSection section = sectionFromJson(type, reader);
                    if (section != null) {
                        sections.add(section);
                    }
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return sections;
    }

    private static XtreamMediaSection sectionFromJson(XtreamContentType type, JsonReader reader) throws IOException {
        String title = "Uncategorized";
        List<XtreamMediaItem> items = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if ("title".equals(field)) {
                title = reader.nextString();
            } else if ("items".equals(field)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    XtreamMediaItem item = itemFromJson(type, reader);
                    if (item != null) {
                        items.add(item);
                    }
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (items.isEmpty()) {
            return null;
        }
        return new XtreamMediaSection(title, items);
    }

    private static XtreamMediaItem itemFromJson(XtreamContentType type, JsonReader reader) throws IOException {
        String title = "Untitled";
        int id = -1;
        String categoryId = "";
        String imageUrl = "";
        String detail = "";
        String extension = "ts";
        String rating = "";
        long addedAt = 0L;
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if ("title".equals(field)) {
                title = reader.nextString();
            } else if ("id".equals(field)) {
                id = reader.nextInt();
            } else if ("categoryId".equals(field)) {
                categoryId = reader.nextString();
            } else if ("imageUrl".equals(field)) {
                imageUrl = reader.nextString();
            } else if ("detail".equals(field)) {
                detail = reader.nextString();
            } else if ("ext".equals(field)) {
                extension = reader.nextString();
            } else if ("rating".equals(field)) {
                rating = reader.nextString();
            } else if ("added".equals(field)) {
                addedAt = reader.nextLong();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (id <= 0) {
            return null;
        }
        return new XtreamMediaItem(type, title, id, categoryId, imageUrl, detail, extension, rating, addedAt);
    }

    private static String stableHash(String username) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((username == null ? "" : username).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                int unsigned = value & 0xff;
                builder.append(HEX[unsigned >>> 4]);
                builder.append(HEX[unsigned & 0x0f]);
            }
            return builder.toString();
        } catch (Exception exception) {
            return "unknown";
        }
    }
}
