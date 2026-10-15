package com.ztv.player.workspace;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.leanback.widget.HorizontalGridView;

import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.data.XtreamMediaSection;
import com.ztv.player.ui.Tr;

import java.util.List;

/** `/xtreams/:id/recently-added`: per-type rails of the newest titles with their added date. */
public final class RecentlyAddedPage extends Page implements Catalog.LibraryListener {
    private static final int LIMIT = 30;
    private final Context context;
    private LinearLayout rails;
    private View firstFocus;

    public RecentlyAddedPage(WorkspaceActivity host) {
        super(host);
        this.context = host;
    }

    @Override
    public Section section() {
        return Section.RECENTLY_ADDED;
    }

    @Override
    public String searchScope() {
        return Tr.t("Recently added", "أُضيف حديثًا");
    }

    @Override
    public String searchPlaceholder() {
        return Tr.t("Filter this section...", "تصفية هذا القسم...");
    }

    @Override
    protected View buildContent() {
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        LinearLayout column = Nator.column(context);
        column.setClipChildren(false);
        Nator.pad(column, 28, 24, 28, 28);
        column.addView(Nator.text(context, Tr.t("Recently added", "أُضيف حديثًا"), 24, Nator.W600, Nator.HEADING));
        rails = Nator.column(context);
        rails.setClipChildren(false);
        column.addView(rails, Nator.matchWidth());
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        render();
        return scroll;
    }

    @Override
    public void onShown() {
        host.catalog.addListener(this);
        for (XtreamContentType type : new XtreamContentType[]{XtreamContentType.MOVIE, XtreamContentType.LIVE, XtreamContentType.SERIES}) {
            if (!host.catalog.isLoaded(type)) {
                host.catalog.load(type, false, this);
            }
        }
    }

    @Override
    public void onHidden() {
        host.catalog.removeListener(this);
    }

    @Override
    public void onLibrary(XtreamContentType type, List<XtreamMediaSection> sections, boolean fromNetwork) {
        if (rails != null && !rails.hasFocus()) {
            render();
        }
    }

    @Override
    public void onLibraryError(XtreamContentType type, Exception error) {
    }

    @Override
    public void requestInitialFocus() {
        if (firstFocus != null) {
            firstFocus.requestFocus();
        }
    }

    private void render() {
        rails.removeAllViews();
        firstFocus = null;
        addRail(Tr.t("Movies", "الأفلام"), XtreamContentType.MOVIE, Tr.t("Browse all movies", "تصفح كل الأفلام"), Section.VOD);
        addRail(Tr.t("Live TV", "القنوات"), XtreamContentType.LIVE, Tr.t("Browse all live TV", "تصفح كل القنوات"), Section.LIVE);
        addRail(Tr.t("Series", "المسلسلات"), XtreamContentType.SERIES, Tr.t("Browse all series", "تصفح كل المسلسلات"), Section.SERIES);
        if (rails.getChildCount() == 0) {
            rails.addView(Widgets.emptyState(context, "new_releases", Tr.t("Nothing new yet", "لا جديد بعد"),
                    Tr.t("Recently added titles appear here once the playlist has loaded.", "تظهر هنا العناوين المضافة حديثًا بعد تحميل الاشتراك.")), Nator.margins(context, Nator.matchWidth(), 0, 80, 0, 0));
        }
    }

    private void addRail(String label, XtreamContentType type, String browse, Section target) {
        List<XtreamMediaItem> items = Catalog.latest(Catalog.allItems(host.catalog.cached(type)), LIMIT);
        if (items.isEmpty()) {
            return;
        }
        LinearLayout section = Nator.column(context);
        section.setClipChildren(false);
        section.addView(Widgets.railHeader(context, label, items.size(), browse, () -> host.openSection(target)),
                Nator.margins(context, Nator.matchWidth(), 3, 14, 0, 14));
        HorizontalGridView rail = Widgets.horizontalRail(context, 16);
        PosterGridAdapter adapter = new PosterGridAdapter(context, PosterGridAdapter.Style.CONTENT, Nator.px(context, 172),
                (item, position) -> {
                    if (item.type() == XtreamContentType.LIVE) {
                        host.play(item, null);
                    } else {
                        host.push(new DetailPage(host, item));
                    }
                });
        adapter.setItems(items);
        rail.setAdapter(adapter);
        int artHeight = type == XtreamContentType.LIVE ? Math.round(172 * 89f / 159f) : 258;
        section.addView(rail, new LinearLayout.LayoutParams(-1, Nator.px(context, artHeight + 70)));
        rails.addView(section, Nator.margins(context, Nator.matchWidth(), 0, 18, 0, 0));
        if (firstFocus == null) {
            firstFocus = rail;
        }
    }
}
