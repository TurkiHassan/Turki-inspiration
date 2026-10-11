package com.ztv.player.data;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class XtreamMediaParser {
    private XtreamMediaParser() {
    }

    public static List<XtreamCategory> parseCategories(String json) throws IOException {
        return parseCategories(new StringReader(json == null ? "[]" : json));
    }

    /** Streaming variant: parses categories directly off the wire, no intermediate String/DOM. */
    public static List<XtreamCategory> parseCategories(Reader source) throws IOException {
        List<XtreamCategory> categories = new ArrayList<>();
        JsonReader reader = newLenientReader(source);
        if (!beginArray(reader)) {
            return categories;
        }
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            String id = "";
            String name = "Uncategorized";
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if ("category_id".equals(field)) {
                    id = nextString(reader, "");
                } else if ("category_name".equals(field)) {
                    name = nextString(reader, "Uncategorized");
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            categories.add(new XtreamCategory(id, name));
        }
        reader.endArray();
        return categories;
    }

    public static List<XtreamMediaItem> parseLiveItems(String json) throws IOException {
        return parseLiveItems(new StringReader(json == null ? "[]" : json));
    }

    public static List<XtreamMediaItem> parseMovieItems(String json) throws IOException {
        return parseMovieItems(new StringReader(json == null ? "[]" : json));
    }

    public static List<XtreamMediaItem> parseSeriesItems(String json) throws IOException {
        return parseSeriesItems(new StringReader(json == null ? "[]" : json));
    }

    public static List<XtreamMediaItem> parseLiveItems(Reader source) throws IOException {
        return parseItems(source, XtreamContentType.LIVE, "stream_id", "stream_icon", "ts");
    }

    public static List<XtreamMediaItem> parseMovieItems(Reader source) throws IOException {
        return parseItems(source, XtreamContentType.MOVIE, "stream_id", "stream_icon", "mp4");
    }

    public static List<XtreamMediaItem> parseSeriesItems(Reader source) throws IOException {
        return parseItems(source, XtreamContentType.SERIES, "series_id", "cover", "mp4");
    }

    public static List<XtreamMediaItem> parseSeriesEpisodes(String json) throws JSONException {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        JSONObject episodesObject = root.optJSONObject("episodes");
        if (episodesObject == null) {
            return new ArrayList<>();
        }

        List<String> seasonKeys = new ArrayList<>();
        JSONArray names = episodesObject.names();
        if (names != null) {
            for (int index = 0; index < names.length(); index++) {
                seasonKeys.add(names.optString(index));
            }
        }
        Collections.sort(seasonKeys, Comparator.comparingInt(XtreamMediaParser::seasonNumber));

        List<XtreamMediaItem> episodes = new ArrayList<>();
        for (String seasonKey : seasonKeys) {
            JSONArray seasonEpisodes = episodesObject.optJSONArray(seasonKey);
            if (seasonEpisodes == null) {
                continue;
            }
            for (int index = 0; index < seasonEpisodes.length(); index++) {
                JSONObject episode = seasonEpisodes.optJSONObject(index);
                if (episode == null) {
                    continue;
                }
                int id = parseId(episode, "id");
                if (id <= 0) {
                    continue;
                }
                String title = episode.optString("title", "Episode " + (index + 1));
                episodes.add(new XtreamMediaItem(
                        XtreamContentType.EPISODE,
                        title,
                        id,
                        seasonKey,
                        episode.optString("info", ""),
                        "Season " + seasonKey,
                        episode.optString("container_extension", "mp4")
                ));
            }
        }
        return episodes;
    }

    public static XtreamMovieInfo parseMovieInfo(String json) throws JSONException {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        JSONObject info = root.optJSONObject("info");
        if (info == null) {
            info = new JSONObject();
        }
        String poster = firstNonEmpty(
                info.optString("movie_image", ""),
                info.optString("cover_big", ""));
        return new XtreamMovieInfo(
                info.optString("plot", info.optString("description", "")),
                poster,
                info.optString("genre", ""),
                info.optString("releasedate", info.optString("release_date", "")),
                info.optString("rating", ""),
                info.optString("duration", ""),
                info.optString("cast", info.optString("actors", "")),
                info.optString("director", ""),
                firstBackdrop(info)
        );
    }

    /** `get_series_info` -> the series' own metadata (same shape the detail hero uses for VOD). */
    public static XtreamMovieInfo parseSeriesInfo(String json) throws JSONException {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        JSONObject info = root.optJSONObject("info");
        if (info == null) {
            info = new JSONObject();
        }
        String runtime = info.optString("episode_run_time", "");
        return new XtreamMovieInfo(
                info.optString("plot", ""),
                info.optString("cover", ""),
                info.optString("genre", ""),
                info.optString("releaseDate", info.optString("release_date", "")),
                info.optString("rating", ""),
                runtime.isEmpty() || "0".equals(runtime) ? "" : runtime + " min/ep",
                info.optString("cast", ""),
                info.optString("director", ""),
                firstBackdrop(info)
        );
    }

    private static String firstBackdrop(JSONObject info) {
        JSONArray backdrops = info.optJSONArray("backdrop_path");
        if (backdrops != null) {
            for (int index = 0; index < backdrops.length(); index++) {
                String url = backdrops.optString(index, "");
                if (!url.trim().isEmpty()) {
                    return url.trim();
                }
            }
            return "";
        }
        return info.optString("backdrop_path", "").trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    public static List<XtreamMediaSection> buildSections(List<XtreamCategory> categories, List<XtreamMediaItem> items) {
        Map<String, List<XtreamMediaItem>> itemsByCategory = new LinkedHashMap<>();
        for (XtreamMediaItem item : items) {
            String key = item.categoryId();
            if (!itemsByCategory.containsKey(key)) {
                itemsByCategory.put(key, new ArrayList<>());
            }
            itemsByCategory.get(key).add(item);
        }

        List<XtreamMediaSection> sections = new ArrayList<>();
        for (XtreamCategory category : categories) {
            List<XtreamMediaItem> categoryItems = itemsByCategory.remove(category.id());
            if (categoryItems != null && !categoryItems.isEmpty()) {
                sections.add(new XtreamMediaSection(category.name(), categoryItems));
            }
        }

        for (Map.Entry<String, List<XtreamMediaItem>> entry : itemsByCategory.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sections.add(new XtreamMediaSection("Uncategorized", entry.getValue()));
            }
        }

        return sections;
    }

    private static List<XtreamMediaItem> parseItems(
            Reader source,
            XtreamContentType type,
            String idField,
            String imageField,
            String defaultExtension
    ) throws IOException {
        List<XtreamMediaItem> items = new ArrayList<>();
        JsonReader reader = newLenientReader(source);
        if (!beginArray(reader)) {
            return items;
        }
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            String name = "Untitled";
            int id = -1;
            String categoryId = "";
            String imageUrl = "";
            String detail = "";
            String extension = defaultExtension;
            String rating = "";
            long addedAt = 0L;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if ("name".equals(field)) {
                    name = nextString(reader, "Untitled");
                } else if (idField.equals(field)) {
                    id = nextId(reader);
                } else if ("category_id".equals(field)) {
                    categoryId = nextString(reader, "");
                } else if (imageField.equals(field)) {
                    imageUrl = nextString(reader, "");
                } else if ("plot".equals(field)) {
                    detail = nextString(reader, "");
                } else if ("container_extension".equals(field)) {
                    extension = nextString(reader, defaultExtension);
                } else if ("rating".equals(field)) {
                    rating = nextString(reader, "");
                } else if ("added".equals(field) || "last_modified".equals(field)) {
                    addedAt = parseEpochSeconds(nextString(reader, ""));
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            if (id <= 0) {
                continue;
            }
            items.add(new XtreamMediaItem(type, name, id, categoryId, imageUrl, detail, extension,
                    normalizeRating(rating), addedAt));
        }
        reader.endArray();
        return items;
    }

    static long parseEpochSeconds(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /** "8.2", "8" -> "8.2", "8.0"; "0", "", "N/A" -> "" (IPTVnator hides a zero rating). */
    static String normalizeRating(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (value <= 0 || Double.isNaN(value)) {
                return "";
            }
            return String.format(java.util.Locale.US, "%.1f", value);
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static JsonReader newLenientReader(Reader source) {
        JsonReader reader = new JsonReader(source);
        // Xtream panels are PHP scripts with occasionally sloppy JSON; be liberal.
        reader.setStrictness(Strictness.LENIENT);
        return reader;
    }

    /** JSON null → empty list; any other non-array payload (auth error object, HTML) → error. */
    private static boolean beginArray(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return false;
        }
        if (token != JsonToken.BEGIN_ARRAY) {
            throw new IOException("Expected a JSON array from server, got " + token);
        }
        reader.beginArray();
        return true;
    }

    /** String-valued field that servers sometimes send as number, boolean, or null. */
    private static String nextString(JsonReader reader, String fallback) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.STRING || token == JsonToken.NUMBER) {
            return reader.nextString();
        }
        if (token == JsonToken.BOOLEAN) {
            return String.valueOf(reader.nextBoolean());
        }
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return fallback;
        }
        reader.skipValue();
        return fallback;
    }

    private static int nextId(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        if (token != JsonToken.NUMBER && token != JsonToken.STRING) {
            reader.skipValue();
            return -1;
        }
        String raw = reader.nextString().trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            try {
                return (int) Double.parseDouble(raw);
            } catch (NumberFormatException alsoIgnored) {
                return -1;
            }
        }
    }

    private static int parseId(JSONObject object, String field) {
        Object rawValue = object.opt(field);
        if (rawValue instanceof Number) {
            return ((Number) rawValue).intValue();
        }
        if (rawValue instanceof String) {
            try {
                return Integer.parseInt((String) rawValue);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static int seasonNumber(String seasonKey) {
        try {
            return Integer.parseInt(seasonKey);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
