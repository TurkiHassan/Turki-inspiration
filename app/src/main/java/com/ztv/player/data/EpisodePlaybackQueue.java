package com.ztv.player.data;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EpisodePlaybackQueue {
    private static final String FIELD_SEPARATOR = "|";
    private static final String ITEM_SEPARATOR = "\n";

    private EpisodePlaybackQueue() {
    }

    public static String encode(List<Item> queue) {
        if (queue == null || queue.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Item item : queue) {
            if (item == null || item.mediaItem() == null || item.streamUrl() == null || item.streamUrl().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(ITEM_SEPARATOR);
            }
            XtreamMediaItem mediaItem = item.mediaItem();
            builder.append(escape(item.streamUrl())).append(FIELD_SEPARATOR)
                    .append(escape(mediaItem.title())).append(FIELD_SEPARATOR)
                    .append(mediaItem.id()).append(FIELD_SEPARATOR)
                    .append(escape(mediaItem.categoryId())).append(FIELD_SEPARATOR)
                    .append(escape(mediaItem.imageUrl())).append(FIELD_SEPARATOR)
                    .append(escape(mediaItem.detail())).append(FIELD_SEPARATOR)
                    .append(escape(mediaItem.containerExtension()));
        }
        return builder.toString();
    }

    public static List<Item> decode(String encodedQueue) {
        if (encodedQueue == null || encodedQueue.isEmpty()) {
            return Collections.emptyList();
        }
        String[] lines = encodedQueue.split(ITEM_SEPARATOR);
        List<Item> items = new ArrayList<>();
        try {
            for (String line : lines) {
                String[] fields = line.split("\\" + FIELD_SEPARATOR, -1);
                if (fields.length != 7) {
                    return Collections.emptyList();
                }
                XtreamMediaItem mediaItem = new XtreamMediaItem(
                        XtreamContentType.EPISODE,
                        unescape(fields[1]),
                        Integer.parseInt(fields[2]),
                        unescape(fields[3]),
                        unescape(fields[4]),
                        unescape(fields[5]),
                        unescape(fields[6])
                );
                items.add(new Item(unescape(fields[0]), mediaItem));
            }
        } catch (NumberFormatException exception) {
            return Collections.emptyList();
        }
        return items;
    }

    private static String escape(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String unescape(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static final class Item {
        private final String streamUrl;
        private final XtreamMediaItem mediaItem;

        public Item(String streamUrl, XtreamMediaItem mediaItem) {
            this.streamUrl = streamUrl == null ? "" : streamUrl;
            this.mediaItem = mediaItem;
        }

        public String streamUrl() {
            return streamUrl;
        }

        public XtreamMediaItem mediaItem() {
            return mediaItem;
        }
    }
}
