package com.ztv.player;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import java.util.ArrayList;

/**
 * Renderer setup shared by the full-screen and inline players.
 *
 * <p>The bundled FFmpeg audio decoder covers AC-3, E-AC-3, DTS and other formats that many TV
 * boxes cannot decode in hardware, which otherwise plays live sports channels with no sound.
 * By default extension renderers are added after the platform ones, so hardware decoding and HDMI
 * passthrough stay preferred and FFmpeg is only used when the device cannot handle the format.
 * Users whose box decodes a format badly can prefer FFmpeg for audio; video always stays on the
 * hardware decoder because software video decoding is too slow on TV boxes.
 *
 * <p>Decoder fallback lets playback move to the next available decoder when the primary one
 * fails to initialise, instead of stopping with an error.
 */
public final class PlayerRenderers {
    private PlayerRenderers() {
    }

    public static RenderersFactory create(Context context) {
        return create(context, false, 0);
    }

    public static RenderersFactory create(Context context, boolean preferFfmpegAudio, int audioDelayMs) {
        return new ZtvRenderersFactory(context, audioDelayMs)
                .setExtensionRendererMode(preferFfmpegAudio
                        ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true);
    }

    private static final class ZtvRenderersFactory extends DefaultRenderersFactory {
        private final int audioDelayMs;

        ZtvRenderersFactory(Context context, int audioDelayMs) {
            super(context);
            this.audioDelayMs = audioDelayMs;
        }

        @Override
        protected void buildVideoRenderers(Context context, @ExtensionRendererMode int extensionRendererMode,
                MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, Handler eventHandler,
                VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
            int videoMode = extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER
                    ? EXTENSION_RENDERER_MODE_ON
                    : extensionRendererMode;
            super.buildVideoRenderers(context, videoMode, mediaCodecSelector, enableDecoderFallback,
                    eventHandler, eventListener, allowedVideoJoiningTimeMs, out);
        }

        @Nullable
        @Override
        protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                boolean enableAudioOutputPlaybackParams) {
            AudioSink sink = super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
            return audioDelayMs == 0 || sink == null ? sink : new AudioDelaySink(sink, audioDelayMs);
        }
    }
}
