package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.data.XtreamMediaSection;
import com.ztv.player.data.WatchProgressStore;
import com.ztv.player.ui.Tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Xtream catalog section (`/xtreams/:id/vod|series|live`): the categories context panel,
 * the category content header with sort chip + Refine, and the cover grid. Live TV
 * switches to IPTVnator's live layout (channel sidebar + embedded player) once a
 * category is selected.
 */
public final class CatalogPage extends Page implements Catalog.LibraryListener {
    private enum Sort {
        SERVER("Server sorting", "ترتيب الخادم"),
        DATE_DESC("Date Added (Latest First)", "تاريخ الإضافة (الأحدث أولًا)"),
        DATE_ASC("Date Added (Oldest First)", "تاريخ الإضافة (الأقدم أولًا)"),
        NAME_ASC("Name A-Z", "الاسم (أ-ي)"),
        NAME_DESC("Name Z-A", "الاسم (ي-أ)"),
        TOP_RATED("Top rated", "الأعلى تقييمًا"),
        LOWEST_RATED("Lowest rated first", "الأقل تقييمًا أولًا");

        private final String english;
        private final String arabic;

        Sort(String english, String arabic) {
            this.english = english;
            this.arabic = arabic;
        }

        String label() {
            return Tr.t(english, arabic);
        }
    }

    private enum CategorySort {
        SERVER("Server sorting", "ترتيب الخادم", "dns"),
        NAME_ASC("Name A-Z", "الاسم (أ-ي)", "sort_by_alpha"),
        NAME_DESC("Name Z-A", "الاسم (ي-أ)", "sort_by_alpha");

        private final String english;
        private final String arabic;
        final String icon;

        CategorySort(String english, String arabic, String icon) {
            this.english = english;
            this.arabic = arabic;
            this.icon = icon;
        }

        String label() {
            return Tr.t(english, arabic);
        }
    }

    private final XtreamContentType type;
    private final Context context;
    private List<XtreamMediaSection> sections;
    private String selectedKey;
    private String query = "";
    private Sort sort;
    private int minRating;
    private CategorySort categorySort = CategorySort.SERVER;
    private String categoryFilter = "";
    private boolean showingData;

    // Panel
    private LinearLayout panel;
    private FrameLayout panelBody;
    private VerticalGridView categoryList;
    private CategoryAdapter categoryAdapter;
    private TextView sortIcon;
    private LinearLayout categorySearchRow;

    // Content
    private FrameLayout contentRoot;
    private VerticalGridView grid;
    private PosterGridAdapter gridAdapter;
    private TextView headerTitle;
    private TextView headerSubtitle;
    private TextView sortChipLabel;
    private TextView ratingChip;
    private int columns = 5;
    private int lastCategoryPosition;
    private int lastGridPosition;

    // Live layout
    private LinearLayout liveLayout;
    private VerticalGridView channelList;
    private ChannelRowAdapter channelAdapter;
    private TextView channelTitle;
    private TextView channelCount;
    private InlinePlayer inlinePlayer;
    private LinearLayout channelSidebar;
    private boolean panelsCollapsed;
    private View restoreButton;
    private XtreamMediaItem playingChannel;
    private int lastChannelPosition;

    public CatalogPage(WorkspaceActivity host, XtreamContentType type) {
        super(host);
        this.type = type;
        this.context = host;
        this.sort = type == XtreamContentType.LIVE ? Sort.SERVER : Sort.DATE_DESC;
    }

    @Override
    public Section section() {
        return type == XtreamContentType.LIVE ? Section.LIVE
                : type == XtreamContentType.SERIES ? Section.SERIES : Section.VOD;
    }

    private String sectionLabel() {
        return type == XtreamContentType.LIVE ? Tr.t("Live TV", "القنوات") : type == XtreamContentType.SERIES ? Tr.t("Series", "المسلسلات") : Tr.t("Movies", "الأفلام");
    }

    private String panelTitle() {
        return type == XtreamContentType.LIVE ? Tr.t("Live Categories", "فئات القنوات")
                : type == XtreamContentType.SERIES ? Tr.t("Series Categories", "فئات المسلسلات") : Tr.t("Movie Categories", "فئات الأفلام");
    }

    // ─── Search capability ──────────────────────────────────────────────────

    @Override
    public String searchScope() {
        XtreamMediaSection section = selectedSection();
        return sectionLabel() + " / " + (section == null ? Tr.t("All items", "كل العناصر") : section.title());
    }

    @Override
    public String searchQuery() {
        return query;
    }

    @Override
    public void onSearch(String value) {
        query = value == null ? "" : value.trim();
        renderContent(false);
    }

    @Override
    public boolean canRefresh() {
        return true;
    }

    @Override
    public void onRefresh() {
        host.catalog.load(type, true, this);
    }

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void onShown() {
        host.catalog.addListener(this);
        if (sections == null) {
            List<XtreamMediaSection> cached = host.catalog.cached(type);
            if (cached != null) {
                adopt(cached);
            }
            host.catalog.load(type, false, this);
        } else if (inlinePlayer != null) {
            inlinePlayer.resume();
        }
    }

    @Override
    public void onHidden() {
        host.catalog.removeListener(this);
        if (inlinePlayer != null) {
            inlinePlayer.pause();
        }
    }

