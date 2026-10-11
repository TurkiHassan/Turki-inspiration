package com.ztv.player.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XtreamUrlFactoryTest {
    @Test
    public void playerApiUrl_usesFixedHostAndEncodesCredentials() {
        XtreamUrlFactory factory = new XtreamUrlFactory("http://example.com:8080");

        String url = factory.playerApiUrl("ali@example.com", "p@ss 123", "get_live_streams");

        assertEquals(
                "http://example.com:8080/player_api.php?username=ali%40example.com&password=p%40ss+123&action=get_live_streams",
                url
        );
    }

    @Test
    public void authUrl_omitsActionForAuthenticationRequest() {
        XtreamUrlFactory factory = new XtreamUrlFactory("http://example.com:8080/");

        String url = factory.authUrl("demo", "secret");

        assertEquals(
                "http://example.com:8080/player_api.php?username=demo&password=secret",
                url
        );
    }

    @Test
    public void liveStreamUrl_buildsDirectTransportStreamUrl() {
        XtreamUrlFactory factory = new XtreamUrlFactory("http://example.com:8080");

        String url = factory.liveStreamUrl("demo", "secret", 42);

        assertEquals("http://example.com:8080/live/demo/secret/42.ts", url);
    }

    @Test
    public void movieAndSeriesStreamUrls_buildDirectPlaybackUrls() {
        XtreamUrlFactory factory = new XtreamUrlFactory("http://example.com:8080");

        assertEquals("http://example.com:8080/movie/demo/secret/55.mkv", factory.movieStreamUrl("demo", "secret", 55, "mkv"));
        assertEquals("http://example.com:8080/series/demo/secret/77.mp4", factory.seriesStreamUrl("demo", "secret", 77, "mp4"));
    }

    @Test
    public void normalizeHost_prependsHttpSchemeWhenMissing() {
        XtreamUrlFactory factory = new XtreamUrlFactory("example.com:8080");

        assertEquals("http://example.com:8080", factory.host());
        assertEquals(
                "http://example.com:8080/player_api.php?username=demo&password=secret",
                factory.authUrl("demo", "secret")
        );
    }

    @Test
    public void normalizeHost_keepsExplicitHttpsScheme() {
        XtreamUrlFactory factory = new XtreamUrlFactory("https://secure.example.com:8080/");

        assertEquals("https://secure.example.com:8080", factory.host());
    }

    @Test
    public void seriesInfoUrl_keepsSeriesIdAsSeparateQueryParameter() {
        XtreamUrlFactory factory = new XtreamUrlFactory("http://example.com:8080");

        assertEquals(
                "http://example.com:8080/player_api.php?username=demo&password=secret&action=get_series_info&series_id=77",
                factory.seriesInfoUrl("demo", "secret", 77)
        );
    }
}
