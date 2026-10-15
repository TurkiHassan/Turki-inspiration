package com.ztv.player.workspace;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ztv.player.PlayerActivity;
import com.ztv.player.R;
import com.ztv.player.data.EpisodePlaybackQueue;
import com.ztv.player.data.FavoritesStore;
import com.ztv.player.data.HistoryStore;
import com.ztv.player.data.KidsStore;
import com.ztv.player.data.LibraryDiskCache;
import com.ztv.player.data.ServerAddressPolicy;
import com.ztv.player.data.ServerConfigStore;
import com.ztv.player.data.SettingsStore;
import com.ztv.player.data.WatchProgressStore;
import com.ztv.player.data.XtreamApiClient;
import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamCredentials;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.data.XtreamSessionStore;
import com.ztv.player.data.XtreamUrlFactory;
import com.ztv.player.ui.PosterLoader;
import com.ztv.player.ui.PosterUrls;
import com.ztv.player.ui.Tr;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * IPTVnator's WorkspaceShellComponent as a native TV activity: the persistent rail,
 * header, optional context panel and the content frame, plus the page stack behind the
 * header's Back.
 */
public final class WorkspaceActivity extends Activity {
    private static final String PREFS = "ztv_workspace";
    private static final String PREF_TITLE = "playlist_title";
    private static final String PREF_LAST_SECTION = "last_section";
    private static final int MAX_QUEUED_EPISODES = 40;
    static final int REQUEST_VOICE = 703;

    // Stores & services (shared with every page).
    XtreamApiClient api;
    Catalog catalog;
    FavoritesStore favorites;
    WatchProgressStore progress;
    HistoryStore history;
    SettingsStore settings;
    ServerConfigStore serverConfig;
    WorkspacePrefs prefs;
    KidsStore kids;
    private KidsGuard kidsGuard;
    private FrameLayout lockLayer;
    private View headerAdd;
    private View switcherView;
    private View kidsBadge;
    private View railKidsExit;
    private XtreamSessionStore sessionStore;
    private XtreamCredentials credentials;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Page> stack = new ArrayList<>();
    private FrameLayout root;
    private LinearLayout railLinks;
    private LinearLayout railContext;
    private View railSettings;
    private final Map<Page.Section, View> railButtons = new EnumMap<>(Page.Section.class);
    private FrameLayout headerBackSlot;
    private TextView switcherName;
    private TextView switcherSubtitle;
    private LinearLayout searchField;
    private TextView searchChip;
    private EditText searchInput;
    private ComponentName voiceRecognizer;
    private boolean voiceResolved;
    private FrameLayout contextFrame;
    private FrameLayout contentFrame;
    private FrameLayout overlayLayer;
    private final List<Runnable> overlayDismissers = new ArrayList<>();
    private boolean suppressSearchEvents;
    private Runnable pendingSearch;
    private long lastBackAt;
    private TextView snack;
    private boolean returnedFromPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Nator.init(this);
        enableImmersiveMode();
        settings = new SettingsStore(this);
        Tr.setArabic("ar".equals(settings.appLanguage()));
        serverConfig = new ServerConfigStore(this);
        prefs = new WorkspacePrefs(this);
        api = new XtreamApiClient(serverConfig);
        catalog = new Catalog(this, api, new LibraryDiskCache(getCacheDir()));
        kids = new KidsStore(this);
        if (kids.autoStart() && kids.hasPin()) {
            kids.setActive(true);
        }
        bindProfile();
        PosterLoader.init(getCacheDir());

        root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.setBackgroundColor(Nator.SHELL_BG);
        setContentView(root);
        buildShell();
        applyKidsChrome();

