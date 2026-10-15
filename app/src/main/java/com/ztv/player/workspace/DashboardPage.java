package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.leanback.widget.HorizontalGridView;

import com.ztv.player.ContinueWatchingPolicy;
import com.ztv.player.R;
import com.ztv.player.data.WatchProgressStore;
import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.ui.Tr;
import com.ztv.player.data.XtreamMediaSection;
import com.ztv.player.ui.PosterLoader;
import com.ztv.player.ui.PosterUrls;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * `/workspace/dashboard`: the welcome empty state without a source, otherwise the
 * cinematic hero followed by the curated rails (Continue Watching, Live now on your
 * favorites, Recently watched live TV, Favorite movies & series, Recently Used Sources,
 * Recently Added).
 */
public final class DashboardPage extends Page implements Catalog.LibraryListener {
    private static final long HERO_ROTATE_MS = 8000;

    private final Context context;
    private final boolean withSource;
    private ScrollView scroll;
    private LinearLayout column;
    private LinearLayout railsColumn;
    private View firstFocus;
    private final List<HeroSlide> slides = new ArrayList<>();
    private int slideIndex;
    private FrameLayout hero;
    private ImageView heroArt;
    private ImageView heroKeyArt;
    private TextView heroEyebrow;
    private TextView heroTitle;
    private TextView heroPill;
    private LinearLayout heroDots;
    private View heroButton;
    private final Runnable rotate = this::nextSlide;

    private static final class HeroSlide {
        final XtreamMediaItem item;
        final String eyebrow;
        final String action;

        HeroSlide(XtreamMediaItem item, String eyebrow, String action) {
            this.item = item;
            this.eyebrow = eyebrow;
            this.action = action;
        }
    }

    public DashboardPage(WorkspaceActivity host) {
        super(host);
        this.context = host;
        this.withSource = host.hasSource();
    }

    boolean builtWithSource() {
        return withSource;
    }

    @Override
    public Section section() {
        return Section.DASHBOARD;
    }

    @Override
    public String searchScope() {
        return withSource ? Tr.t("Advanced search", "بحث متقدم") : null;
    }

    @Override
    public String searchPlaceholder() {
        return Tr.t("Search in this playlist...", "ابحث في هذا الاشتراك...");
    }

    @Override
    public void onSearchSubmit(String query) {
        if (query != null && query.trim().length() >= 3) {
            SearchPage page = new SearchPage(host);
            page.presetQuery(query.trim());
            host.push(page);
        }
    }

    @Override
    protected View buildContent() {
        if (!withSource) {
            return buildWelcome();
        }
        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        column = Nator.column(context);
        column.setClipChildren(false);
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        hero = buildHero();
        column.addView(hero, new LinearLayout.LayoutParams(-1, Nator.px(context, 320)));
        railsColumn = Nator.column(context);
        railsColumn.setClipChildren(false);
        Nator.pad(railsColumn, 28, 0, 28, 32);
        column.addView(railsColumn, new LinearLayout.LayoutParams(-1, -2));
        rebuild();
        return scroll;
    }

    @Override
    public void onShown() {
        if (withSource) {
            host.catalog.addListener(this);
            XtreamContentType[] types = host.kidsMode()
                    ? new XtreamContentType[]{XtreamContentType.LIVE, XtreamContentType.MOVIE, XtreamContentType.SERIES}
                    : new XtreamContentType[]{XtreamContentType.MOVIE, XtreamContentType.SERIES};
            for (XtreamContentType type : types) {
                if (!host.catalog.isLoaded(type)) {
                    host.catalog.load(type, false, this);
                }
            }
            scheduleRotate();
        }
    }

    @Override
    public void onHidden() {
        host.catalog.removeListener(this);
        host.cancel(rotate);
    }

    @Override
    public void onDestroy() {
        onHidden();
    }

    @Override
    public void onPlaybackReturned() {
        if (withSource) {
            rebuild();
        }
    }

