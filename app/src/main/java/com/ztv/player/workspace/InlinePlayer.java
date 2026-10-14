package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.ztv.player.PlaybackBufferPolicy;
import com.ztv.player.PlayerRenderers;
import com.ztv.player.data.XtreamContentType;

/**
 * IPTVnator's embedded web player (`app-web-player-view`) for the live layout: black
 * viewport, the shared player-controls dock (time, LIVE badge, volume, the blue
 * play/pause disc, tune, PiP, fullscreen) over a bottom scrim, and the top-right info.
 */
public final class InlinePlayer extends FrameLayout {
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final PlayerView view;
    private final ProgressBar spinner;
    private final LinearLayout controls;
    private final TextView time;
    private final TextView playGlyph;
    private final TextView message;
    private ExoPlayer player;
    private long startedAt;
    private final Runnable tick = this::updateClock;
    private final Runnable hideControls = this::hideControlsNow;

    public InlinePlayer(Context context) {
        super(context);
        setBackgroundColor(0xFF000000);
        setFocusable(true);
        setContentDescription("Live preview");
        setOnFocusChangeListener((v, focused) -> setForeground(focused ? Nator.focusRing(context, 0) : null));
        view = new PlayerView(context);
        view.setUseController(false);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setShutterBackgroundColor(0xFF000000);
        view.setKeepContentOnPlayerReset(false);
        view.setFocusable(false);
        addView(view, new LayoutParams(-1, -1));

        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xB3FFFFFF));
        addView(spinner, new LayoutParams(Nator.px(context, 50), Nator.px(context, 50), Gravity.CENTER));
        spinner.setVisibility(GONE);

        message = Nator.text(context, "", 14, Nator.W500, Nator.PLAYER_TEXT);
        message.setGravity(Gravity.CENTER);
        addView(message, new LayoutParams(-2, -2, Gravity.CENTER));
        message.setVisibility(GONE);

        TextView info = Nator.icon(context, "info_outline", 24, Nator.PLAYER_TEXT);
        LayoutParams infoParams = new LayoutParams(Nator.px(context, 24), Nator.px(context, 24), Gravity.TOP | Gravity.END);
        infoParams.setMargins(0, Nator.px(context, 16), Nator.px(context, 16), 0);
        addView(info, infoParams);

        // player-controls__bottom-scrim + dock.
        controls = Nator.column(context);
        controls.setGravity(Gravity.BOTTOM);
        GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xEB04070B, 0x8C04070B, 0x0004070B});
        controls.setBackground(scrim);
        Nator.pad(controls, 14, 0, 14, 10);
        LinearLayout top = Nator.row(context);
        time = Nator.text(context, "0:00", 13, Nator.W500, Nator.PLAYER_TEXT);
        top.addView(time);
        top.addView(Nator.spacer(context));
        LinearLayout live = Nator.row(context);
        live.setBackground(Nator.rounded(context, Nator.LIVE_BADGE, 4));
        Nator.pad(live, 8, 2, 8, 2);
        View dot = new View(context);
        dot.setBackground(Nator.oval(0xFFFFFFFF));
        live.addView(dot, Nator.margins(context, Nator.size(context, 6, 6), 0, 0, 5, 0));
        live.addView(Nator.text(context, "LIVE", 10.56f, Nator.W700, 0xFFFFFFFF));
        top.addView(live);
        controls.addView(top, Nator.margins(context, Nator.matchWidth(), 0, 0, 0, 8));

        LinearLayout bar = Nator.row(context);
        bar.addView(Nator.icon(context, "volume_up", 20, Nator.PLAYER_TEXT), Nator.margins(context, Nator.size(context, 32, 32), 2, 0, 0, 0));
        bar.addView(Nator.spacer(context));
        FrameLayout disc = new FrameLayout(context);
        GradientDrawable discBg = Nator.oval(Nator.PLAYER_ACCENT);
        discBg.setStroke(Nator.px(context, 4), Nator.alpha(0xFF4F8EFF, 0.18f));
        disc.setBackground(discBg);
        playGlyph = Nator.icon(context, "pause", 22, 0xFFFFFFFF);
        disc.addView(playGlyph, new LayoutParams(-1, -1));
        bar.addView(disc, Nator.size(context, 40, 40));
        bar.addView(Nator.spacer(context));
        bar.addView(Nator.icon(context, "tune", 20, Nator.PLAYER_TEXT), Nator.size(context, 36, 32));
        bar.addView(Nator.icon(context, "picture_in_picture", 20, Nator.PLAYER_MUTED), Nator.size(context, 36, 32));
        bar.addView(Nator.icon(context, "fullscreen", 20, Nator.PLAYER_TEXT), Nator.size(context, 36, 32));
        controls.addView(bar, Nator.matchWidth());
        addView(controls, new LayoutParams(-1, Nator.px(context, 140), Gravity.BOTTOM));
        controls.setAlpha(0f);
    }

    public void play(String url) {
        if (player == null) {
            PlaybackBufferPolicy.BufferConfig config = PlaybackBufferPolicy.configFor(XtreamContentType.LIVE.name());
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(config.minBufferMs(), config.maxBufferMs(),
                            config.bufferForPlaybackMs(), config.bufferForPlaybackAfterRebufferMs())
                    .setPrioritizeTimeOverSizeThresholds(config.prioritizeTimeOverSizeThresholds())
                    .build();
            player = new ExoPlayer.Builder(getContext(), PlayerRenderers.create(getContext()))
                    .setLoadControl(loadControl)
                    .setMediaSourceFactory(buildMediaSourceFactory())
                    .build();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    spinner.setVisibility(state == Player.STATE_BUFFERING ? VISIBLE : GONE);
                    if (state == Player.STATE_READY) {
                        message.setVisibility(GONE);
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    Nator.setIcon(playGlyph, isPlaying ? "pause" : "play_arrow");
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    spinner.setVisibility(GONE);
                    message.setText("This channel could not be played");
                    message.setVisibility(VISIBLE);
                }
            });
            view.setPlayer(player);
        }
        message.setVisibility(GONE);
        spinner.setVisibility(VISIBLE);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();
        startedAt = System.currentTimeMillis();
        flashControls();
        removeCallbacks(tick);
        post(tick);
    }

    private void updateClock() {
        long seconds = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000L);
        time.setText(seconds >= 3600
                ? String.format(java.util.Locale.US, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
                : String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60));
        postDelayed(tick, 1000);
    }

    /** Controls show on (re)start and hide after 3s, like the hover-revealed dock. */
    public void flashControls() {
        controls.animate().cancel();
        controls.setAlpha(1f);
        removeCallbacks(hideControls);
        postDelayed(hideControls, 3000);
    }

    private void hideControlsNow() {
        controls.animate().alpha(0f).setDuration(250).start();
    }

    public boolean isActive() {
        return player != null;
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    public void resume() {
        if (player != null) {
            player.play();
        }
    }

    public void release() {
        removeCallbacks(tick);
        removeCallbacks(hideControls);
        spinner.setVisibility(GONE);
        message.setVisibility(GONE);
        view.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    /** Keep embedded live playback transport-compatible with the dedicated player. */
    private DefaultMediaSourceFactory buildMediaSourceFactory() {
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS);
        return new DefaultMediaSourceFactory(new DefaultDataSource.Factory(getContext(), httpFactory));
    }
}
