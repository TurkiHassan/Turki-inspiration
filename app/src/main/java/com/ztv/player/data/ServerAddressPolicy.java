package com.ztv.player.data;

import java.net.URI;
import java.util.Locale;

/** Turns whatever a viewer types or pastes into the Server field into a clean Xtream base URL. */
public final class ServerAddressPolicy {
    private ServerAddressPolicy() {
    }

    /**
     * "example.com:8080", "http://example.com:8080/", or a pasted
     * "http://example.com:8080/get.php?username=..." all become "http://example.com:8080".
     * Returns "" when the input is not a usable server address.
     */
    public static String normalize(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty() || value.contains(" ")) {
            return "";
        }
        if (value.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*") && !value.matches("(?i)^https?://.*")) {
            return ""; // some other scheme (ftp://, rtmp://...) -- not an Xtream server
        }
        if (!value.matches("(?i)^https?://.*")) {
            value = "http://" + value;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
            String host = uri.getHost();
            if (host == null || host.isEmpty() || !(scheme.equals("http") || scheme.equals("https"))) {
                return "";
            }
            int port = uri.getPort();
            if (port == 0 || port > 65535) {
                return "";
            }
            return scheme + "://" + host.toLowerCase(Locale.US) + (port > 0 ? ":" + port : "");
        } catch (Exception ignored) {
            return "";
        }
    }

    /** What the field shows: the address without the default "http://" prefix. */
    public static String display(String host) {
        String value = host == null ? "" : host.trim();
        return value.replaceFirst("(?i)^http://", "");
    }
}