    @Override
    public void onLibrary(XtreamContentType type, List<XtreamMediaSection> sections, boolean fromNetwork) {
        if (railsColumn == null || (type == XtreamContentType.LIVE && !host.kidsMode())) {
            return;
        }
        if (!railsColumn.hasFocus()) {
            rebuild();
            return;
        }
        if (host.kidsMode()) {
            // Focus is in a rail: only add the Kids rails that were still missing.
            addKidsRails();
            return;
        }
        // The viewer is in a rail: refresh the hero and add late rails without touching focus.
        buildSlides();
        showSlide(slideIndex);
        scheduleRotate();
        if (railsColumn.findViewWithTag(RECENTLY_ADDED_TAG) == null) {
            addRecentlyAddedRail();
        }
    }

    @Override
    public void onLibraryError(XtreamContentType type, Exception error) {
    }

    @Override
    public void requestInitialFocus() {
        if (heroButton != null && hero != null && hero.getVisibility() == View.VISIBLE) {
            heroButton.requestFocus();
        } else if (firstFocus != null) {
            firstFocus.requestFocus();
        } else {
            View rail = host.railButton(Section.DASHBOARD);
            if (rail != null) {
                rail.requestFocus();
            }
        }
    }

    // ─── Welcome (no playlists) ─────────────────────────────────────────────

