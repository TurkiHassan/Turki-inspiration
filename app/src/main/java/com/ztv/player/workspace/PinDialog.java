package com.ztv.player.workspace;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ztv.player.data.KidsStore;
import com.ztv.player.ui.Tr;

/**
 * Parent PIN entry for the TV: a 3×4 keypad that works with the D-pad, plus the remote's
 * number keys. Verifies the saved PIN, or creates a new one (typed twice).
 */
final class PinDialog {
    interface Done {
        void run();
    }

    private static final int MAX_LENGTH = 8;

    private final WorkspaceActivity host;
    private final boolean create;
    private final String title;
    private final Done done;
    private final StringBuilder typed = new StringBuilder();
    private String firstEntry;
    private TextView dots;
    private TextView message;
    private LinearLayout keypad;

    private PinDialog(WorkspaceActivity host, boolean create, String title, Done done) {
        this.host = host;
        this.create = create;
        this.title = title;
        this.done = done;
    }

    /** Asks for the parent PIN; {@code done} runs only after the right one. */
    static void verify(WorkspaceActivity host, String title, Done done) {
        new PinDialog(host, false, title, done).show();
    }

    /** Asks for a new PIN twice and saves it. */
    static void create(WorkspaceActivity host, Done done) {
        new PinDialog(host, true, Tr.t("Create a parent PIN", "أنشئ رمزًا سريًا للوالدين"), done).show();
    }

    private void show() {
        LinearLayout card = Nator.column(host);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Nator.rounded(host, Nator.WIDGET_BG, 20));
        Nator.pad(card, 28, 26, 28, 18);
        card.addView(Nator.icon(host, "lock", 34, Nator.SELECTION), Nator.size(host, 40, 40));
        TextView heading = Nator.text(host, title, 20, Nator.W600, Nator.HEADING);
        heading.setGravity(Gravity.CENTER);
        card.addView(heading, Nator.margins(host, Nator.matchWidth(), 0, 10, 0, 0));
        message = Nator.text(host, "", 14, Nator.W400, Nator.BODY);
        message.setGravity(Gravity.CENTER);
        card.addView(message, Nator.margins(host, Nator.matchWidth(), 0, 6, 0, 0));
        dots = Nator.text(host, "", 28, Nator.W600, Nator.ON_SURFACE);
        dots.setGravity(Gravity.CENTER);
        dots.setLetterSpacing(0.3f);
        card.addView(dots, Nator.margins(host, Nator.size(host, -1, 44), 0, 12, 0, 6));

        keypad = Nator.column(host);
        keypad.setGravity(Gravity.CENTER_HORIZONTAL);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"⌫", "0", "✓"}};
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = Nator.row(host);
            row.setGravity(Gravity.CENTER);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            for (int c = 0; c < rows[r].length; c++) {
                String key = rows[r][c];
                TextView button = Nator.text(host, key, 22, Nator.W600, Nator.ON_SURFACE);
                button.setGravity(Gravity.CENTER);
                Nator.focusable(button, 999);
                button.setBackground(Nator.states(Nator.oval(Nator.CHIP_BG), Nator.oval(Nator.SELECTION_SURFACE_STRONG)));
                button.setContentDescription("⌫".equals(key) ? Tr.t("Delete", "حذف")
                        : "✓".equals(key) ? Tr.t("Done", "تم") : key);
                button.setOnClickListener(v -> press(key));
                button.setOnKeyListener(this::onKey);
                Widgets.trapFocus(button, r == 0, c == 0, c == rows[r].length - 1, false);
                row.addView(button, Nator.margins(host, Nator.size(host, 64, 64), 8, 6, 8, 6));
            }
            keypad.addView(row);
        }
        card.addView(keypad);

        LinearLayout actions = Nator.row(host);
        actions.setGravity(Gravity.END);
        TextView cancel = host.dialogTextButton(Tr.t("Cancel", "إلغاء"), host::dismissTopOverlay);
        Widgets.trapFocus(cancel, false, true, true, true);
        cancel.setOnKeyListener(this::onKey);
        actions.addView(cancel);
        card.addView(actions, Nator.margins(host, Nator.matchWidth(), 0, 8, 0, 0));

        host.showDialog(card, 420, null);
        prompt();
        ((LinearLayout) keypad.getChildAt(0)).getChildAt(1).requestFocus();
    }

    private boolean onKey(View view, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            press(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            press("⌫");
            return true;
        }
        return false;
    }

    private void press(String key) {
        if ("⌫".equals(key)) {
            if (typed.length() > 0) {
                typed.setLength(typed.length() - 1);
            }
        } else if ("✓".equals(key)) {
            submit();
            return;
        } else if (typed.length() < MAX_LENGTH) {
            typed.append(key);
            if (!create && typed.length() == MAX_LENGTH) {
                submit();
                return;
            }
        }
        render();
    }

    private void submit() {
        String pin = typed.toString();
        typed.setLength(0);
        long now = System.currentTimeMillis();
        if (create) {
            if (pin.length() < KidsStore.MIN_PIN_LENGTH) {
                error(Tr.t("Use at least 4 digits", "استخدم 4 أرقام على الأقل"));
            } else if (firstEntry == null) {
                firstEntry = pin;
                prompt();
            } else if (!firstEntry.equals(pin)) {
                firstEntry = null;
                error(Tr.t("The two PINs didn't match. Start again.", "الرمزان غير متطابقين. ابدأ من جديد."));
            } else {
                host.kids.setPin(pin);
                host.dismissTopOverlay();
                done.run();
            }
            return;
        }
        long wait = host.kids.lockoutRemainingMs(now);
        if (wait > 0) {
            error(lockedText(wait));
        } else if (host.kids.checkPin(pin, now)) {
            host.dismissTopOverlay();
            done.run();
        } else {
            long locked = host.kids.lockoutRemainingMs(now);
            error(locked > 0 ? lockedText(locked) : Tr.t("Wrong PIN. Try again.", "رمز غير صحيح. حاول مرة أخرى."));
        }
    }

    private static String lockedText(long ms) {
        long seconds = Math.max(1, (ms + 999) / 1000);
        return Tr.t("Too many tries. Wait " + seconds + " s.", "محاولات كثيرة. انتظر " + seconds + " ثانية.");
    }

    private void prompt() {
        message.setTextColor(Nator.BODY);
        if (create) {
            message.setText(firstEntry == null
                    ? Tr.t("Enter 4 to 8 digits, then ✓", "أدخل من 4 إلى 8 أرقام ثم ✓")
                    : Tr.t("Enter the same PIN again", "أدخل الرمز نفسه مرة أخرى"));
        } else {
            message.setText(Tr.t("Enter the parent PIN, then ✓", "أدخل رمز الوالدين ثم ✓"));
        }
        render();
    }

    private void error(String text) {
        message.setText(text);
        message.setTextColor(Nator.ERROR);
        render();
    }

    private void render() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < typed.length(); i++) {
            out.append('•');
        }
        dots.setText(out.length() == 0 ? "–" : out.toString());
    }
}
