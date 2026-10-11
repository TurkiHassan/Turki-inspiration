package com.ztv.player.data;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class XtreamUrlFactory {
    private final String host;

    public XtreamUrlFactory(String host) {
        this.host = normalizeHost(host);
    }

    public String host() {
        return host;
    }

    public String authUrl(String username, String password) {
        return host + "/player_api.php?username=" + encodeQuery(username)
                + "&password=" + encodeQuery(password);
    }

    public String playerApiUrl(String username, String password, String action) {
        String url = authUrl(username, password);
        if (action == null || action.trim().isEmpty()) {
            return url;
        }
        return url + "&action=" + encodeQuery(action.trim());
    }

    public String seriesInfoUrl(String username, String password, int seriesId) {
        return authUrl(username, password) + "&action=get_series_info&series_id=" + seriesId;
    }

    public String vodInfoUrl(String username, String password, int vodId) {
        return authUrl(username, password) + "&action=get_vod_info&vod_id=" + vodId;
    }

    public String shortEpgUrl(String username, String password, int streamId, int limit) {
        return authUrl(username, password) + "&action=get_short_epg&stream_id=" + streamId + "&limit=" + limit;
    }

    public String liveStreamUrl(String username, String password, int streamId) {
        return host + "/live/" + encodePath(username) + "/" + encodePath(password) + "/" + streamId + ".ts";
    }

    public String movieStreamUrl(String username, String password, int streamId, String extension) {
        return host + "/movie/" + encodePath(username) + "/" + encodePath(password)
                + "/" + streamId + "." + safeExtension(extension);
    }

    public String seriesStreamUrl(String username, String password, int streamId, String extension) {
        return host + "/series/" + encodePath(username) + "/" + encodePath(password)
                + "/" + streamId + "." + safeExtension(extension);
    }

    public static String normalizeHost(String rawHost) {
        String value = rawHost == null ? "" : rawHost.trim();
        if (!value.isEmpty() && !value.matches("(?i)^https?://.*")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String encodeQuery(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 is not available", exception);
        }
    }

    private static String encodePath(String value) {
        return encodeQuery(value).replace("+", "%20");
    }

    private static String safeExtension(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return "mp4";
        }
        return extension.trim().replace(".", "");
    }
}
