package com.ztv.player.data;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class XtreamMediaParserTest {
    @Test
    public void parseCategories_preservesServerOrder() throws Exception {
        String json = "["
                + "{\"category_id\":\"20\",\"category_name\":\"Movies\"},"
                + "{\"category_id\":\"10\",\"category_name\":\"Sports\"}"
                + "]";

        List<XtreamCategory> categories = XtreamMediaParser.parseCategories(json);

        assertEquals("20", categories.get(0).id());
        assertEquals("Movies", categories.get(0).name());
        assertEquals("10", categories.get(1).id());
        assertEquals("Sports", categories.get(1).name());
    }

    @Test
    public void buildSections_usesCategoryOrderThenItemOrder() throws Exception {
        List<XtreamCategory> categories = XtreamMediaParser.parseCategories("["
                + "{\"category_id\":\"2\",\"category_name\":\"Second\"},"
                + "{\"category_id\":\"1\",\"category_name\":\"First\"}"
                + "]");
        List<XtreamMediaItem> items = XtreamMediaParser.parseLiveItems("["
                + "{\"name\":\"A\",\"stream_id\":100,\"category_id\":\"1\"},"
                + "{\"name\":\"B\",\"stream_id\":200,\"category_id\":\"2\"},"
                + "{\"name\":\"C\",\"stream_id\":300,\"category_id\":\"2\"}"
                + "]");

        List<XtreamMediaSection> sections = XtreamMediaParser.buildSections(categories, items);

        assertEquals("Second", sections.get(0).title());
        assertEquals("B", sections.get(0).items().get(0).title());
        assertEquals("C", sections.get(0).items().get(1).title());
        assertEquals("First", sections.get(1).title());
        assertEquals("A", sections.get(1).items().get(0).title());
    }

    @Test
    public void parseMoviesAndSeries_keepXtreamIdentifiers() throws Exception {
        List<XtreamMediaItem> movies = XtreamMediaParser.parseMovieItems("["
                + "{\"name\":\"Movie One\",\"stream_id\":\"55\",\"category_id\":\"8\",\"container_extension\":\"mkv\"}"
                + "]");
        List<XtreamMediaItem> series = XtreamMediaParser.parseSeriesItems("["
                + "{\"name\":\"Series One\",\"series_id\":77,\"category_id\":\"9\"}"
                + "]");

        assertEquals(XtreamContentType.MOVIE, movies.get(0).type());
        assertEquals(55, movies.get(0).id());
        assertEquals("mkv", movies.get(0).containerExtension());
        assertEquals(XtreamContentType.SERIES, series.get(0).type());
        assertEquals(77, series.get(0).id());
    }

    @Test
    public void parseItems_skipsUnknownNestedFieldsAndCoercesTypes() throws Exception {
        // Real panels bury the six fields we need among ~25 others, including nested
        // objects/arrays; ids and category ids arrive as numbers or strings.
        String json = "["
                + "{\"num\":1,\"name\":\"Movie A\",\"stream_id\":\"901\",\"category_id\":7,"
                + "\"backdrop_path\":[\"https://img/b1.jpg\",\"https://img/b2.jpg\"],"
                + "\"meta\":{\"nested\":{\"deep\":[1,2,3]}},"
                + "\"stream_icon\":null,\"rating\":5.5,\"added\":\"1700000000\"},"
                + "{\"name\":\"No Id\",\"category_id\":\"7\"},"
                + "{\"name\":\"Movie B\",\"stream_id\":902,\"category_id\":\"8\",\"stream_icon\":\"https://img/b.png\"}"
                + "]";

        List<XtreamMediaItem> movies = XtreamMediaParser.parseMovieItems(json);

        assertEquals(2, movies.size());
        assertEquals("Movie A", movies.get(0).title());
        assertEquals(901, movies.get(0).id());
        assertEquals("7", movies.get(0).categoryId());
        assertEquals("", movies.get(0).imageUrl());
        assertEquals("Movie B", movies.get(1).title());
        assertEquals("https://img/b.png", movies.get(1).imageUrl());
    }

    @Test
    public void parseItems_nullPayloadYieldsEmptyList() throws Exception {
        assertEquals(0, XtreamMediaParser.parseLiveItems("null").size());
        assertEquals(0, XtreamMediaParser.parseCategories("null").size());
    }

    @Test(expected = java.io.IOException.class)
    public void parseItems_objectPayloadThrows() throws Exception {
        // An auth-error object instead of the catalog array must surface as an error,
        // not render as an empty library.
        XtreamMediaParser.parseLiveItems("{\"user_info\":{\"auth\":0}}");
    }

    @Test
    public void parseSeriesEpisodes_ordersBySeasonThenEpisodeArrayOrder() throws Exception {
        String json = "{"
                + "\"episodes\":{"
                + "\"2\":[{\"id\":\"220\",\"title\":\"S2 E1\",\"container_extension\":\"mkv\"}],"
                + "\"1\":[{\"id\":110,\"title\":\"S1 E1\"},{\"id\":111,\"title\":\"S1 E2\",\"container_extension\":\"mp4\"}]"
                + "}"
                + "}";

        List<XtreamMediaItem> episodes = XtreamMediaParser.parseSeriesEpisodes(json);

        assertEquals(3, episodes.size());
        assertEquals("S1 E1", episodes.get(0).title());
        assertEquals(110, episodes.get(0).id());
        assertEquals("mp4", episodes.get(0).containerExtension());
        assertEquals("S1 E2", episodes.get(1).title());
        assertEquals("S2 E1", episodes.get(2).title());
        assertEquals("mkv", episodes.get(2).containerExtension());
    }

    @Test
    public void parseItems_readsRatingAndAddedStamp() throws Exception {
        List<XtreamMediaItem> movies = XtreamMediaParser.parseMovieItems("["
                + "{\"name\":\"Rated\",\"stream_id\":1,\"rating\":\"8\",\"added\":\"1712000000\"},"
                + "{\"name\":\"Zero\",\"stream_id\":2,\"rating\":0,\"added\":null},"
                + "{\"name\":\"Text\",\"stream_id\":3,\"rating\":\"N/A\"}"
                + "]");
        List<XtreamMediaItem> series = XtreamMediaParser.parseSeriesItems("["
                + "{\"name\":\"Show\",\"series_id\":9,\"rating\":7.25,\"last_modified\":\"1700000000\"}"
                + "]");

        assertEquals("8.0", movies.get(0).rating());
        assertEquals(1712000000L, movies.get(0).addedAt());
        assertEquals("", movies.get(1).rating());
        assertEquals(0L, movies.get(1).addedAt());
        assertEquals("", movies.get(2).rating());
        assertEquals("7.3", series.get(0).rating());
        assertEquals(1700000000L, series.get(0).addedAt());
    }

    @Test
    public void parseSeriesInfo_readsShowMetadataAndBackdrop() throws Exception {
        XtreamMovieInfo info = XtreamMediaParser.parseSeriesInfo("{\"info\":{"
                + "\"plot\":\"A crew patrols the rooftops.\",\"cover\":\"https://img/c.jpg\","
                + "\"genre\":\"Drama\",\"releaseDate\":\"2025-01-02\",\"rating\":\"8.5\","
                + "\"episode_run_time\":\"44\",\"cast\":\"A, B\",\"director\":\"Ari Vale\","
                + "\"backdrop_path\":[\"https://img/b.jpg\"]},\"episodes\":{}}");

        assertEquals("A crew patrols the rooftops.", info.plot());
        assertEquals("https://img/c.jpg", info.poster());
        assertEquals("44 min/ep", info.duration());
        assertEquals("https://img/b.jpg", info.backdrop());
        assertEquals("Ari Vale", info.director());
    }

    @Test
    public void parseMovieInfo_acceptsStringBackdrop() throws Exception {
        XtreamMovieInfo info = XtreamMediaParser.parseMovieInfo("{\"info\":{\"backdrop_path\":\"https://img/w.jpg\"}}");

        assertEquals("https://img/w.jpg", info.backdrop());
    }
}
