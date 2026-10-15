package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.VerticalGridView;

import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.data.XtreamMediaSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * `/xtreams/:id/search`: type checkboxes (Live TV, Movies, Series) + "Visible categories
 * only", the "Search for content" empty state and the mixed result grid.
 */
public final class SearchPage extends Page implements Catalog.LibraryListener {
    private static final int MAX_RESULTS = 300;
    private static final int MIN_TYPED = 3;
    private static final int MIN_SPOKEN = 2;
    private final Context context;
    private final Map<XtreamContentType, Boolean> enabled = new EnumMap<>(XtreamContentType.class);
    private boolean visibleOnly;
    private String query = "";
    private boolean spoken;
    private final List<String> heard = new ArrayList<>();
    private LinearLayout heardRow;
    private VerticalGridView grid;
    private PosterGridAdapter adapter;
    private View empty;
    private TextView emptyTitle;
    private TextView emptyBody;
    private TextView resultCount;

    public SearchPage(WorkspaceActivity host) {
        super(host);
        this.context = host;
        enabled.put(XtreamContentType.LIVE, true);
        enabled.put(XtreamContentType.MOVIE, true);
        enabled.put(XtreamContentType.SERIES, true);
    }

    void presetQuery(String value) {
        query = value;
    }

    /**
     * Recognition alternatives, best guess first. Searches the one that matches the catalog
     * best and offers the others as "Also heard" chips. Returns the phrase it searched.
     */
    String applySpoken(List<String> alternatives) {
        heard.clear();
        for (String alternative : alternatives) {
            String value = alternative == null ? "" : alternative.trim();
            boolean duplicate = false;
            for (String existing : heard) {
                duplicate |= SearchMatcher.fold(existing).equals(SearchMatcher.fold(value));
            }
            if (SearchMatcher.of(value).length() >= MIN_SPOKEN && !duplicate) {
                heard.add(value);
            }
        }
        query = pickSpoken(heard);
        spoken = true;
        render();
        return query;
    }

