package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ztv.player.SeriesResumeResolver;
import com.ztv.player.data.WatchProgressStore;
import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.data.XtreamMovieInfo;
import com.ztv.player.data.XtreamSeriesDetail;
import com.ztv.player.ui.PosterLoader;
import com.ztv.player.ui.PosterUrls;
import com.ztv.player.ui.Tr;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * `app-portal-detail-shell` + `app-content-hero` for a movie or a series: backdrop with
 * the two-layer scrim, poster, kind label, title, meta chips, three-line description,
 * the light Play button + ghost icon actions, credits, "Cast & crew", and for series the
 * Episodes section with season pills and episode rows.
 */
public final class DetailPage extends Page {
    private final XtreamMediaItem item;
    private final Context context;
    private final Page parent;
    private ScrollView scroll;
    private LinearLayout column;
    private FrameLayout hero;
    private ImageView backdrop;
    private ImageView poster;
    private TextView kind;
    private TextView title;
    private LinearLayout chips;
    private TextView description;
    private LinearLayout actions;
    private LinearLayout credits;
    private LinearLayout castSection;
    private LinearLayout episodesSection;
    private View playButton;
    private FrameLayout favoriteButton;
    private FrameLayout watchedButton;
    private XtreamMovieInfo info;
    private XtreamSeriesDetail seriesDetail;
    private final Map<String, List<XtreamMediaItem>> seasons = new LinkedHashMap<>();
    private String selectedSeason;
    private boolean episodeGrid;

    public DetailPage(WorkspaceActivity host, XtreamMediaItem item) {
        super(host);
        this.item = item;
        this.context = host;
        this.parent = host.top();
    }

    @Override
    public Section section() {
        return parent != null ? parent.section() : item.type() == XtreamContentType.SERIES ? Section.SERIES : Section.VOD;
    }

    @Override
    protected View buildContextPanel() {
        return parent != null ? parent.contextPanel() : null;
    }

    @Override
    public String searchScope() {
        return parent != null ? parent.searchScope() : null;
    }

    @Override
    public String searchPlaceholder() {
        return parent != null ? parent.searchPlaceholder() : super.searchPlaceholder();
    }

    @Override
    public void onSearch(String query) {
        if (parent != null && query != null && !query.trim().isEmpty()) {
            host.pop();
            parent.onSearch(query);
        }
    }

    @Override
    public void requestInitialFocus() {
        if (playButton != null) {
            playButton.requestFocus();
        }
    }

    @Override
    public void focusFromRail() {
        if (parent != null && parent.contextPanel() != null) {
            parent.focusFromRail();
        } else {
            requestInitialFocus();
        }
    }

    @Override
    public void onPlaybackReturned() {
        renderActions();
        if (item.type() == XtreamContentType.SERIES) {
            renderEpisodes();
        }
    }

