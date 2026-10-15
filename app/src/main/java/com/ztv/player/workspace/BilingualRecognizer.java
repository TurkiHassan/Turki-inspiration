package com.ztv.player.workspace;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Records one utterance itself, then has the system recognizer transcribe that same audio in
 * Arabic and in English (Android 13+ accepts caller audio). The caller keeps whichever reading
 * matches the catalog, so nobody has to pick a language before speaking.
 */
@RequiresApi(33)
final class BilingualRecognizer {
    interface Callback {
        void onRecording();

        void onLevel(float level);

        void onCaptured();

        void onResults(List<String> alternatives);

        void onError(int error);
    }

    static final String[] LANGUAGES = {"ar-SA", "en-US"};
    private static final int RATE = 16_000;
    private static final int FRAME = RATE / 50;
    private static final int START_FRAMES = 3;
    private static final int END_SILENCE_FRAMES = 45;
    private static final int NO_SPEECH_FRAMES = 400;
    private static final int MAX_FRAMES = 500;
    /** The remote mic connects ~0.7 s in and the input glitches when it is rerouted. */
    private static final int SETTLE_FRAMES = 50;
    private static final double MIN_SPEECH_RMS = 120;
    private static final double START_NOISE = 60;

    private final Context context;
    private final Callback callback;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<List<String>> heard = new ArrayList<>();
    private volatile boolean cancelled;
    private SpeechRecognizer recognizer;
    private ParcelFileDescriptor source;
    private int firstError;

    BilingualRecognizer(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    void start() {
        new Thread(this::capture, "ztv-voice-capture").start();
    }

    void cancel() {
        cancelled = true;
        ui.removeCallbacksAndMessages(null);
        closeSession();
    }

    private void capture() {
        int minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord record;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, RATE));
        } catch (RuntimeException e) {
            post(() -> callback.onError(SpeechRecognizer.ERROR_AUDIO));
            return;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            post(() -> callback.onError(SpeechRecognizer.ERROR_AUDIO));
            return;
        }
        ByteArrayOutputStream audio = new ByteArrayOutputStream(RATE * 2 * 4);
        short[] frame = new short[FRAME];
        byte[] bytes = new byte[FRAME * 2];
        double noise = START_NOISE;
        int loud = 0;
        int quiet = 0;
        boolean speaking = false;
        boolean finished = false;
        record.startRecording();
        for (int index = 0; index < MAX_FRAMES && !cancelled; index++) {
            int read = record.read(frame, 0, FRAME);
            if (read <= 0) {
                break;
            }
            double sum = 0;
            for (int i = 0; i < read; i++) {
                sum += (double) frame[i] * frame[i];
                bytes[i * 2] = (byte) frame[i];
                bytes[i * 2 + 1] = (byte) (frame[i] >> 8);
            }
            audio.write(bytes, 0, read * 2);
            double rms = Math.sqrt(sum / read);
            if (index == SETTLE_FRAMES) {
                post(callback::onRecording);
            }
            // The first second carries the input's start-up click and the remote-mic reroute.
            if (index < SETTLE_FRAMES || rms < 1) {
                continue;
            }
            if (!speaking) {
                noise = rms < noise ? noise * 0.5 + rms * 0.5 : noise * 0.98 + rms * 0.02;
            }
            boolean voice = rms > Math.max(MIN_SPEECH_RMS, noise * 3);
            if (index % 2 == 0) {
                float level = (float) Math.max(0, Math.min(1, (20 * Math.log10(Math.max(rms, 1)) - 40) / 35));
                post(() -> callback.onLevel(level));
            }
            if (!speaking) {
                loud = voice ? loud + 1 : 0;
                speaking = loud >= START_FRAMES;
                if (!speaking && index >= NO_SPEECH_FRAMES) {
                    break;
                }
            } else {
                quiet = voice ? 0 : quiet + 1;
                if (quiet >= END_SILENCE_FRAMES) {
                    finished = true;
                    break;
                }
            }
        }
        record.stop();
        record.release();
        if (cancelled) {
            return;
        }
        if (!speaking) {
            post(() -> callback.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT));
            return;
        }
        byte[] pcm = audio.toByteArray();
        post(() -> {
            callback.onCaptured();
            recognize(pcm, 0);
        });
    }

    /** One language at a time: the recognition service rejects concurrent sessions. */
    private void recognize(byte[] pcm, int language) {
        if (cancelled) {
            return;
        }
        if (language >= LANGUAGES.length) {
            finish();
            return;
        }
        ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            callback.onError(SpeechRecognizer.ERROR_CLIENT);
            return;
        }
        source = pipe[0];
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new Session(() -> recognize(pcm, language + 1)));
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGES[language]);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0]);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE);
        intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
        recognizer.startListening(intent);
        new Thread(() -> {
            try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                out.write(pcm);
            } catch (IOException ignored) {
                // The session ended or was cancelled before reading everything.
            }
        }, "ztv-voice-feed").start();
    }

    private void finish() {
        List<String> merged = new ArrayList<>();
        int longest = 0;
        for (List<String> list : heard) {
            longest = Math.max(longest, list.size());
        }
        for (int i = 0; i < longest; i++) {
            for (List<String> list : heard) {
                if (i < list.size()) {
                    merged.add(list.get(i));
                }
            }
        }
        if (merged.isEmpty()) {
            callback.onError(firstError == 0 ? SpeechRecognizer.ERROR_NO_MATCH : firstError);
        } else {
            callback.onResults(merged);
        }
    }

    private void closeSession() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
        if (source != null) {
            try {
                source.close();
            } catch (IOException ignored) {
                // Already closed by the recognizer.
            }
            source = null;
        }
    }

    private void post(Runnable action) {
        ui.post(() -> {
            if (!cancelled) {
                action.run();
            }
        });
    }

    private final class Session implements RecognitionListener {
        private final Runnable next;
        private boolean done;

        Session(Runnable next) {
            this.next = next;
        }

        private void complete() {
            if (done || cancelled) {
                return;
            }
            done = true;
            ui.post(() -> {
                closeSession();
                next.run();
            });
        }

        private void collect(Bundle results) {
            ArrayList<String> words = results == null ? null
                    : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (words != null && !words.isEmpty() && !words.get(0).trim().isEmpty()) {
                heard.add(words);
            }
        }

        @Override
        public void onResults(Bundle results) {
            collect(results);
            complete();
        }

        /** Caller-supplied audio is transcribed as a segmented session. */
        @Override
        public void onSegmentResults(Bundle segmentResults) {
            collect(segmentResults);
        }

        @Override
        public void onEndOfSegmentedSession() {
            complete();
        }

        @Override
        public void onError(int error) {
            if (firstError == 0) {
                firstError = error;
            }
            complete();
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }
}
