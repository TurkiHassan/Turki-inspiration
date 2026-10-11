package com.ztv.player.workspace;

import android.view.KeyEvent;
import android.view.View;

/**
 * One routed workspace view (IPTVnator's router-outlet page). The shell owns the rail,
 * the header and the frame; a page supplies the optional context panel and the content.
 */
public abstract class Page {
    public enum Section {
        DASHBOARD, SOURCES, FAVORITES, RECENT, VOD, LIVE, SERIES, RECENTLY_ADDED, SEARCH, SETTINGS
    }

    protected final WorkspaceActivity host;
    private View contextView;
    private View contentView;

    protected Page(WorkspaceActivity host) {
        this.host = host;
    }

    /** Rail link this page lights up. */
    public abstract Section section();

    /** The left context panel, or null for single-pane pages (dashboard, details hide it? no). */
    protected View buildContextPanel() {
        return null;
    }

    protected abstract View buildContent();

    public final View contextPanel() {
        if (contextView == null) {
            contextView = buildContextPanel();
        }
        return contextView;
    }

    public final View content() {
        if (contentView == null) {
            contentView = buildContent();
        }
        return contentView;
    }

    /** Header search capability: chip label ("Movies / All items"), or null to disable. */
    public String searchScope() {
        return null;
    }

    public String searchPlaceholder() {
        return "Search in this section...";
    }

    public String searchQuery() {
        return "";
    }

    public void onSearch(String query) {
    }

    /** Header search submitted with OK/Enter. */
    public void onSearchSubmit(String query) {
        onSearch(query);
    }

    /** Called each time the page becomes visible (first show and after popping back to it). */
    public void onShown() {
    }

    public void onHidden() {
    }

    /** Back press inside the page (close an overlay, leave fullscreen). True = consumed. */
    public boolean onBack() {
        return false;
    }

    /** Raw key hook before the focus system sees it. True = consumed. */
    public boolean onKey(KeyEvent event) {
        return false;
    }

    /** Where focus lands when the page opens. */
    public void requestInitialFocus() {
        View content = content();
        if (content != null) {
            content.requestFocus();
        }
    }

    /** D-pad RIGHT from the side rail: the context panel when there is one, else the content. */
    public void focusFromRail() {
        requestInitialFocus();
    }

    /** Playback returned (player activity closed): refresh progress / recents. */
    public void onPlaybackReturned() {
    }

    public void onDestroy() {
    }

    /** Whether the header shows the playlist refresh action on this page. */
    public boolean canRefresh() {
        return false;
    }

    public void onRefresh() {
    }
}
