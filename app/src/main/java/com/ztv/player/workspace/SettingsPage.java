package com.ztv.player.workspace;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ztv.player.BuildConfig;
import com.ztv.player.data.BackupManager;
import com.ztv.player.data.ServerAddressPolicy;
import com.ztv.player.data.XtreamContentType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * `/workspace/settings/:section`: the grouped section nav (App / Library / Data + About in
 * the footer) and the section page with its heading, description and setting cards.
 */
public final class SettingsPage extends Page {
    static final String PREF_COVER_SIZE = "cover_size";
    static final String PREF_SHOW_TITLES = "show_cover_titles";
    static final String PREF_RAIL_CONTINUE = "rail_continue";
    static final String PREF_RAIL_LIVE_FAVORITES = "rail_live_favorites";
    static final String PREF_RAIL_RECENT_LIVE = "rail_recent_live";
    static final String PREF_RAIL_FAVORITE_VOD = "rail_favorite_vod";
    static final String PREF_RAIL_SOURCES = "rail_sources";
    static final String PREF_RAIL_RECENTLY_ADDED = "rail_recently_added";
    static final int REQUEST_EXPORT = 701;
    static final int REQUEST_IMPORT = 702;

    private static final String[][] NAV = {
            {"APP", null, null},
            {"general", "General", "tune"},
            {"playback", "Playback", "play_circle"},
            {"dashboard", "Dashboard", "dashboard"},
            {"LIBRARY", null, null},
            {"categories", "Categories", "movie"},
            {"DATA", null, null},
            {"backup", "Backup & data", "backup"},
    };

    private final Context context;
    private String sectionKey = "general";
    private LinearLayout nav;
    private FrameLayout body;
    private View firstControl;

    public SettingsPage(WorkspaceActivity host) {
        super(host);
        this.context = host;
    }

    @Override
    public Section section() {
        return Section.SETTINGS;
    }

    @Override
    public String searchScope() {
        return "";
    }

    @Override
    public String searchPlaceholder() {
        return "Search settings...";
    }

    private static final String[][] SEARCH_INDEX = {
            {"general", "general appearance cover size titles covers startup start language theme"},
            {"playback", "playback player auto-play autoplay next episode recently viewed history size"},
            {"dashboard", "dashboard rails continue watching favorites recently added sources live"},
            {"categories", "categories category hide hidden manage library"},
            {"backup", "backup export import restore data cache clear sign out logout"},
            {"about", "about version server interface iptvnator license"},
    };

