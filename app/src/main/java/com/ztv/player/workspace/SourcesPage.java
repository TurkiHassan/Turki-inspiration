package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.VerticalGridView;

import com.ztv.player.data.ServerAddressPolicy;
import com.ztv.player.ui.Tr;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * `/workspace/sources`: the Type filter panel and the playlist list. ZTV keeps one Xtream
 * account, so the list holds that source with its edit and delete actions.
 */
public final class SourcesPage extends Page {
    static final String PREF_ADDED_AT = "source_added_at";

    private final Context context;
    private String typeFilter = "all";
    private LinearLayout list;
    private TextView countChip;
    private View firstFocus;

    public SourcesPage(WorkspaceActivity host) {
        super(host);
        this.context = host;
    }

    @Override
    public Section section() {
        return Section.SOURCES;
    }

    @Override
    public String searchScope() {
        return Tr.t("Sources", "المصادر");
    }

    @Override
    public String searchPlaceholder() {
        return Tr.t("Search sources (all playlists)...", "ابحث في المصادر (كل الاشتراكات)...");
    }

    @Override
    public void onSearch(String query) {
        render(query == null ? "" : query.trim());
    }

    @Override
    protected View buildContextPanel() {
        LinearLayout panel = Nator.column(context);
        LinearLayout heading = Nator.column(context);
        Nator.pad(heading, 6, 4, 0, 0);
        heading.addView(Nator.text(context, Tr.t("Sources", "المصادر"), 15.04f, Nator.W600, Nator.ON_SURFACE));
        heading.addView(Nator.text(context, Tr.t("Filter and sort playlists", "تصفية الاشتراكات وترتيبها"), 12.48f, Nator.W400, Nator.ON_SURFACE_VARIANT),
                Nator.margins(context, Nator.wrap(), 0, 4, 0, 0));
        panel.addView(heading);
        panel.addView(Nator.hairline(context, Nator.OUTLINE_VARIANT), Nator.margins(context, new LinearLayout.LayoutParams(-1, Math.max(1, Nator.px(context, 1))), 0, 10, 0, 6));
        TextView sectionTitle = Nator.text(context, Tr.t("TYPE", "النوع"), 11.52f, Nator.W600, Nator.ON_SURFACE_VARIANT);
        sectionTitle.setLetterSpacing(0.06f);
        Nator.pad(sectionTitle, 18, 10, 10, 6);
        panel.addView(sectionTitle);

        CategoryAdapter adapter = new CategoryAdapter(context, true, (row, position) -> {
            typeFilter = row.key;
            ((CategoryAdapter) ((VerticalGridView) panel.getChildAt(panel.getChildCount() - 1)).getAdapter()).setSelectedKey(row.key);
            render("");
        });
        int count = host.hasSource() ? 1 : 0;
        List<CategoryAdapter.Row> rows = new ArrayList<>();
        rows.add(new CategoryAdapter.Row("all", Tr.t("All", "الكل"), count, "layers"));
        rows.add(new CategoryAdapter.Row("m3u", Tr.t("M3U (local, url, text)", "M3U (ملف، رابط، نص)"), 0, "playlist_play"));
        rows.add(new CategoryAdapter.Row("xtream", "Xtream", count, "cloud"));
        rows.add(new CategoryAdapter.Row("stalker", "Stalker", 0, "cast"));
        adapter.setRows(rows);
        adapter.setSelectedKey(typeFilter);
        VerticalGridView grid = Widgets.verticalGrid(context, 1, 2, 0);
        Nator.pad(grid, 8, 0, 8, 0);
        grid.setAdapter(adapter);
        panel.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        return panel;
    }

