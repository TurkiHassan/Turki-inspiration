package com.ztv.player.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XtreamMediaSection {
    private final String title;
    private final List<XtreamMediaItem> items;

    public XtreamMediaSection(String title, List<XtreamMediaItem> items) {
        this.title = title == null || title.trim().isEmpty() ? "Uncategorized" : title.trim();
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public String title() {
        return title;
    }

    public List<XtreamMediaItem> items() {
        return items;
    }
}
