package com.ztv.player.workspace;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.VerticalGridView;

import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * `/workspace/global-favorites` and `/workspace/global-recent`: the unified content-card
 * grid with the "This playlist / All playlists" scope toggle and the clear action.
 */
public final class CollectionPage extends Page {
    public enum Kind {
        FAVORITES, RECENT
    }

    private final Kind kind;
    private final Context context;
    private VerticalGridView grid;
    private PosterGridAdapter adapter;
    private View empty;
    private String query = "";
    private boolean allPlaylists;
    private LinearLayout toggle;

    public CollectionPage(WorkspaceActivity host, Kind kind) {
        super(host);
        this.kind = kind;
        this.context = host;
    }

    @Override
    public Section section() {
        return kind == Kind.FAVORITES ? Section.FAVORITES : Section.RECENT;
    }

    @Override
    public String searchScope() {
        return kind == Kind.FAVORITES ? "Global favorites" : "Recently viewed";
    }

    @Override
    public String searchPlaceholder() {
        return "Filter this section...";
    }

    @Override
    public String searchQuery() {
        return query;
    }

    @Override
    public void onSearch(String value) {
        query = value == null ? "" : value.trim();
        render();
    }

    @Override
    public void onShown() {
        render();
    }

    @Override
    public void onPlaybackReturned() {
        render();
    }

    @Override
    public void requestInitialFocus() {
        if (adapter != null && adapter.getItemCount() > 0) {
            grid.requestFocus();
        } else if (toggle != null) {
            toggle.getChildAt(0).requestFocus();
        }
    }

    @Override
    protected View buildContent() {
        LinearLayout column = Nator.column(context);
        LinearLayout header = Nator.row(context);
        Nator.pad(header, 16, 0, 22, 0);
        header.addView(Nator.text(context, kind == Kind.FAVORITES ? "Favorites" : "Recently viewed", 15.04f, Nator.W600, Nator.ON_SURFACE));
        header.addView(Nator.spacer(context));
        toggle = Nator.row(context);
        toggle.setBackground(Nator.roundedStroke(context, 0, 28, 1, Nator.OUTLINE));
        toggle.setClipToOutline(true);
        toggle.addView(scopeButton("playlist_play", "This playlist", false), Nator.size(context, 113, 41));
        View divider = Nator.hairline(context, Nator.OUTLINE);
        toggle.addView(divider, new LinearLayout.LayoutParams(Math.max(1, Nator.px(context, 1)), -1));
        toggle.addView(scopeButton("public", "All playlists", true), Nator.size(context, 109, 41));
        header.addView(toggle, Nator.size(context, -2, 43));
        header.addView(host.iconButton("delete_sweep", 24, kind == Kind.FAVORITES ? "Clear favorites" : "Clear history",
                v -> confirmClear()), Nator.margins(context, Nator.size(context, 40, 40), 10, 0, 0, 0));
        column.addView(header, new LinearLayout.LayoutParams(-1, Nator.px(context, 66)));

        FrameLayout body = new FrameLayout(context);
        body.setClipChildren(false);
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        int available = screen - Nator.px(context, 60) - Nator.px(context, 32);
        int gap = Nator.px(context, 16);
        int min = Nator.px(context, 148);
        int columns = Math.max(1, (available + gap) / (min + gap));
        int width = Math.min(Nator.px(context, 156), (available - gap * (columns - 1)) / columns);
        adapter = new PosterGridAdapter(context, PosterGridAdapter.Style.CONTENT, width, new PosterGridAdapter.Listener() {
            @Override
            public void onOpen(XtreamMediaItem item, int position) {
                if (item.type() == XtreamContentType.LIVE || item.type() == XtreamContentType.EPISODE) {
                    host.play(item, null);
                } else {
                    host.push(new DetailPage(host, item));
                }
            }

            @Override
            public void onMenu(XtreamMediaItem item, int position) {
                remove(item, position);
            }

            @Override
            public void onRemove(XtreamMediaItem item, int position) {
                remove(item, position);
            }
        });
        adapter.setRemovable(kind == Kind.FAVORITES);
        grid = Widgets.verticalGrid(context, columns, 16, 16);
        grid.setAdapter(adapter);
        Nator.pad(grid, 16, 4, 16, 24);
        Widgets.enablePartialRowDown(grid, columns);
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        empty = Widgets.emptyState(context, kind == Kind.FAVORITES ? "favorite_border" : "history",
                kind == Kind.FAVORITES ? "No favorites yet" : "Nothing watched yet",
                kind == Kind.FAVORITES ? "Press Menu on any channel, movie or series to add it here."
                        : "Channels, movies and series you open will show up here.");
        empty.setVisibility(View.GONE);
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        column.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        return column;
    }