    /** Lowest (best) match rank wins; ties keep the recognizer's own order. */
    private String pickSpoken(List<String> candidates) {
        String best = candidates.isEmpty() ? "" : candidates.get(0);
        int bestScore = Integer.MAX_VALUE;
        List<XtreamMediaItem> items = scopedItems();
        for (String candidate : candidates) {
            SearchMatcher matcher = SearchMatcher.of(candidate);
            int score = Integer.MAX_VALUE;
            for (XtreamMediaItem item : items) {
                int itemScore = matcher.score(item.title());
                if (itemScore != SearchMatcher.NO_MATCH && itemScore < score) {
                    score = itemScore;
                    if (score == 0) {
                        break;
                    }
                }
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private void chooseHeard(String value) {
        query = value;
        spoken = true;
        host.showSearchText(value);
        render();
        if (adapter.getItemCount() > 0) {
            grid.requestFocus();
        } else if (heardRow.getChildCount() > 1) {
            heardRow.getChildAt(1).requestFocus();
        }
    }

    @Override
    public Section section() {
        return Section.SEARCH;
    }

    @Override
    public String searchScope() {
        return "Advanced search";
    }

    @Override
    public String searchPlaceholder() {
        return "Search in this playlist...";
    }

    @Override
    public String searchQuery() {
        return query;
    }

    @Override
    public void onSearch(String value) {
        query = value == null ? "" : value.trim();
        spoken = false;
        heard.clear();
        render();
    }

    @Override
    public void onShown() {
        host.catalog.addListener(this);
        for (XtreamContentType type : enabled.keySet()) {
            if (!host.catalog.isLoaded(type)) {
                host.catalog.load(type, false, this);
            }
        }
        render();
    }

    @Override
    public void onHidden() {
        host.catalog.removeListener(this);
    }

    @Override
    public void onLibrary(XtreamContentType type, List<XtreamMediaSection> sections, boolean fromNetwork) {
        if (grid != null && !grid.hasFocus()) {
            render();
        }
    }

    @Override
    public void onLibraryError(XtreamContentType type, Exception error) {
    }

    @Override
    public void requestInitialFocus() {
        if (adapter != null && adapter.getItemCount() > 0) {
            grid.requestFocus();
        } else {
            host.focusSearch();
        }
    }

    @Override
    protected View buildContent() {
        LinearLayout column = Nator.column(context);
        LinearLayout header = Nator.row(context);
        Nator.pad(header, 20, 0, 20, 0);
        header.addView(Nator.text(context, "Search", 19.2f, Nator.W600, Nator.ON_SURFACE), Nator.margins(context, Nator.wrap(), 0, 0, 40, 0));
        header.addView(checkbox("Live TV", XtreamContentType.LIVE), Nator.margins(context, Nator.wrap(), 0, 0, 24, 0));
        header.addView(checkbox("Movies", XtreamContentType.MOVIE), Nator.margins(context, Nator.wrap(), 0, 0, 24, 0));
        header.addView(checkbox("Series", XtreamContentType.SERIES), Nator.margins(context, Nator.wrap(), 0, 0, 24, 0));
        resultCount = Nator.countChip(context, "", Nator.BODY);
        resultCount.setVisibility(View.GONE);
        header.addView(resultCount);
        header.addView(Nator.spacer(context));
        header.addView(checkbox("Visible categories only", null));
        column.addView(header, new LinearLayout.LayoutParams(-1, Nator.px(context, 72)));
        heardRow = Nator.row(context);
        Nator.pad(heardRow, 20, 0, 20, 12);
        heardRow.setVisibility(View.GONE);
        column.addView(heardRow, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout body = new FrameLayout(context);
        body.setClipChildren(false);
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        int available = screen - Nator.px(context, 60) - Nator.px(context, 40);
        int gap = Nator.px(context, 16);
        int min = Nator.px(context, 148);
        int columns = Math.max(1, (available + gap) / (min + gap));
        int width = (available - gap * (columns - 1)) / columns;
        adapter = new PosterGridAdapter(context, PosterGridAdapter.Style.CONTENT, width, (item, position) -> {
            if (item.type() == XtreamContentType.LIVE) {
                host.play(item, null);
            } else {
                host.push(new DetailPage(host, item));
            }
        });
        grid = Widgets.verticalGrid(context, columns, 16, 16);
        grid.setAdapter(adapter);
        Nator.pad(grid, 20, 4, 20, 24);
        Widgets.enablePartialRowDown(grid, columns);
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout emptyBox = Widgets.emptyState(context, "search", "Search for content",
                emptyHint());
        emptyTitle = (TextView) emptyBox.getChildAt(1);
        emptyBody = (TextView) emptyBox.getChildAt(2);
        empty = emptyBox;
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        emptyParams.topMargin = Nator.px(context, 100);
        body.addView(empty, emptyParams);
        column.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        return column;
    }

    /** mat-checkbox: 18px box, #abc7ff when checked, label 14px/400. */
    private View checkbox(String label, XtreamContentType type) {
        LinearLayout row = Nator.row(context);
        Nator.pad(row, 6, 6, 8, 6);
        Nator.focusable(row, 8);
        row.setBackground(Nator.states(new GradientDrawable(), Nator.rounded(context, Nator.HOVER_OVERLAY, 8)));
        TextView box = Nator.icon(context, "check", 14, 0xFF002F65);
        row.addView(box, Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 10, 0));
        row.addView(Nator.text(context, label, 14, Nator.W400, Nator.ON_SURFACE));
        Runnable style = () -> {
            boolean on = type == null ? visibleOnly : Boolean.TRUE.equals(enabled.get(type));
            box.setBackground(on ? Nator.rounded(context, 0xFFABC7FF, 2) : Nator.roundedStroke(context, 0, 2, 2, Nator.ON_SURFACE_VARIANT));
            box.setText(on ? Nator.glyph("check") : "");
        };
        style.run();
        row.setOnClickListener(v -> {
            if (type == null) {
                visibleOnly = !visibleOnly;
            } else {
                enabled.put(type, !Boolean.TRUE.equals(enabled.get(type)));
            }
            style.run();
            render();
        });
        return row;
    }

    private String emptyHint() {
        return host.voiceSearchAvailable()
                ? "Type at least 3 characters, or press the mic and say a channel, movie or series."
                : "Enter at least 3 characters to search across live TV, movies and series.";
    }

    private void renderHeard() {
        heardRow.removeAllViews();
        List<String> others = new ArrayList<>();
        for (String alternative : heard) {
            if (spoken && !alternative.equals(query) && others.size() < 4
                    && !search(SearchMatcher.of(alternative), 1).isEmpty()) {
                others.add(alternative);
            }
        }
        if (others.isEmpty()) {
            heardRow.setVisibility(View.GONE);
            return;
        }
        heardRow.addView(Nator.text(context, "Also heard", 13, Nator.W500, Nator.BODY), Nator.margins(context, Nator.wrap(), 0, 0, 14, 0));
        for (String alternative : others) {
            TextView chip = Nator.text(context, alternative, 13.5f, Nator.W500, Nator.ON_SURFACE);
            chip.setGravity(Gravity.CENTER);
            Nator.pad(chip, 14, 0, 14, 0);
            Nator.focusable(chip, 999);
            chip.setBackground(Nator.states(Nator.roundedStroke(context, Nator.CHIP_BG, 999, 1, Nator.OUTLINE_VARIANT),
                    Nator.roundedStroke(context, Nator.SELECTION_SURFACE, 999, 1, Nator.SELECTION_BORDER)));
            chip.setOnClickListener(v -> chooseHeard(alternative));
            heardRow.addView(chip, Nator.margins(context, Nator.size(context, -2, 32), 0, 0, 10, 0));
        }
        heardRow.setVisibility(View.VISIBLE);
    }

    private void render() {
        if (adapter == null) {
            return;
        }
        renderHeard();
        SearchMatcher matcher = SearchMatcher.of(query);
        if (matcher.length() < (spoken ? MIN_SPOKEN : MIN_TYPED)) {
            adapter.setItems(new ArrayList<>());
            resultCount.setVisibility(View.GONE);
            emptyTitle.setText("Search for content");
            emptyBody.setText(emptyHint());
            empty.setVisibility(View.VISIBLE);
            return;
        }
        List<XtreamMediaItem> results = search(matcher, MAX_RESULTS);
        adapter.setItems(results);
        resultCount.setText(Nator.plural(results.size(), "result", "results"));
        resultCount.setVisibility(View.VISIBLE);
        if (results.isEmpty()) {
            emptyTitle.setText("No results");
            emptyBody.setText("Nothing matches \"" + query + "\". Try another title or enable more types.");
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    /** Best-ranked matches across the enabled types; ties keep the catalog order. */
    private List<XtreamMediaItem> search(SearchMatcher matcher, int limit) {
        List<XtreamMediaItem> matches = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (XtreamMediaItem item : scopedItems()) {
            int score = matcher.score(item.title());
            if (score != SearchMatcher.NO_MATCH) {
                matches.add(item);
                scores.add(score);
                if (limit == 1) {
                    return matches;
                }
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            order.add(i);
        }
        Collections.sort(order, (a, b) -> Integer.compare(scores.get(a), scores.get(b)));
        List<XtreamMediaItem> ranked = new ArrayList<>(Math.min(limit, matches.size()));
        for (int i = 0; i < order.size() && ranked.size() < limit; i++) {
            ranked.add(matches.get(order.get(i)));
        }
        return ranked;
    }

    /** Every item of the enabled types, minus hidden categories when "Visible only" is on. */
    private List<XtreamMediaItem> scopedItems() {
        List<XtreamMediaItem> items = new ArrayList<>();
        for (XtreamContentType type : new XtreamContentType[]{XtreamContentType.LIVE, XtreamContentType.MOVIE, XtreamContentType.SERIES}) {
            if (!Boolean.TRUE.equals(enabled.get(type))) {
                continue;
            }
            List<XtreamMediaSection> sections = host.catalog.cached(type);
            if (sections == null) {
                continue;
            }
            Set<String> hidden = visibleOnly ? host.settings.hiddenSections(type) : null;
            List<XtreamMediaSection> scoped = new ArrayList<>();
            for (XtreamMediaSection section : sections) {
                if (hidden == null || !hidden.contains(section.title())) {
                    scoped.add(section);
                }
            }
            items.addAll(Catalog.allItems(scoped));
        }
        return items;
    }
}