        // Keystore-backed session read stays off the main thread (pitfall 8).
        new Thread(() -> {
            XtreamSessionStore store = new XtreamSessionStore(this);
            XtreamCredentials saved = store.load();
            runOnUiThread(() -> {
                sessionStore = store;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (saved.isComplete()) {
                    adoptCredentials(saved);
                    openStartPage();
                } else {
                    showWelcome();
                }
            });
        }, "ztv-session-init").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        kidsGuard.start();
    }

    @Override
    protected void onPause() {
        kidsGuard.stop();
        super.onPause();
    }

    // ─── Kids mode ──────────────────────────────────────────────────────────

    public boolean kidsMode() {
        return kids.isActive();
    }

    /** Kids mode keeps its own favorites, history and progress, and filters the catalog. */
    private void bindProfile() {
        String profile = kids.isActive() ? "kids" : null;
        favorites = new FavoritesStore(this, profile);
        progress = new WatchProgressStore(this, profile);
        history = new HistoryStore(this, profile);
        catalog.setKidsPolicy(kids.isActive() ? kids.policy() : null);
    }

    private void applyKidsChrome() {
        boolean on = kids.isActive();
        View sources = railButtons.get(Page.Section.SOURCES);
        if (sources != null) {
            sources.setVisibility(on ? View.GONE : View.VISIBLE);
        }
        railSettings.setVisibility(on ? View.GONE : View.VISIBLE);
        railKidsExit.setVisibility(on ? View.VISIBLE : View.GONE);
        headerAdd.setVisibility(on ? View.GONE : View.VISIBLE);
        kidsBadge.setVisibility(on ? View.VISIBLE : View.GONE);
        switcherView.setFocusable(!on);
        switcherView.setClickable(!on);
        kidsGuard.check();
    }

    /** Settings › Kids mode › Start: the parent hands the TV over. */
    void enterKidsMode() {
        kids.setActive(true);
        bindProfile();
        applyKidsChrome();
        resetTo(new DashboardPage(this));
        snack(Tr.t("Kids mode is on", "تم تشغيل وضع الأطفال"));
    }

    void exitKidsMode() {
        PinDialog.verify(this, Tr.t("Exit Kids mode", "الخروج من وضع الأطفال"), () -> {
            kids.setActive(false);
            bindProfile();
            applyKidsChrome();
            resetTo(new DashboardPage(this));
            snack(Tr.t("Kids mode is off", "تم إيقاف وضع الأطفال"));
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (returnedFromPlayer) {
            returnedFromPlayer = false;
            Page top = top();
            if (top != null) {
                top.onPlaybackReturned();
            }
        }
    }

    @Override
    protected void onStop() {
        Page page = top();
        if (page != null) {
            page.onHostStopped();
        }
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VOICE) {
            List<String> heard = resultCode == RESULT_OK && data != null
                    ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) : null;
            if (heard != null && !heard.isEmpty()) {
                applyVoiceSearch(heard);
            }
            return;
        }
        Page page = top();
        if (resultCode == RESULT_OK && data != null && data.getData() != null && page instanceof SettingsPage) {
            ((SettingsPage) page).onDocument(requestCode, data.getData());
        }
    }

    @Override
    protected void onDestroy() {
        for (Page page : stack) {
            page.onDestroy();
        }
        catalog.shutdown();
        api.shutdown();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveMode();
        }
    }

    private void enableImmersiveMode() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ─── Session ────────────────────────────────────────────────────────────

    public XtreamCredentials credentials() {
        return credentials;
    }

    public boolean hasSource() {
        return credentials != null && credentials.isComplete();
    }

    private void adoptCredentials(XtreamCredentials saved) {
        credentials = saved.withHost(serverConfig.effectiveHost());
        catalog.setCredentials(credentials);
        updateSwitcher();
        rebuildRailContext();
    }

    public String playlistTitle() {
        String title = prefs.string(PREF_TITLE, "");
        if (title.isEmpty() && credentials != null) {
            return ServerAddressPolicy.display(credentials.host());
        }
        return title.isEmpty() ? "ZTV" : title;
    }

    /** Called by the Add playlist dialog after the account authenticated. */
    void onSourceAdded(XtreamCredentials signedIn, String title, boolean serverChanged) {
        if (serverChanged) {
            catalog.clearDisk();
            prefs.put(SourcesPage.PREF_ADDED_AT, String.valueOf(System.currentTimeMillis()));
        }
        sessionStore.save(signedIn);
        prefs.put(PREF_TITLE, title == null ? "" : title.trim());
        adoptCredentials(signedIn);
        resetTo(new CatalogPage(this, XtreamContentType.MOVIE));
    }

    /** Sources › Delete: signs out and returns to the welcome state. */
    void removeSource() {
        if (sessionStore != null) {
            sessionStore.clear();
        }
        catalog.clearDisk();
        credentials = null;
        catalog.setCredentials(null);
        prefs.put(PREF_TITLE, "");
        updateSwitcher();
        rebuildRailContext();
        showWelcome();
    }

    void updateSourceTitle(String title) {
        prefs.put(PREF_TITLE, title == null ? "" : title.trim());
        updateSwitcher();
    }

    private void showWelcome() {
        resetTo(new DashboardPage(this));
    }

    /** Launch extra `ztv.section` (e.g. VOD, LIVE) opens that section directly. */
    static final String EXTRA_SECTION = "ztv.section";

    private void openStartPage() {
        Page.Section section = Page.Section.DASHBOARD;
        String requested = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_SECTION);
        if (requested != null) {
            try {
                openSection(Page.Section.valueOf(requested));
                catalog.prefetch(XtreamContentType.MOVIE, XtreamContentType.LIVE, XtreamContentType.SERIES);
                return;
            } catch (IllegalArgumentException ignored) {
                // Unknown section: fall through to the normal start page.
            }
        }
        if (settings.startupHome()) {
            section = Page.Section.DASHBOARD;
        } else {
            try {
                section = Page.Section.valueOf(prefs.string(PREF_LAST_SECTION, Page.Section.DASHBOARD.name()));
            } catch (IllegalArgumentException ignored) {
                section = Page.Section.DASHBOARD;
            }
        }
        openSection(section);
        catalog.prefetch(XtreamContentType.MOVIE, XtreamContentType.LIVE, XtreamContentType.SERIES);
    }

    // ─── Navigation ─────────────────────────────────────────────────────────

    public Page top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /** Rail link: a fresh root for that section (IPTVnator routerLink). */
    public void openSection(Page.Section section) {
        if (kids.isActive() && (section == Page.Section.SETTINGS || section == Page.Section.SOURCES)) {
            return;
        }
        Page page;
        switch (section) {
            case SOURCES:
                page = new SourcesPage(this);
                break;
            case FAVORITES:
                page = new CollectionPage(this, CollectionPage.Kind.FAVORITES);
                break;
            case RECENT:
                page = new CollectionPage(this, CollectionPage.Kind.RECENT);
                break;
            case VOD:
                page = new CatalogPage(this, XtreamContentType.MOVIE);
                break;
            case LIVE:
                page = new CatalogPage(this, XtreamContentType.LIVE);
                break;
            case SERIES:
                page = new CatalogPage(this, XtreamContentType.SERIES);
                break;
            case RECENTLY_ADDED:
                page = new RecentlyAddedPage(this);
                break;
            case SEARCH:
                page = new SearchPage(this);
                break;
            case SETTINGS:
                page = new SettingsPage(this);
                break;
            case DASHBOARD:
            default:
                page = new DashboardPage(this);
                break;
        }
        if (section != Page.Section.SETTINGS) {
            prefs.put(PREF_LAST_SECTION, section.name());
        }
        Page current = top();
        if (current != null && current.section() == section && stack.size() == 1
                && !(current instanceof DashboardPage && hasSource() != ((DashboardPage) current).builtWithSource())) {
            current.requestInitialFocus();
            return;
        }
        resetTo(page);
    }

    public void resetTo(Page page) {
        for (Page old : stack) {
            old.onHidden();
            old.onDestroy();
        }
        stack.clear();
        stack.add(page);
        show(page, true);
    }

    public void push(Page page) {
        Page current = top();
        if (current != null) {
            current.onHidden();
        }
        stack.add(page);
        show(page, true);
    }

    public void pop() {
        if (stack.size() <= 1) {
            return;
        }
        Page leaving = stack.remove(stack.size() - 1);
        leaving.onHidden();
        leaving.onDestroy();
        show(top(), false);
    }

    private void show(Page page, boolean fresh) {
        dismissOverlays();
        View panel = page.contextPanel();
        contextFrame.removeAllViews();
        if (panel != null) {
            detach(panel);
            contextFrame.addView(panel, new FrameLayout.LayoutParams(-1, -1));
            contextFrame.setVisibility(View.VISIBLE);
        } else {
            contextFrame.setVisibility(View.GONE);
        }
        contentFrame.removeAllViews();
        View content = page.content();
        detach(content);
        contentFrame.addView(content, new FrameLayout.LayoutParams(-1, -1));
        highlightRail(page.section());
        headerBackSlot.setVisibility(stack.size() > 1 ? View.VISIBLE : View.GONE);
        syncSearch(page);
        page.onShown();
        ui.post(page::requestInitialFocus);
    }

    private static void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    /** Re-reads the page's panel/content after it rebuilt them (e.g. live layout switch). */
    public void refreshChrome(Page page) {
        if (page != top()) {
            return;
        }
        syncSearch(page);
        headerBackSlot.setVisibility(stack.size() > 1 ? View.VISIBLE : View.GONE);
    }

    public void openDetail(XtreamMediaItem item) {
        if (item.type() == XtreamContentType.MOVIE || item.type() == XtreamContentType.SERIES) {
            push(new DetailPage(this, item));
        } else if (item.type() == XtreamContentType.LIVE) {
            play(item, null);
        }
    }

    // ─── Playback ───────────────────────────────────────────────────────────

    public String streamUrl(XtreamMediaItem item) {
        XtreamUrlFactory factory = new XtreamUrlFactory(serverConfig.effectiveHost());
        if (item.type() == XtreamContentType.MOVIE) {
            return factory.movieStreamUrl(credentials.username(), credentials.password(), item.id(), item.containerExtension());
        }
        if (item.type() == XtreamContentType.EPISODE) {
            return factory.seriesStreamUrl(credentials.username(), credentials.password(), item.id(), item.containerExtension());
        }
        return factory.liveStreamUrl(credentials.username(), credentials.password(), item.id());
    }

    public void play(XtreamMediaItem item, List<XtreamMediaItem> siblingsOrEpisodes) {
        kidsGuard.check();
        if (kidsGuard.locked()) {
            return;
        }
        if (!hasSource()) {
            return;
        }
        // The embedded live preview owns a separate ExoPlayer. Release it before handing
        // playback to the full-screen activity so the two players never overlap in audio.
        Page page = top();
        if (page != null) {
            page.onPlaybackStarting();
        }
        history.record(item);
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, item.title());
        intent.putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl(item));
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_TYPE, item.type().name());
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_ID, item.id());
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_CATEGORY, item.categoryId());
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_IMAGE, item.imageUrl());
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_DETAIL, item.detail());
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_EXT, item.containerExtension());
        intent.putExtra(PlayerActivity.EXTRA_HOST, serverConfig.effectiveHost());
        intent.putExtra(PlayerActivity.EXTRA_USER, credentials.username());
        intent.putExtra(PlayerActivity.EXTRA_PASS, credentials.password());
        if (item.type() == XtreamContentType.MOVIE || item.type() == XtreamContentType.EPISODE) {
            intent.putExtra(PlayerActivity.EXTRA_RESUME_POSITION, progress.positionMs(item.type(), item.id()));
            if (item.type() == XtreamContentType.EPISODE && siblingsOrEpisodes != null) {
                String queue = episodeQueueAfter(siblingsOrEpisodes, item);
                if (!queue.isEmpty()) {
                    intent.putExtra(PlayerActivity.EXTRA_NEXT_EPISODE_QUEUE, queue);
                }
            }
        } else if (item.type() == XtreamContentType.LIVE) {
            intent.putExtra(PlayerActivity.EXTRA_OPENED_AT_MS, SystemClock.elapsedRealtime());
            if (siblingsOrEpisodes != null && !siblingsOrEpisodes.isEmpty()) {
                intent.putExtra(PlayerActivity.EXTRA_CHANNEL_LIST, serializeChannels(siblingsOrEpisodes));
            }
        }
        returnedFromPlayer = true;
        startActivity(intent);
    }

    private String episodeQueueAfter(List<XtreamMediaItem> episodes, XtreamMediaItem current) {
        List<EpisodePlaybackQueue.Item> queue = new ArrayList<>();
        boolean found = false;
        for (XtreamMediaItem episode : episodes) {
            if (queue.size() >= MAX_QUEUED_EPISODES) {
                break;
            }
            if (found && episode.type() == XtreamContentType.EPISODE) {
                XtreamMediaItem slim = new XtreamMediaItem(episode.type(), episode.title(), episode.id(),
                        episode.categoryId(), PosterUrls.isUsable(episode.imageUrl()) ? episode.imageUrl() : "",
                        "", episode.containerExtension());
                queue.add(new EpisodePlaybackQueue.Item(streamUrl(episode), slim));
            } else if (episode.id() == current.id()) {
                found = true;
            }
        }
        return EpisodePlaybackQueue.encode(queue);
    }

    private static String serializeChannels(List<XtreamMediaItem> items) {
        JSONArray array = new JSONArray();
        for (XtreamMediaItem item : items) {
            try {
                JSONObject object = new JSONObject();
                object.put("title", item.title());
                object.put("id", item.id());
                object.put("categoryId", item.categoryId());
                object.put("imageUrl", item.imageUrl());
                object.put("detail", "");
                object.put("ext", item.containerExtension());
                array.put(object);
            } catch (Exception ignored) {
                // Skip unserializable rows.
            }
        }
        return array.toString();
    }

    // ─── Keys ───────────────────────────────────────────────────────────────

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The Kids lock screen ignores Back and search until a parent unlocks it.
        if (overlayDismissers.isEmpty() && kidsGuard.locked()
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_SEARCH)) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && !overlayDismissers.isEmpty()
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE)) {
            dismissTopOverlay();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && !overlayDismissers.isEmpty()
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE)) {
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_SEARCH && overlayDismissers.isEmpty()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                startVoiceSearch();
            }
            return true;
        }
        Page page = top();
        if (overlayDismissers.isEmpty() && page != null && page.onKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onSearchRequested() {
        startVoiceSearch();
        return true;
    }

    // ─── Voice search ───────────────────────────────────────────────────────

    public boolean voiceSearchAvailable() {
        if (!voiceResolved) {
            voiceResolved = true;
            voiceRecognizer = pickRecognizer(getPackageManager().queryIntentActivities(
                    new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0));
        }
        return SpeechRecognizer.isRecognitionAvailable(this) || voiceRecognizer != null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_VOICE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceSearch();
            } else {
                snack("Allow the microphone to search by voice");
            }
        }
    }

    /** Google TV's own mic overlay when present, so the TV never shows an app chooser. */
    private static ComponentName pickRecognizer(List<ResolveInfo> candidates) {
        String[] preferred = {"com.google.android.katniss", "com.google.android.googlequicksearchbox"};
        for (String packageName : preferred) {
            for (ResolveInfo info : candidates) {
                if (packageName.equals(info.activityInfo.packageName)) {
                    return new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        ResolveInfo first = candidates.get(0);
        return new ComponentName(first.activityInfo.packageName, first.activityInfo.name);
    }

    /**
     * Listens through the system speech service in ZTV's own card; devices without one fall
     * back to whatever app answers the recognize-speech intent.
     */
    public void startVoiceSearch() {
        if (!hasSource()) {
            snack("Add a playlist to search");
            return;
        }
        if (!voiceSearchAvailable()) {
            snack("Voice search isn't available on this device");
            return;
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_VOICE);
                return;
            }
            new VoiceSearchDialog(this, settings.voiceLanguage(), this::applyVoiceSearch).show();
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.setComponent(voiceRecognizer);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a channel, movie or series");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        VoiceSearchDialog.putLanguage(intent, settings.voiceLanguage());
        try {
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (ActivityNotFoundException | SecurityException e) {
            voiceRecognizer = null;
            snack("Voice search isn't available on this device");
        }
    }

    private void applyVoiceSearch(List<String> heard) {
        Page current = top();
        SearchPage page = current instanceof SearchPage ? (SearchPage) current : new SearchPage(this);
        String query = page.applySpoken(heard);
        if (query.isEmpty()) {
            return;
        }
        if (page == current) {
            showSearchText(query);
            page.requestInitialFocus();
        } else {
            push(page);
        }
    }

    /** Puts {@code text} in the header search field without re-running the page search. */
    public void showSearchText(String text) {
        if (pendingSearch != null) {
            ui.removeCallbacks(pendingSearch);
        }
        suppressSearchEvents = true;
        searchInput.setText(text);
        suppressSearchEvents = false;
    }

    @Override
    public void onBackPressed() {
        Page page = top();
        if (page != null && page.onBack()) {
            return;
        }
        View focused = getCurrentFocus();
        if (focused == searchInput && searchInput.getText().length() > 0) {
            searchInput.setText("");
            return;
        }
        if (stack.size() > 1) {
            pop();
            return;
        }
        View railTarget = railButtons.get(page == null ? Page.Section.DASHBOARD : page.section());
        if (focused != null && railTarget != null && !isInRail(focused)) {
            railTarget.requestFocus();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackAt < 2200) {
            finish();
            return;
        }
        lastBackAt = now;
        snack("Press back again to exit");
    }

    private boolean isInRail(View view) {
        View current = view;
        while (current != null) {
            if (current == railLinks || current == railContext || current == railSettings) {
                return true;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    // ─── Shell construction ─────────────────────────────────────────────────

    private void buildShell() {
        lockLayer = new FrameLayout(this);
        lockLayer.setVisibility(View.GONE);
        kidsGuard = new KidsGuard(this, kids, lockLayer);
        LinearLayout shell = Nator.row(this);
        shell.setGravity(Gravity.TOP);
        shell.addView(buildRail(), new LinearLayout.LayoutParams(Nator.px(this, 60), -1));

        LinearLayout right = Nator.column(this);
        right.addView(buildHeader(), new LinearLayout.LayoutParams(-1, Nator.px(this, 56)));

        LinearLayout body = Nator.row(this);
        body.setGravity(Gravity.TOP);
        body.setBackgroundColor(Nator.RAIL_BG);
        contextFrame = new FrameLayout(this);
        contextFrame.setBackgroundColor(Nator.RAIL_BG);
        Nator.pad(contextFrame, 12, 12, 10, 10);
        body.addView(contextFrame, new LinearLayout.LayoutParams(Nator.px(this, 323), -1));

        contentFrame = new FrameLayout(this);
        contentFrame.setBackgroundColor(Nator.CONTENT_BG);
        float radius = Nator.fpx(this, 16);
        // Only the top-left corner is rounded: the outline overhangs the other edges.
        contentFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth() + (int) radius, view.getHeight() + (int) radius, radius);
            }
        });
        contentFrame.setClipToOutline(true);
        contentFrame.setElevation(Nator.fpx(this, 4));
        body.addView(contentFrame, new LinearLayout.LayoutParams(0, -1, 1f));
        right.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        shell.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        root.addView(lockLayer, new FrameLayout.LayoutParams(-1, -1));

        overlayLayer = new FrameLayout(this);
        overlayLayer.setVisibility(View.GONE);
        root.addView(overlayLayer, new FrameLayout.LayoutParams(-1, -1));
    }

    private View buildRail() {
        LinearLayout rail = Nator.column(this);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackgroundColor(Nator.RAIL_BG);
        Nator.pad(rail, 8, 10, 8, 10);

        railLinks = Nator.column(this);
        railLinks.setGravity(Gravity.CENTER_HORIZONTAL);
        addRailLink(railLinks, Page.Section.DASHBOARD, "dashboard", "Dashboard");
        addRailLink(railLinks, Page.Section.SOURCES, "playlist_play", "Sources");
        addRailLink(railLinks, Page.Section.FAVORITES, "favorite", "Global favorites");
        addRailLink(railLinks, Page.Section.RECENT, "history", "Recently viewed");
        rail.addView(railLinks, Nator.wrap());

        railContext = Nator.column(this);
        railContext.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.addView(railContext, Nator.margins(this, Nator.size(this, 49, -2), 0, 4, 0, 4));

        rail.addView(Nator.spacer(this), new LinearLayout.LayoutParams(1, 0, 1f));
        FrameLayout settingsHolder = new FrameLayout(this);
        railSettings = railButton(Page.Section.SETTINGS, "settings", "Settings", true);
        settingsHolder.addView(railSettings);
        FrameLayout exitKids = new FrameLayout(this);
        exitKids.setContentDescription(Tr.t("Exit Kids mode", "الخروج من وضع الأطفال"));
        Nator.focusable(exitKids, 14);
        exitKids.setBackground(Nator.states(new GradientDrawable(), Nator.rounded(this, Nator.HOVER_OVERLAY, 14)));
        exitKids.addView(Nator.icon(this, "lock", 20, Nator.SELECTION), new FrameLayout.LayoutParams(-1, -1));
        exitKids.setOnClickListener(v -> exitKidsMode());
        railKidsExit = exitKids;
        settingsHolder.addView(exitKids, new FrameLayout.LayoutParams(Nator.px(this, 44), Nator.px(this, 44)));
        rail.addView(settingsHolder, Nator.wrap());
        return rail;
    }

    private void rebuildRailContext() {
        if (railContext == null) {
            return;
        }
        railContext.removeAllViews();
        railButtons.remove(Page.Section.VOD);
        railButtons.remove(Page.Section.LIVE);
        railButtons.remove(Page.Section.SERIES);
        railButtons.remove(Page.Section.RECENTLY_ADDED);
        railButtons.remove(Page.Section.SEARCH);
        if (!hasSource()) {
            railContext.setVisibility(View.GONE);
            return;
        }
        railContext.setVisibility(View.VISIBLE);
        // .rail-context-region: 3% wash, 4% hairline, 14px radius, xtream accent bar.
        GradientDrawable region = Nator.roundedStroke(this, Nator.alpha(Nator.ON_SURFACE, 0.03f), 14, 1,
                Nator.alpha(Nator.ON_SURFACE, 0.04f));
        railContext.setBackground(region);
        Nator.pad(railContext, 2, 5, 2, 5);
        FrameLayout accent = new FrameLayout(this);
        View bar = new View(this);
        bar.setBackground(Nator.rounded(this, Nator.alpha(0xFF7C8CF5, 0.5f), 2));
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(Nator.px(this, 25), Nator.px(this, 2));
        barParams.gravity = Gravity.CENTER_HORIZONTAL;
        accent.addView(bar, barParams);
        railContext.addView(accent, Nator.margins(this, Nator.size(this, 45, 2), 0, -6, 0, 4));
        addRailLink(railContext, Page.Section.VOD, "movie", "Movies");
        addRailLink(railContext, Page.Section.LIVE, "live_tv", "Live TV");
        addRailLink(railContext, Page.Section.SERIES, "tv", "Series");
        View divider = Nator.hairline(this, Nator.alpha(Nator.OUTLINE_VARIANT, 0.25f * 2f));
        railContext.addView(divider, Nator.margins(this, Nator.size(this, 24, 1), 0, 3, 0, 3));
        addRailLink(railContext, Page.Section.RECENTLY_ADDED, "new_releases", "Recently added");
        addRailLink(railContext, Page.Section.SEARCH, "search", "Search");
        Page page = top();
        if (page != null) {
            highlightRail(page.section());
        }
    }

    private void addRailLink(LinearLayout parent, Page.Section section, String icon, String label) {
        View button = railButton(section, icon, label, false);
        parent.addView(button, Nator.margins(this, Nator.size(this, 44, 44), 0, 0, 0, 2));
    }

    private View railButton(Page.Section section, String icon, String label, boolean footer) {
        FrameLayout button = new FrameLayout(this);
        button.setContentDescription(label);
        Nator.focusable(button, 14);
        button.setClipChildren(false);
        TextView glyph = Nator.icon(this, icon, 20, footer
                ? Nator.alpha(Nator.ON_SURFACE, 0.65f) : Nator.ON_SURFACE_VARIANT);
        button.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        View indicator = new View(this);
        indicator.setBackground(Nator.pill(this, Nator.SELECTION));
        indicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(Nator.px(this, 3), Nator.px(this, 18));
        indicatorParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        indicatorParams.rightMargin = -Nator.px(this, 4);
        button.addView(indicator, indicatorParams);
        button.setTag(new RailViews(glyph, indicator, footer));
        button.setOnClickListener(v -> openSection(section));
        button.setOnFocusChangeListener((v, focused) -> styleRail(button, focused, isRailActive(section)));
        button.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && top() != null) {
                top().focusFromRail();
                return true;
            }
            return false;
        });
        railButtons.put(section, button);
        styleRail(button, false, false);
        if (button.getParent() == null) {
            button.setLayoutParams(new FrameLayout.LayoutParams(Nator.px(this, 44), Nator.px(this, 44)));
        }
        return button;
    }

    private Page.Section activeSection;

    private boolean isRailActive(Page.Section section) {
        return section == activeSection;
    }

    private void highlightRail(Page.Section section) {
        activeSection = section;
        for (Map.Entry<Page.Section, View> entry : railButtons.entrySet()) {
            View button = entry.getValue();
            styleRail(button, button.isFocused(), entry.getKey() == section);
        }
    }

    private static final class RailViews {
        final TextView glyph;
        final View indicator;
        final boolean footer;

        RailViews(TextView glyph, View indicator, boolean footer) {
            this.glyph = glyph;
            this.indicator = indicator;
            this.footer = footer;
        }
    }

    private void styleRail(View button, boolean focused, boolean active) {
        RailViews views = (RailViews) button.getTag();
        if (views == null) {
            return;
        }
        if (active) {
            button.setBackground(Nator.railSelection(this));
            views.glyph.setTextColor(Nator.SELECTION);
            views.indicator.setVisibility(View.VISIBLE);
            button.setTranslationY(-Nator.fpx(this, 1));
        } else {
            button.setBackground(focused ? Nator.rounded(this, Nator.HOVER_OVERLAY, 14) : null);
            views.glyph.setTextColor(focused ? Nator.ON_SURFACE
                    : views.footer ? Nator.alpha(Nator.ON_SURFACE, 0.65f) : Nator.ON_SURFACE_VARIANT);
            views.indicator.setVisibility(View.GONE);
            button.setTranslationY(0f);
        }
    }

    public View railButton(Page.Section section) {
        return railButtons.get(section);
    }

    private View buildHeader() {
        LinearLayout header = Nator.row(this);
        header.setBackgroundColor(Nator.HEADER_BG);
        Nator.pad(header, 16, 0, 24, 0);

        headerBackSlot = new FrameLayout(this);
        View back = iconButton("arrow_back", 24, "Back", v -> {
            if (stack.size() > 1) {
                pop();
            }
        });
        headerBackSlot.addView(back);
        header.addView(headerBackSlot, Nator.margins(this, Nator.size(this, 40, 40), 0, 0, 12, 0));

        switcherView = buildSwitcher();
        header.addView(switcherView, Nator.margins(this, Nator.size(this, 260, 44), 0, 0, 12, 0));
        header.addView(buildSearchField(), Nator.margins(this, Nator.size(this, 586, 38), 0, 0, 8, 0));
        if (voiceSearchAvailable()) {
            header.addView(iconButton("mic", 24, "Voice search", v -> startVoiceSearch()),
                    Nator.margins(this, Nator.size(this, 40, 40), 0, 0, 4, 0));
        }
        header.addView(iconButton("keyboard", 24, "Open remote shortcuts", v -> showShortcuts()),
                Nator.margins(this, Nator.size(this, 40, 40), 0, 0, 4, 0));
        headerAdd = iconButton("add", 24, "Add playlist", v -> showAddPlaylist(null));
        header.addView(headerAdd, Nator.size(this, 40, 40));
        header.addView(Nator.spacer(this));
        LinearLayout badge = Nator.row(this);
        badge.setBackground(Nator.pill(this, Nator.SELECTION_SURFACE));
        Nator.pad(badge, 10, 0, 12, 0);
        badge.addView(Nator.icon(this, "child_care", 18, Nator.SELECTION), Nator.margins(this, Nator.size(this, 20, 20), 0, 0, 6, 0));
        TextView badgeText = Nator.line(this, "", 13, Nator.W600, Nator.SELECTION);
        badge.addView(badgeText);
        kidsGuard.bindBadge(badgeText);
        kidsBadge = badge;
        header.addView(badge, Nator.size(this, -2, 32));
        return header;
    }

    /** 40px mat-icon-button: circle hover wash + focus ring. */
    public View iconButton(String icon, float iconSize, String label, View.OnClickListener click) {
        FrameLayout button = new FrameLayout(this);
        button.setContentDescription(label);
        Nator.focusable(button, 999);
        button.setBackground(Nator.states(new GradientDrawable(), Nator.oval(Nator.HOVER_OVERLAY)));
        TextView glyph = Nator.icon(this, icon, iconSize, Nator.ON_SURFACE_VARIANT);
        button.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        button.setOnClickListener(click);
        return button;
    }

    private View buildSwitcher() {
        LinearLayout switcher = Nator.row(this);
        switcher.setBackground(Nator.roundedStroke(this, Nator.alpha(Nator.ON_SURFACE, 0.05f), 10, 1, Nator.OUTLINE_VARIANT));
        Nator.pad(switcher, 4, 4, 8, 4);
        Nator.focusable(switcher, 10);
        switcher.setContentDescription("Select playlist");

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(Nator.rounded(this, Nator.alpha(Nator.ON_SURFACE, 0.08f), 8));
        iconBox.setClipToOutline(true);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ztv_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iconBox.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        switcher.addView(iconBox, Nator.margins(this, Nator.size(this, 30, 30), 1, 0, 8, 0));

        LinearLayout texts = Nator.column(this);
        switcherName = Nator.line(this, "Select playlist", 15.2f, Nator.W600, Nator.ON_SURFACE);
        switcherSubtitle = Nator.line(this, "No sources available", 11.84f, Nator.W400, Nator.ON_SURFACE_VARIANT);
        texts.addView(switcherName);
        texts.addView(switcherSubtitle, Nator.margins(this, Nator.wrap(), 0, 1, 0, 0));
        switcher.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        switcher.addView(Nator.icon(this, "expand_more", 20, Nator.ON_SURFACE_VARIANT), Nator.size(this, 20, 20));
        switcher.setOnFocusChangeListener((v, focused) -> switcher.setBackground(Nator.roundedStroke(this,
                Nator.alpha(Nator.ON_SURFACE, focused ? 0.09f : 0.05f), 10, 1, Nator.OUTLINE_VARIANT)));
        switcher.setOnClickListener(v -> showSwitcherMenu(switcher));
        return switcher;
    }

    private void updateSwitcher() {
        if (switcherName == null) {
            return;
        }
        if (hasSource()) {
            switcherName.setText(playlistTitle());
            switcherSubtitle.setText("Xtream Code");
        } else {
            switcherName.setText("Select playlist");
            switcherSubtitle.setText("No sources available");
        }
    }

    private View buildSearchField() {
        searchField = Nator.row(this);
        searchField.setBackground(Nator.roundedStroke(this, Nator.SEARCH_BG, 10, 1, Nator.SEARCH_BORDER));
        Nator.pad(searchField, 12, 0, 10, 0);
        searchField.addView(Nator.icon(this, "search", 18, Nator.ON_SURFACE_VARIANT), Nator.margins(this, Nator.size(this, 18, 18), 0, 0, 8, 0));

        searchChip = Nator.line(this, "", 12, Nator.W600, Nator.ON_SURFACE);
        searchChip.setGravity(Gravity.CENTER);
        searchChip.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        searchChip.setBackground(Nator.roundedStroke(this, Nator.CHIP_BG, 999, 1, Nator.OUTLINE_VARIANT));
        Nator.pad(searchChip, 10, 0, 10, 0);
        searchField.addView(searchChip, Nator.margins(this, Nator.size(this, -2, 26), 0, 0, 10, 0));

        searchInput = new EditText(this);
        searchInput.setBackground(null);
        searchInput.setPadding(0, 0, 0, 0);
        searchInput.setSingleLine(true);
        searchInput.setTypeface(Nator.face(Nator.W400));
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14 * Nator.K);
        searchInput.setTextColor(Nator.ON_SURFACE);
        searchInput.setHintTextColor(Nator.BODY);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressSearchEvents) {
                    return;
                }
                if (pendingSearch != null) {
                    ui.removeCallbacks(pendingSearch);
                }
                String query = s.toString();
                pendingSearch = () -> {
                    Page page = top();
                    if (page != null) {
                        page.onSearch(query);
                    }
                };
                ui.postDelayed(pendingSearch, 300);
            }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            Page page = top();
            if (page != null) {
                page.onSearchSubmit(searchInput.getText().toString());
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
            return true;
        });
        searchInput.setOnFocusChangeListener((v, focused) -> searchField.setBackground(Nator.roundedStroke(this,
                focused ? Nator.alpha(0xFFFFFFFF, 0.08f) : Nator.SEARCH_BG, 10, focused ? 1.5f : 1,
                focused ? Nator.FOCUS_RING : Nator.SEARCH_BORDER)));
        searchField.addView(searchInput, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView kbd = Nator.text(this, "OK", 11.2f, Nator.W600, Nator.ON_SURFACE_VARIANT);
        kbd.setGravity(Gravity.CENTER);
        kbd.setBackground(Nator.roundedStroke(this, Nator.CHIP_BG, 5, 1, Nator.OUTLINE_VARIANT));
        searchField.addView(kbd, Nator.margins(this, Nator.size(this, 36, 22), 8, 0, 0, 0));
        return searchField;
    }

    private void syncSearch(Page page) {
        String scope = page.searchScope();
        boolean enabled = scope != null && hasSource();
        suppressSearchEvents = true;
        searchInput.setText(enabled ? page.searchQuery() : "");
        suppressSearchEvents = false;
        searchInput.setEnabled(enabled);
        searchInput.setFocusable(enabled);
        searchInput.setHint(enabled ? page.searchPlaceholder() : (hasSource() ? "Search unavailable here" : "Add a playlist to search"));
        searchChip.setText(scope == null ? "" : scope);
        searchChip.setVisibility(scope == null || scope.isEmpty() ? View.GONE : View.VISIBLE);
        searchField.setAlpha(enabled ? 1f : 0.6f);
    }

    public void updateSearchScope(Page page) {
        if (page == top()) {
            String scope = page.searchScope();
            searchChip.setText(scope == null ? "" : scope);
            searchChip.setVisibility(scope == null || scope.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    public void focusSearch() {
        searchInput.requestFocus();
    }

    // ─── Overlays (dialogs, menus, snack bar) ───────────────────────────────

    /** Shows {@code card} centered over a 60% scrim; Back dismisses. */
    public void showDialog(View card, float cssWidth, Runnable onDismiss) {
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setClickable(true);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Nator.px(this, cssWidth), -2, Gravity.CENTER);
        scrim.addView(card, params);
        overlayLayer.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        overlayLayer.setVisibility(View.VISIBLE);
        View previousFocus = getCurrentFocus();
        overlayDismissers.add(() -> {
            overlayLayer.removeView(scrim);
            if (overlayLayer.getChildCount() == 0) {
                overlayLayer.setVisibility(View.GONE);
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
            if (previousFocus != null && previousFocus.isAttachedToWindow()) {
                previousFocus.requestFocus();
            }
        });
    }

    /** mat-menu anchored under {@code anchor}. */
    public void showMenu(View anchor, View menu) {
        FrameLayout scrim = new FrameLayout(this);
        scrim.setClickable(true);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int[] rootAt = new int[2];
        root.getLocationInWindow(rootAt);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2);
        params.leftMargin = at[0] - rootAt[0];
        params.topMargin = at[1] - rootAt[1] + anchor.getHeight() + Nator.px(this, 4);
        menu.setElevation(Nator.fpx(this, 8));
        scrim.addView(menu, params);
        overlayLayer.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        overlayLayer.setVisibility(View.VISIBLE);
        View previousFocus = getCurrentFocus();
        overlayDismissers.add(() -> {
            overlayLayer.removeView(scrim);
            if (overlayLayer.getChildCount() == 0) {
                overlayLayer.setVisibility(View.GONE);
            }
            if (previousFocus != null && previousFocus.isAttachedToWindow()) {
                previousFocus.requestFocus();
            }
        });
        menu.post(menu::requestFocus);
    }

    public void dismissTopOverlay() {
        if (overlayDismissers.isEmpty()) {
            return;
        }
        Runnable dismiss = overlayDismissers.remove(overlayDismissers.size() - 1);
        dismiss.run();
    }

    public void dismissOverlays() {
        while (!overlayDismissers.isEmpty()) {
            dismissTopOverlay();
        }
    }

    /** Builds a mat-menu panel; each entry is {label, icon-or-null, checked?}. */
    public LinearLayout menuPanel() {
        LinearLayout menu = Nator.column(this);
        menu.setBackground(Nator.rounded(this, Nator.MENU_BG, 4));
        Nator.pad(menu, 0, 8, 0, 8);
        menu.setMinimumWidth(Nator.px(this, 112 * 2));
        return menu;
    }

    public View menuItem(LinearLayout menu, String label, String icon, boolean checked, Runnable action) {
        LinearLayout item = Nator.row(this);
        Nator.pad(item, 16, 0, 16, 0);
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackground(Nator.states(new GradientDrawable(), new android.graphics.drawable.ColorDrawable(Nator.HOVER_OVERLAY)));
        if (icon != null) {
            item.addView(Nator.icon(this, icon, 24, Nator.ON_SURFACE_VARIANT), Nator.margins(this, Nator.size(this, 24, 24), 0, 0, 16, 0));
        }
        TextView text = Nator.line(this, label, 14, Nator.W400, Nator.ON_SURFACE);
        item.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        if (checked) {
            item.addView(Nator.icon(this, "check", 24, Nator.ON_SURFACE_VARIANT), Nator.margins(this, Nator.size(this, 24, 24), 16, 0, 0, 0));
        }
        item.setOnClickListener(v -> {
            dismissTopOverlay();
            action.run();
        });
        menu.addView(item, new LinearLayout.LayoutParams(-1, Nator.px(this, 48)));
        return item;
    }

    /** Material snack bar, bottom center, auto-hides after 3s. */
    public void snack(String message) {
        if (snack == null) {
            snack = Nator.text(this, "", 14, Nator.W400, 0xFF2F3033);
            snack.setBackground(Nator.rounded(this, 0xFFE3E2E6, 4));
            Nator.pad(snack, 16, 14, 16, 14);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            params.bottomMargin = Nator.px(this, 24);
            snack.setElevation(Nator.fpx(this, 6));
            root.addView(snack, params);
        }
        snack.setText(message);
        snack.setVisibility(View.VISIBLE);
        snack.setAlpha(0f);
        snack.animate().alpha(1f).setDuration(150).start();
        ui.removeCallbacks(hideSnack);
        ui.postDelayed(hideSnack, 3000);
    }

    private final Runnable hideSnack = () -> {
        if (snack != null) {
            snack.animate().alpha(0f).setDuration(150).withEndAction(() -> snack.setVisibility(View.GONE)).start();
        }
    };

    private void showSwitcherMenu(View anchor) {
        LinearLayout menu = menuPanel();
        if (hasSource()) {
            menuItem(menu, playlistTitle(), "cloud", true, () -> openSection(Page.Section.VOD));
            menuItem(menu, "Account info", "info", false, this::showAccountInfo);
            menuItem(menu, "Refresh playlist", "refresh", false, () -> {
                catalog.refreshAll();
                snack("Refreshing playlist...");
            });
        }
        menuItem(menu, "Manage sources", "playlist_play", false, () -> openSection(Page.Section.SOURCES));
        showMenu(anchor, menu);
    }

    public void showAddPlaylist(XtreamCredentials prefill) {
        if (kids.isActive()) {
            return;
        }
        new AddPlaylistDialog(this, prefill).show();
    }

    public void showAccountInfo() {
        new AccountInfoDialog(this).show();
    }

    private void showShortcuts() {
        LinearLayout card = Nator.column(this);
        card.setBackground(Nator.rounded(this, Nator.WIDGET_BG, 16));
        Nator.pad(card, 24, 22, 24, 18);
        card.addView(Nator.text(this, "Remote shortcuts", 20, Nator.W600, Nator.HEADING));
        String[][] rows = {
                {"OK", "Open / play the focused item"},
                {"Back", "Previous page, then the rail, then exit"},
                {"◀ ▶ ▲ ▼", "Move between rail, categories and content"},
                {"CH+ / CH−", "Next / previous channel while playing"},
                {"Menu", "Toggle favorite on the focused item"},
                {"Mic / Search", "Voice search: say a channel, movie or series"},
        };
        for (String[] row : rows) {
            LinearLayout line = Nator.row(this);
            TextView key = Nator.text(this, row[0], 12, Nator.W600, Nator.ON_SURFACE);
            key.setGravity(Gravity.CENTER);
            key.setBackground(Nator.roundedStroke(this, Nator.CHIP_BG, 5, 1, Nator.OUTLINE_VARIANT));
            Nator.pad(key, 8, 4, 8, 4);
            line.addView(key, Nator.margins(this, Nator.size(this, 96, -2), 0, 0, 14, 0));
            line.addView(Nator.text(this, row[1], 13.5f, Nator.W400, Nator.BODY), new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(line, Nator.margins(this, Nator.matchWidth(), 0, 14, 0, 0));
        }
        View close = dialogTextButton("Close", this::dismissTopOverlay);
        LinearLayout actions = Nator.row(this);
        actions.setGravity(Gravity.END);
        actions.addView(close);
        card.addView(actions, Nator.margins(this, Nator.matchWidth(), 0, 18, 0, 0));
        showDialog(card, 520, null);
        close.requestFocus();
    }

    /** Material text button (dialog actions). */
    public TextView dialogTextButton(String label, Runnable action) {
        TextView button = Nator.text(this, label, 14, Nator.W500, 0xFFABC7FF);
        button.setGravity(Gravity.CENTER);
        Nator.pad(button, 12, 0, 12, 0);
        button.setMinHeight(Nator.px(this, 40));
        Nator.focusable(button, 999);
        button.setBackground(Nator.states(new GradientDrawable(), Nator.pill(this, Nator.alpha(0xFFABC7FF, 0.12f))));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    /** Material filled (unelevated) button. */
    public TextView dialogFilledButton(String label, Runnable action) {
        TextView button = Nator.text(this, label, 14, Nator.W500, 0xFF002F65);
        button.setGravity(Gravity.CENTER);
        Nator.pad(button, 24, 0, 24, 0);
        button.setMinHeight(Nator.px(this, 40));
        Nator.focusable(button, 999);
        button.setBackground(Nator.states(Nator.pill(this, 0xFFABC7FF), Nator.pill(this, 0xFFC4D7FF)));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    public void runLater(Runnable task, long delayMs) {
        ui.postDelayed(task, delayMs);
    }

    public void cancel(Runnable task) {
        ui.removeCallbacks(task);
    }

    /** Tiny prefs wrapper for workspace-only state. */
    static final class WorkspacePrefs {
        private final SharedPreferences prefs;

        WorkspacePrefs(Activity activity) {
            prefs = activity.getSharedPreferences(PREFS, MODE_PRIVATE);
        }

        String string(String key, String fallback) {
            return prefs.getString(key, fallback);
        }

        void put(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }

        boolean bool(String key, boolean fallback) {
            return prefs.getBoolean(key, fallback);
        }

        void put(String key, boolean value) {
            prefs.edit().putBoolean(key, value).apply();
        }
    }
}