    @Override
    public void onSearch(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < 2) {
            return;
        }
        for (String[] entry : SEARCH_INDEX) {
            if (entry[1].contains(needle) && !entry[0].equals(sectionKey)) {
                sectionKey = entry[0];
                restyleNav();
                renderSection();
                return;
            }
        }
    }

    @Override
    public void requestInitialFocus() {
        View selected = nav == null ? null : nav.findViewWithTag("nav-" + sectionKey);
        if (selected != null) {
            selected.requestFocus();
        }
    }

    // ─── Nav panel ──────────────────────────────────────────────────────────

    @Override
    protected View buildContextPanel() {
        LinearLayout panel = Nator.column(context);
        TextView title = Nator.text(context, "Settings", 20, Nator.W600, Nator.HEADING);
        Nator.pad(title, 6, 2, 6, 14);
        panel.addView(title);
        nav = Nator.column(context);
        Nator.pad(nav, 8, 0, 8, 0);
        for (String[] entry : NAV) {
            if (entry[1] == null) {
                TextView group = Nator.text(context, entry[0], 11, Nator.W600, Nator.MUTED);
                group.setLetterSpacing(0.06f);
                Nator.pad(group, 10, nav.getChildCount() == 0 ? 0 : 14, 10, 6);
                nav.addView(group);
            } else {
                nav.addView(navItem(entry[0], entry[1], entry[2], null), Nator.margins(context, new LinearLayout.LayoutParams(-1, Nator.px(context, 34)), 0, 0, 0, 2));
            }
        }
        panel.addView(nav, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout footer = Nator.column(context);
        footer.addView(Nator.hairline(context, Nator.SEPARATOR), new LinearLayout.LayoutParams(-1, Math.max(1, Nator.px(context, 1))));
        LinearLayout footerItems = Nator.column(context);
        Nator.pad(footerItems, 8, 8, 8, 4);
        footerItems.addView(navItem("about", "About", "info", BuildConfig.VERSION_NAME), new LinearLayout.LayoutParams(-1, Nator.px(context, 34)));
        footer.addView(footerItems);
        panel.addView(footer);
        return panel;
    }

    private View navItem(String key, String label, String icon, String meta) {
        LinearLayout item = Nator.row(context);
        item.setTag("nav-" + key);
        Nator.pad(item, 10, 0, 10, 0);
        Nator.focusable(item, 8);
        TextView glyph = Nator.icon(context, icon, 18, Nator.MUTED);
        item.addView(glyph, Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 10, 0));
        TextView text = Nator.line(context, label, 14, Nator.W500, Nator.ON_SURFACE_VARIANT);
        item.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        if (meta != null) {
            item.addView(Nator.text(context, meta, 11, Nator.W500, Nator.MUTED));
        }
        Runnable style = () -> {
            boolean active = key.equals(sectionKey);
            boolean focused = item.isFocused();
            item.setBackground(active ? Nator.rounded(context, Nator.SELECTION_SURFACE, 8)
                    : focused ? Nator.rounded(context, Nator.HOVER_OVERLAY, 8) : null);
            glyph.setTextColor(active ? Nator.SELECTION : Nator.MUTED);
            text.setTextColor(active ? Nator.SELECTION : focused ? Nator.ON_SURFACE : Nator.ON_SURFACE_VARIANT);
        };
        item.setOnFocusChangeListener((v, focused) -> style.run());
        item.setOnClickListener(v -> {
            sectionKey = key;
            restyleNav();
            renderSection();
        });
        item.setTag(R_STYLE, style);
        style.run();
        return item;
    }

    private static final int R_STYLE = 0x7f0e0001;

    private void restyleNav() {
        restyle(nav);
        View footer = nav.getRootView();
        View about = footer == null ? null : footer.findViewWithTag("nav-about");
        if (about != null && about.getTag(R_STYLE) instanceof Runnable) {
            ((Runnable) about.getTag(R_STYLE)).run();
        }
    }

    private void restyle(LinearLayout group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Object style = group.getChildAt(i).getTag(R_STYLE);
            if (style instanceof Runnable) {
                ((Runnable) style).run();
            }
        }
    }

    // ─── Section body ───────────────────────────────────────────────────────

    @Override
    protected View buildContent() {
        body = new FrameLayout(context);
        renderSection();
        return body;
    }

    private void renderSection() {
        body.removeAllViews();
        firstControl = null;
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page = Nator.column(context);
        Nator.pad(page, 30, 28, 30, 40);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        switch (sectionKey) {
            case "playback":
                heading(page, "Playback", "How streams start, continue and what the player remembers.");
                LinearLayout player = card(page, "Player");
                toggleRow(player, "Auto-play next episode", "When an episode ends, the next one in the season starts automatically.",
                        host.settings.autoPlayNextEpisode(), value -> host.settings.setAutoPlayNextEpisode(value));
                segmentedRow(player, "Recently viewed size", "How many channels and titles Recently viewed keeps per type.",
                        new String[]{"10", "15", "30"}, new String[]{"history", "history", "history"},
                        String.valueOf(host.settings.recentChannelsCount()), value -> host.settings.setRecentChannelsCount(Integer.parseInt(value)));
                break;
            case "dashboard":
                heading(page, "Dashboard", "Choose which rails the dashboard shows. Empty rails hide themselves.");
                LinearLayout rails = card(page, "Rails");
                railToggle(rails, PREF_RAIL_CONTINUE, "Continue Watching", "Unfinished movies and episodes.");
                railToggle(rails, PREF_RAIL_LIVE_FAVORITES, "Live now on your favorites", "Your favorite channels.");
                railToggle(rails, PREF_RAIL_RECENT_LIVE, "Recently watched live TV", "Channels you opened lately.");
                railToggle(rails, PREF_RAIL_FAVORITE_VOD, "Favorite movies & series", "Titles you marked as favorites.");
                railToggle(rails, PREF_RAIL_SOURCES, "Recently Used Sources", "Your Xtream playlist.");
                railToggle(rails, PREF_RAIL_RECENTLY_ADDED, "Recently Added on Xtream", "The newest movies and series.");
                break;
            case "categories":
                heading(page, "Categories", "Hide categories you never watch. Hidden categories disappear from the panel and search.");
                LinearLayout cats = card(page, "Manage categories");
                actionRow(cats, "Live TV categories", hiddenSummary(XtreamContentType.LIVE), "Manage", () -> manage(XtreamContentType.LIVE));
                actionRow(cats, "Movie categories", hiddenSummary(XtreamContentType.MOVIE), "Manage", () -> manage(XtreamContentType.MOVIE));
                actionRow(cats, "Series categories", hiddenSummary(XtreamContentType.SERIES), "Manage", () -> manage(XtreamContentType.SERIES));
                break;
            case "backup":
                heading(page, "Backup & data", "Export or restore favorites, history, watch progress and settings. Credentials are never included.");
                LinearLayout backup = card(page, "Backup");
                actionRow(backup, "Export backup", "Save a JSON file with your library data.", "Export", this::startExport);
                actionRow(backup, "Import backup", "Replace current data with a saved backup.", "Import", this::startImport);
                LinearLayout data = card(page, "Data");
                actionRow(data, "Clear catalog cache", "Re-download channel, movie and series lists on next open.", "Clear", () -> {
                    host.catalog.clearDisk();
                    host.snack("Catalog cache cleared");
                });
                actionRow(data, "Sign out", "Remove the Xtream account from this device.", "Sign out", () -> host.openSection(Section.SOURCES));
                break;
            case "about":
                heading(page, "About", "ZTV for Android TV.");
                LinearLayout about = card(page, "Application");
                infoRow(about, "Version", BuildConfig.VERSION_NAME + (BuildConfig.DEBUG ? " (debug)" : ""));
                infoRow(about, "Server", host.hasSource() ? ServerAddressPolicy.display(host.credentials().host()) : "—");
                infoRow(about, "Interface", "Based on IPTVnator by 4gray (MIT License)");
                break;
            case "general":
            default:
                heading(page, "General", "Language, appearance and how the app starts.");
                LinearLayout appearance = card(page, "Appearance");
                segmentedRow(appearance, "Cover size",
                        "Adjust the size of poster cards across grids and rails. Larger sizes are easier to see on TVs; smaller sizes show more content per row.",
                        new String[]{"Small", "Medium", "Large"}, new String[]{"view_module", "view_comfy", "view_quilt"},
                        capitalize(host.prefs.string(PREF_COVER_SIZE, "medium")),
                        value -> host.prefs.put(PREF_COVER_SIZE, value.toLowerCase(Locale.ROOT)));
                toggleRow(appearance, "Show titles under covers",
                        "Turn off for a posters-only wall in movie and series grids: the title appears on the cover when you focus it. Live channels, search results and dashboard rails keep their labels.",
                        host.prefs.bool(PREF_SHOW_TITLES, true), value -> host.prefs.put(PREF_SHOW_TITLES, value));
                LinearLayout startup = card(page, "Startup");
                segmentedRow(startup, "Startup view",
                        "Choose whether ZTV opens the dashboard or restores your last section-level view.",
                        new String[]{"Dashboard", "Last view"}, new String[]{"dashboard", "restore"},
                        host.settings.startupHome() ? "Dashboard" : "Last view",
                        value -> host.settings.setStartupHome("Dashboard".equals(value)));
                break;
        }
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String hiddenSummary(XtreamContentType type) {
        int hidden = host.settings.hiddenSections(type).size();
        return hidden == 0 ? "All categories visible" : Nator.plural(hidden, "category", "categories") + " hidden";
    }

    private void manage(XtreamContentType type) {
        host.catalog.load(type, false, new Catalog.LibraryListener() {
            private boolean opened;

            @Override
            public void onLibrary(XtreamContentType loaded, java.util.List<com.ztv.player.data.XtreamMediaSection> sections, boolean fromNetwork) {
                if (!opened) {
                    opened = true;
                    new ManageCategoriesDialog(host, type, sections, () -> renderSection()).show();
                }
            }

            @Override
            public void onLibraryError(XtreamContentType loaded, Exception error) {
                host.snack("Categories could not be loaded");
            }
        });
    }

    private void heading(LinearLayout page, String title, String description) {
        page.addView(Nator.text(context, title, 26, Nator.W600, Nator.HEADING));
        TextView desc = Nator.text(context, description, 13.5f, Nator.W400, Nator.BODY);
        Nator.lineHeight(desc, 19.6f);
        page.addView(desc, Nator.margins(context, Nator.size(context, 800, -2), 0, 6, 0, 0));
    }

    private LinearLayout card(LinearLayout page, String title) {
        page.addView(Nator.text(context, title, 14, Nator.W600, Nator.HEADING), Nator.margins(context, Nator.wrap(), 2, 34, 0, 0));
        LinearLayout card = Nator.column(context);
        card.setBackground(Nator.roundedStroke(context, Nator.WIDGET_BG, 12, 1, 0x12FFFFFF));
        page.addView(card, Nator.margins(context, Nator.size(context, 800, -2), 0, 14, 0, 0));
        return card;
    }

    private LinearLayout settingRow(LinearLayout card, String title, String description) {
        if (card.getChildCount() > 0) {
            card.addView(Nator.hairline(context, 0x0FFFFFFF), new LinearLayout.LayoutParams(-1, Math.max(1, Nator.px(context, 1))));
        }
        LinearLayout row = Nator.row(context);
        Nator.pad(row, 16, 14, 16, 14);
        LinearLayout texts = Nator.column(context);
        texts.addView(Nator.text(context, title, 13.5f, Nator.W500, Nator.HEADING));
        if (description != null && !description.isEmpty()) {
            TextView desc = Nator.text(context, description, 12.5f, Nator.W400, Nator.alpha(0xFFE3E2E6, 0.68f));
            Nator.lineHeight(desc, 18.75f);
            texts.addView(desc, Nator.margins(context, Nator.size(context, 479, -2), 0, 3, 0, 0));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(row, Nator.matchWidth());
        return row;
    }

    private interface Setter<T> {
        void set(T value);
    }

    /** mat-slide-toggle: 52x32 track, selected = #abc7ff track with the dark handle. */
    private void toggleRow(LinearLayout card, String title, String description, boolean initial, Setter<Boolean> setter) {
        LinearLayout row = settingRow(card, title, description);
        FrameLayout toggle = new FrameLayout(context);
        View handle = new View(context);
        boolean[] state = {initial};
        Runnable style = () -> {
            toggle.setBackground(state[0] ? Nator.pill(context, 0xFFABC7FF)
                    : Nator.roundedStroke(context, 0xFF2F3036, 999, 2, Nator.OUTLINE));
            handle.setBackground(Nator.oval(state[0] ? 0xFF002F65 : Nator.OUTLINE));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    Nator.px(context, state[0] ? 24 : 16), Nator.px(context, state[0] ? 24 : 16),
                    Gravity.CENTER_VERTICAL | (state[0] ? Gravity.END : Gravity.START));
            params.setMargins(Nator.px(context, state[0] ? 0 : 8), 0, Nator.px(context, state[0] ? 4 : 0), 0);
            handle.setLayoutParams(params);
        };
        toggle.addView(handle);
        style.run();
        Nator.focusable(toggle, 999);
        toggle.setOnClickListener(v -> {
            state[0] = !state[0];
            style.run();
            setter.set(state[0]);
        });
        row.addView(toggle, Nator.margins(context, Nator.size(context, 52, 32), 16, 0, 0, 0));
        if (firstControl == null) {
            firstControl = toggle;
        }
    }

    private void railToggle(LinearLayout card, String key, String title, String description) {
        toggleRow(card, title, description, host.prefs.bool(key, true), value -> host.prefs.put(key, value));
    }

    /** `.theme-switcher`: pill group, active option on the selection-surface tint. */
    private void segmentedRow(LinearLayout card, String title, String description, String[] options, String[] icons,
                              String current, Setter<String> setter) {
        LinearLayout row = settingRow(card, title, description);
        LinearLayout group = Nator.row(context);
        group.setBackground(Nator.roundedStroke(context, 0xF52C313D, 999, 1, 0x1FFFFFFF));
        Nator.pad(group, 3, 3, 3, 3);
        String[] selected = {current};
        for (int i = 0; i < options.length; i++) {
            String option = options[i];
            LinearLayout button = Nator.row(context);
            button.setGravity(Gravity.CENTER);
            Nator.pad(button, 12, 0, 12, 0);
            Nator.focusable(button, 999);
            TextView glyph = Nator.icon(context, icons[i], 14, Nator.BODY);
            TextView text = Nator.text(context, option, 12.5f, Nator.W500, Nator.BODY);
            button.addView(glyph, Nator.margins(context, Nator.size(context, 14, 14), 0, 0, 6, 0));
            button.addView(text);
            Runnable style = () -> {
                boolean active = option.equals(selected[0]);
                button.setBackground(active ? Nator.pill(context, Nator.SELECTION_SURFACE)
                        : button.isFocused() ? Nator.pill(context, Nator.HOVER_OVERLAY) : null);
                glyph.setTextColor(active ? Nator.SELECTION : Nator.BODY);
                text.setTextColor(active ? Nator.SELECTION : button.isFocused() ? Nator.ON_SURFACE : Nator.BODY);
            };
            button.setTag(R_STYLE, style);
            button.setOnFocusChangeListener((v, focused) -> style.run());
            button.setOnClickListener(v -> {
                selected[0] = option;
                setter.set(option);
                restyle(group);
            });
            style.run();
            group.addView(button, Nator.size(context, -2, 28));
            if (firstControl == null) {
                firstControl = button;
            }
        }
        row.addView(group, Nator.margins(context, Nator.wrap(), 16, 0, 0, 0));
    }

    private void actionRow(LinearLayout card, String title, String description, String action, Runnable run) {
        LinearLayout row = settingRow(card, title, description);
        TextView button = Widgets.outlinedPill(context, null, action, run);
        row.addView(button, Nator.margins(context, Nator.wrap(), 16, 0, 0, 0));
        if (firstControl == null) {
            firstControl = button;
        }
    }

    private void infoRow(LinearLayout card, String title, String value) {
        LinearLayout row = settingRow(card, title, null);
        row.addView(Nator.text(context, value, 13, Nator.W400, Nator.BODY));
    }

    // ─── Backup (SAF) ───────────────────────────────────────────────────────

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "ztv_backup_" + stamp + ".json");
        try {
            host.startActivityForResult(intent, REQUEST_EXPORT);
        } catch (Exception exception) {
            host.snack("No file app available to save the backup");
        }
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try {
            host.startActivityForResult(intent, REQUEST_IMPORT);
        } catch (Exception exception) {
            host.snack("No file app available to pick a backup");
        }
    }

    void onDocument(int requestCode, Uri uri) {
        BackupManager backup = new BackupManager(context);
        if (requestCode == REQUEST_EXPORT) {
            new Thread(() -> {
                String result;
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        throw new IOException("Could not open destination file");
                    }
                    out.write(backup.export().getBytes(StandardCharsets.UTF_8));
                    result = "Backup saved";
                } catch (Exception exception) {
                    result = "Export failed: " + exception.getMessage();
                }
                String message = result;
                host.runOnUiThread(() -> host.snack(message));
            }, "ztv-backup-export").start();
        } else if (requestCode == REQUEST_IMPORT) {
            new Thread(() -> {
                String result;
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        throw new IOException("Could not open backup file");
                    }
                    StringBuilder builder = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        builder.append(buffer, 0, read);
                    }
                    backup.restoreFrom(builder.toString());
                    result = "Backup restored";
                } catch (Exception exception) {
                    result = "Restore failed: " + exception.getMessage();
                }
                String message = result;
                host.runOnUiThread(() -> {
                    host.snack(message);
                    renderSection();
                });
            }, "ztv-backup-import").start();
        }
    }
}