    @Override
    public void onDestroy() {
        host.catalog.removeListener(this);
        releaseInlinePlayback();
    }

    @Override
    public void onPlaybackStarting() {
        releaseInlinePlayback();
    }

    @Override
    public boolean playingMedia() {
        return inlinePlayer != null && inlinePlayer.isPlaying();
    }

    @Override
    public void onHostStopped() {
        releaseInlinePlayback();
    }

    @Override
    public void onPlaybackReturned() {
        if (gridAdapter != null) {
            gridAdapter.notifyDataSetChanged();
        }
        if (inlinePlayer != null && playingChannel != null) {
            inlinePlayer.play(host.streamUrl(playingChannel));
            focusChannelList();
        }
    }

    @Override
    public void onLibrary(XtreamContentType loadedType, List<XtreamMediaSection> loaded, boolean fromNetwork) {
        if (loadedType != type) {
            return;
        }
        boolean focusInside = contentRoot != null && contentRoot.hasFocus();
        if (showingData && fromNetwork && focusInside) {
            // Browsing the cached copy: adopt silently, never yank focus (pitfall 6).
            sections = loaded;
            refreshCategoryRows();
            return;
        }
        adopt(loaded);
    }

    @Override
    public void onLibraryError(XtreamContentType loadedType, Exception error) {
        if (loadedType != type || showingData) {
            return;
        }
        showError();
    }

    private void adopt(List<XtreamMediaSection> loaded) {
        boolean first = !showingData;
        sections = loaded;
        showingData = true;
        refreshCategoryRows();
        renderContent(first);
        if (first && host.top() == this) {
            host.runLater(this::requestInitialFocus, 50);
        }
    }

    @Override
    public void requestInitialFocus() {
        if (!showingData) {
            View rail = host.railButton(section());
            if (rail != null) {
                rail.requestFocus();
            }
            return;
        }
        if (liveLayout != null && liveLayout.getParent() != null && channelList != null) {
            channelList.requestFocus();
        } else if (grid != null && gridAdapter != null && gridAdapter.getItemCount() > 0) {
            grid.requestFocus();
        } else if (categoryList != null) {
            categoryList.requestFocus();
        }
    }

    // ─── Context panel ──────────────────────────────────────────────────────