    @Override
    protected View buildContent() {
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(true);

        // Page layers: the backdrop stage (hero 432px + the 140px tail the first section
        // overlaps) sits behind the content column, exactly like app-portal-detail-shell.
        FrameLayout page = new FrameLayout(context);
        page.setClipChildren(false);
        page.addView(buildBackdrop(), new FrameLayout.LayoutParams(-1, Nator.px(context, 432 + 140)));

        column = Nator.column(context);
        column.setClipChildren(false);
        FrameLayout stage = new FrameLayout(context);
        stage.setMinimumHeight(Nator.px(context, 432));
        stage.addView(buildHero(), new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        column.addView(stage, new LinearLayout.LayoutParams(-1, -2));
        castSection = Nator.column(context);
        Nator.pad(castSection, 32, 0, 32, 0);
        column.addView(castSection, new LinearLayout.LayoutParams(-1, -2));
        episodesSection = Nator.column(context);
        Nator.pad(episodesSection, 32, 0, 32, 24);
        column.addView(episodesSection, new LinearLayout.LayoutParams(-1, -2));
        page.addView(column, new FrameLayout.LayoutParams(-1, -2));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        renderBasics();
        loadDetails();
        return scroll;
    }

    private View buildBackdrop() {
        hero = new FrameLayout(context);
        // The blur RenderEffect draws past the image bounds; keep it inside the stage.
        hero.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
        hero.setClipToOutline(true);
        backdrop = new ImageView(context);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.55f);
        hero.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        // Two-layer scrim built from --app-content-bg: left-to-right and bottom-up.
        View horizontal = new View(context);
        horizontal.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Nator.alpha(Nator.CONTENT_BG, 0.92f), Nator.alpha(Nator.CONTENT_BG, 0.55f), Nator.alpha(Nator.CONTENT_BG, 0.15f)}));
        hero.addView(horizontal, new FrameLayout.LayoutParams(-1, -1));
        View vertical = new View(context);
        vertical.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Nator.CONTENT_BG, Nator.alpha(Nator.CONTENT_BG, 0.35f), Nator.alpha(Nator.CONTENT_BG, 0.0f)}));
        hero.addView(vertical, new FrameLayout.LayoutParams(-1, -1));
        return hero;
    }

    private View buildHero() {
        LinearLayout row = Nator.row(context);
        row.setGravity(Gravity.BOTTOM);
        Nator.pad(row, 32, 40, 32, 0);
        row.setClipChildren(false);

        FrameLayout posterBox = new FrameLayout(context);
        posterBox.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2A2A2A, 0xFF1A1A1A}));
        posterBox.setClipToOutline(true);
        posterBox.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Nator.fpx(context, 10));
            }
        });
        posterBox.setElevation(Nator.fpx(context, 18));
        poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterBox.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        row.addView(posterBox, Nator.margins(context, Nator.size(context, 190, 285), 0, 0, 32, 0));

        LinearLayout details = Nator.column(context);
        kind = Nator.line(context, "", 11, Nator.W600, Nator.BODY);
        kind.setLetterSpacing(0.12f);
        details.addView(kind);
        title = Nator.text(context, item.title(), 40, Nator.W700, Nator.HEADING);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        details.addView(title, Nator.margins(context, Nator.matchWidth(), 0, 14, 0, 0));
        chips = Nator.row(context);
        details.addView(chips, Nator.margins(context, Nator.matchWidth(), 0, 14, 0, 0));
        description = Nator.text(context, "", 13.5f, Nator.W400, Nator.DESCRIPTION);
        Nator.lineHeight(description, 20.9f);
        description.setMaxLines(3);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        details.addView(description, Nator.margins(context, Nator.matchWidth(), 0, 14, 0, 0));
        actions = Nator.row(context);
        actions.setClipChildren(false);
        details.addView(actions, Nator.margins(context, Nator.matchWidth(), 0, 14, 0, 0));
        credits = Nator.column(context);
        details.addView(credits, Nator.margins(context, Nator.matchWidth(), 0, 14, 0, 0));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private void renderBasics() {
        String posterUrl = PosterUrls.isUsable(item.imageUrl()) ? item.imageUrl() : "";
        PosterLoader.load(posterUrl, poster, false);
        loadBackdrop(posterUrl, true);
        String kindLabel = item.type() == XtreamContentType.SERIES ? Tr.t("Series", "مسلسل") : Tr.t("Movie", "فيلم");
        kind.setText((kindLabel + " · " + host.playlistTitle()).toUpperCase(Locale.ROOT));
        renderChips();
        renderActions();
    }

    /** The provider's backdrop, else the poster blurred into a wash (hero--compact). */
    private void loadBackdrop(String url, boolean blurred) {
        PosterLoader.load(url, backdrop, false);
        if (blurred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(Nator.fpx(context, 40), Nator.fpx(context, 40), Shader.TileMode.CLAMP));
            backdrop.setAlpha(0.45f);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdrop.setRenderEffect(null);
            backdrop.setAlpha(0.6f);
        } else {
            backdrop.setAlpha(blurred ? 0.25f : 0.6f);
        }
    }

    private void loadDetails() {
        if (item.type() == XtreamContentType.SERIES) {
            host.catalog.seriesDetail(item.id(), detail -> {
                if (detail == null) {
                    renderEpisodesError();
                    return;
                }
                seriesDetail = detail;
                info = detail.info();
                buildSeasons(detail.episodes());
                applyInfo();
                renderEpisodes();
            });
            renderEpisodesLoading();
        } else {
            host.catalog.movieInfo(item.id(), loaded -> {
                info = loaded;
                applyInfo();
            });
        }
    }

    private void applyInfo() {
        if (info == null || scroll == null) {
            return;
        }
        if (!info.backdrop().isEmpty()) {
            loadBackdrop(info.backdrop(), false);
        }
        if (PosterUrls.isUsable(info.poster()) && !PosterUrls.isUsable(item.imageUrl())) {
            PosterLoader.load(info.poster(), poster, false);
        }
        String plot = info.plot().isEmpty() ? item.detail() : info.plot();
        description.setText(plot);
        description.setVisibility(plot.isEmpty() ? View.GONE : View.VISIBLE);
        renderChips();
        renderActions();
        renderCredits();
        renderCast();
    }

    // ─── Chips / actions / credits ──────────────────────────────────────────

    private void renderChips() {
        chips.removeAllViews();
        List<String> parts = new ArrayList<>();
        if (info != null) {
            String year = yearOf(info.releaseDate());
            if (!year.isEmpty()) {
                parts.add(year);
            }
            String duration = formatDuration(info.duration());
            if (!duration.isEmpty()) {
                parts.add(duration);
            }
            for (String genre : info.genre().split("[,/]")) {
                if (!genre.trim().isEmpty() && parts.size() < 5) {
                    parts.add(genre.trim());
                }
            }
        }
        for (String part : parts) {
            chips.addView(Nator.metaChip(context, part, Nator.HEADING, Nator.W500), Nator.margins(context, Nator.wrap(), 0, 0, 6, 0));
        }
        String rating = info != null && !info.rating().isEmpty() ? normalizeRating(info.rating()) : item.rating();
        if (!rating.isEmpty()) {
            chips.addView(Nator.metaChip(context, "★ " + rating, Nator.RATING_GOLD, Nator.W600));
        }
        chips.setVisibility(chips.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void renderActions() {
        actions.removeAllViews();
        String meta;
        XtreamMediaItem target = item;
        if (item.type() == XtreamContentType.SERIES) {
            XtreamMediaItem resume = resumeEpisode();
            target = resume;
            meta = resume == null ? "" : episodeCode(resume) + " · " + resume.title();
        } else {
            WatchProgressStore.EntrySnapshot snapshot = host.progress.snapshot(XtreamContentType.MOVIE, item.id());
            if (snapshot != null && snapshot.inProgress()) {
                meta = Tr.t("Resume · " + snapshot.progressPercent() + "% watched",
                        "استئناف · شوهد " + snapshot.progressPercent() + "%");
            } else {
                meta = info == null ? "" : formatDuration(info.duration());
            }
        }
        XtreamMediaItem playTarget = target;
        String label = item.type() == XtreamContentType.MOVIE
                && host.progress.snapshot(XtreamContentType.MOVIE, item.id()) != null
                && host.progress.snapshot(XtreamContentType.MOVIE, item.id()).inProgress() ? Tr.t("Resume", "استئناف") : Tr.t("Play", "تشغيل");
        LinearLayout play = Widgets.lightPrimary(context, "play_arrow", label, meta, () -> {
            if (item.type() == XtreamContentType.SERIES) {
                if (playTarget != null) {
                    host.play(playTarget, allEpisodes());
                }
            } else {
                host.play(item, null);
            }
        });
        if (item.type() == XtreamContentType.SERIES && playTarget == null) {
            play.setAlpha(0.5f);
        }
        playButton = play;
        actions.addView(play, Nator.margins(context, Nator.size(context, -2, 44), 0, 0, 10, 0));
        favoriteButton = Widgets.ghostIconButton(context, "favorite_border", Tr.t("Add to favorites", "إضافة إلى المفضلة"), this::toggleFavorite);
        actions.addView(favoriteButton, Nator.margins(context, Nator.size(context, 44, 44), 0, 0, 10, 0));
        updateFavorite();
        if (item.type() == XtreamContentType.MOVIE) {
            watchedButton = Widgets.ghostIconButton(context, "check_circle_outline", Tr.t("Mark as watched", "تحديد كمُشاهَد"), this::toggleWatched);
            actions.addView(watchedButton, Nator.margins(context, Nator.size(context, 44, 44), 0, 0, 10, 0));
            updateWatched();
        }
        FrameLayout more = Widgets.ghostIconButton(context, "more_horiz", Tr.t("More", "المزيد"), null);
        more.setOnClickListener(v -> showMore(v));
        actions.addView(more, Nator.size(context, 44, 44));
        if (scroll != null && scroll.hasFocus() == false && host.top() == this) {
            playButton.post(() -> {
                if (host.getCurrentFocus() == null || !scroll.hasFocus()) {
                    playButton.requestFocus();
                }
            });
        }
    }

    private void toggleFavorite() {
        boolean now = host.favorites.toggle(item);
        updateFavorite();
        host.snack(now ? Tr.t("Added to favorites", "أُضيف إلى المفضلة") : Tr.t("Removed from favorites", "أُزيل من المفضلة"));
    }

    private void updateFavorite() {
        boolean favorite = host.favorites.isFavorite(item.type(), item.id());
        TextView glyph = Widgets.glyphOf(favoriteButton);
        Nator.setIcon(glyph, favorite ? "favorite" : "favorite_border");
        glyph.setTextColor(favorite ? 0xFFFF6B81 : Nator.HEADING);
    }

    private void toggleWatched() {
        WatchProgressStore.EntrySnapshot snapshot = host.progress.snapshot(XtreamContentType.MOVIE, item.id());
        if (snapshot != null && snapshot.completed()) {
            host.progress.remove(XtreamContentType.MOVIE, item.id());
        } else {
            host.progress.markCompleted(item, 1L);
        }
        updateWatched();
        renderActions();
    }

    private void updateWatched() {
        if (watchedButton == null) {
            return;
        }
        WatchProgressStore.EntrySnapshot snapshot = host.progress.snapshot(XtreamContentType.MOVIE, item.id());
        boolean watched = snapshot != null && snapshot.completed();
        TextView glyph = Widgets.glyphOf(watchedButton);
        Nator.setIcon(glyph, watched ? "check_circle" : "check_circle_outline");
        glyph.setTextColor(watched ? Nator.SUCCESS : Nator.HEADING);
    }

    private void showMore(View anchor) {
        LinearLayout menu = host.menuPanel();
        if (!host.kidsMode()) {
            // Another player would escape Kids mode's watch-time limit.
            host.menuItem(menu, Tr.t("Open in external player", "فتح في مشغّل خارجي"), "open_in_new", false, () -> {
                XtreamMediaItem target = item.type() == XtreamContentType.SERIES ? resumeEpisode() : item;
                if (target != null) {
                    openExternally(target);
                }
            });
        }
        host.menuItem(menu, host.favorites.isFavorite(item.type(), item.id()) ? Tr.t("Remove from favorites", "إزالة من المفضلة") : Tr.t("Add to favorites", "إضافة إلى المفضلة"),
                "favorite_border", false, this::toggleFavorite);
        host.menuItem(menu, Tr.t("Remove from recently viewed", "إزالة من «شوهد مؤخرًا»"), "history", false, () -> {
            host.history.remove(item.type(), item.id());
            host.snack(Tr.t("Removed from recently viewed", "أُزيل من «شوهد مؤخرًا»"));
        });
        host.showMenu(anchor, menu);
    }

    private void openExternally(XtreamMediaItem target) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(android.net.Uri.parse(host.streamUrl(target)), "video/*");
        try {
            host.startActivity(android.content.Intent.createChooser(intent, Tr.t("Open with", "فتح باستخدام")));
        } catch (Exception exception) {
            host.snack(Tr.t("No external player available", "لا يوجد مشغّل خارجي"));
        }
    }

    private void renderCredits() {
        credits.removeAllViews();
        if (info == null) {
            return;
        }
        LinearLayout left = Nator.column(context);
        List<String> cast = names(info.cast());
        if (!cast.isEmpty()) {
            LinearLayout line = Nator.row(context);
            line.addView(Nator.text(context, Tr.t("Starring", "بطولة"), 13, Nator.W500, Nator.EYEBROW), Nator.size(context, 72, -2));
            StringBuilder shown = new StringBuilder();
            for (int i = 0; i < Math.min(3, cast.size()); i++) {
                shown.append(i == 0 ? "" : ", ").append(cast.get(i));
            }
            line.addView(Nator.line(context, shown.toString(), 13, Nator.W400, Nator.HEADING), new LinearLayout.LayoutParams(0, -2, 1f));
            if (cast.size() > 3) {
                line.addView(Nator.text(context, Tr.t("and more", "وآخرون"), 13, Nator.W400, Nator.BODY), Nator.margins(context, Nator.wrap(), 8, 0, 0, 0));
            }
            left.addView(line, Nator.matchWidth());
        }
        if (!info.director().trim().isEmpty()) {
            LinearLayout line = Nator.row(context);
            line.addView(Nator.text(context, Tr.t("Director", "إخراج"), 13, Nator.W500, Nator.EYEBROW), Nator.size(context, 72, -2));
            line.addView(Nator.line(context, info.director().trim(), 13, Nator.W400, Nator.HEADING), new LinearLayout.LayoutParams(0, -2, 1f));
            left.addView(line, Nator.margins(context, Nator.matchWidth(), 0, 4, 0, 0));
        }
        LinearLayout wrap = Nator.row(context);
        wrap.setGravity(Gravity.TOP);
        wrap.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        String rating = normalizeRating(info.rating());
        if (!rating.isEmpty()) {
            LinearLayout box = Nator.column(context);
            box.addView(Nator.text(context, Tr.t("Rating", "التقييم"), 13, Nator.W400, Nator.MUTED));
            box.addView(Nator.text(context, rating, 14, Nator.W500, Nator.HEADING), Nator.margins(context, Nator.wrap(), 0, 3, 0, 0));
            wrap.addView(box, Nator.margins(context, Nator.wrap(), 24, 0, 0, 0));
        }
        credits.addView(wrap, Nator.matchWidth());
    }

    private void renderCast() {
        castSection.removeAllViews();
        if (info == null) {
            return;
        }
        List<String[]> people = new ArrayList<>();
        if (!info.director().trim().isEmpty()) {
            people.add(new String[]{info.director().trim(), Tr.t("Director", "إخراج")});
        }
        for (String name : names(info.cast())) {
            people.add(new String[]{name, ""});
        }
        if (people.isEmpty()) {
            return;
        }
        LinearLayout header = Nator.row(context);
        header.addView(Nator.text(context, Tr.t("Cast & crew", "طاقم العمل"), 18, Nator.W600, Nator.HEADING));
        header.addView(Nator.text(context, String.valueOf(people.size()), 12.5f, Nator.W400, Nator.EYEBROW), Nator.margins(context, Nator.wrap(), 10, 0, 0, 0));
        castSection.addView(header, Nator.margins(context, Nator.wrap(), 0, 36, 0, 0));
        HorizontalScrollView strip = new HorizontalScrollView(context);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipChildren(false);
        LinearLayout row = Nator.row(context);
        row.setGravity(Gravity.TOP);
        for (String[] person : people) {
            LinearLayout cell = Nator.column(context);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView avatar = Nator.text(context, initials(person[0]), 18, Nator.W600, Nator.BODY);
            avatar.setGravity(Gravity.CENTER);
            GradientDrawable circle = Nator.oval(Nator.WIDGET_BG);
            circle.setStroke(Math.max(1, Nator.px(context, 1)), 0x24FFFFFF);
            avatar.setBackground(circle);
            cell.addView(avatar, Nator.size(context, 72, 72));
            TextView name = Nator.line(context, person[0], 12.5f, Nator.W400, Nator.HEADING);
            name.setGravity(Gravity.CENTER);
            name.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            cell.addView(name, Nator.margins(context, Nator.size(context, 96, -2), 0, 8, 0, 0));
            if (!person[1].isEmpty()) {
                TextView role = Nator.line(context, person[1], 11.5f, Nator.W400, Nator.EYEBROW);
                role.setGravity(Gravity.CENTER);
                role.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                cell.addView(role, Nator.margins(context, Nator.size(context, 96, -2), 0, 2, 0, 0));
            }
            row.addView(cell, Nator.margins(context, Nator.wrap(), 0, 0, 14, 0));
        }
        strip.addView(row);
        castSection.addView(strip, Nator.margins(context, Nator.matchWidth(), 0, 22, 0, 8));
    }

    // ─── Series episodes ────────────────────────────────────────────────────

    private void buildSeasons(List<XtreamMediaItem> episodes) {
        seasons.clear();
        for (XtreamMediaItem episode : episodes) {
            String key = episode.categoryId().isEmpty() ? "1" : episode.categoryId();
            List<XtreamMediaItem> list = seasons.get(key);
            if (list == null) {
                list = new ArrayList<>();
                seasons.put(key, list);
            }
            list.add(episode);
        }
        XtreamMediaItem resume = resumeEpisode();
        selectedSeason = resume != null ? resume.categoryId() : seasons.isEmpty() ? null : seasons.keySet().iterator().next();
    }

    private List<XtreamMediaItem> allEpisodes() {
        List<XtreamMediaItem> all = new ArrayList<>();
        for (List<XtreamMediaItem> list : seasons.values()) {
            all.addAll(list);
        }
        return all;
    }

    private XtreamMediaItem resumeEpisode() {
        List<XtreamMediaItem> all = allEpisodes();
        if (all.isEmpty()) {
            return null;
        }
        XtreamMediaItem resume = SeriesResumeResolver.findResumeEpisode(host.progress.list(XtreamContentType.EPISODE), all);
        if (resume != null) {
            return resume;
        }
        XtreamMediaItem last = SeriesResumeResolver.findLastWatchedEpisode(host.progress.snapshotsFor(XtreamContentType.EPISODE), all);
        if (last != null) {
            int index = SeriesResumeResolver.findEpisodeIndex(all, last);
            if (index >= 0 && index + 1 < all.size()) {
                return all.get(index + 1);
            }
            return last;
        }
        return all.get(0);
    }

    private String episodeCode(XtreamMediaItem episode) {
        List<XtreamMediaItem> list = seasons.get(episode.categoryId());
        int number = list == null ? 1 : list.indexOf(episode) + 1;
        int season = 1;
        try {
            season = Integer.parseInt(episode.categoryId());
        } catch (NumberFormatException ignored) {
            // Non-numeric season keys keep S01.
        }
        return String.format(Locale.US, "S%02dE%02d", season, Math.max(1, number));
    }

    private void renderEpisodesLoading() {
        episodesSection.removeAllViews();
        LinearLayout header = Nator.row(context);
        header.addView(Nator.text(context, Tr.t("Episodes", "الحلقات"), 18, Nator.W600, Nator.HEADING));
        episodesSection.addView(header, Nator.margins(context, Nator.wrap(), 0, 36, 0, 0));
        for (int i = 0; i < 3; i++) {
            LinearLayout row = Nator.row(context);
            Nator.pad(row, 8, 10, 12, 10);
            row.addView(Widgets.skeletonBar(context, 20, 12), Nator.margins(context, Nator.wrap(), 8, 0, 24, 0));
            View thumb = new View(context);
            thumb.setBackground(Nator.rounded(context, Nator.alpha(0xFFE6E1E5, 0.08f), 6));
            row.addView(thumb, Nator.margins(context, Nator.size(context, 168, 95), 0, 0, 16, 0));
            LinearLayout texts = Nator.column(context);
            texts.addView(Widgets.skeletonBar(context, 180, 12));
            texts.addView(Widgets.skeletonBar(context, 320, 9), Nator.margins(context, Nator.wrap(), 0, 12, 0, 0));
            row.addView(texts);
            episodesSection.addView(row, Nator.margins(context, Nator.matchWidth(), 0, 12, 0, 0));
        }
    }

    private void renderEpisodesError() {
        episodesSection.removeAllViews();
        episodesSection.addView(Widgets.emptyState(context, "error_outline", Tr.t("Episodes unavailable", "تعذّر عرض الحلقات"),
                Tr.t("The series details could not be loaded. Try again in a moment.", "تعذّر تحميل تفاصيل المسلسل. حاول مرة أخرى بعد قليل.")));
    }

    private void renderEpisodes() {
        if (seriesDetail == null) {
            return;
        }
        episodesSection.removeAllViews();
        List<XtreamMediaItem> episodes = selectedSeason == null ? new ArrayList<>() : seasons.get(selectedSeason);
        if (episodes == null) {
            episodes = new ArrayList<>();
        }
        LinearLayout header = Nator.row(context);
        header.addView(Nator.text(context, Tr.t("Episodes", "الحلقات"), 18, Nator.W600, Nator.HEADING), Nator.margins(context, Nator.wrap(), 0, 0, 10, 0));
        HorizontalScrollView pillsScroll = new HorizontalScrollView(context);
        pillsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout pills = Nator.row(context);
        for (String season : seasons.keySet()) {
            boolean selected = season.equals(selectedSeason);
            TextView pill = Nator.text(context, Tr.t("Season ", "الموسم ") + season, 13, selected ? Nator.W500 : Nator.W400,
                    selected ? Nator.HEADING : Nator.BODY);
            pill.setGravity(Gravity.CENTER);
            Nator.pad(pill, 12, 0, 12, 0);
            pill.setBackground(Nator.states(
                    selected ? Nator.roundedStroke(context, Nator.WIDGET_BG, 999, 1, Nator.GHOST_BORDER)
                            : Nator.roundedStroke(context, 0, 999, 1, 0),
                    Nator.roundedStroke(context, selected ? Nator.WIDGET_BG : Nator.HOVER_OVERLAY, 999, 1, Nator.GHOST_BORDER)));
            Nator.focusable(pill, 999);
            pill.setOnClickListener(v -> {
                selectedSeason = season;
                renderEpisodes();
                View again = episodesSection.findViewWithTag("season-" + season);
                if (again != null) {
                    again.requestFocus();
                }
            });
            pill.setTag("season-" + season);
            pills.addView(pill, Nator.margins(context, Nator.size(context, -2, 30), 0, 0, 4, 0));
        }
        pillsScroll.addView(pills);
        header.addView(pillsScroll);
        header.addView(Nator.text(context, Nator.plural(episodes.size(), "episode", "episodes", "حلقة واحدة", "حلقتان", "حلقات", "حلقة"), 12.5f, Nator.W400, Nator.EYEBROW),
                Nator.margins(context, Nator.wrap(), 10, 0, 0, 0));
        header.addView(Nator.spacer(context));
        header.addView(viewToggle());
        episodesSection.addView(header, Nator.margins(context, Nator.matchWidth(), 0, 36, 0, 14));

        if (episodeGrid) {
            renderEpisodeGrid(episodes);
        } else {
            for (int i = 0; i < episodes.size(); i++) {
                episodesSection.addView(episodeRow(episodes.get(i), i + 1), Nator.margins(context, Nator.matchWidth(), 0, 0, 0, 2));
            }
        }
    }

    private View viewToggle() {
        LinearLayout group = Nator.row(context);
        group.setBackground(Nator.roundedStroke(context, Nator.WIDGET_BG, 8, 1, Nator.GHOST_BORDER));
        Nator.pad(group, 2, 2, 2, 2);
        String[] icons = {"grid_view", "view_list"};
        for (int i = 0; i < icons.length; i++) {
            boolean active = (i == 0) == episodeGrid;
            FrameLayout button = new FrameLayout(context);
            button.setBackground(Nator.states(active ? Nator.rounded(context, Nator.GHOST_BG, 6) : new GradientDrawable(),
                    Nator.rounded(context, Nator.alpha(0xFFE6E1E5, 0.2f), 6)));
            Nator.focusable(button, 6);
            button.addView(Nator.icon(context, icons[i], 16, active ? Nator.HEADING : Nator.EYEBROW), new FrameLayout.LayoutParams(-1, -1));
            boolean grid = i == 0;
            button.setOnClickListener(v -> {
                episodeGrid = grid;
                renderEpisodes();
            });
            group.addView(button, Nator.size(context, 30, 26));
        }
        return group;
    }

    private View episodeRow(XtreamMediaItem episode, int number) {
        EpisodeInfo meta = EpisodeInfo.of(episode);
        LinearLayout row = Nator.row(context);
        Nator.pad(row, 8, 10, 12, 10);
        Nator.focusable(row, 10);
        row.setBackground(Nator.states(new GradientDrawable(), Nator.rounded(context, Nator.WIDGET_BG, 10)));

        TextView index = Nator.text(context, String.valueOf(number), 15, Nator.W600, Nator.EYEBROW);
        index.setGravity(Gravity.CENTER);
        row.addView(index, Nator.margins(context, Nator.size(context, 36, -2), 0, 0, 16, 0));

        FrameLayout thumb = new FrameLayout(context);
        thumb.setBackground(Nator.roundedStroke(context, Nator.alpha(0xFFE6E1E5, 0.08f), 6, 1, Nator.alpha(0xFFE6E1E5, 0.12f)));
        thumb.setClipToOutline(true);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        String thumbUrl = !meta.image.isEmpty() ? meta.image : (PosterUrls.isUsable(item.imageUrl()) ? item.imageUrl() : "");
        PosterLoader.load(thumbUrl, image, false);
        thumb.addView(image, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(context);
        shade.setBackgroundColor(0x5905070B);
        thumb.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout disc = new FrameLayout(context);
        disc.setBackground(Nator.oval(0xF2F2F4F8));
        disc.addView(Nator.icon(context, "play_arrow", 18, Nator.CTA_FG), new FrameLayout.LayoutParams(-1, -1));
        thumb.addView(disc, new FrameLayout.LayoutParams(Nator.px(context, 34), Nator.px(context, 34), Gravity.CENTER));
        WatchProgressStore.EntrySnapshot snapshot = host.progress.snapshot(XtreamContentType.EPISODE, episode.id());
        if (snapshot != null && snapshot.progressPercent() > 0) {
            LinearLayout track = Nator.row(context);
            track.setBackgroundColor(0x66000000);
            View fill = new View(context);
            fill.setBackgroundColor(snapshot.completed() ? Nator.SUCCESS : Nator.SELECTION);
            track.addView(fill, new LinearLayout.LayoutParams(0, -1, snapshot.completed() ? 100 : snapshot.progressPercent()));
            track.addView(new View(context), new LinearLayout.LayoutParams(0, -1, snapshot.completed() ? 0 : 100 - snapshot.progressPercent()));
            thumb.addView(track, new FrameLayout.LayoutParams(-1, Nator.px(context, 3), Gravity.BOTTOM));
        }
        row.addView(thumb, Nator.margins(context, Nator.size(context, 168, 95), 0, 0, 16, 0));

        LinearLayout texts = Nator.column(context);
        LinearLayout titleLine = Nator.row(context);
        titleLine.addView(Nator.line(context, episode.title(), 14.5f, Nator.W600, Nator.HEADING), new LinearLayout.LayoutParams(-2, -2));
        String metaLine = meta.metaLine();
        if (!metaLine.isEmpty()) {
            titleLine.addView(Nator.line(context, metaLine, 12, Nator.W400, Nator.EYEBROW), Nator.margins(context, Nator.wrap(), 10, 0, 0, 0));
        }
        texts.addView(titleLine);
        if (!meta.plot.isEmpty()) {
            TextView plot = Nator.text(context, meta.plot, 12.5f, Nator.W400, 0xFFB5BBCB);
            plot.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            Nator.lineHeight(plot, 18.1f);
            plot.setMaxLines(2);
            plot.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(plot, Nator.margins(context, Nator.matchWidth(), 0, 4, 0, 0));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        boolean watched = snapshot != null && snapshot.completed();
        row.addView(Nator.icon(context, watched ? "check_circle" : "check_circle_outline", 16, watched ? Nator.SUCCESS : Nator.BODY),
                Nator.margins(context, Nator.size(context, 28, 28), 12, 0, 8, 0));
        row.addView(Nator.icon(context, "more_horiz", 16, Nator.BODY), Nator.size(context, 28, 28));
        row.setOnClickListener(v -> host.play(episode, allEpisodes()));
        return row;
    }

    private void renderEpisodeGrid(List<XtreamMediaItem> episodes) {
        int perRow = 4;
        LinearLayout current = null;
        for (int i = 0; i < episodes.size(); i++) {
            if (i % perRow == 0) {
                current = Nator.row(context);
                current.setGravity(Gravity.TOP);
                episodesSection.addView(current, Nator.margins(context, Nator.matchWidth(), 0, 0, 0, 16));
            }
            XtreamMediaItem episode = episodes.get(i);
            EpisodeInfo meta = EpisodeInfo.of(episode);
            LinearLayout card = Nator.column(context);
            Nator.focusable(card, 8);
            card.setBackground(Nator.states(new GradientDrawable(), Nator.rounded(context, Nator.WIDGET_BG, 8)));
            Nator.pad(card, 4, 4, 4, 8);
            FrameLayout thumb = new FrameLayout(context);
            thumb.setBackground(Nator.rounded(context, Nator.alpha(0xFFE6E1E5, 0.08f), 6));
            thumb.setClipToOutline(true);
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            PosterLoader.load(!meta.image.isEmpty() ? meta.image : item.imageUrl(), image, false);
            thumb.addView(image, new FrameLayout.LayoutParams(-1, -1));
            card.addView(thumb, Nator.size(context, 190, 107));
            card.addView(Nator.line(context, (i + 1) + ". " + episode.title(), 13, Nator.W600, Nator.HEADING),
                    Nator.margins(context, Nator.size(context, 190, -2), 0, 8, 0, 0));
            card.addView(Nator.line(context, meta.metaLine(), 11.5f, Nator.W400, Nator.EYEBROW),
                    Nator.margins(context, Nator.size(context, 190, -2), 0, 3, 0, 0));
            card.setOnClickListener(v -> host.play(episode, allEpisodes()));
            current.addView(card, Nator.margins(context, Nator.wrap(), 0, 0, 12, 0));
        }
    }

    // ─── Formatting ─────────────────────────────────────────────────────────

    static String formatDuration(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String value = raw.trim();
        if (value.endsWith("min/ep")) {
            return value;
        }
        int minutes;
        try {
            if (value.contains(":")) {
                String[] parts = value.split(":");
                int h = Integer.parseInt(parts[0]);
                int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                minutes = h * 60 + m;
            } else {
                minutes = Integer.parseInt(value.replaceAll("[^0-9]", ""));
            }
        } catch (NumberFormatException exception) {
            return value;
        }
        if (minutes <= 0) {
            return "";
        }
        if (minutes < 60) {
            return minutes + Tr.t(" min", " د");
        }
        return (minutes / 60) + Tr.t(" h ", " س ") + (minutes % 60) + Tr.t(" min", " د");
    }

    static String yearOf(String date) {
        if (date == null) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(date);
        return matcher.find() ? matcher.group() : "";
    }

    static String normalizeRating(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return value <= 0 ? "" : String.format(Locale.US, "%.1f", value);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static List<String> names(String csv) {
        List<String> result = new ArrayList<>();
        if (csv == null) {
            return result;
        }
        for (String part : csv.split(",")) {
            if (!part.trim().isEmpty()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && builder.length() < 2) {
                builder.appendCodePoint(Character.toUpperCase(part.codePointAt(0)));
            }
        }
        return builder.toString();
    }

    /** Fields IPTVnator shows per episode, read from the Xtream `info` object we carry. */
    static final class EpisodeInfo {
        String image = "";
        String plot = "";
        String duration = "";
        String airDate = "";

        static EpisodeInfo of(XtreamMediaItem episode) {
            EpisodeInfo info = new EpisodeInfo();
            String raw = episode.imageUrl();
            if (raw == null || !raw.trim().startsWith("{")) {
                if (PosterUrls.isUsable(raw)) {
                    info.image = raw;
                }
                return info;
            }
            try {
                JSONObject json = new JSONObject(raw);
                info.image = json.optString("movie_image", "");
                info.plot = json.optString("plot", json.optString("overview", ""));
                info.duration = json.optString("duration", "");
                info.airDate = json.optString("air_date", json.optString("releasedate", ""));
            } catch (Exception ignored) {
                // Unparseable info: leave the defaults.
            }
            return info;
        }

        String metaLine() {
            List<String> parts = new ArrayList<>();
            String minutes = formatDuration(duration);
            if (!minutes.isEmpty()) {
                parts.add(minutes);
            }
            String date = formatDate(airDate);
            if (!date.isEmpty()) {
                parts.add(date);
            }
            return TextUtils.join(" · ", parts);
        }

        private static String formatDate(String raw) {
            if (raw == null || raw.length() < 10) {
                return raw == null ? "" : raw;
            }
            try {
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat out = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);
                return out.format(in.parse(raw.substring(0, 10)));
            } catch (Exception ignored) {
                return raw;
            }
        }
    }
}
