package com.ztv.player.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of `get_series_info`: the show's metadata plus its episodes in season order. */
public final class XtreamSeriesDetail {
    private final XtreamMovieInfo info;
    private final List<XtreamMediaItem> episodes;

    public XtreamSeriesDetail(XtreamMovieInfo info, List<XtreamMediaItem> episodes) {
        this.info = info;
        this.episodes = Collections.unmodifiableList(new ArrayList<>(episodes));
    }

    public XtreamMovieInfo info() {
        return info;
    }

    public List<XtreamMediaItem> episodes() {
        return episodes;
    }
}
