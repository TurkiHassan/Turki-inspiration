package com.ztv.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.ztv.player.data.SettingsStore;
import com.ztv.player.data.EpisodePlaybackQueue;
import com.ztv.player.data.HostFailoverPolicy;
import com.ztv.player.data.HistoryStore;
import com.ztv.player.data.KidsStore;
import com.ztv.player.data.HttpStatusResolver;
import com.ztv.player.data.PlaybackFailurePolicy;
import com.ztv.player.data.ServerConfigStore;
import com.ztv.player.data.XtreamApiClient;
import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamCredentials;
import com.ztv.player.data.XtreamEpgProgram;
import com.ztv.player.data.EpgNowNext;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.data.XtreamUrlFactory;
import com.ztv.player.data.WatchProgressStore;
import com.ztv.player.ui.Tr;
import com.ztv.player.ui.ZtvUi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PlayerActivity extends Activity {
    private static final String TAG = "ZTV";
    private static final long KIDS_TICK_MS = 15_000L;
    public static final String EXTRA_STREAM_URL = "com.ztv.player.STREAM_URL";
    public static final String EXTRA_CHANNEL_NAME = "com.ztv.player.CHANNEL_NAME";
    public static final String EXTRA_RESUME_POSITION = "com.ztv.player.RESUME_POSITION";
    public static final String EXTRA_MEDIA_TYPE = "com.ztv.player.MEDIA_TYPE";
    public static final String EXTRA_MEDIA_ID = "com.ztv.player.MEDIA_ID";
    public static final String EXTRA_MEDIA_CATEGORY = "com.ztv.player.MEDIA_CATEGORY";
    public static final String EXTRA_MEDIA_IMAGE = "com.ztv.player.MEDIA_IMAGE";
    public static final String EXTRA_MEDIA_DETAIL = "com.ztv.player.MEDIA_DETAIL";
    public static final String EXTRA_MEDIA_EXT = "com.ztv.player.MEDIA_EXT";
    public static final String EXTRA_HOST = "com.ztv.player.HOST";
    public static final String EXTRA_USER = "com.ztv.player.USER";
    public static final String EXTRA_PASS = "com.ztv.player.PASS";
    public static final String EXTRA_OPENED_AT_MS = "com.ztv.player.OPENED_AT_MS";
    public static final String EXTRA_NEXT_STREAM_URL = "com.ztv.player.NEXT_STREAM_URL";
    public static final String EXTRA_NEXT_CHANNEL_NAME = "com.ztv.player.NEXT_CHANNEL_NAME";
    public static final String EXTRA_NEXT_MEDIA_ID = "com.ztv.player.NEXT_MEDIA_ID";
    public static final String EXTRA_NEXT_MEDIA_CATEGORY = "com.ztv.player.NEXT_MEDIA_CATEGORY";
    public static final String EXTRA_NEXT_MEDIA_IMAGE = "com.ztv.player.NEXT_MEDIA_IMAGE";
    public static final String EXTRA_NEXT_MEDIA_DETAIL = "com.ztv.player.NEXT_MEDIA_DETAIL";
    public static final String EXTRA_NEXT_MEDIA_EXT = "com.ztv.player.NEXT_MEDIA_EXT";
    public static final String EXTRA_NEXT_EPISODE_QUEUE = "com.ztv.player.NEXT_EPISODE_QUEUE";
    public static final String EXTRA_CHANNEL_LIST = "com.ztv.player.CHANNEL_LIST";

    private static final int MAX_RETRIES_PER_HOST = 1;
    private static final long RETRY_DELAY_MS = 2000L;
    private static final long LIVE_EDGE_CHECK_MS = 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private KidsStore kidsStore;
    private long kidsTickAt;
    private final Runnable kidsTick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            Calendar today = Calendar.getInstance();
            if (player != null && player.isPlaying()) {
                kidsStore.addWatched(KidsStore.dayKey(today), now - kidsTickAt);
            }
            kidsTickAt = now;
            if (kidsStore.block(today) != null) {
                // The workspace shows the Kids lock screen when it comes back.
                if (player != null) {
                    player.pause();
                }
                finish();
                return;
            }
            handler.postDelayed(this, KIDS_TICK_MS);
        }
    };
    private long liveEdgeStopMs = LiveEdgePolicy.CATCH_UP_STOP_MS;
    private final Runnable liveEdgeTick = new Runnable() {
        @Override
        public void run() {
            if (player == null) {
                return;
            }
            // Streams with a known live offset (HLS) are already kept near live by ExoPlayer.
            if (player.getCurrentLiveOffset() == C.TIME_UNSET) {
                float current = player.getPlaybackParameters().speed;
                float next = LiveEdgePolicy.nextSpeed(player.isPlaying(),
                        player.getTotalBufferedDuration(), current, liveEdgeStopMs);
                if (next != current) {
                    player.setPlaybackSpeed(next);
                }
            }
            handler.postDelayed(this, LIVE_EDGE_CHECK_MS);
        }
    };
    private XtreamApiClient apiClient;
    private ServerConfigStore serverConfigStore;
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar progress;
    private LinearLayout errorBox;
    private TextView errorText;
    private TextView errorHeadline;
    private TextView epgText;
    private TextView titleText;
    private View liveInfoOverlay;
    private ImageView channelLogo;
    private TextView channelKicker;
    private ZtvUi.Meter epgMeter;
    private String streamUrl;
    private int retryCount;
    private WatchProgressStore progressStore;
    private SettingsStore settingsStore;
    private HistoryStore historyStore;
    private XtreamMediaItem trackedItem;
    private List<XtreamMediaItem> channelList = new ArrayList<>();
    private int channelIndex = -1;
    private List<EpisodePlaybackQueue.Item> nextEpisodeQueue = Collections.emptyList();
    private XtreamMediaItem nextEpisodeItem;
    private String nextEpisodeStreamUrl;
    private long resumePositionMs;
    private boolean epgLoadRequested;
    private boolean readyLogged;
    private boolean controllerVisible;
    private boolean nextEpisodeScheduled;
    private String currentPlaybackHost = "";
    private final Set<String> attemptedPlaybackHosts = new LinkedHashSet<>();
    private final Runnable hideLiveInfoRunnable = () -> {
        if (liveInfoOverlay == null) {
            return;
        }
        liveInfoOverlay.animate().cancel();
        liveInfoOverlay.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction(() -> {
                    if (liveInfoOverlay != null && liveInfoOverlay.getAlpha() == 0f) {
                        liveInfoOverlay.setVisibility(View.GONE);
                    }
                })
                .start();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        serverConfigStore = new ServerConfigStore(this);
        apiClient = new XtreamApiClient(serverConfigStore);
        currentPlaybackHost = serverConfigStore.effectiveHost();
        streamUrl = streamUrlForHost(currentPlaybackHost);
        if (streamUrl.isEmpty()) {
            streamUrl = getIntent().getStringExtra(EXTRA_STREAM_URL);
        }
        if (!currentPlaybackHost.isEmpty()) {
            attemptedPlaybackHosts.add(currentPlaybackHost);
        }
        String channelName = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);

        kidsStore = new KidsStore(this);
        if (kidsStore.block(Calendar.getInstance()) != null) {
            finish();
            return;
        }
        String profile = kidsStore.isActive() ? "kids" : null;
        progressStore = new WatchProgressStore(this, profile);
        settingsStore = new SettingsStore(this);
        historyStore = new HistoryStore(this, profile);
        resumePositionMs = getIntent().getLongExtra(EXTRA_RESUME_POSITION, 0L);
        trackedItem = buildTrackedItem(channelName);
        loadNextEpisodeQueue();
        loadChannelList();

        com.ztv.player.workspace.Nator.init(this);
        Tr.setArabic("ar".equals(settingsStore.appLanguage()));
        FrameLayout root = new FrameLayout(this);
        // The player keeps a left-to-right timeline in every language; only its text is translated.
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        // Video viewports stay black (IPTVnator player boundary).
        root.setBackgroundColor(0xFF000000);

        playerView = createPlayerView();
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        if (isLiveStream()) {
            liveInfoOverlay = buildLiveInfoOverlay(channelName);
            FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
            );
            root.addView(liveInfoOverlay, infoParams);
        } else {
            playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility ->
                    controllerVisible = visibility == View.VISIBLE);
        }

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        root.addView(progress, progressParams);

        root.addView(buildErrorBox(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
        showLiveInfoTemporarily();

        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(this, PlayerRenderers.create(this,
                        SettingsStore.AUDIO_DECODER_FFMPEG.equals(settingsStore.audioDecoder()),
                        settingsStore.audioDelayMs()))
                .setSeekBackIncrementMs(PlayerControlsPolicy.seekBackIncrementMs())
                .setSeekForwardIncrementMs(PlayerControlsPolicy.seekForwardIncrementMs())
                .setLoadControl(buildLoadControl())
                .setTrackSelector(buildTrackSelector())
                .setMediaSourceFactory(buildMediaSourceFactory());
        player = playerBuilder.build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    showProgress(true);
                } else if (state == Player.STATE_READY) {
                    retryCount = 0;
                    if (!currentPlaybackHost.isEmpty()) {
                        serverConfigStore.markHealthy(currentPlaybackHost);
                        getIntent().putExtra(EXTRA_HOST, currentPlaybackHost);
                    }
                    showProgress(false);
                    hideError();
                    logLiveReadyOnce();
                    showLiveInfoTemporarily();
                    handler.postDelayed(PlayerActivity.this::loadEpgOnce, 500);
                } else if (state == Player.STATE_ENDED) {
                    showProgress(false);
                    scheduleNextEpisodeIfNeeded();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Playback failed", error);
                handlePlaybackError(error);
            }
        });
        if (settingsStore.matchFrameRate()) {
            player.setVideoFrameMetadataListener(new FrameRateWatcher(handler, new DisplayModeSwitcher(this)));
        }
        playerView.setPlayer(player);

        startPlayback();
        if (isLiveStream() && settingsStore.stayNearLive()) {
            liveEdgeStopMs = LiveEdgePolicy.stopThresholdMs(bufferConfig());
            handler.postDelayed(liveEdgeTick, LIVE_EDGE_CHECK_MS);
        }
        playerView.requestFocus();
        handler.postDelayed(this::loadEpgOnce, 4000);
    }

    private void loadEpgOnce() {
        if (epgLoadRequested) {
            return;
        }
        epgLoadRequested = true;
        loadEpg();
    }

    private void loadEpg() {
        String typeName = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
        int streamId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        String host = serverConfigStore.effectiveHost();
        if (!XtreamContentType.LIVE.name().equals(typeName) || streamId <= 0 || host == null || host.isEmpty()) {
            return;
        }
        XtreamCredentials credentials = new XtreamCredentials(
                host, getIntent().getStringExtra(EXTRA_USER), getIntent().getStringExtra(EXTRA_PASS));
        apiClient.fetchShortEpg(credentials, streamId, new XtreamApiClient.Callback<List<XtreamEpgProgram>>() {
            @Override
            public void onSuccess(List<XtreamEpgProgram> programs) {
                renderEpg(programs);
            }

            @Override
            public void onError(Exception exception) {
                Log.w(TAG, "No EPG for stream " + streamId, exception);
            }
        });
    }

    private void renderEpg(List<XtreamEpgProgram> programs) {
        if (programs == null || programs.isEmpty() || epgText == null) {
            return;
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        int nowIndex = Math.max(0, EpgNowNext.nowIndex(programs, nowSeconds));
        XtreamEpgProgram now = programs.get(nowIndex);
        if (epgMeter != null) {
            epgMeter.set(EpgNowNext.progressPercent(now, nowSeconds));
        }
        StringBuilder nowLine = new StringBuilder();
        nowLine.append(Tr.t("Now: ", "الآن: ")).append(now.title());
        if (!now.start().isEmpty()) {
            nowLine.append("  (").append(now.start()).append(" – ").append(now.end()).append(")");
        }

        StringBuilder nextLine = new StringBuilder();
        if (nowIndex + 1 < programs.size()) {
            XtreamEpgProgram next = programs.get(nowIndex + 1);
            nextLine.append("\nNext: ").append(next.title());
            if (!next.start().isEmpty()) {
                nextLine.append("  (").append(next.start()).append(")");
            }
        }

        if (settingsStore.dimUpcomingProgram() && nextLine.length() > 0) {
            SpannableString spannable = new SpannableString(nowLine.toString() + nextLine);
            spannable.setSpan(new ForegroundColorSpan(ZtvUi.TEXT_PRIMARY), 0, nowLine.length(), 0);
            spannable.setSpan(new ForegroundColorSpan(ZtvUi.TEXT_MUTED), nowLine.length(), spannable.length(), 0);
            epgText.setText(spannable);
        } else {
            epgText.setText(nowLine.toString() + nextLine);
        }
        epgText.setVisibility(View.VISIBLE);
    }

    private LinearLayout buildErrorBox() {
        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setVisibility(View.GONE);

        errorBox.setBackgroundColor(0xB3000000);

        // Viewer-facing headline in Arabic. The bundled DM Sans face has no Arabic glyphs, so this
        // line uses the system font, which shapes Arabic correctly on every Android TV build.
        errorHeadline = com.ztv.player.workspace.Nator.text(this, "", 16.8f,
                com.ztv.player.workspace.Nator.W600, com.ztv.player.workspace.Nator.PLAYER_TEXT);
        errorHeadline.setTypeface(Typeface.DEFAULT);
        errorHeadline.setGravity(Gravity.CENTER);
        errorHeadline.setVisibility(View.GONE);

        errorText = com.ztv.player.workspace.Nator.text(this, Tr.t("This stream could not be played", "تعذّر تشغيل هذا البث"), 16.8f,
                com.ztv.player.workspace.Nator.W500, com.ztv.player.workspace.Nator.PLAYER_TEXT);
        errorText.setGravity(Gravity.CENTER);

        TextView retry = com.ztv.player.workspace.Nator.text(this, Tr.t("Retry", "إعادة المحاولة"), 14,
                com.ztv.player.workspace.Nator.W500, 0xFF002F65);
        retry.setGravity(Gravity.CENTER);
        retry.setBackground(com.ztv.player.workspace.Nator.states(
                com.ztv.player.workspace.Nator.pill(this, 0xFFABC7FF),
                com.ztv.player.workspace.Nator.roundedStroke(this, 0xFFC4D7FF, 999, 2, com.ztv.player.workspace.Nator.PLAYER_TEXT)));
        retry.setFocusable(true);
        retry.setClickable(true);
        retry.setOnClickListener(view -> {
            retryCount = 0;
            attemptedPlaybackHosts.clear();
            currentPlaybackHost = serverConfigStore.effectiveHost();
            if (!currentPlaybackHost.isEmpty()) {
                attemptedPlaybackHosts.add(currentPlaybackHost);
                String candidateUrl = streamUrlForHost(currentPlaybackHost);
                if (!candidateUrl.isEmpty()) {
                    streamUrl = candidateUrl;
                }
            }
            hideError();
            startPlayback();
        });

        errorBox.addView(errorHeadline, ZtvUi.wrapTop(0));
        errorBox.addView(errorText, ZtvUi.wrapTop(8));
        errorBox.addView(retry, ZtvUi.fixedTop(this, 120, 40, 20));
        return errorBox;
    }

    private void startPlayback() {
        if (player == null || streamUrl == null || streamUrl.isEmpty()) {
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "perf preparing_media type=" + getIntent().getStringExtra(EXTRA_MEDIA_TYPE)
                    + " id=" + getIntent().getIntExtra(EXTRA_MEDIA_ID, -1));
        }
        showProgress(true);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)));
        player.prepare();
        if (resumePositionMs > 0) {
            player.seekTo(resumePositionMs);
            resumePositionMs = 0;
        }
        player.play();
    }

    private XtreamMediaItem buildTrackedItem(String title) {
        String typeName = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
        int id = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        if (typeName == null || id <= 0) {
            return null;
        }
        try {
            XtreamContentType type = XtreamContentType.valueOf(typeName);
            return new XtreamMediaItem(
                    type,
                    title == null ? "Untitled" : title,
                    id,
                    getIntent().getStringExtra(EXTRA_MEDIA_CATEGORY),
                    getIntent().getStringExtra(EXTRA_MEDIA_IMAGE),
                    getIntent().getStringExtra(EXTRA_MEDIA_DETAIL),
                    getIntent().getStringExtra(EXTRA_MEDIA_EXT)
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private XtreamMediaItem buildNextEpisodeItem() {
        int id = getIntent().getIntExtra(EXTRA_NEXT_MEDIA_ID, -1);
        String title = getIntent().getStringExtra(EXTRA_NEXT_CHANNEL_NAME);
        if (id <= 0 || title == null || title.trim().isEmpty()) {
            return null;
        }
        return new XtreamMediaItem(
                XtreamContentType.EPISODE,
                title,
                id,
                getIntent().getStringExtra(EXTRA_NEXT_MEDIA_CATEGORY),
                getIntent().getStringExtra(EXTRA_NEXT_MEDIA_IMAGE),
                getIntent().getStringExtra(EXTRA_NEXT_MEDIA_DETAIL),
                getIntent().getStringExtra(EXTRA_NEXT_MEDIA_EXT)
        );
    }

    private void loadNextEpisodeQueue() {
        nextEpisodeQueue = EpisodePlaybackQueue.decode(getIntent().getStringExtra(EXTRA_NEXT_EPISODE_QUEUE));
        if (!nextEpisodeQueue.isEmpty()) {
            EpisodePlaybackQueue.Item nextItem = nextEpisodeQueue.get(0);
            nextEpisodeStreamUrl = nextItem.streamUrl();
            nextEpisodeItem = nextItem.mediaItem();
            return;
        }
        nextEpisodeStreamUrl = getIntent().getStringExtra(EXTRA_NEXT_STREAM_URL);
        nextEpisodeItem = buildNextEpisodeItem();
    }

    private void loadChannelList() {
        String json = getIntent().getStringExtra(EXTRA_CHANNEL_LIST);
        if (json == null || json.isEmpty()) {
            return;
        }
        channelList = parseChannelList(json);
        int currentId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        for (int index = 0; index < channelList.size(); index++) {
            if (channelList.get(index).id() == currentId) {
                channelIndex = index;
                break;
            }
        }
    }

    private static List<XtreamMediaItem> parseChannelList(String json) {
        List<XtreamMediaItem> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) {
                    continue;
                }
                items.add(new XtreamMediaItem(
                        XtreamContentType.LIVE,
                        object.optString("title", "Untitled"),
                        object.optInt("id", -1),
                        object.optString("categoryId", ""),
                        object.optString("imageUrl", ""),
                        object.optString("detail", ""),
                        object.optString("ext", "ts")
                ));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return items;
    }

    /** Switches to the next (direction > 0) or previous (direction < 0) channel in the
     *  category the user was browsing, without leaving the player. Wraps at both ends. */
    private void switchChannel(int direction) {
        if (channelList.isEmpty() || channelIndex < 0) {
            return;
        }
        int newIndex = ((channelIndex + direction) % channelList.size() + channelList.size()) % channelList.size();
        if (newIndex == channelIndex) {
            return;
        }
        channelIndex = newIndex;
        XtreamMediaItem newItem = channelList.get(channelIndex);
        trackedItem = newItem;
        historyStore.record(newItem);

        getIntent().putExtra(EXTRA_MEDIA_ID, newItem.id());
        getIntent().putExtra(EXTRA_CHANNEL_NAME, newItem.title());
        getIntent().putExtra(EXTRA_MEDIA_CATEGORY, newItem.categoryId());
        getIntent().putExtra(EXTRA_MEDIA_IMAGE, newItem.imageUrl());
        getIntent().putExtra(EXTRA_MEDIA_DETAIL, newItem.detail());
        getIntent().putExtra(EXTRA_MEDIA_EXT, newItem.containerExtension());

        XtreamUrlFactory urlFactory = new XtreamUrlFactory(serverConfigStore.effectiveHost());
        streamUrl = urlFactory.liveStreamUrl(
                getIntent().getStringExtra(EXTRA_USER), getIntent().getStringExtra(EXTRA_PASS), newItem.id());
        retryCount = 0;
        epgLoadRequested = false;
        if (epgText != null) {
            epgText.setVisibility(View.GONE);
        }
        if (titleText != null) {
            titleText.setText(newItem.title());
        }
        if (epgMeter != null) {
            epgMeter.set(-1);
        }
        bindChannelIdentity();
        startPlayback();
        showLiveInfoTemporarily();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && isLiveStream()) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_INFO
                    || keyCode == KeyEvent.KEYCODE_MENU) {
                showLiveInfoTemporarily();
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && handleEnhancedPlayerKey(event.getKeyCode())) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && isLiveStream() && !channelList.isEmpty()) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                switchChannel(1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                switchChannel(-1);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (PlayerControlsPolicy.useEnhancedControls(getIntent().getStringExtra(EXTRA_MEDIA_TYPE))
                && controllerVisible
                && playerView != null) {
            playerView.hideController();
            return;
        }
        returnToLibrary();
    }

    private boolean handleEnhancedPlayerKey(int keyCode) {
        if (!PlayerControlsPolicy.useEnhancedControls(getIntent().getStringExtra(EXTRA_MEDIA_TYPE))
                || controllerVisible
                || player == null
                || playerView == null) {
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
            playerView.showController();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            seekBy(-PlayerControlsPolicy.seekBackIncrementMs());
            playerView.showController();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            seekBy(PlayerControlsPolicy.seekForwardIncrementMs());
            playerView.showController();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            playerView.showController();
            return true;
        }
        return false;
    }

    private void seekBy(long deltaMs) {
        long currentPosition = Math.max(0L, player.getCurrentPosition());
        long duration = player.getDuration();
        long target = currentPosition + deltaMs;
        if (duration > 0) {
            target = Math.min(duration, target);
        }
        player.seekTo(Math.max(0L, target));
    }

    private void returnToLibrary() {
        saveProgress();
        String mediaTypeName = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
        if (PlayerControlsPolicy.shouldOpenLibraryOnBack(mediaTypeName, isTaskRoot())) {
            Intent intent = new Intent(this, com.ztv.player.workspace.WorkspaceActivity.class);
            XtreamContentType libraryType = libraryTypeFor(mediaTypeName);
            intent.putExtra("ztv.section", libraryType == XtreamContentType.MOVIE ? "VOD"
                    : libraryType == XtreamContentType.SERIES ? "SERIES" : "LIVE");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
    }

    private static XtreamContentType libraryTypeFor(String mediaTypeName) {
        if (XtreamContentType.MOVIE.name().equals(mediaTypeName)) {
            return XtreamContentType.MOVIE;
        }
        if (XtreamContentType.EPISODE.name().equals(mediaTypeName)
                || XtreamContentType.SERIES.name().equals(mediaTypeName)) {
            return XtreamContentType.SERIES;
        }
        return XtreamContentType.LIVE;
    }

    private void saveProgress() {
        if (trackedItem == null || player == null) {
            return;
        }
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration <= 0) {
            return;
        }
        if (position >= duration * 0.95) {
            progressStore.markCompleted(trackedItem, duration);
        } else if (position > 10000) {
            progressStore.save(trackedItem, position, duration);
        }
    }

    private void handlePlaybackError(PlaybackException error) {
        if (player == null) {
            return;
        }
        int httpStatus = HttpStatusResolver.responseCode(error);
        boolean providerRejection = PlaybackFailurePolicy.isProviderRejection(httpStatus);
        if (PlaybackFailurePolicy.shouldRetry(providerRejection, retryCount, MAX_RETRIES_PER_HOST)) {
            retryCount++;
            errorText.setText(Tr.t("Reconnecting (", "جارٍ إعادة الاتصال (") + retryCount + "/" + MAX_RETRIES_PER_HOST + ")...");
            showProgress(true);
            handler.postDelayed(() -> {
                if (player != null) {
                    player.prepare();
                    player.play();
                }
            }, RETRY_DELAY_MS);
            return;
        }
        if (PlaybackFailurePolicy.shouldFailOver(providerRejection, error.errorCode)
                && selectNextPlaybackHost()) {
            retryCount = 0;
            errorText.setText(Tr.t("Switching to a backup server...", "جارٍ التحويل إلى خادم احتياطي..."));
            showProgress(true);
            handler.postDelayed(this::startPlayback, 600L);
            return;
        }
        showProgress(false);
        showPlaybackFailure(httpStatus);
    }

    /**
     * Reports the real cause instead of a generic message: when the provider answered with an HTTP
     * status, the stream is refused (or missing) on the provider side, which the viewer cannot fix
     * from this screen.
     */
    private void showPlaybackFailure(int httpStatus) {
        boolean providerAnswered = httpStatus >= 400;
        if (errorHeadline != null) {
            errorHeadline.setText(providerAnswered
                    ? Tr.t("This title can't be played — the provider answered " + httpStatus,
                            "تعذّر تشغيل هذا العنوان — المزوّد ردّ بـ " + httpStatus)
                    : Tr.t("This title can't be played", "تعذّر تشغيل هذا العنوان"));
            errorHeadline.setVisibility(View.VISIBLE);
        }
        errorText.setText(providerAnswered
                ? Tr.t("The provider refused this stream (HTTP " + httpStatus
                        + "). This title is unavailable on the provider side.",
                        "رفض المزوّد هذا البث (HTTP " + httpStatus + "). هذا العنوان غير متاح لدى المزوّد.")
                : Tr.t("Stream unavailable. No owner-managed server could play it.",
                        "البث غير متاح. لم يتمكن أي خادم من تشغيله."));
        showError();
    }

    private boolean selectNextPlaybackHost() {
        String nextHost = HostFailoverPolicy.nextUntriedHost(
                serverConfigStore.orderedHosts(), attemptedPlaybackHosts);
        if (nextHost.isEmpty()) {
            return false;
        }
        String candidateUrl = streamUrlForHost(nextHost);
        if (candidateUrl.isEmpty()) {
            return false;
        }
        currentPlaybackHost = nextHost;
        attemptedPlaybackHosts.add(nextHost);
        streamUrl = candidateUrl;
        getIntent().putExtra(EXTRA_HOST, nextHost);
        epgLoadRequested = false;
        return true;
    }

    private String streamUrlForHost(String host) {
        String username = getIntent().getStringExtra(EXTRA_USER);
        String password = getIntent().getStringExtra(EXTRA_PASS);
        String typeName = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
        int streamId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        if (host == null || host.isEmpty() || username == null || username.isEmpty()
                || password == null || password.isEmpty() || typeName == null || streamId <= 0) {
            return "";
        }
        XtreamUrlFactory urlFactory = new XtreamUrlFactory(host);
        String extension = getIntent().getStringExtra(EXTRA_MEDIA_EXT);
        if (XtreamContentType.MOVIE.name().equals(typeName)) {
            return urlFactory.movieStreamUrl(username, password, streamId, extension);
        }
        if (XtreamContentType.EPISODE.name().equals(typeName)) {
            return urlFactory.seriesStreamUrl(username, password, streamId, extension);
        }
        return urlFactory.liveStreamUrl(username, password, streamId);
    }

    private void showProgress(boolean visible) {
        if (progress != null) {
            progress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void showError() {
        if (errorBox != null) {
            errorBox.setVisibility(View.VISIBLE);
            errorBox.requestFocus();
        }
    }

    private void hideError() {
        if (errorBox != null) {
            errorBox.setVisibility(View.GONE);
        }
    }

    private boolean isLiveStream() {
        return XtreamContentType.LIVE.name().equals(getIntent().getStringExtra(EXTRA_MEDIA_TYPE));
    }

    /**
     * IPTVnator's fullscreen live dock: bottom scrim, the channel row (logo shell, name,
     * programme + timeline) with the red LIVE badge, and the control bar under it.
     */
    private LinearLayout buildLiveInfoOverlay(String channelName) {
        android.content.Context ctx = this;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.BOTTOM);
        panel.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xEB04070B, 0x8C04070B, 0x0004070B}));
        com.ztv.player.workspace.Nator.pad(panel, 32, 90, 32, 20);

        LinearLayout row = com.ztv.player.workspace.Nator.row(ctx);
        android.widget.FrameLayout shell = new android.widget.FrameLayout(ctx);
        shell.setBackground(com.ztv.player.workspace.Nator.roundedStroke(ctx, 0x1F7F7F7F, 10, 1, 0x14FFFFFF));
        shell.setClipToOutline(true);
        channelLogo = new ImageView(this);
        channelLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int logoPad = com.ztv.player.workspace.Nator.px(ctx, 6);
        channelLogo.setPadding(logoPad, logoPad, logoPad, logoPad);
        shell.addView(channelLogo, new android.widget.FrameLayout.LayoutParams(-1, -1));
        row.addView(shell, com.ztv.player.workspace.Nator.margins(ctx, com.ztv.player.workspace.Nator.size(ctx, 56, 56), 0, 0, 14, 0));

        LinearLayout textStack = com.ztv.player.workspace.Nator.column(ctx);
        channelKicker = com.ztv.player.workspace.Nator.text(ctx, Tr.t("LIVE", "مباشر"), 11, com.ztv.player.workspace.Nator.W600, com.ztv.player.workspace.Nator.PLAYER_MUTED);
        channelKicker.setLetterSpacing(0.12f);
        channelKicker.setFontFeatureSettings("tnum");
        textStack.addView(channelKicker);
        titleText = com.ztv.player.workspace.Nator.line(ctx, channelName == null ? "ZTV" : channelName, 22, com.ztv.player.workspace.Nator.W600, com.ztv.player.workspace.Nator.PLAYER_TEXT);
        textStack.addView(titleText, com.ztv.player.workspace.Nator.margins(ctx, com.ztv.player.workspace.Nator.matchWidth(), 0, 4, 0, 0));
        epgText = com.ztv.player.workspace.Nator.text(ctx, Tr.t("Live channel", "قناة مباشرة"), 13, com.ztv.player.workspace.Nator.W400, 0xFFB5BBCB);
        epgText.setMaxLines(2);
        epgText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textStack.addView(epgText, com.ztv.player.workspace.Nator.margins(ctx, com.ztv.player.workspace.Nator.matchWidth(), 0, 4, 0, 0));
        epgMeter = ZtvUi.meter(this, android.graphics.Color.argb(60, 255, 255, 255));
        epgMeter.set(-1);
        textStack.addView(epgMeter.track, com.ztv.player.workspace.Nator.margins(ctx, com.ztv.player.workspace.Nator.size(ctx, 340, 4), 0, 8, 0, 0));
        row.addView(textStack, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout live = com.ztv.player.workspace.Nator.row(ctx);
        live.setBackground(com.ztv.player.workspace.Nator.rounded(ctx, com.ztv.player.workspace.Nator.LIVE_BADGE, 4));
        com.ztv.player.workspace.Nator.pad(live, 8, 2, 8, 2);
        View dot = new View(ctx);
        dot.setBackground(com.ztv.player.workspace.Nator.oval(0xFFFFFFFF));
        live.addView(dot, com.ztv.player.workspace.Nator.margins(ctx, com.ztv.player.workspace.Nator.size(ctx, 6, 6), 0, 0, 5, 0));
        live.addView(com.ztv.player.workspace.Nator.text(ctx, Tr.t("LIVE", "مباشر"), 10.56f, com.ztv.player.workspace.Nator.W700, 0xFFFFFFFF));
        row.addView(live);
        panel.addView(row, com.ztv.player.workspace.Nator.matchWidth());

        LinearLayout bar = com.ztv.player.workspace.Nator.row(ctx);
        bar.addView(com.ztv.player.workspace.Nator.icon(ctx, "volume_up", 20, com.ztv.player.workspace.Nator.PLAYER_TEXT), com.ztv.player.workspace.Nator.size(ctx, 32, 32));
        bar.addView(com.ztv.player.workspace.Nator.text(ctx, "CH ▲▼  switch channel", 12, com.ztv.player.workspace.Nator.W500, com.ztv.player.workspace.Nator.PLAYER_MUTED),
                com.ztv.player.workspace.Nator.margins(ctx, com.ztv.player.workspace.Nator.wrap(), 10, 0, 0, 0));
        bar.addView(com.ztv.player.workspace.Nator.spacer(ctx));
        android.widget.FrameLayout disc = new android.widget.FrameLayout(ctx);
        android.graphics.drawable.GradientDrawable discBg = com.ztv.player.workspace.Nator.oval(com.ztv.player.workspace.Nator.PLAYER_ACCENT);
        discBg.setStroke(com.ztv.player.workspace.Nator.px(ctx, 4), 0x2E4F8EFF);
        disc.setBackground(discBg);
        disc.addView(com.ztv.player.workspace.Nator.icon(ctx, "pause", 22, 0xFFFFFFFF), new android.widget.FrameLayout.LayoutParams(-1, -1));
        bar.addView(disc, com.ztv.player.workspace.Nator.size(ctx, 40, 40));
        bar.addView(com.ztv.player.workspace.Nator.spacer(ctx));
        bar.addView(com.ztv.player.workspace.Nator.icon(ctx, "tune", 20, com.ztv.player.workspace.Nator.PLAYER_TEXT), com.ztv.player.workspace.Nator.size(ctx, 36, 32));
        bar.addView(com.ztv.player.workspace.Nator.icon(ctx, "picture_in_picture", 20, com.ztv.player.workspace.Nator.PLAYER_MUTED), com.ztv.player.workspace.Nator.size(ctx, 36, 32));
        bar.addView(com.ztv.player.workspace.Nator.icon(ctx, "fullscreen_exit", 20, com.ztv.player.workspace.Nator.PLAYER_TEXT), com.ztv.player.workspace.Nator.size(ctx, 36, 32));
        panel.addView(bar, com.ztv.player.workspace.Nator.margins(ctx, com.ztv.player.workspace.Nator.matchWidth(), 0, 14, 0, 0));
        bindChannelIdentity();
        return panel;
    }

    /** Logo + "12 · LIVE" for the current channel; the number is its position in the category. */
    private void bindChannelIdentity() {
        if (channelLogo == null || channelKicker == null) {
            return;
        }
        String logoUrl = getIntent().getStringExtra(EXTRA_MEDIA_IMAGE);
        boolean hasLogo = logoUrl != null && !logoUrl.trim().isEmpty();
        channelLogo.setVisibility(hasLogo ? View.VISIBLE : View.GONE);
        if (hasLogo) {
            com.ztv.player.ui.PosterLoader.load(logoUrl, channelLogo, false);
        }
        channelKicker.setText(channelIndex >= 0 && !channelList.isEmpty()
                ? Tr.t("CHANNEL ", "القناة ") + (channelIndex + 1) + "  ·  " + channelList.size() : Tr.t("LIVE TV", "بث مباشر"));
    }

    private void showLiveInfoTemporarily() {
        if (!isLiveStream() || liveInfoOverlay == null) {
            return;
        }
        handler.removeCallbacks(hideLiveInfoRunnable);
        liveInfoOverlay.animate().cancel();
        liveInfoOverlay.setAlpha(1f);
        liveInfoOverlay.setVisibility(View.VISIBLE);
        handler.postDelayed(hideLiveInfoRunnable, 4500L);
    }

    private DefaultMediaSourceFactory buildMediaSourceFactory() {
        // Xtream VOD URLs commonly 302-redirect (often http<->https); the default data
        // source refuses cross-protocol redirects, which shows up as a slow/failed start.
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(10000);
        return new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, httpFactory));
    }

    /**
     * Tunneled playback hands audio/video sync to the TV hardware. ExoPlayer only engages it when
     * both the video decoder and the audio path support it (not with the FFmpeg audio decoder),
     * and otherwise plays normally, so the setting is safe to leave on for unsupported streams.
     */
    private DefaultTrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
        if (settingsStore.tunneledPlayback()) {
            trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingEnabled(true));
        }
        return trackSelector;
    }

    private PlaybackBufferPolicy.BufferConfig bufferConfig() {
        return PlaybackBufferPolicy.configFor(getIntent().getStringExtra(EXTRA_MEDIA_TYPE),
                SettingsStore.LIVE_BUFFER_LARGE.equals(settingsStore.liveBuffer()));
    }

    private DefaultLoadControl buildLoadControl() {
        PlaybackBufferPolicy.BufferConfig config = bufferConfig();
        DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        config.minBufferMs(),
                        config.maxBufferMs(),
                        config.bufferForPlaybackMs(),
                        config.bufferForPlaybackAfterRebufferMs())
                .setPrioritizeTimeOverSizeThresholds(config.prioritizeTimeOverSizeThresholds());
        if (config.backBufferMs() > 0) {
            builder.setBackBuffer(config.backBufferMs(), config.retainBackBufferFromKeyframe());
        }
        return builder.build();
    }

    private boolean hasNextEpisode() {
        return nextEpisodeItem != null && nextEpisodeStreamUrl != null && !nextEpisodeStreamUrl.isEmpty();
    }

    private void openNextEpisode() {
        if (!hasNextEpisode()) {
            return;
        }
        nextEpisodeScheduled = false;
        saveProgress();
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(EXTRA_CHANNEL_NAME, nextEpisodeItem.title());
        String activeHost = serverConfigStore.effectiveHost();
        XtreamUrlFactory urlFactory = new XtreamUrlFactory(activeHost);
        String username = getIntent().getStringExtra(EXTRA_USER);
        String password = getIntent().getStringExtra(EXTRA_PASS);
        String resolvedNextUrl = urlFactory.seriesStreamUrl(
                username, password, nextEpisodeItem.id(), nextEpisodeItem.containerExtension());
        intent.putExtra(EXTRA_STREAM_URL, resolvedNextUrl);
        intent.putExtra(EXTRA_MEDIA_TYPE, XtreamContentType.EPISODE.name());
        intent.putExtra(EXTRA_MEDIA_ID, nextEpisodeItem.id());
        intent.putExtra(EXTRA_MEDIA_CATEGORY, nextEpisodeItem.categoryId());
        intent.putExtra(EXTRA_MEDIA_IMAGE, nextEpisodeItem.imageUrl());
        intent.putExtra(EXTRA_MEDIA_DETAIL, nextEpisodeItem.detail());
        intent.putExtra(EXTRA_MEDIA_EXT, nextEpisodeItem.containerExtension());
        intent.putExtra(EXTRA_HOST, activeHost);
        intent.putExtra(EXTRA_USER, username);
        intent.putExtra(EXTRA_PASS, password);
        intent.putExtra(EXTRA_RESUME_POSITION,
                progressStore.positionMs(XtreamContentType.EPISODE, nextEpisodeItem.id()));
        String remainingQueue = EpisodePlaybackQueue.encode(remainingNextEpisodeQueue());
        if (!remainingQueue.isEmpty()) {
            intent.putExtra(EXTRA_NEXT_EPISODE_QUEUE, remainingQueue);
        }
        startActivity(intent);
        finish();
    }

    private List<EpisodePlaybackQueue.Item> remainingNextEpisodeQueue() {
        if (nextEpisodeQueue == null || nextEpisodeQueue.size() <= 1) {
            return Collections.emptyList();
        }
        return new ArrayList<>(nextEpisodeQueue.subList(1, nextEpisodeQueue.size()));
    }

    private void scheduleNextEpisodeIfNeeded() {
        if (nextEpisodeScheduled || !hasNextEpisode()
                || settingsStore == null || !settingsStore.autoPlayNextEpisode()) {
            return;
        }
        nextEpisodeScheduled = true;
        if (playerView != null) {
            playerView.showController();
        }
        handler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && nextEpisodeScheduled) {
                openNextEpisode();
            }
        }, 10_000L);
    }

    private void logLiveReadyOnce() {
        if (!BuildConfig.DEBUG || readyLogged || !isLiveStream()) {
            return;
        }
        readyLogged = true;
        long openedAtMs = getIntent().getLongExtra(EXTRA_OPENED_AT_MS, 0L);
        if (openedAtMs > 0L) {
            Log.d(TAG, "perf live_playback_ready id=" + getIntent().getIntExtra(EXTRA_MEDIA_ID, -1)
                    + " openToReadyMs=" + (SystemClock.elapsedRealtime() - openedAtMs));
        } else {
            Log.d(TAG, "perf live_playback_ready id=" + getIntent().getIntExtra(EXTRA_MEDIA_ID, -1));
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
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (kidsStore != null && kidsStore.isActive()) {
            kidsTickAt = SystemClock.elapsedRealtime();
            handler.removeCallbacks(kidsTick);
            handler.postDelayed(kidsTick, KIDS_TICK_MS);
        }
    }

    @Override
    protected void onPause() {
        if (kidsStore != null && kidsStore.isActive()) {
            handler.removeCallbacks(kidsTick);
            long now = SystemClock.elapsedRealtime();
            if (player != null && player.isPlaying()) {
                kidsStore.addWatched(KidsStore.dayKey(Calendar.getInstance()), now - kidsTickAt);
            }
            kidsTickAt = now;
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        saveProgress();
        if (player != null) {
            player.pause();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (apiClient != null) {
            apiClient.shutdown();
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private PlayerView createPlayerView() {
        String mediaTypeName = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
        boolean enhancedControls = PlayerControlsPolicy.useEnhancedControls(mediaTypeName);
        PlayerView playerView = enhancedControls
                ? (PlayerView) getLayoutInflater().inflate(R.layout.ztv_vod_player_view, null, false)
                : new PlayerView(this);
        playerView.setUseController(enhancedControls);
        playerView.setControllerShowTimeoutMs(PlayerControlsPolicy.controllerShowTimeoutMs(mediaTypeName));
        playerView.setShowRewindButton(enhancedControls);
        playerView.setShowFastForwardButton(enhancedControls);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        playerView.setShowSubtitleButton(enhancedControls);
        playerView.setFocusable(true);
        playerView.setFocusableInTouchMode(true);
        configureVodControlTitle(playerView, getIntent().getStringExtra(EXTRA_CHANNEL_NAME));
        configureNextEpisodeButton(playerView, mediaTypeName);
        return playerView;
    }

    private void configureVodControlTitle(PlayerView playerView, String title) {
        TextView titleView = playerView.findViewById(R.id.ztv_player_bar_title);
        if (titleView != null) {
            titleView.setText(title == null || title.trim().isEmpty() ? "ZTV" : title);
        }
        TextView metaView = playerView.findViewById(R.id.ztv_player_bar_meta);
        if (metaView != null) {
            String mediaTypeName = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
            String typeLabel = XtreamContentType.EPISODE.name().equals(mediaTypeName)
                    ? Tr.t("Episode", "حلقة")
                    : Tr.t("Movie", "فيلم");
            metaView.setText((Tr.t("Now playing  ·  ", "يُعرض الآن  ·  ") + typeLabel).toUpperCase(java.util.Locale.ROOT));
        }
    }

    private void configureNextEpisodeButton(PlayerView playerView, String mediaTypeName) {
        ImageButton nextButton = playerView.findViewById(R.id.ztv_next_episode);
        if (nextButton == null) {
            return;
        }
        boolean showButton = PlayerControlsPolicy.showNextEpisodeButton(mediaTypeName, hasNextEpisode());
        nextButton.setVisibility(showButton ? View.VISIBLE : View.GONE);
        nextButton.setEnabled(showButton);
        nextButton.setFocusable(showButton);
        if (showButton) {
            nextButton.setOnClickListener(view -> openNextEpisode());
        }
    }
}
