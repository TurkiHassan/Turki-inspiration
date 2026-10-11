package com.ztv.player.data;

public final class XtreamMediaItem {
    private final XtreamContentType type;
    private final String title;
    private final int id;
    private final String categoryId;
    private final String imageUrl;
    private final String detail;
    private final String containerExtension;
    /** Provider rating as sent by the panel ("8.2"); empty when the panel sends none. */
    private final String rating;
    /** Unix seconds the panel added the title (`added` / series `last_modified`); 0 = unknown. */
    private final long addedAt;

    public XtreamMediaItem(
            XtreamContentType type,
            String title,
            int id,
            String categoryId,
            String imageUrl,
            String detail,
            String containerExtension
    ) {
        this(type, title, id, categoryId, imageUrl, detail, containerExtension, "", 0L);
    }

    public XtreamMediaItem(
            XtreamContentType type,
            String title,
            int id,
            String categoryId,
            String imageUrl,
            String detail,
            String containerExtension,
            String rating,
            long addedAt
    ) {
        this.type = type;
        this.title = title == null || title.trim().isEmpty() ? "Untitled" : title.trim();
        this.id = id;
        this.categoryId = categoryId == null ? "" : categoryId;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.detail = detail == null ? "" : detail;
        this.containerExtension = containerExtension == null || containerExtension.trim().isEmpty()
                ? "mp4"
                : containerExtension.trim();
        this.rating = rating == null ? "" : rating.trim();
        this.addedAt = Math.max(0L, addedAt);
    }

    public XtreamContentType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public int id() {
        return id;
    }

    public String categoryId() {
        return categoryId;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public String detail() {
        return detail;
    }

    public String containerExtension() {
        return containerExtension;
    }

    public String rating() {
        return rating;
    }

    public long addedAt() {
        return addedAt;
    }
}
