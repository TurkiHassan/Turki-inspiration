package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ztv.player.data.ServerAddressPolicy;
import com.ztv.player.data.XtreamApiClient;
import com.ztv.player.data.XtreamCredentials;
import com.ztv.player.ui.Tr;

/**
 * IPTVnator's "Add playlist" dialog: the six connection methods (Xtream credentials is the
 * one ZTV speaks), the outlined fields, the cleartext note and Clear / Cancel /
 * Test HTTPS and HTTP / Add playlist.
 */
final class AddPlaylistDialog {
    private final WorkspaceActivity host;
    private final Context context;
    private final XtreamCredentials prefill;
    private EditText title;
    private EditText server;
    private EditText username;
    private EditText password;
    private TextView status;
    private TextView addButton;
    private boolean busy;

    AddPlaylistDialog(WorkspaceActivity host, XtreamCredentials prefill) {
        this.host = host;
        this.context = host;
        this.prefill = prefill;
    }

    void show() {
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(Nator.rounded(context, 0xFF121316, 28));
        LinearLayout card = Nator.column(context);
        Nator.pad(card, 24, 20, 24, 20);
        scroll.addView(card, new ScrollView.LayoutParams(-1, -2));

        card.addView(Nator.text(context, prefill != null ? Tr.t("Edit playlist", "تعديل الاشتراك") : Tr.t("Add playlist", "إضافة اشتراك"), 20, Nator.W600, Nator.HEADING));
        card.addView(Nator.text(context, Tr.t("Pick how you want to connect. You can rename it later.", "اختر طريقة الاتصال. يمكنك تغيير الاسم لاحقًا."), 13, Nator.W400, Nator.BODY),
                Nator.margins(context, Nator.wrap(), 0, 4, 0, 14));

        String[][] methods = {
                {"link", Tr.t("M3U URL", "رابط M3U"), Tr.t("Paste a link to a remote .m3u or .m3u8", "الصق رابط ملف ‎.m3u أو ‎.m3u8")},
                {"description", Tr.t("M3U file", "ملف M3U"), Tr.t("Upload a playlist from your computer", "ارفع قائمة من جهازك")},
                {"cloud", Tr.t("Xtream credentials", "بيانات Xtream"), Tr.t("Connect with host, username and password", "اتصل بالخادم واسم المستخدم وكلمة المرور")},
                {"cast", Tr.t("Stalker portal", "بوابة Stalker"), Tr.t("MAC address and portal URL", "عنوان MAC ورابط البوابة")},
                {"subject", Tr.t("Raw m3u text", "نص m3u"), Tr.t("For one-off lists or when you only have text", "لقوائم مؤقتة أو حين لا تملك إلا النص")},
                {"auto_awesome", Tr.t("Auto-detect", "اكتشاف تلقائي"), Tr.t("Paste the message from your provider", "الصق الرسالة التي أرسلها مزوّدك")},
        };
        LinearLayout gridRow = null;
        View xtreamCard = null;
        for (int i = 0; i < methods.length; i++) {
            if (i % 3 == 0) {
                gridRow = Nator.row(context);
                gridRow.setGravity(Gravity.TOP);
                card.addView(gridRow, Nator.margins(context, Nator.matchWidth(), 0, i == 0 ? 0 : 10, 0, 0));
            }
            boolean active = i == 2;
            View method = methodCard(methods[i][0], methods[i][1], methods[i][2], active);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Nator.px(context, 122), 1f);
            params.setMarginEnd(i % 3 == 2 ? 0 : Nator.px(context, 10));
            gridRow.addView(method, params);
            if (active) {
                xtreamCard = method;
            }
        }

        title = field("Playlist title*");
        server = field("Server URL*");
        server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        username = field("Username*");
        password = field("Password*");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (prefill != null) {
            title.setText(host.playlistTitle());
            server.setText(ServerAddressPolicy.display(prefill.host()));
            username.setText(prefill.username());
        } else {
            server.setText(ServerAddressPolicy.display(host.serverConfig.effectiveHost()));
        }

