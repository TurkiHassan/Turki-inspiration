package com.ztv.player.workspace;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.VerticalGridView;

import com.ztv.player.data.KidsPolicy;
import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.data.XtreamMediaSection;
import com.ztv.player.ui.Tr;

import java.util.ArrayList;
import java.util.List;

/**
 * The parent's picker: categories of one type on the left (OK allows the whole category),
 * the focused category's items on the right (OK shows or hides one item).
 */
final class KidsContentPage extends Page implements Catalog.LibraryListener {
    private static final XtreamContentType[] TYPES = {XtreamContentType.LIVE, XtreamContentType.MOVIE, XtreamContentType.SERIES};

    private final Context context;
    private XtreamContentType type = XtreamContentType.LIVE;
    private List<XtreamMediaSection> sections = new ArrayList<>();
    private XtreamMediaSection shownSection;
    private LinearLayout typeTabs;
    private VerticalGridView categoryGrid;
    private VerticalGridView itemGrid;
    private CheckRowAdapter categories;
    private CheckRowAdapter items;
    private TextView itemsTitle;

    KidsContentPage(WorkspaceActivity host) {
        super(host);
        this.context = host;
    }

    @Override
    public Section section() {
        return Section.SETTINGS;
    }

    @Override
    public void onShown() {
        host.catalog.addListener(this);
        loadType();
    }

    @Override
    public void onHidden() {
        host.catalog.removeListener(this);
    }

    @Override
    public void onLibrary(XtreamContentType loaded, List<XtreamMediaSection> ignored, boolean fromNetwork) {
        if (loaded == type) {
            refreshSections();
        }
    }

    @Override
    public void onLibraryError(XtreamContentType loaded, Exception error) {
    }

    @Override
    public void requestInitialFocus() {
        if (categoryGrid != null && categories.getItemCount() > 0) {
            categoryGrid.requestFocus();
        } else if (typeTabs != null) {
            typeTabs.getChildAt(0).requestFocus();
        }
    }

