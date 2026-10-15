package com.ztv.player.workspace;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ztv.player.data.SettingsStore;

import java.util.ArrayList;
import java.util.List;

/**
 * The listening card: a mic that swells with the voice, the words as they are recognised,
 * and a retry on OK. Talks to the system {@link SpeechRecognizer} directly so the text comes
 * back to ZTV (Google TV's own voice activity does not return results to other apps), and the
 * TV remote's microphone is opened by the system as soon as recording starts.
 */
final class VoiceSearchDialog implements RecognitionListener {
    interface Listener {
        void onHeard(List<String> alternatives);
    }

    private static final int MIC_IDLE = 0xFF3A4152;
    private static final int MIC_LIVE = 0xFFE5484D;
    private static final String HINT = "Say a channel, movie or series";
    /** Google's recognizer reads this undocumented extra for extra spoken languages. */
    private static final String EXTRA_ADDITIONAL_LANGUAGES = "android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES";

    private final WorkspaceActivity host;
    private String language;
    private final Listener listener;
    private SpeechRecognizer recognizer;
    private BilingualRecognizer both;
    private FrameLayout mic;
    private View pulse;
    private TextView status;
    private TextView transcript;
    private LinearLayout languages;
    private boolean open;

    VoiceSearchDialog(WorkspaceActivity host, String language, Listener listener) {
        this.host = host;
        this.language = language;
        this.listener = listener;
    }

    void show() {
        LinearLayout card = Nator.column(host);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Nator.rounded(host, Nator.WIDGET_BG, 20));
        Nator.pad(card, 28, 30, 28, 18);

        FrameLayout stage = new FrameLayout(host);
        stage.setClipChildren(false);
        pulse = new View(host);
        pulse.setBackground(Nator.oval(Nator.alpha(MIC_LIVE, 0.22f)));
        stage.addView(pulse, new FrameLayout.LayoutParams(Nator.px(host, 96), Nator.px(host, 96), Gravity.CENTER));
        mic = new FrameLayout(host);
        mic.setContentDescription("Listen again");
        Nator.focusable(mic, 999);
        mic.setBackground(Nator.oval(MIC_IDLE));
        mic.addView(Nator.icon(host, "mic", 40, 0xFFFFFFFF), new FrameLayout.LayoutParams(-1, -1));
        mic.setOnClickListener(v -> listen());
        Widgets.trapFocus(mic, true, true, true, false);
        stage.addView(mic, new FrameLayout.LayoutParams(Nator.px(host, 84), Nator.px(host, 84), Gravity.CENTER));
        card.addView(stage, Nator.size(host, 140, 120));

        status = Nator.text(host, "Starting the microphone…", 20, Nator.W600, Nator.HEADING);
        status.setGravity(Gravity.CENTER);
        card.addView(status, Nator.margins(host, Nator.matchWidth(), 0, 10, 0, 0));
        transcript = Nator.text(host, HINT, 16, Nator.W400, Nator.BODY);
        transcript.setGravity(Gravity.CENTER);
        transcript.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        transcript.setMinLines(2);
        card.addView(transcript, Nator.margins(host, Nator.matchWidth(), 0, 8, 0, 0));
        languages = Nator.row(host);
        languages.setGravity(Gravity.CENTER);
        card.addView(languages, Nator.margins(host, Nator.matchWidth(), 0, 16, 0, 0));
        renderLanguages();

        LinearLayout actions = Nator.row(host);
        actions.setGravity(Gravity.END);
        TextView cancel = host.dialogTextButton("Cancel", host::dismissTopOverlay);
        Widgets.trapFocus(cancel, false, true, true, true);
        actions.addView(cancel);
        card.addView(actions, Nator.margins(host, Nator.matchWidth(), 0, 10, 0, 0));