    @Override
    protected View buildContextPanel() {
        panel = Nator.column(context);
        LinearLayout header = Nator.row(context);
        Nator.pad(header, 6, 0, 0, 0);
        header.addView(Nator.line(context, panelTitle(), 15.04f, Nator.W600, Nator.ON_SURFACE), new LinearLayout.LayoutParams(0, -2, 1f));
        View search = host.iconButton("search", 18, Tr.t("Search categories", "البحث في الفئات"), v -> toggleCategorySearch());
        header.addView(search, Nator.size(context, 30, 30));
        FrameLayout sortButton = (FrameLayout) host.iconButton(categorySort.icon, 18, Tr.t("Sort categories", "ترتيب الفئات"), v -> showCategorySortMenu(v));
        sortIcon = (TextView) sortButton.getChildAt(0);
        header.addView(sortButton, Nator.size(context, 30, 30));
        header.addView(host.iconButton("tune", 18, Tr.t("Manage categories", "إدارة الفئات"), v -> showManageCategories()), Nator.size(context, 30, 30));
        if (type == XtreamContentType.LIVE) {
            header.addView(host.iconButton("chevron_left", 18, Tr.t("Hide panels", "إخفاء القوائم"), v -> collapsePanels()), Nator.size(context, 30, 30));
        }
        panel.addView(header, new LinearLayout.LayoutParams(-1, Nator.px(context, 36)));
        panel.addView(Nator.hairline(context, Nator.OUTLINE_VARIANT), Nator.margins(context, new LinearLayout.LayoutParams(-1, Math.max(1, Nator.px(context, 1))), 0, 6, 0, 6));

        categorySearchRow = Nator.row(context);
        categorySearchRow.setBackground(Nator.roundedStroke(context, Nator.SEARCH_BG, 10, 1, Nator.SEARCH_BORDER));
        Nator.pad(categorySearchRow, 10, 0, 10, 0);
        categorySearchRow.addView(Nator.icon(context, "search", 16, Nator.BODY), Nator.margins(context, Nator.size(context, 16, 16), 0, 0, 8, 0));
        EditText filter = new EditText(context);
        filter.setBackground(null);
        filter.setPadding(0, 0, 0, 0);
        filter.setSingleLine(true);
        filter.setHint(Tr.t("Filter categories", "تصفية الفئات"));
        filter.setHintTextColor(Nator.BODY);
        filter.setTextColor(Nator.ON_SURFACE);
        filter.setTypeface(Nator.face(Nator.W400));
        filter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14 * Nator.K);
        filter.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                categoryFilter = s.toString().trim();
                refreshCategoryRows();
            }
        });
        categorySearchRow.addView(filter, new LinearLayout.LayoutParams(0, -1, 1f));
        categorySearchRow.setVisibility(View.GONE);
        panel.addView(categorySearchRow, Nator.margins(context, new LinearLayout.LayoutParams(-1, Nator.px(context, 34)), 8, 0, 8, 6));

        panelBody = new FrameLayout(context);
        categoryAdapter = new CategoryAdapter(context, false, this::onCategoryPicked);
        categoryList = Widgets.verticalGrid(context, 1, 2, 0);
        Nator.pad(categoryList, 8, 0, 8, 12);
        categoryList.setAdapter(categoryAdapter);
        categoryList.setWindowAlignmentOffsetPercent(40f);
        categoryList.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child,
                                                  int position, int subposition) {
                if (position >= 0) {
                    lastCategoryPosition = position;
                }
            }
        });
        categoryList.setOnKeyInterceptListener(event -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                rememberCategoryPosition();
                if (event.getKeyCode() == Widgets.forwardKey()) {
                    focusContentFromCategory();
                    return true;
                }
                if (event.getKeyCode() == Widgets.backKey()) {
                    focusRail();
                    return true;
                }
            }
            return Widgets.escapeEdge(categoryList, event, 1, false);
        });
        panelBody.addView(categoryList, new FrameLayout.LayoutParams(-1, -1));
        if (!showingData) {
            panelBody.addView(Widgets.categorySkeleton(context, 16), new FrameLayout.LayoutParams(-1, -1));
        }
        panel.addView(panelBody, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (sections != null) {
            refreshCategoryRows();
        }
        return panel;
    }

    private void toggleCategorySearch() {
        boolean show = categorySearchRow.getVisibility() != View.VISIBLE;
        categorySearchRow.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            categorySearchRow.getChildAt(1).requestFocus();
        } else {
            ((EditText) categorySearchRow.getChildAt(1)).setText("");
        }
    }

    private void showCategorySortMenu(View anchor) {
        LinearLayout menu = host.menuPanel();
        for (CategorySort option : CategorySort.values()) {
            host.menuItem(menu, option.label(), option.icon, option == categorySort, () -> {
                categorySort = option;
                Nator.setIcon(sortIcon, option.icon);
                refreshCategoryRows();
            });
        }
        host.showMenu(anchor, menu);
    }

    private List<XtreamMediaSection> visibleSections() {
        List<XtreamMediaSection> result = new ArrayList<>();
        if (sections == null) {
            return result;
        }
        Set<String> hidden = host.settings.hiddenSections(type);
        for (XtreamMediaSection section : sections) {
            if (!hidden.contains(section.title())) {
                result.add(section);
            }
        }
        return result;
    }

    private void refreshCategoryRows() {
        if (categoryAdapter == null || sections == null) {
            return;
        }
        if (panelBody != null && panelBody.getChildCount() > 1) {
            panelBody.removeViewAt(1);
        }
        List<CategoryAdapter.Row> rows = new ArrayList<>();
        String needle = categoryFilter.toLowerCase(Locale.ROOT);
        for (XtreamMediaSection section : visibleSections()) {
            if (!needle.isEmpty() && !section.title().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            rows.add(new CategoryAdapter.Row(section.title(), section.title(), section.items().size()));
        }
        if (categorySort == CategorySort.NAME_ASC) {
            Collections.sort(rows, (a, b) -> a.label.compareToIgnoreCase(b.label));
        } else if (categorySort == CategorySort.NAME_DESC) {
            Collections.sort(rows, (a, b) -> b.label.compareToIgnoreCase(a.label));
        }
        categoryAdapter.setRows(rows);
        categoryAdapter.setSelectedKey(selectedKey);
    }

    private void onCategoryPicked(CategoryAdapter.Row row, int position) {
        lastCategoryPosition = position;
        // OK on the selected category returns to "All items".
        selectedKey = row.key.equals(selectedKey) ? null : row.key;
        categoryAdapter.setSelectedKey(selectedKey);
        host.updateSearchScope(this);
        renderContent(true);
        if (type == XtreamContentType.LIVE && selectedKey != null && channelList != null) {
            focusChannelList();
        }
    }

    private XtreamMediaSection selectedSection() {
        if (selectedKey == null || sections == null) {
            return null;
        }
        for (XtreamMediaSection section : sections) {
            if (section.title().equals(selectedKey)) {
                return section;
            }
        }
        return null;
    }

    private void showManageCategories() {
        if (sections == null) {
            return;
        }
        new ManageCategoriesDialog(host, type, sections, this::refreshCategoryRows).show();
    }

    // ─── Content ────────────────────────────────────────────────────────────

    @Override
    protected View buildContent() {
        contentRoot = new FrameLayout(context);
        contentRoot.setClipChildren(false);
        if (!showingData) {
            contentRoot.addView(gridSkeleton(), new FrameLayout.LayoutParams(-1, -1));
        }
        return contentRoot;
    }

    private View gridSkeleton() {
        LinearLayout column = Nator.column(context);
        LinearLayout band = Nator.column(context);
        band.setBackgroundColor(Nator.WIDGET_HEADER_BG);
        Nator.pad(band, 16, 10, 16, 12);
        band.addView(Widgets.skeletonBar(context, 90, 12));
        band.addView(Widgets.skeletonBar(context, 60, 9), Nator.margins(context, Nator.wrap(), 0, 10, 0, 0));
        column.addView(band, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout cards = Nator.row(context);
        cards.setGravity(Gravity.TOP);
        Nator.pad(cards, 20, 16, 20, 0);
        int width = cardWidth();
        for (int i = 0; i < columns; i++) {
            View card = new View(context);
            card.setBackground(Nator.rounded(context, Nator.THUMB_BG, 10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width,
                    type == XtreamContentType.LIVE ? Math.round(width * 89f / 159f) : Math.round(width * 1.5f));
            params.setMarginEnd(Nator.px(context, gap()));
            cards.addView(card, params);
        }
        column.addView(cards);
        return column;
    }

    private void showError() {
        contentRoot.removeAllViews();
        LinearLayout box = Widgets.emptyState(context, "cloud_off", Tr.t("Portal unavailable", "تعذّر الوصول إلى الخادم"),
                Tr.t("The portal could not be reached. Check the server URL or retry when the source is available again.", "تعذّر الوصول إلى الخادم. تحقق من عنوان الخادم أو أعد المحاولة حين يعود المصدر متاحًا."));
        LinearLayout actions = Nator.row(context);
        actions.setGravity(Gravity.CENTER);
        actions.addView(Widgets.outlinedPill(context, null, Tr.t("Home", "الرئيسية"), () -> host.openSection(Section.DASHBOARD)),
                Nator.margins(context, Nator.wrap(), 0, 0, 8, 0));
        actions.addView(Widgets.outlinedPill(context, null, Tr.t("Playlist Settings", "إعدادات الاشتراك"), () -> host.openSection(Section.SOURCES)));
        box.addView(actions, Nator.margins(context, Nator.wrap(), 0, 22, 0, 0));
        TextView retry = host.dialogFilledButton(Tr.t("Retry", "إعادة المحاولة"), () -> {
            contentRoot.removeAllViews();
            contentRoot.addView(gridSkeleton(), new FrameLayout.LayoutParams(-1, -1));
            host.catalog.load(type, true, this);
        });
        box.addView(retry, Nator.margins(context, Nator.wrap(), 0, 26, 0, 0));
        contentRoot.addView(box, new FrameLayout.LayoutParams(-1, -1));
        retry.requestFocus();
    }

    private float gap() {
        String size = host.prefs.string(SettingsPage.PREF_COVER_SIZE, "medium");
        return "small".equals(size) ? 12 : "large".equals(size) ? 20 : 16;
    }

    private float minCoverWidth() {
        String size = host.prefs.string(SettingsPage.PREF_COVER_SIZE, "medium");
        return "small".equals(size) ? 120 : "large".equals(size) ? 200 : 148;
    }

    /** grid-template-columns: repeat(auto-fill, minmax(min, 1fr)) over the content width. */
    private int cardWidth() {
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        int available = screen - Nator.px(context, 60) - Nator.px(context, 323) - Nator.px(context, 40);
        int gapPx = Nator.px(context, gap());
        int minPx = Nator.px(context, minCoverWidth());
        columns = Math.max(1, (available + gapPx) / (minPx + gapPx));
        return (available - gapPx * (columns - 1)) / columns;
    }

    private List<XtreamMediaItem> currentItems() {
        XtreamMediaSection section = selectedSection();
        List<XtreamMediaItem> base = section != null ? new ArrayList<>(section.items())
                : Catalog.allItems(visibleSections());
        List<XtreamMediaItem> result = new ArrayList<>();
        String needle = query.toLowerCase(Locale.ROOT);
        for (XtreamMediaItem item : base) {
            if (!needle.isEmpty() && !item.title().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            if (minRating > 0 && ratingOf(item) < minRating) {
                continue;
            }
            result.add(item);
        }
        Comparator<XtreamMediaItem> comparator = null;
        switch (sort) {
            case DATE_DESC:
                comparator = (a, b) -> Long.compare(b.addedAt(), a.addedAt());
                break;
            case DATE_ASC:
                comparator = (a, b) -> Long.compare(a.addedAt(), b.addedAt());
                break;
            case NAME_ASC:
                comparator = (a, b) -> a.title().compareToIgnoreCase(b.title());
                break;
            case NAME_DESC:
                comparator = (a, b) -> b.title().compareToIgnoreCase(a.title());
                break;
            case TOP_RATED:
                comparator = (a, b) -> Double.compare(ratingOf(b), ratingOf(a));
                break;
            case LOWEST_RATED:
                comparator = (a, b) -> Double.compare(ratingOf(a), ratingOf(b));
                break;
            default:
                break;
        }
        if (comparator != null) {
            Collections.sort(result, comparator);
        }
        return result;
    }

    private static double ratingOf(XtreamMediaItem item) {
        try {
            return item.rating().isEmpty() ? 0 : Double.parseDouble(item.rating());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void renderContent(boolean resetScroll) {
        if (contentRoot == null || sections == null) {
            return;
        }
        if (type == XtreamContentType.LIVE && selectedKey != null) {
            renderLiveLayout();
            return;
        }
        releaseLiveLayout();
        if (grid == null) {
            buildGridContent();
        } else if (contentRoot.getChildCount() == 0 || contentRoot.getChildAt(0) != gridContainer) {
            contentRoot.removeAllViews();
            contentRoot.addView(gridContainer, new FrameLayout.LayoutParams(-1, -1));
        }
        List<XtreamMediaItem> items = currentItems();
        XtreamMediaSection section = selectedSection();
        headerTitle.setText(section == null ? Tr.t("All items", "كل العناصر") : section.title());
        headerSubtitle.setText(Nator.plural(items.size(), "item", "items", "عنصر واحد", "عنصران", "عناصر", "عنصر"));
        if (sortChipLabel != null) {
            sortChipLabel.setText(sort.label());
        }
        if (ratingChip != null) {
            ratingChip.setVisibility(minRating > 0 ? View.VISIBLE : View.GONE);
            ratingChip.setText(minRating + ".0 and higher");
        }
        gridAdapter.setShowTitles(host.prefs.bool(SettingsPage.PREF_SHOW_TITLES, true) || !query.isEmpty());
        gridAdapter.setItems(items);
        Widgets.gridKeys(grid, columns, this::focusCategoryList);
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (resetScroll && !items.isEmpty()) {
            grid.setSelectedPosition(0);
        }
    }

    private FrameLayout gridContainer;
    private View emptyView;

    private void buildGridContent() {
        gridContainer = new FrameLayout(context);
        LinearLayout column = Nator.column(context);
        boolean live = type == XtreamContentType.LIVE;

        // .category-content-header
        LinearLayout band = Nator.column(context);
        band.setBackgroundColor(Nator.WIDGET_HEADER_BG);
        Nator.pad(band, 16, 8, 16, 8);
        LinearLayout titleRow = Nator.row(context);
        headerTitle = Nator.line(context, Tr.t("All items", "كل العناصر"), 15.2f, Nator.W600, Nator.HEADING);
        titleRow.addView(headerTitle);
        if (live) {
            headerSubtitle = Nator.countChip(context, "", Nator.BODY);
            titleRow.addView(headerSubtitle, Nator.margins(context, Nator.wrap(), 10, 0, 0, 0));
            band.setMinimumHeight(Nator.px(context, 52));
            band.setGravity(Gravity.CENTER_VERTICAL);
            band.addView(titleRow);
        } else {
            band.addView(titleRow);
            headerSubtitle = Nator.text(context, "", 12.48f, Nator.W500, Nator.BODY);
            band.addView(headerSubtitle, Nator.margins(context, Nator.wrap(), 0, 2, 0, 0));
            LinearLayout chips = Nator.row(context);
            LinearLayout sortChip = Nator.row(context);
            sortChip.setBackground(Nator.states(
                    Nator.roundedStroke(context, Nator.REFINE_BG, 999, 1, 0x0FFFFFFF),
                    Nator.roundedStroke(context, Nator.CARD_HOVER_BG, 999, 1, 0x1FFFFFFF)));
            Nator.pad(sortChip, 10, 0, 12, 0);
            Nator.focusable(sortChip, 999);
            sortChip.addView(Nator.icon(context, "sort", 18, Nator.ON_SURFACE_VARIANT), Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 6, 0));
            sortChipLabel = Nator.line(context, sort.label(), 12.48f, Nator.W600, Nator.ON_SURFACE_VARIANT);
            sortChip.addView(sortChipLabel);
            sortChip.setOnClickListener(v -> showRefine(v));
            chips.addView(sortChip, Nator.size(context, -2, 34));
            ratingChip = Nator.text(context, "", 12.48f, Nator.W600, Nator.SELECTION);
            ratingChip.setGravity(Gravity.CENTER);
            ratingChip.setBackground(Nator.selection(context, 999));
            Nator.pad(ratingChip, 12, 0, 12, 0);
            Nator.focusable(ratingChip, 999);
            ratingChip.setOnClickListener(v -> {
                minRating = 0;
                renderContent(true);
            });
            ratingChip.setVisibility(View.GONE);
            chips.addView(ratingChip, Nator.margins(context, Nator.size(context, -2, 34), 8, 0, 0, 0));
            chips.addView(Nator.spacer(context));
            LinearLayout refine = Nator.row(context);
            refine.setBackground(Nator.states(
                    Nator.roundedStroke(context, 0, 999, 1, 0x14FFFFFF),
                    Nator.roundedStroke(context, Nator.HOVER_OVERLAY, 999, 1, 0x29FFFFFF)));
            Nator.pad(refine, 13, 0, 16, 0);
            Nator.focusable(refine, 999);
            refine.addView(Nator.icon(context, "tune", 18, Nator.ON_SURFACE), Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 8, 0));
            refine.addView(Nator.text(context, Tr.t("Refine", "تصفية"), 14, Nator.W500, Nator.ON_SURFACE));
            refine.setOnClickListener(v -> showRefine(v));
            chips.addView(refine, Nator.size(context, -2, 40));
            band.addView(chips, Nator.margins(context, Nator.matchWidth(), 0, 8, 0, 0));
        }
        column.addView(band, new LinearLayout.LayoutParams(-1, -2));

        int width = cardWidth();
        gridAdapter = new PosterGridAdapter(context, PosterGridAdapter.Style.GRID, width, new PosterGridAdapter.Listener() {
            @Override
            public void onOpen(XtreamMediaItem item, int position) {
                lastGridPosition = position;
                if (item.type() == XtreamContentType.LIVE) {
                    openChannelFromGrid(item);
                } else {
                    host.openDetail(item);
                }
            }

            @Override
            public void onMenu(XtreamMediaItem item, int position) {
                boolean nowFavorite = host.favorites.toggle(item);
                host.snack(nowFavorite ? Tr.t("Added to favorites", "أُضيف إلى المفضلة") : Tr.t("Removed from favorites", "أُزيل من المفضلة"));
            }
        });
        Map<Integer, WatchProgressStore.EntrySnapshot> snapshots =
                type == XtreamContentType.MOVIE ? host.progress.snapshotsFor(XtreamContentType.MOVIE) : null;
        if (snapshots != null) {
            gridAdapter.setProgressSource(item -> {
                WatchProgressStore.EntrySnapshot snapshot = host.progress.snapshot(XtreamContentType.MOVIE, item.id());
                return snapshot != null && snapshot.inProgress() ? snapshot.progressPercent() : 0;
            });
        }
        grid = Widgets.verticalGrid(context, columns, live ? 16 : 16, gap());
        grid.setAdapter(gridAdapter);
        grid.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child,
                                                  int position, int subposition) {
                if (position >= 0) {
                    lastGridPosition = position;
                }
            }
        });
        Nator.pad(grid, 20, live ? 17 : 16, 20, 24);
        FrameLayout gridFrame = new FrameLayout(context);
        gridFrame.setClipChildren(false);
        gridFrame.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        emptyView = Widgets.emptyState(context, "search_off", Tr.t("Nothing found", "لا توجد نتائج"),
                query.isEmpty() ? Tr.t("This category has no items.", "هذه الفئة فارغة.") : Tr.t("No results match your search.", "لا نتائج تطابق بحثك."));
        emptyView.setVisibility(View.GONE);
        gridFrame.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        column.addView(gridFrame, new LinearLayout.LayoutParams(-1, 0, 1f));
        gridContainer.addView(column, new FrameLayout.LayoutParams(-1, -1));
        contentRoot.removeAllViews();
        contentRoot.addView(gridContainer, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Refine menu: "Sort by" + "Minimum rating" sections. */
    private void showRefine(View anchor) {
        LinearLayout menu = host.menuPanel();
        menu.addView(menuSectionLabel(Tr.t("Sort by", "الترتيب حسب")));
        for (Sort option : Sort.values()) {
            if (option == Sort.SERVER) {
                continue;
            }
            host.menuItem(menu, option.label(), null, option == sort, () -> {
                sort = option;
                renderContent(true);
            });
        }
        menu.addView(menuSectionLabel(Tr.t("Minimum rating", "أقل تقييم")));
        int[] ratings = {0, 5, 6, 7, 8};
        for (int value : ratings) {
            host.menuItem(menu, value == 0 ? Tr.t("Any rating", "أي تقييم") : Tr.t(value + ".0 and higher", value + ".0 فأعلى"), value == 0 ? null : "star",
                    value == minRating, () -> {
                        minRating = value;
                        renderContent(true);
                    });
        }
        host.showMenu(anchor, menu);
    }

    private TextView menuSectionLabel(String label) {
        TextView view = Nator.text(context, label.toUpperCase(Locale.ROOT), 11, Nator.W600, Nator.EYEBROW);
        view.setLetterSpacing(0.06f);
        Nator.pad(view, 16, 10, 16, 6);
        return view;
    }

    // ─── Live layout ────────────────────────────────────────────────────────

    private void openChannelFromGrid(XtreamMediaItem item) {
        String key = null;
        for (XtreamMediaSection section : visibleSections()) {
            for (XtreamMediaItem candidate : section.items()) {
                if (candidate.id() == item.id()) {
                    key = section.title();
                    break;
                }
            }
            if (key != null) {
                break;
            }
        }
        if (key == null) {
            host.play(item, null);
            return;
        }
        selectedKey = key;
        categoryAdapter.setSelectedKey(key);
        host.updateSearchScope(this);
        renderLiveLayout();
        playChannel(item);
        int index = channelAdapter.indexOf(item.id());
        if (index >= 0) {
            channelList.setSelectedPosition(index);
            channelList.post(() -> channelList.requestFocus());
        }
    }

    private void renderLiveLayout() {
        if (liveLayout == null) {
            buildLiveLayout();
        }
        if (liveLayout.getParent() == null) {
            contentRoot.removeAllViews();
            contentRoot.addView(liveLayout, new FrameLayout.LayoutParams(-1, -1));
        }
        XtreamMediaSection section = selectedSection();
        List<XtreamMediaItem> items = currentItems();
        channelTitle.setText(section == null ? Tr.t("Channels", "القنوات") : section.title());
        channelCount.setText(String.valueOf(items.size()));
        channelAdapter.setFavoriteIds(host.favorites.idSet(XtreamContentType.LIVE));
        channelAdapter.setItems(items);
        if (playingChannel != null) {
            channelAdapter.setPlaying(playingChannel.id());
        }
    }

    private void buildLiveLayout() {
        liveLayout = Nator.row(context);
        liveLayout.setGravity(Gravity.TOP);

        channelSidebar = Nator.column(context);
        channelSidebar.setBackgroundColor(0xFF1A1F2B);
        LinearLayout header = Nator.row(context);
        header.setBackgroundColor(Nator.WIDGET_HEADER_BG);
        Nator.pad(header, 12, 0, 8, 0);
        channelTitle = Nator.line(context, "", 15.04f, Nator.W600, Nator.ON_SURFACE);
        header.addView(channelTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        channelCount = Nator.text(context, "", 11.52f, Nator.W500, Nator.ON_SURFACE_VARIANT);
        channelCount.setBackground(Nator.pill(context, Nator.REFINE_BG));
        Nator.pad(channelCount, 8, 2, 8, 2);
        header.addView(channelCount, Nator.margins(context, Nator.wrap(), 8, 0, 8, 0));
        header.addView(host.iconButton("sort_by_alpha", 24, Tr.t("Sort channels", "ترتيب القنوات"), this::showChannelSortMenu), Nator.size(context, 40, 40));
        header.addView(host.iconButton("chevron_left", 24, Tr.t("Hide panels", "إخفاء القوائم"), v -> collapsePanels()), Nator.size(context, 40, 40));
        channelSidebar.addView(header, new LinearLayout.LayoutParams(-1, Nator.px(context, 56)));

        channelAdapter = new ChannelRowAdapter(context, host.catalog, host.favorites.idSet(XtreamContentType.LIVE),
                new ChannelRowAdapter.Listener() {
                    @Override
                    public void onChannel(XtreamMediaItem item, int position) {
                        lastChannelPosition = position;
                        if (playingChannel != null && playingChannel.id() == item.id()) {
                            host.play(item, channelAdapter.items());
                        } else {
                            playChannel(item);
                        }
                    }

                    @Override
                    public void onToggleFavorite(XtreamMediaItem item, int position) {
                        boolean nowFavorite = host.favorites.toggle(item);
                        channelAdapter.setFavoriteIds(host.favorites.idSet(XtreamContentType.LIVE));
                        host.snack(nowFavorite ? Tr.t("Added to favorites", "أُضيف إلى المفضلة") : Tr.t("Removed from favorites", "أُزيل من المفضلة"));
                    }

                    @Override
                    public void onRowFocused(XtreamMediaItem item, int position) {
                    }
                });
        channelList = Widgets.verticalGrid(context, 1, 1, 0);
        channelList.setAdapter(channelAdapter);
        Nator.pad(channelList, 0, 1, 0, 12);
        channelList.setWindowAlignmentOffsetPercent(40f);
        channelList.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child,
                                                  int position, int subposition) {
                if (position >= 0) {
                    lastChannelPosition = position;
                }
            }
        });
        // ArrowLeft from a channel returns to the selected category.
        channelList.setOnKeyInterceptListener(event -> {
            if (Widgets.escapeEdge(channelList, event, 1, false)) {
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == Widgets.backKey()
                    && !panelsCollapsed && categoryList != null) {
                rememberChannelPosition();
                focusCategoryList();
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == Widgets.forwardKey()) {
                rememberChannelPosition();
                focusInlinePreview();
                return true;
            }
            return false;
        });
        channelSidebar.addView(channelList, new LinearLayout.LayoutParams(-1, 0, 1f));
        liveLayout.addView(channelSidebar, new LinearLayout.LayoutParams(Nator.px(context, 390), -1));

        View divider = new View(context);
        divider.setBackgroundColor(Nator.CONTENT_BG);
        liveLayout.addView(divider, new LinearLayout.LayoutParams(Nator.px(context, 9), -1));

        FrameLayout playerArea = new FrameLayout(context);
        inlinePlayer = new InlinePlayer(context);
        inlinePlayer.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (keyCode == Widgets.backKey()) {
                focusChannelList();
                return true;
            }
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                    && playingChannel != null) {
                host.play(playingChannel, channelAdapter.items());
                return true;
            }
            return false;
        });
        playerArea.addView(inlinePlayer, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout idle = Widgets.emptyState(context, "live_tv", Tr.t("Select a channel", "اختر قناة"), Tr.t("Press OK on a channel to start watching. Press OK again for full screen.", "اضغط OK على قناة لبدء المشاهدة، واضغط OK مرة أخرى لملء الشاشة."));
        idle.setTag("idle");
        playerArea.addView(idle, new FrameLayout.LayoutParams(-1, -1));
        restoreButton = host.iconButton("chevron_right", 24, Tr.t("Show panels", "إظهار القوائم"), v -> expandPanels());
        restoreButton.setBackground(Nator.states(Nator.oval(0x99000000), Nator.oval(0xCC000000)));
        FrameLayout.LayoutParams restoreParams = new FrameLayout.LayoutParams(Nator.px(context, 40), Nator.px(context, 40), Gravity.TOP | Gravity.START);
        restoreParams.setMargins(0, Nator.px(context, 12), 0, 0);
        restoreParams.setMarginStart(Nator.px(context, 12));
        restoreParams.setMarginEnd(0);
        playerArea.addView(restoreButton, restoreParams);
        restoreButton.setVisibility(View.GONE);
        liveLayout.addView(playerArea, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private void showChannelSortMenu(View anchor) {
        LinearLayout menu = host.menuPanel();
        Sort[] options = {Sort.SERVER, Sort.NAME_ASC, Sort.NAME_DESC};
        String[] labels = {Tr.t("Server order", "ترتيب الخادم"), Tr.t("Name A-Z", "الاسم (أ-ي)"), Tr.t("Name Z-A", "الاسم (ي-أ)")};
        for (int i = 0; i < options.length; i++) {
            Sort option = options[i];
            host.menuItem(menu, labels[i], null, sort == option, () -> {
                sort = option;
                renderLiveLayout();
            });
        }
        host.showMenu(anchor, menu);
    }

    private void playChannel(XtreamMediaItem item) {
        playingChannel = item;
        channelAdapter.setPlaying(item.id());
        View idle = liveLayout.findViewWithTag("idle");
        if (idle != null) {
            idle.setVisibility(View.GONE);
        }
        host.history.record(item);
        inlinePlayer.play(host.streamUrl(item));
    }

    private void collapsePanels() {
        if (liveLayout == null || liveLayout.getParent() == null) {
            return;
        }
        panelsCollapsed = true;
        if (panel != null) {
            ((View) panel.getParent()).setVisibility(View.GONE);
        }
        channelSidebar.setVisibility(View.GONE);
        restoreButton.setVisibility(View.VISIBLE);
        restoreButton.requestFocus();
        if (inlinePlayer != null) {
            inlinePlayer.flashControls();
        }
    }

    private void expandPanels() {
        panelsCollapsed = false;
        if (panel != null) {
            ((View) panel.getParent()).setVisibility(View.VISIBLE);
        }
        if (channelSidebar != null) {
            channelSidebar.setVisibility(View.VISIBLE);
        }
        if (restoreButton != null) {
            restoreButton.setVisibility(View.GONE);
        }
        if (channelList != null) {
            focusChannelList();
        }
    }

    private void releaseLiveLayout() {
        if (liveLayout != null && liveLayout.getParent() != null) {
            if (panelsCollapsed) {
                expandPanels();
            }
            releaseInlinePlayback();
            playingChannel = null;
            View idle = liveLayout.findViewWithTag("idle");
            if (idle != null) {
                idle.setVisibility(View.VISIBLE);
            }
            contentRoot.removeView(liveLayout);
        }
    }

    /** Releases the preview rather than pausing it so it cannot keep audio alive in background. */
    private void releaseInlinePlayback() {
        if (inlinePlayer != null) {
            inlinePlayer.release();
        }
    }

    @Override
    public void focusFromRail() {
        focusCategoryList();
    }

    @Override
    public boolean onBack() {
        if (panelsCollapsed) {
            expandPanels();
            return true;
        }
        if (type == XtreamContentType.LIVE && selectedKey != null && channelList != null && channelList.hasFocus()) {
            focusCategoryList();
            return true;
        }
        if (grid != null && grid.hasFocus() && categoryList != null) {
            focusCategoryList();
            return true;
        }
        return false;
    }

    /** Explicit TV focus graph: rail ⇄ categories ⇄ content/channels ⇄ live preview. */
    private void focusContentFromCategory() {
        if (type == XtreamContentType.LIVE && selectedKey != null && channelList != null) {
            focusChannelList();
        } else {
            focusGrid();
        }
    }

    private void focusCategoryList() {
        if (categoryList == null || categoryAdapter == null || categoryAdapter.getItemCount() == 0
                || panel == null || panel.getParent() == null || ((View) panel.getParent()).getVisibility() != View.VISIBLE) {
            requestInitialFocus();
            return;
        }
        int selected = categoryAdapter.indexOf(selectedKey);
        int target = selected >= 0 ? selected : boundedPosition(lastCategoryPosition, categoryAdapter.getItemCount());
        categoryList.setSelectedPosition(target);
        categoryList.post(categoryList::requestFocus);
    }

    private void focusGrid() {
        if (grid == null || gridAdapter == null || gridAdapter.getItemCount() == 0) {
            return;
        }
        grid.setSelectedPosition(boundedPosition(lastGridPosition, gridAdapter.getItemCount()));
        grid.post(grid::requestFocus);
    }

    private void focusChannelList() {
        if (channelList == null || channelAdapter == null || channelAdapter.getItemCount() == 0) {
            focusCategoryList();
            return;
        }
        int playing = playingChannel == null ? -1 : channelAdapter.indexOf(playingChannel.id());
        int target = playing >= 0 ? playing : boundedPosition(lastChannelPosition, channelAdapter.getItemCount());
        channelList.setSelectedPosition(target);
        channelList.post(channelList::requestFocus);
    }

    private void focusInlinePreview() {
        if (inlinePlayer != null) {
            inlinePlayer.requestFocus();
        }
    }

    private void focusRail() {
        View rail = host.railButton(section());
        if (rail != null) {
            rail.requestFocus();
        }
    }

    private void rememberCategoryPosition() {
        if (categoryList != null && categoryList.getSelectedPosition() >= 0) {
            lastCategoryPosition = categoryList.getSelectedPosition();
        }
    }

    private void rememberChannelPosition() {
        if (channelList != null && channelList.getSelectedPosition() >= 0) {
            lastChannelPosition = channelList.getSelectedPosition();
        }
    }

    private static int boundedPosition(int position, int itemCount) {
        return Math.max(0, Math.min(position, itemCount - 1));
    }

    @Override
    public boolean onKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && playingChannel != null && channelList != null
                && (event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_UP || event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            int index = channelAdapter.indexOf(playingChannel.id());
            int next = index + (event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1);
            if (next >= 0 && next < channelAdapter.getItemCount()) {
                playChannel(channelAdapter.items().get(next));
                channelList.setSelectedPosition(next);
            }
            return true;
        }
        return false;
    }
}