    @Override
    protected View buildContextPanel() {
        LinearLayout panel = Nator.column(context);
        TextView title = Nator.text(context, Tr.t("Allowed content", "المحتوى المسموح"), 20, Nator.W600, Nator.HEADING);
        Nator.pad(title, 6, 2, 6, 10);
        panel.addView(title);
        typeTabs = Nator.row(context);
        String[] labels = {Tr.t("Live TV", "القنوات"), Tr.t("Movies", "الأفلام"), Tr.t("Series", "المسلسلات")};
        for (int i = 0; i < TYPES.length; i++) {
            XtreamContentType tabType = TYPES[i];
            TextView tab = Nator.text(context, labels[i], 13, Nator.W500, Nator.BODY);
            tab.setGravity(android.view.Gravity.CENTER);
            Nator.pad(tab, 12, 0, 12, 0);
            Nator.focusable(tab, 999);
            tab.setOnClickListener(v -> {
                type = tabType;
                styleTabs();
                loadType();
            });
            typeTabs.addView(tab, Nator.margins(context, Nator.size(context, -2, 30), 0, 0, 6, 0));
        }
        styleTabs();
        panel.addView(typeTabs, Nator.margins(context, Nator.wrap(), 4, 0, 0, 8));
        TextView hint = Nator.text(context, Tr.t("OK allows or hides a whole category. Press → to pick single items.",
                "زر OK يسمح بالفئة كاملة أو يخفيها. اضغط → لاختيار عناصر بعينها."), 12, Nator.W400, Nator.MUTED);
        Nator.pad(hint, 6, 0, 6, 8);
        panel.addView(hint);

        categories = new CheckRowAdapter(context, (row, position) -> toggleCategory(position), (row, position) -> showItems(position));
        categoryGrid = Widgets.verticalGrid(context, 1, 2, 0);
        categoryGrid.setAdapter(categories);
        categoryGrid.setOnKeyInterceptListener(event -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT
                    && items.getItemCount() > 0) {
                itemGrid.requestFocus();
                return true;
            }
            return false;
        });
        panel.addView(categoryGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        return panel;
    }

    @Override
    protected View buildContent() {
        LinearLayout column = Nator.column(context);
        Nator.pad(column, 24, 18, 24, 12);
        itemsTitle = Nator.text(context, "", 20, Nator.W600, Nator.HEADING);
        column.addView(itemsTitle);
        TextView itemsHint = Nator.text(context, Tr.t("OK shows or hides one item. New items from your provider stay hidden until you allow them.",
                "زر OK يُظهر عنصرًا أو يخفيه. ما يضيفه المزوّد لاحقًا يبقى مخفيًا حتى تسمح به."), 12.5f, Nator.W400, Nator.BODY);
        column.addView(itemsHint, Nator.margins(context, Nator.matchWidth(), 0, 4, 0, 10));
        items = new CheckRowAdapter(context, (row, position) -> toggleItem(position), null);
        itemGrid = Widgets.verticalGrid(context, 2, 2, 12);
        itemGrid.setAdapter(items);
        itemGrid.setOnKeyInterceptListener(event -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
                    && itemGrid.getSelectedPosition() % 2 == 0) {
                categoryGrid.requestFocus();
                return true;
            }
            return false;
        });
        column.addView(itemGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        return column;
    }

    private void styleTabs() {
        for (int i = 0; i < typeTabs.getChildCount(); i++) {
            TextView tab = (TextView) typeTabs.getChildAt(i);
            boolean active = TYPES[i] == type;
            tab.setBackground(Nator.states(active ? Nator.pill(context, Nator.SELECTION_SURFACE)
                    : Nator.roundedStroke(context, 0, 999, 1, Nator.OUTLINE_VARIANT), Nator.pill(context, Nator.HOVER_OVERLAY)));
            tab.setTextColor(active ? Nator.SELECTION : Nator.BODY);
        }
    }

    private void loadType() {
        sections = new ArrayList<>();
        shownSection = null;
        if (host.catalog.cachedUnfiltered(type) == null) {
            host.catalog.load(type, false, this);
        }
        refreshSections();
    }

    private void refreshSections() {
        List<XtreamMediaSection> loaded = host.catalog.cachedUnfiltered(type);
        sections = loaded == null ? new ArrayList<>() : loaded;
        List<CheckRowAdapter.Row> rows = new ArrayList<>();
        for (XtreamMediaSection section : sections) {
            rows.add(categoryRow(section));
        }
        categories.setRows(rows);
        if (sections.isEmpty()) {
            itemsTitle.setText(Tr.t("Loading…", "جارٍ التحميل…"));
            items.setRows(new ArrayList<>());
        } else {
            int index = shownSection == null ? 0 : Math.max(0, sections.indexOf(shownSection));
            showItems(index);
        }
    }

    private CheckRowAdapter.Row categoryRow(XtreamMediaSection section) {
        KidsPolicy policy = host.kids.policy();
        int visible = 0;
        for (XtreamMediaItem item : section.items()) {
            if (policy.allows(item, section.title())) {
                visible++;
            }
        }
        int total = section.items().size();
        CheckRowAdapter.State state = visible == 0 ? CheckRowAdapter.State.OFF
                : visible == total ? CheckRowAdapter.State.ON : CheckRowAdapter.State.PARTIAL;
        return new CheckRowAdapter.Row(KidsPolicy.categoryKey(section, type), section.title(), visible + "/" + total, state);
    }

    private void showItems(int position) {
        if (position < 0 || position >= sections.size()) {
            return;
        }
        XtreamMediaSection section = sections.get(position);
        shownSection = section;
        categories.setSelectedKey(KidsPolicy.categoryKey(section, type));
        itemsTitle.setText(section.title());
        KidsPolicy policy = host.kids.policy();
        List<CheckRowAdapter.Row> rows = new ArrayList<>();
        for (XtreamMediaItem item : section.items()) {
            rows.add(new CheckRowAdapter.Row(KidsPolicy.itemKey(type, item.id()), item.title(), null,
                    policy.allows(item, section.title()) ? CheckRowAdapter.State.ON : CheckRowAdapter.State.OFF));
        }
        items.setRows(rows);
    }

    private void toggleCategory(int position) {
        XtreamMediaSection section = sections.get(position);
        String key = KidsPolicy.categoryKey(section, type);
        boolean allow = categories.rows().get(position).state != CheckRowAdapter.State.ON;
        List<String> keys = new ArrayList<>();
        for (XtreamMediaItem item : section.items()) {
            keys.add(KidsPolicy.itemKey(type, item.id()));
        }
        host.kids.setCategory(key, allow, keys);
        categories.replace(position, categoryRow(section));
        showItems(position);
    }

    private void toggleItem(int position) {
        if (shownSection == null) {
            return;
        }
        XtreamMediaItem item = shownSection.items().get(position);
        KidsPolicy policy = host.kids.policy();
        boolean visible = policy.allows(item, shownSection.title());
        boolean categoryAllowed = policy.allowsCategory(KidsPolicy.categoryKey(type, item.categoryId(), shownSection.title()));
        host.kids.setItemVisible(KidsPolicy.itemKey(type, item.id()), categoryAllowed, !visible);
        items.replace(position, new CheckRowAdapter.Row(KidsPolicy.itemKey(type, item.id()), item.title(), null,
                visible ? CheckRowAdapter.State.OFF : CheckRowAdapter.State.ON));
        int sectionIndex = sections.indexOf(shownSection);
        if (sectionIndex >= 0) {
            categories.replace(sectionIndex, categoryRow(shownSection));
        }
    }
}