    private View buildWelcome() {
        FrameLayout frame = new FrameLayout(context);
        LinearLayout box = Nator.column(context);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout logoBox = new FrameLayout(context);
        logoBox.setBackground(Nator.rounded(context, 0xFF1C2333, 22));
        logoBox.setClipToOutline(true);
        logoBox.setElevation(Nator.fpx(context, 10));
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.ztv_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logoBox.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        box.addView(logoBox, Nator.size(context, 96, 96));

        TextView title = Nator.text(context, Tr.t("Welcome to ZTV", "مرحبًا بك في ZTV"), 24.48f, Nator.W600, 0xFF60A5FA);
        title.getPaint().setShader(null);
        box.addView(title, Nator.margins(context, Nator.wrap(), 0, 26, 0, 0));
        title.post(() -> title.getPaint().setShader(new android.graphics.LinearGradient(0, 0, title.getWidth(), title.getHeight(),
                new int[]{0xFF60A5FA, 0xFF3B82F6, 0xFF6366F1}, null, Shader.TileMode.CLAMP)));
        box.addView(Nator.text(context, Tr.t("Your IPTV library, in one place.", "مكتبتك التلفزيونية في مكان واحد."), 15.2f, Nator.W500, Nator.ON_SURFACE),
                Nator.margins(context, Nator.wrap(), 0, 10, 0, 0));
        TextView description = Nator.text(context,
                "Connect your Xtream Codes account — then enjoy live TV, movies and series on every screen.",
                13, Nator.W400, Nator.alpha(Nator.ON_SURFACE, 0.72f));
        description.setGravity(Gravity.CENTER);
        Nator.lineHeight(description, 19.5f);
        box.addView(description, Nator.margins(context, Nator.size(context, 480, -2), 0, 10, 0, 0));

        LinearLayout cta = Nator.row(context);
        cta.setGravity(Gravity.CENTER);
        GradientDrawable ctaBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF3B82F6, 0xFF2563EB});
        ctaBg.setCornerRadius(Nator.fpx(context, 12));
        GradientDrawable ctaFocus = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF60A5FA, 0xFF3B82F6});
        ctaFocus.setCornerRadius(Nator.fpx(context, 12));
        cta.setBackground(Nator.states(ctaBg, ctaFocus));
        cta.setElevation(Nator.fpx(context, 6));
        Nator.pad(cta, 28, 10, 28, 10);
        Nator.focusable(cta, 12);
        cta.addView(Nator.icon(context, "add_circle", 18, 0xFFFFFFFF), Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 8, 0));
        cta.addView(Nator.text(context, Tr.t("Add Your First Playlist", "أضف اشتراكك الأول"), 14, Nator.W500, 0xFFFFFFFF));
        cta.setOnClickListener(v -> host.showAddPlaylist(null));
        box.addView(cta, Nator.margins(context, Nator.size(context, -2, 40), 0, 18, 0, 0));
        firstFocus = cta;

        LinearLayout features = Nator.row(context);
        features.setGravity(Gravity.TOP);
        features.addView(featureCard("live_tv", Tr.t("Live TV", "القنوات"), Tr.t("Stream live channels with full schedule.", "شاهد القنوات المباشرة مع دليل البرامج."), -1.2f),
                Nator.margins(context, Nator.size(context, 261, 139), 0, 0, 18, 0));
        features.addView(featureCard("movie", Tr.t("Movies & Series", "الأفلام والمسلسلات"), Tr.t("On-demand library with covers and details.", "مكتبة عند الطلب بالأغلفة والتفاصيل."), 0.8f),
                Nator.margins(context, Nator.size(context, 261, 139), 0, 0, 18, 0));
        features.addView(featureCard("event_note", Tr.t("EPG & Catch-up", "دليل البرامج والإعادة"), Tr.t("Program guide and time-shifted playback.", "دليل البرامج ومشاهدة ما فاتك."), -0.6f),
                Nator.size(context, 261, 139));
        box.addView(features, Nator.margins(context, Nator.wrap(), 0, 30, 0, 0));
        frame.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return frame;
    }

    private View featureCard(String icon, String title, String description, float rotation) {
        LinearLayout card = Nator.column(context);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x1460A5FA, 0x0A2563EB});
        bg.setCornerRadius(Nator.fpx(context, 14));
        bg.setStroke(Math.max(1, Nator.px(context, 1)), 0x2E60A5FA);
        card.setBackground(bg);
        Nator.pad(card, 14, 16, 14, 14);
        card.setRotation(rotation);
        FrameLayout iconWrap = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF3B82F6, 0xFF2563EB});
        iconBg.setCornerRadius(Nator.fpx(context, 11));
        iconWrap.setBackground(iconBg);
        iconWrap.setElevation(Nator.fpx(context, 4));
        iconWrap.addView(Nator.icon(context, icon, 22, 0xFFFFFFFF), new FrameLayout.LayoutParams(-1, -1));
        card.addView(iconWrap, Nator.size(context, 40, 40));
        card.addView(Nator.text(context, title, 14.4f, Nator.W600, Nator.ON_SURFACE), Nator.margins(context, Nator.wrap(), 0, 8, 0, 0));
        TextView desc = Nator.text(context, description, 12.5f, Nator.W400, Nator.alpha(Nator.ON_SURFACE, 0.7f));
        desc.setGravity(Gravity.CENTER);
        card.addView(desc, Nator.margins(context, Nator.matchWidth(), 0, 8, 0, 0));
        return card;
    }

    // ─── Hero ───────────────────────────────────────────────────────────────

    private FrameLayout buildHero() {
        FrameLayout frame = new FrameLayout(context);
        frame.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
        frame.setClipToOutline(true);
        heroArt = new ImageView(context);
        heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroArt.setAlpha(0.6f);
        frame.addView(heroArt, new FrameLayout.LayoutParams(-1, -1));
        View sideScrim = new View(context);
        sideScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Nator.CONTENT_BG, Nator.alpha(Nator.CONTENT_BG, 0.7f), Nator.alpha(Nator.CONTENT_BG, 0.05f)}));
        frame.addView(sideScrim, new FrameLayout.LayoutParams(-1, -1));
        View bottomScrim = new View(context);
        bottomScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Nator.CONTENT_BG, Nator.alpha(Nator.CONTENT_BG, 0.0f)}));
        frame.addView(bottomScrim, new FrameLayout.LayoutParams(-1, Nator.px(context, 160), Gravity.BOTTOM));

        // No backdrop from Xtream: blurred poster wash + the poster as key art on the right.
        FrameLayout keyArtBox = new FrameLayout(context);
        keyArtBox.setBackground(Nator.rounded(context, Nator.WIDGET_BG, 12));
        keyArtBox.setClipToOutline(true);
        keyArtBox.setElevation(Nator.fpx(context, 16));
        heroKeyArt = new ImageView(context);
        heroKeyArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        keyArtBox.addView(heroKeyArt, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams keyArtParams = new FrameLayout.LayoutParams(Nator.px(context, 164), Nator.px(context, 246), Gravity.END | Gravity.TOP);
        keyArtParams.setMargins(0, Nator.px(context, 24), 0, 0);
        keyArtParams.setMarginStart(0);
        keyArtParams.setMarginEnd(Nator.px(context, 96));
        frame.addView(keyArtBox, keyArtParams);

        LinearLayout content = Nator.column(context);
        Nator.pad(content, 28, 0, 28, 30);
        heroEyebrow = Nator.line(context, "", 11, Nator.W600, 0xFFBBBFCA);
        heroEyebrow.setLetterSpacing(0.14f);
        content.addView(heroEyebrow);
        heroTitle = Nator.text(context, "", 43.52f, Nator.W700, Nator.HEADING);
        heroTitle.setMaxLines(2);
        heroTitle.setMaxWidth(Nator.px(context, 700));
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(heroTitle, Nator.margins(context, Nator.wrap(), 0, 12, 0, 0));
        heroPill = Nator.metaChip(context, "", 0xFFBBBFCA, Nator.W500);
        content.addView(heroPill, Nator.margins(context, Nator.wrap(), 0, 10, 0, 0));
        LinearLayout buttons = Nator.row(context);
        heroButton = Widgets.lightPrimary(context, "info", Tr.t("Details", "التفاصيل"), null, this::openSlide);
        buttons.addView(heroButton, Nator.size(context, -2, 38));
        content.addView(buttons, Nator.margins(context, Nator.wrap(), 0, 16, 0, 0));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        heroDots = Nator.row(context);
        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        dotsParams.setMargins(0, 0, 0, Nator.px(context, 18));
        dotsParams.setMarginStart(0);
        dotsParams.setMarginEnd(Nator.px(context, 96 + 82 - 30));
        frame.addView(heroDots, dotsParams);
        return frame;
    }

    private void buildSlides() {
        slides.clear();
        List<WatchProgressStore.EntrySnapshot> resume = ContinueWatchingPolicy.rail(
                host.progress.snapshotsFor(XtreamContentType.MOVIE).values(),
                host.progress.snapshotsFor(XtreamContentType.EPISODE).values(), 3);
        for (WatchProgressStore.EntrySnapshot snapshot : resume) {
            String type = snapshot.item().type() == XtreamContentType.EPISODE ? Tr.t("Episode", "حلقة") : Tr.t("Movie", "فيلم");
            slides.add(new HeroSlide(snapshot.item(), type + "  ·  " + Tr.t("Continue watching", "تابع المشاهدة"), Tr.t("Resume", "استئناف")));
        }
        for (XtreamContentType type : new XtreamContentType[]{XtreamContentType.MOVIE, XtreamContentType.SERIES}) {
            for (XtreamMediaItem item : host.favorites.list(type)) {
                if (slides.size() >= 6) {
                    break;
                }
                slides.add(new HeroSlide(item, (type == XtreamContentType.SERIES ? Tr.t("Series", "مسلسل") : Tr.t("Movie", "فيلم"))
                        + "  ·  " + Tr.t("Favorite", "مفضّل"), Tr.t("Details", "التفاصيل")));
            }
        }
        if (slides.size() < 3) {
            List<XtreamMediaItem> latest = Catalog.latest(Catalog.allItems(host.catalog.cached(XtreamContentType.MOVIE)), 3);
            for (XtreamMediaItem item : latest) {
                slides.add(new HeroSlide(item, Tr.t("Movie  ·  Recently added", "فيلم  ·  أُضيف حديثًا"), Tr.t("Details", "التفاصيل")));
            }
        }
    }

    private void showSlide(int index) {
        if (slides.isEmpty()) {
            hero.setVisibility(View.GONE);
            return;
        }
        hero.setVisibility(View.VISIBLE);
        slideIndex = (index + slides.size()) % slides.size();
        HeroSlide slide = slides.get(slideIndex);
        heroEyebrow.setText(slide.eyebrow.toUpperCase(Locale.ROOT));
        heroTitle.setText(slide.item.title());
        heroPill.setText(host.playlistTitle());
        String url = PosterUrls.isUsable(slide.item.imageUrl()) ? slide.item.imageUrl() : "";
        PosterLoader.load(url, heroArt, false);
        PosterLoader.load(url, heroKeyArt, false);
        ((View) heroKeyArt.getParent()).setVisibility(url.isEmpty() ? View.GONE : View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            heroArt.setRenderEffect(RenderEffect.createBlurEffect(Nator.fpx(context, 30), Nator.fpx(context, 30), Shader.TileMode.CLAMP));
        }
        heroDots.removeAllViews();
        if (slides.size() > 1) {
            for (int i = 0; i < slides.size(); i++) {
                View dot = new View(context);
                boolean active = i == slideIndex;
                dot.setBackground(Nator.pill(context, active ? Nator.HEADING : Nator.alpha(Nator.HEADING, 0.3f)));
                heroDots.addView(dot, Nator.margins(context, Nator.size(context, active ? 18 : 6, 6), 3, 0, 3, 0));
            }
        }
    }

    private void nextSlide() {
        if (heroButton != null && !heroButton.isFocused()) {
            showSlide(slideIndex + 1);
        }
        scheduleRotate();
    }

    private void scheduleRotate() {
        host.cancel(rotate);
        if (slides.size() > 1) {
            host.runLater(rotate, HERO_ROTATE_MS);
        }
    }

    private void openSlide() {
        if (slides.isEmpty()) {
            return;
        }
        XtreamMediaItem item = slides.get(slideIndex).item;
        open(item);
    }

    private void open(XtreamMediaItem item) {
        if (item.type() == XtreamContentType.EPISODE || item.type() == XtreamContentType.LIVE) {
            host.play(item, null);
        } else {
            host.push(new DetailPage(host, item));
        }
    }

    // ─── Rails ──────────────────────────────────────────────────────────────

    private void rebuild() {
        boolean hadFocus = railsColumn.hasFocus();
        buildSlides();
        showSlide(slideIndex);
        railsColumn.removeAllViews();
        firstFocus = null;

        List<WatchProgressStore.EntrySnapshot> resume = ContinueWatchingPolicy.rail(
                host.progress.snapshotsFor(XtreamContentType.MOVIE).values(),
                host.progress.snapshotsFor(XtreamContentType.EPISODE).values(), 20);
        if (host.prefs.bool(SettingsPage.PREF_RAIL_CONTINUE, true) && !resume.isEmpty()) {
            List<RailCardAdapter.Card> cards = new ArrayList<>();
            for (WatchProgressStore.EntrySnapshot snapshot : resume) {
                cards.add(new RailCardAdapter.Card(snapshot.item(),
                        ContinueWatchingPolicy.timeLeft(snapshot.positionMs(), snapshot.durationMs()), snapshot.progressPercent()));
            }
            addRail(Tr.t("Continue Watching", "تابع المشاهدة"), cards, false, null);
        }

        List<XtreamMediaItem> favoriteChannels = host.favorites.list(XtreamContentType.LIVE);
        if (host.prefs.bool(SettingsPage.PREF_RAIL_LIVE_FAVORITES, true) && !favoriteChannels.isEmpty()) {
            addRail(Tr.t("Live now on your favorites", "الآن على قنواتك المفضلة"), cards(favoriteChannels), true, () -> host.openSection(Section.FAVORITES));
        }
        List<XtreamMediaItem> recentChannels = host.history.list(XtreamContentType.LIVE);
        if (host.prefs.bool(SettingsPage.PREF_RAIL_RECENT_LIVE, true) && !recentChannels.isEmpty()) {
            addRail(Tr.t("Recently watched live TV", "قنوات شوهدت مؤخرًا"), cards(recentChannels), true, () -> host.openSection(Section.RECENT));
        }
        List<XtreamMediaItem> favoriteVod = new ArrayList<>(host.favorites.list(XtreamContentType.MOVIE));
        favoriteVod.addAll(host.favorites.list(XtreamContentType.SERIES));
        if (host.prefs.bool(SettingsPage.PREF_RAIL_FAVORITE_VOD, true) && !favoriteVod.isEmpty()) {
            addRail(Tr.t("Favorite movies & series", "الأفلام والمسلسلات المفضلة"), cards(favoriteVod), false, () -> host.openSection(Section.FAVORITES));
        }
        if (host.kidsMode()) {
            addKidsRails();
        } else {
            if (host.prefs.bool(SettingsPage.PREF_RAIL_SOURCES, true)) {
                addSourcesRail();
            }
            addRecentlyAddedRail();
        }
        if (hadFocus && firstFocus != null) {
            firstFocus.requestFocus();
        }
    }

    private static final String RECENTLY_ADDED_TAG = "rail-recently-added";
    private static final int KIDS_RAIL_SIZE = 30;

    /** Kids mode home: what the parent allowed, so the first screen is never empty. */
    private void addKidsRails() {
        List<XtreamMediaItem> channels = Catalog.allItems(host.catalog.cached(XtreamContentType.LIVE));
        if (!channels.isEmpty() && railsColumn.findViewWithTag("kids-live") == null) {
            addRail(Tr.t("Your channels", "قنواتك"), cards(first(channels)), true, () -> host.openSection(Section.LIVE))
                    .setTag("kids-live");
        }
        List<XtreamMediaItem> movies = Catalog.latest(Catalog.allItems(host.catalog.cached(XtreamContentType.MOVIE)), KIDS_RAIL_SIZE);
        if (!movies.isEmpty() && railsColumn.findViewWithTag("kids-movies") == null) {
            addRail(Tr.t("Your movies", "أفلامك"), cards(movies), false, () -> host.openSection(Section.VOD))
                    .setTag("kids-movies");
        }
        List<XtreamMediaItem> series = Catalog.latest(Catalog.allItems(host.catalog.cached(XtreamContentType.SERIES)), KIDS_RAIL_SIZE);
        if (!series.isEmpty() && railsColumn.findViewWithTag("kids-series") == null) {
            addRail(Tr.t("Your series", "مسلسلاتك"), cards(series), false, () -> host.openSection(Section.SERIES))
                    .setTag("kids-series");
        }
    }

    private static List<XtreamMediaItem> first(List<XtreamMediaItem> items) {
        return items.size() > KIDS_RAIL_SIZE ? new ArrayList<>(items.subList(0, KIDS_RAIL_SIZE)) : items;
    }

    private void addRecentlyAddedRail() {
        List<XtreamMediaItem> latest = new ArrayList<>(Catalog.latest(Catalog.allItems(host.catalog.cached(XtreamContentType.MOVIE)), 12));
        latest.addAll(Catalog.latest(Catalog.allItems(host.catalog.cached(XtreamContentType.SERIES)), 8));
        latest = Catalog.latest(latest, 20);
        if (host.prefs.bool(SettingsPage.PREF_RAIL_RECENTLY_ADDED, true) && !latest.isEmpty()) {
            View rail = addRail(Tr.t("Recently Added on Xtream", "أُضيف حديثًا"), cards(latest), false, () -> host.openSection(Section.RECENTLY_ADDED));
            rail.setTag(RECENTLY_ADDED_TAG);
        }
    }

    private List<RailCardAdapter.Card> cards(List<XtreamMediaItem> items) {
        List<RailCardAdapter.Card> cards = new ArrayList<>();
        for (XtreamMediaItem item : items) {
            cards.add(new RailCardAdapter.Card(item, "", 0));
        }
        return cards;
    }

    private View addRail(String title, List<RailCardAdapter.Card> cards, boolean channels, Runnable seeAll) {
        railsColumn.addView(Widgets.railHeader(context, title, cards.size(), seeAll == null ? null : Tr.t("See all", "عرض الكل"), seeAll),
                Nator.margins(context, Nator.matchWidth(), 2, railsColumn.getChildCount() == 0 ? 14 : 30, 0, 12));
        HorizontalGridView rail = Widgets.horizontalRail(context, 16);
        RailCardAdapter adapter = new RailCardAdapter(context, channels, channels ? 220 : 172, new RailCardAdapter.Listener() {
            @Override
            public void onOpen(RailCardAdapter.Card card, int position) {
                if (channels) {
                    host.play(card.item, null);
                } else {
                    open(card.item);
                }
            }
        });
        adapter.setCards(cards);
        rail.setAdapter(adapter);
        int cardHeight = channels ? Math.round(220 * 9f / 16f) : Math.round(172 * 1.5f);
        railsColumn.addView(rail, new LinearLayout.LayoutParams(-1, Nator.px(context, cardHeight + 52)));
        if (firstFocus == null) {
            firstFocus = rail;
        }
        return rail;
    }

    private void addSourcesRail() {
        railsColumn.addView(Widgets.railHeader(context, Tr.t("Recently Used Sources", "المصادر المستخدمة مؤخرًا"), 1, Tr.t("See all", "عرض الكل"), () -> host.openSection(Section.SOURCES)),
                Nator.margins(context, Nator.matchWidth(), 2, railsColumn.getChildCount() == 0 ? 14 : 30, 0, 12));
        LinearLayout tile = Nator.column(context);
        FrameLayout art = new FrameLayout(context);
        GradientDrawable artBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF27314A, 0xFF1A1F2B});
        artBg.setCornerRadius(Nator.fpx(context, 12));
        artBg.setStroke(Math.max(1, Nator.px(context, 1)), 0x0DFFFFFF);
        art.setBackground(artBg);
        art.addView(Nator.icon(context, "cloud", 44, 0xFF5B8BD9), new FrameLayout.LayoutParams(-1, -1));
        art.addView(Nator.icon(context, "more_vert", 18, Nator.ON_SURFACE_VARIANT),
                new FrameLayout.LayoutParams(Nator.px(context, 28), Nator.px(context, 28), Gravity.TOP | Gravity.END));
        tile.addView(art, Nator.size(context, 170, 96));
        tile.addView(Nator.line(context, host.playlistTitle(), 14, Nator.W600, Nator.HEADING), Nator.margins(context, Nator.size(context, 170, -2), 2, 10, 0, 0));
        tile.addView(Nator.line(context, "Xtream", 12, Nator.W400, Nator.EYEBROW), Nator.margins(context, Nator.wrap(), 2, 3, 0, 0));
        Nator.focusable(art, 12);
        tile.setFocusable(false);
        art.setOnClickListener(v -> host.openSection(Section.VOD));
        Nator.liftOnFocus(art, tile, 3);
        LinearLayout row = Nator.row(context);
        row.addView(tile);
        railsColumn.addView(row, Nator.matchWidth());
        if (firstFocus == null) {
            firstFocus = art;
        }
    }
}
