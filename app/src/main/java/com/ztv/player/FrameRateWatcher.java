package com.ztv.player;

import android.media.MediaFormat;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;

/**
 * Finds the frame rate of the video being rendered and hands it to {@link DisplayModeSwitcher}
 * on the main thread. Uses the declared rate when the stream has one, otherwise measures it.
 * Runs on the playback thread; does no work per frame once the rate for a format is known.
 */
final class FrameRateWatcher implements VideoFrameMetadataListener {
    private final Handler mainHandler;
    private final DisplayModeSwitcher switcher;
    private final FrameRateEstimator estimator = new FrameRateEstimator();
    private Format format;
    private boolean reported;

    FrameRateWatcher(Handler mainHandler, DisplayModeSwitcher switcher) {
        this.mainHandler = mainHandler;
        this.switcher = switcher;
    }

    @Override
    public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs,
            Format format, @Nullable MediaFormat mediaFormat) {
        if (format != this.format) {
            this.format = format;
            estimator.reset();
            reported = false;
        }
        if (reported) {
            return;
        }
        float frameRate = format.frameRate > 0f
                ? FrameRateMatchPolicy.snapToStandard(format.frameRate)
                : estimator.onFrame(presentationTimeUs);
        if (frameRate > 0f) {
            reported = true;
            mainHandler.post(() -> switcher.matchFrameRate(frameRate));
        }
    }
}