        card.addView(fieldBox(title), Nator.margins(context, new LinearLayout.LayoutParams(-1, Nator.px(context, 52)), 0, 18, 0, 0));
        card.addView(fieldBox(server), Nator.margins(context, new LinearLayout.LayoutParams(-1, Nator.px(context, 52)), 0, 12, 0, 0));
        TextView hint = Nator.text(context, Tr.t("Server address, e.g. http://host:port", "عنوان الخادم، مثل http://host:port"), 11, Nator.W400, Nator.MUTED);
        card.addView(hint, Nator.margins(context, Nator.wrap(), 16, 4, 0, 0));
        card.addView(fieldBox(username), Nator.margins(context, new LinearLayout.LayoutParams(-1, Nator.px(context, 52)), 0, 10, 0, 0));
        card.addView(fieldBox(password), Nator.margins(context, new LinearLayout.LayoutParams(-1, Nator.px(context, 52)), 0, 12, 0, 0));

        status = Nator.text(context, Tr.t("This test may send your username and password over unencrypted HTTP if HTTPS is unavailable.", "قد يرسل هذا الاختبار اسم المستخدم وكلمة المرور عبر HTTP غير المشفّر إن لم يتوفر HTTPS."),
                13.5f, Nator.W400, Nator.ON_SURFACE);
        card.addView(status, Nator.margins(context, Nator.matchWidth(), 0, 16, 0, 0));

