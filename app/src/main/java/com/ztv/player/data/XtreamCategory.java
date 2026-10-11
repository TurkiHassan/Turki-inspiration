package com.ztv.player.data;

public final class XtreamCategory {
    private final String id;
    private final String name;

    public XtreamCategory(String id, String name) {
        this.id = id == null ? "" : id;
        this.name = (name == null || name.trim().isEmpty()) ? "Uncategorized" : name.trim();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }
}