        open = true;
        host.showDialog(card, 500, this::release);
        mic.requestFocus();
        listen();
    }

    /** Auto hears both languages; Arabic also finds Latin channel names (see SearchMatcher). */
    private void renderLanguages() {
        languages.removeAllViews();
        String[] labels = {"Auto", "العربية", "English"};
        String[] tags = {SettingsStore.VOICE_BILINGUAL, SettingsStore.VOICE_ARABIC, SettingsStore.VOICE_ENGLISH};
        for (int i = 0; i < labels.length; i++) {
            String tag = tags[i];
            int position = i;
            boolean active = tag.equals(language);
            TextView chip = Nator.text(host, labels[i], 13.5f, Nator.W500, active ? Nator.SELECTION : Nator.BODY);
            chip.setGravity(Gravity.CENTER);
            Nator.pad(chip, 16, 0, 16, 0);
            Nator.focusable(chip, 999);
            chip.setBackground(Nator.states(
                    active ? Nator.pill(host, Nator.SELECTION_SURFACE) : Nator.roundedStroke(host, 0, 999, 1, Nator.OUTLINE_VARIANT),
                    Nator.pill(host, Nator.HOVER_OVERLAY)));
            chip.setOnClickListener(v -> {
                language = tag;
                host.settings.setVoiceLanguage(tag);
                renderLanguages();
                languages.getChildAt(position).requestFocus();
                listen();
            });
            Widgets.trapFocus(chip, false, i == 0, i == labels.length - 1, false);
            languages.addView(chip, Nator.margins(host, Nator.size(host, -2, 32), 6, 0, 6, 0));
        }
    }

    private void listen() {
        if (!open) {
            return;
        }
        if (both != null) {
            both.cancel();
            both = null;
        }
        if (SettingsStore.VOICE_BILINGUAL.equals(language) && Build.VERSION.SDK_INT >= 33) {
            if (recognizer != null) {
                recognizer.cancel();
            }
            listenInBoth();
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(host);
            recognizer.setRecognitionListener(this);
        } else {
            recognizer.cancel();
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, host.getPackageName());
        putLanguage(intent, language);
        status.setText("Starting the microphone…");
        transcript.setText(HINT);
        transcript.setTextColor(Nator.BODY);
        setLive(false);
        recognizer.startListening(intent);
    }

    /**
     * Bilingual listens in Arabic and names English as an extra language. Google's per-phrase
     * language detection only runs offline (no Arabic pack on most TVs), so English channel
     * names usually arrive in Arabic letters and SearchMatcher maps them back.
     */
    static void putLanguage(Intent intent, String language) {
        if (SettingsStore.VOICE_DEVICE.equals(language)) {
            return;
        }
        boolean bilingual = SettingsStore.VOICE_BILINGUAL.equals(language);
        String primary = bilingual ? SettingsStore.VOICE_ARABIC : language;
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, primary);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primary);
        if (bilingual) {
            intent.putExtra(EXTRA_ADDITIONAL_LANGUAGES, new String[]{SettingsStore.VOICE_ENGLISH});
        }
    }

    private void listenInBoth() {
        status.setText("Starting the microphone…");
        transcript.setText(HINT);
        transcript.setTextColor(Nator.BODY);
        setLive(false);
        both = new BilingualRecognizer(host, new BilingualRecognizer.Callback() {
            @Override
            public void onRecording() {
                onReadyForSpeech(null);
            }

            @Override
            public void onLevel(float level) {
                onRmsChanged(level * 10f);
            }

            @Override
            public void onCaptured() {
                status.setText("Searching in Arabic and English…");
                setLive(false);
            }

            @Override
            public void onResults(List<String> alternatives) {
                Bundle bundle = new Bundle();
                bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, new ArrayList<>(alternatives));
                VoiceSearchDialog.this.onResults(bundle);
            }

            @Override
            public void onError(int error) {
                VoiceSearchDialog.this.onError(error);
            }
        });
        both.start();
    }

    private void setLive(boolean live) {
        mic.setBackground(Nator.oval(live ? MIC_LIVE : MIC_IDLE));
        pulse.setVisibility(live ? View.VISIBLE : View.INVISIBLE);
        if (!live) {
            pulse.setScaleX(1f);
            pulse.setScaleY(1f);
        }
    }

    private void release() {
        open = false;
        if (both != null) {
            both.cancel();
            both = null;
        }
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        if (open) {
            status.setText("Listening…");
            setLive(true);
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        if (open && pulse.getVisibility() == View.VISIBLE) {
            float level = Math.max(0f, Math.min(10f, rmsdB)) / 10f;
            float scale = 1f + level * 0.45f;
            pulse.animate().scaleX(scale).scaleY(scale).setDuration(90).start();
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        if (open) {
            status.setText("Searching…");
            setLive(false);
        }
    }

    @Override
    public void onError(int error) {
        if (!open) {
            return;
        }
        setLive(false);
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                status.setText("Didn't catch that");
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_SERVER:
                status.setText("Speech service unreachable");
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                status.setText("Microphone permission needed");
                break;
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                status.setText("This language isn't available");
                break;
            default:
                status.setText("Voice search stopped");
                break;
        }
        transcript.setText("Press OK on the mic to try again");
        transcript.setTextColor(Nator.BODY);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        List<String> words = partialResults == null ? null
                : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (open && words != null && !words.isEmpty() && !words.get(0).trim().isEmpty()) {
            transcript.setText(words.get(0));
            transcript.setTextColor(Nator.ON_SURFACE);
        }
    }

    @Override
    public void onResults(Bundle results) {
        if (!open) {
            return;
        }
        ArrayList<String> heard = results == null ? null
                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (heard == null || heard.isEmpty() || heard.get(0).trim().isEmpty()) {
            onError(SpeechRecognizer.ERROR_NO_MATCH);
            return;
        }
        transcript.setText(heard.get(0));
        transcript.setTextColor(Nator.ON_SURFACE);
        host.dismissTopOverlay();
        listener.onHeard(heard);
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }
}