        LinearLayout actions = Nator.row(context);
        actions.addView(host.dialogTextButton(Tr.t("Clear", "مسح"), () -> {
            title.setText("");
            server.setText("");
            username.setText("");
            password.setText("");
        }));
        actions.addView(Nator.spacer(context));
        actions.addView(host.dialogTextButton(Tr.t("Cancel", "إلغاء"), host::dismissTopOverlay), Nator.margins(context, Nator.wrap(), 0, 0, 8, 0));
        actions.addView(Widgets.outlinedPill(context, null, Tr.t("Test HTTPS and HTTP", "اختبار HTTPS وHTTP"), () -> submit(true)),
                Nator.margins(context, Nator.wrap(), 0, 0, 8, 0));
        addButton = host.dialogFilledButton(prefill != null ? Tr.t("Save", "حفظ") : Tr.t("Add playlist", "إضافة اشتراك"), () -> submit(false));
        actions.addView(addButton);
        card.addView(actions, Nator.margins(context, Nator.matchWidth(), 0, 18, 0, 0));

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateEnabled();
            }
        };
        for (EditText edit : new EditText[]{title, server, username, password}) {
            edit.addTextChangedListener(watcher);
        }
        password.setOnEditorActionListener((v, actionId, event) -> {
            submit(false);
            return true;
        });
        updateEnabled();

        host.showDialog(scroll, 780, null);
        View first = prefill != null ? password : title;
        first.post(first::requestFocus);
        if (xtreamCard != null) {
            xtreamCard.setNextFocusDownId(View.NO_ID);
        }
    }

    private View methodCard(String icon, String label, String sub, boolean active) {
        LinearLayout card = Nator.column(context);
        Nator.pad(card, 12, 10, 11, 12);
        GradientDrawable rest = active
                ? Nator.roundedStroke(context, Nator.alpha(Nator.SELECTION, 0.12f), 10, 1, Nator.SELECTION)
                : Nator.roundedStroke(context, Nator.alpha(0xFF0D0F12, 0.7f), 10, 1, 0x0DFFFFFF);
        GradientDrawable focused = active
                ? Nator.roundedStroke(context, Nator.alpha(Nator.SELECTION, 0.18f), 10, 1, Nator.SELECTION)
                : Nator.roundedStroke(context, Nator.CARD_HOVER_BG, 10, 1, 0x1FFFFFFF);
        card.setBackground(Nator.states(rest, focused));
        Nator.focusable(card, 10);
        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Nator.rounded(context, active ? Nator.alpha(Nator.SELECTION, 0.22f) : Nator.alpha(0xFF232840, 0.75f), 8));
        iconBox.addView(Nator.icon(context, icon, 18, active ? Nator.SELECTION : Nator.MUTED), new FrameLayout.LayoutParams(-1, -1));
        card.addView(iconBox, Nator.size(context, 32, 32));
        card.addView(Nator.line(context, label, 13, Nator.W600, active ? Nator.SELECTION : Nator.HEADING), Nator.margins(context, Nator.wrap(), 0, 10, 0, 0));
        TextView subtitle = Nator.text(context, sub, 11.5f, Nator.W400, Nator.MUTED);
        subtitle.setMaxLines(2);
        card.addView(subtitle, Nator.margins(context, Nator.matchWidth(), 0, 6, 0, 0));
        if (!active) {
            card.setAlpha(0.55f);
            card.setOnClickListener(v -> host.snack(Tr.t("ZTV connects with Xtream credentials", "يتصل ZTV ببيانات Xtream")));
        }
        return card;
    }

    private EditText field(String label) {
        EditText edit = new EditText(context);
        edit.setHint(label);
        edit.setSingleLine(true);
        edit.setBackground(null);
        edit.setTypeface(Nator.face(Nator.W400));
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15 * Nator.K);
        edit.setTextColor(Nator.HEADING);
        edit.setHintTextColor(Nator.BODY);
        edit.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        Nator.pad(edit, 16, 0, 16, 0);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        return edit;
    }

    /** Outlined Material field: 1px outline, focused = 2px selection outline. */
    private View fieldBox(EditText edit) {
        FrameLayout box = new FrameLayout(context);
        GradientDrawable rest = Nator.roundedStroke(context, 0xF52C313D, 10, 1, Nator.OUTLINE);
        GradientDrawable focused = Nator.roundedStroke(context, 0xF52C313D, 10, 2, Nator.SELECTION);
        box.setBackground(rest);
        edit.setOnFocusChangeListener((v, hasFocus) -> box.setBackground(hasFocus ? focused : rest));
        box.addView(edit, new FrameLayout.LayoutParams(-1, -1));
        return box;
    }

    private boolean valid() {
        return !server.getText().toString().trim().isEmpty()
                && !username.getText().toString().trim().isEmpty()
                && !password.getText().toString().isEmpty();
    }

    private void updateEnabled() {
        boolean enabled = valid() && !busy;
        addButton.setAlpha(enabled ? 1f : 0.45f);
    }

    private void submit(boolean testOnly) {
        if (busy || !valid()) {
            status.setText(Tr.t("Fill in server URL, username and password.", "أدخل عنوان الخادم واسم المستخدم وكلمة المرور."));
            status.setTextColor(Nator.ERROR);
            return;
        }
        String host = ServerAddressPolicy.normalize(server.getText().toString());
        if (host.isEmpty()) {
            status.setText(Tr.t("Enter a valid server address, e.g. example.com:8080", "أدخل عنوان خادم صحيحًا، مثل example.com:8080"));
            status.setTextColor(Nator.ERROR);
            server.requestFocus();
            return;
        }
        XtreamCredentials credentials = new XtreamCredentials(host, username.getText().toString().trim(), password.getText().toString());
        busy = true;
        updateEnabled();
        status.setText(testOnly ? Tr.t("Testing connection...", "جارٍ اختبار الاتصال...") : Tr.t("Connecting to " + ServerAddressPolicy.display(host) + "...", "جارٍ الاتصال بـ " + ServerAddressPolicy.display(host) + "..."));
        status.setTextColor(Nator.ON_SURFACE);
        this.host.api.authenticateAt(credentials, new XtreamApiClient.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean authenticated) {
                busy = false;
                updateEnabled();
                if (!authenticated) {
                    status.setText(Tr.t("Invalid account or inactive subscription.", "الحساب غير صحيح أو الاشتراك غير فعّال."));
                    status.setTextColor(Nator.ERROR);
                    return;
                }
                if (testOnly) {
                    status.setText("Connection OK — the portal is active.");
                    status.setTextColor(Nator.SUCCESS);
                    return;
                }
                boolean serverChanged = AddPlaylistDialog.this.host.serverConfig.setUserHost(host);
                XtreamCredentials signedIn = credentials.withHost(AddPlaylistDialog.this.host.serverConfig.effectiveHost());
                String name = title.getText().toString().trim();
                XtreamCredentials previous = AddPlaylistDialog.this.host.credentials();
                boolean sameAccount = previous != null && previous.username().equals(signedIn.username());
                AddPlaylistDialog.this.host.dismissTopOverlay();
                AddPlaylistDialog.this.host.onSourceAdded(signedIn, name, serverChanged || !sameAccount);
            }

            @Override
            public void onError(Exception exception) {
                busy = false;
                updateEnabled();
                status.setText(XtreamApiClient.isServerUnreachable(exception)
                        ? Tr.t("Can't reach " + ServerAddressPolicy.display(host) + ". Check the server address and try again.",
                        "تعذّر الوصول إلى " + ServerAddressPolicy.display(host) + ". تحقق من عنوان الخادم وحاول مرة أخرى.")
                        : Tr.t("Unable to sign in. Check the account and try again.", "تعذّر تسجيل الدخول. تحقق من الحساب وحاول مرة أخرى."));
                status.setTextColor(Nator.ERROR);
            }
        });
    }
}