    @Override
    protected View buildContent() {
        LinearLayout column = Nator.column(context);
        Nator.pad(column, 16, 6, 26, 16);
        LinearLayout header = Nator.row(context);
        header.addView(Nator.text(context, Tr.t("All Playlists", "كل الاشتراكات"), 15.2f, Nator.W600, Nator.HEADING));
        countChip = Nator.countChip(context, "", Nator.BODY);
        header.addView(countChip, Nator.margins(context, Nator.wrap(), 10, 0, 0, 0));
        header.addView(Nator.spacer(context));
        LinearLayout sort = Nator.row(context);
        sort.setBackground(Nator.states(Nator.roundedStroke(context, 0, 9999, 1, Nator.OUTLINE),
                Nator.roundedStroke(context, Nator.alpha(0xFFABC7FF, 0.12f), 9999, 1, Nator.OUTLINE)));
        Nator.pad(sort, 17, 0, 24, 0);
        Nator.focusable(sort, 9999);
        sort.addView(Nator.icon(context, "sort", 18, 0xFFABC7FF), Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 10, 0));
        sort.addView(Nator.icon(context, "arrow_drop_down", 18, 0xFFABC7FF), Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 12, 0));
        sort.addView(Nator.text(context, Tr.t("Date added (Newest first)", "تاريخ الإضافة (الأحدث أولًا)"), 13.6f, Nator.W500, 0xFFABC7FF));
        sort.setOnClickListener(v -> host.snack(Tr.t("Only one source is configured", "هناك مصدر واحد فقط")));
        header.addView(sort, Nator.size(context, -2, 40));
        column.addView(header, new LinearLayout.LayoutParams(-1, Nator.px(context, 52)));
        list = Nator.column(context);
        column.addView(list, Nator.margins(context, Nator.matchWidth(), 0, 10, 0, 0));
        render("");
        return column;
    }

    @Override
    public void requestInitialFocus() {
        if (firstFocus != null) {
            firstFocus.requestFocus();
        } else {
            super.requestInitialFocus();
        }
    }

    private void render(String query) {
        if (list == null) {
            return;
        }
        list.removeAllViews();
        firstFocus = null;
        boolean visible = host.hasSource()
                && ("all".equals(typeFilter) || "xtream".equals(typeFilter))
                && (query.isEmpty() || host.playlistTitle().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)));
        countChip.setText(Nator.plural(visible ? 1 : 0, "playlist", "playlists", "اشتراك واحد", "اشتراكان", "اشتراكات", "اشتراك"));
        if (!visible) {
            LinearLayout empty = Widgets.emptyState(context, "playlist_add", Tr.t("No playlists here", "لا اشتراكات هنا"),
                    host.hasSource() ? Tr.t("No playlist matches this filter.", "لا اشتراك يطابق هذه التصفية.") : Tr.t("Add an Xtream account to get started.", "أضف حساب Xtream للبدء."));
            TextView add = host.dialogFilledButton(Tr.t("Add playlist", "إضافة اشتراك"), () -> host.showAddPlaylist(null));
            empty.addView(add, Nator.margins(context, Nator.wrap(), 0, 20, 0, 0));
            list.addView(empty, Nator.margins(context, Nator.matchWidth(), 0, 80, 0, 0));
            firstFocus = add;
            return;
        }
        list.addView(sourceRow());
    }

    private View sourceRow() {
        LinearLayout row = Nator.row(context);
        row.setBackground(Nator.selection(context, 8));
        Nator.pad(row, 12, 8, 10, 8);
        row.setMinimumHeight(Nator.px(context, 68));

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.addView(Nator.icon(context, "cloud", 24, Nator.SELECTION), new FrameLayout.LayoutParams(Nator.px(context, 24), Nator.px(context, 24)));
        View dot = new View(context);
        GradientDrawable dotBg = Nator.oval(0xFF4CAF50);
        dotBg.setStroke(Nator.px(context, 2), Nator.alpha(Nator.SELECTION, 0.24f));
        dot.setBackground(dotBg);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(Nator.px(context, 14), Nator.px(context, 14), Gravity.BOTTOM | Gravity.END);
        iconBox.addView(dot, dotParams);
        row.addView(iconBox, Nator.margins(context, Nator.size(context, 28, 30), 1, 0, 8, 0));

        LinearLayout texts = Nator.column(context);
        texts.addView(Nator.line(context, host.playlistTitle(), 15.36f, Nator.W600, Nator.SELECTION));
        texts.addView(Nator.line(context, "Xtream | Added: " + addedDate() + " | " + ServerAddressPolicy.display(host.credentials().host()),
                13.12f, Nator.W400, Nator.SELECTION), Nator.margins(context, Nator.wrap(), 0, 2, 0, 0));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        View open = host.iconButton("play_arrow", 20, Tr.t("Open playlist", "فتح الاشتراك"), v -> host.openSection(Section.VOD));
        View info = host.iconButton("info_outline", 20, Tr.t("Account info", "معلومات الحساب"), v -> host.showAccountInfo());
        View edit = host.iconButton("edit", 20, Tr.t("Edit playlist", "تعديل الاشتراك"), v -> host.showAddPlaylist(host.credentials()));
        View delete = host.iconButton("delete", 20, Tr.t("Delete playlist", "حذف الاشتراك"), v -> confirmDelete());
        for (View action : new View[]{open, info, edit, delete}) {
            ((TextView) ((FrameLayout) action).getChildAt(0)).setTextColor(Nator.SELECTION);
            row.addView(action, Nator.margins(context, Nator.size(context, 36, 36), 0, 0, 2, 0));
        }
        firstFocus = open;
        return row;
    }

    private String addedDate() {
        String raw = host.prefs.string(PREF_ADDED_AT, "");
        long at;
        try {
            at = raw.isEmpty() ? System.currentTimeMillis() : Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            at = System.currentTimeMillis();
        }
        return new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(new Date(at));
    }

    private void confirmDelete() {
        LinearLayout card = Nator.column(context);
        card.setBackground(Nator.rounded(context, 0xFF121316, 28));
        Nator.pad(card, 24, 24, 24, 18);
        card.addView(Nator.text(context, Tr.t("Delete playlist?", "حذف الاشتراك؟"), 20, Nator.W600, Nator.HEADING));
        TextView body = Nator.text(context, Tr.t("\"" + host.playlistTitle() + "\" will be removed from ZTV and you will be signed out. "
                + "Favorites and watch history stay on this device.",
                "سيُحذف «" + host.playlistTitle() + "» من ZTV وسيُسجَّل خروجك. تبقى المفضلة وسجل المشاهدة على هذا الجهاز."),
                13.5f, Nator.W400, Nator.BODY);
        Nator.lineHeight(body, 20);
        card.addView(body, Nator.margins(context, Nator.matchWidth(), 0, 12, 0, 0));
        LinearLayout actions = Nator.row(context);
        actions.setGravity(Gravity.END);
        TextView cancel = host.dialogTextButton(Tr.t("Cancel", "إلغاء"), host::dismissTopOverlay);
        actions.addView(cancel, Nator.margins(context, Nator.wrap(), 0, 0, 8, 0));
        actions.addView(host.dialogFilledButton(Tr.t("Delete", "حذف"), () -> {
            host.dismissTopOverlay();
            host.removeSource();
        }));
        card.addView(actions, Nator.margins(context, Nator.matchWidth(), 0, 22, 0, 0));
        host.showDialog(card, 440, null);
        cancel.requestFocus();
    }
}