    private View scopeButton(String icon, String label, boolean all) {
        LinearLayout button = Nator.row(context);
        button.setGravity(Gravity.CENTER);
        Nator.focusable(button, 0);
        TextView glyph = Nator.icon(context, icon, 18, Nator.ON_SURFACE);
        TextView text = Nator.text(context, label, 12, Nator.W500, Nator.ON_SURFACE);
        button.addView(glyph, Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 4, 0));
        button.addView(text);
        button.setTag(new TextView[]{glyph, text});
        button.setOnClickListener(v -> {
            allPlaylists = all;
            styleToggle();
            render();
        });
        button.setOnFocusChangeListener((v, focused) -> styleToggle());
        return button;
    }

    private void styleToggle() {
        for (int i = 0; i < toggle.getChildCount(); i++) {
            View child = toggle.getChildAt(i);
            if (!(child.getTag() instanceof TextView[])) {
                continue;
            }
            boolean all = i > 0;
            boolean active = all == allPlaylists;
            child.setBackgroundColor(active ? 0xFF3E4759 : child.isFocused() ? Nator.HOVER_OVERLAY : 0);
            for (TextView view : (TextView[]) child.getTag()) {
                view.setTextColor(active ? 0xFFDAE2F9 : Nator.ON_SURFACE);
            }
        }
    }

    private List<XtreamMediaItem> items() {
        List<XtreamMediaItem> items = new ArrayList<>();
        XtreamContentType[] types = {XtreamContentType.LIVE, XtreamContentType.MOVIE, XtreamContentType.SERIES};
        for (XtreamContentType type : types) {
            items.addAll(kind == Kind.FAVORITES ? host.favorites.list(type) : host.history.list(type));
        }
        if (!query.isEmpty()) {
            List<XtreamMediaItem> filtered = new ArrayList<>();
            String needle = query.toLowerCase(Locale.ROOT);
            for (XtreamMediaItem item : items) {
                if (item.title().toLowerCase(Locale.ROOT).contains(needle)) {
                    filtered.add(item);
                }
            }
            return filtered;
        }
        return items;
    }

    private void render() {
        if (adapter == null) {
            return;
        }
        styleToggle();
        List<XtreamMediaItem> items = items();
        adapter.setItems(items);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void remove(XtreamMediaItem item, int position) {
        if (kind == Kind.FAVORITES) {
            host.favorites.remove(item.type(), item.id());
            host.snack("Removed from favorites");
        } else {
            host.history.remove(item.type(), item.id());
            host.snack("Removed from recently viewed");
        }
        adapter.removeAt(position);
        empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void confirmClear() {
        LinearLayout card = Nator.column(context);
        card.setBackground(Nator.rounded(context, 0xFF121316, 28));
        Nator.pad(card, 24, 24, 24, 18);
        card.addView(Nator.text(context, kind == Kind.FAVORITES ? "Clear favorites?" : "Clear recently viewed?", 20, Nator.W600, Nator.HEADING));
        card.addView(Nator.text(context, "This removes every item from this list.", 13.5f, Nator.W400, Nator.BODY),
                Nator.margins(context, Nator.matchWidth(), 0, 12, 0, 0));
        LinearLayout actions = Nator.row(context);
        actions.setGravity(Gravity.END);
        TextView cancel = host.dialogTextButton("Cancel", host::dismissTopOverlay);
        actions.addView(cancel, Nator.margins(context, Nator.wrap(), 0, 0, 8, 0));
        actions.addView(host.dialogFilledButton("Clear", () -> {
            host.dismissTopOverlay();
            for (XtreamContentType type : new XtreamContentType[]{XtreamContentType.LIVE, XtreamContentType.MOVIE, XtreamContentType.SERIES}) {
                if (kind == Kind.FAVORITES) {
                    host.favorites.replaceAll(type, new ArrayList<>());
                } else {
                    host.history.replaceAll(type, new ArrayList<>());
                }
            }
            render();
        }));
        card.addView(actions, Nator.margins(context, Nator.matchWidth(), 0, 22, 0, 0));
        host.showDialog(card, 440, null);
        cancel.requestFocus();
    }
}
