package com.ztv.player.workspace;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ztv.player.data.ServerAddressPolicy;
import com.ztv.player.data.XtreamAccountInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Xtream account info dialog (status pill, expiry with the warn/expired tints, server, user). */
final class AccountInfoDialog {
    private final WorkspaceActivity host;
    private final Context context;
    private LinearLayout rows;

    AccountInfoDialog(WorkspaceActivity host) {
        this.host = host;
        this.context = host;
    }

    void show() {
        if (!host.hasSource()) {
            return;
        }
        LinearLayout card = Nator.column(context);
        card.setBackground(Nator.rounded(context, 0xFF121316, 28));
        Nator.pad(card, 24, 22, 24, 18);
        card.addView(Nator.text(context, "Account info", 20, Nator.W600, Nator.HEADING));
        card.addView(Nator.text(context, host.playlistTitle(), 13, Nator.W400, Nator.BODY), Nator.margins(context, Nator.wrap(), 0, 4, 0, 0));
        rows = Nator.column(context);
        rows.setBackground(Nator.roundedStroke(context, Nator.WIDGET_BG, 12, 1, 0x12FFFFFF));
        card.addView(rows, Nator.margins(context, Nator.matchWidth(), 0, 18, 0, 0));
        row("Server", ServerAddressPolicy.display(host.credentials().host()), Nator.HEADING, 0);
        row("Username", host.credentials().username(), Nator.HEADING, 0);
        row("Status", "Loading...", Nator.BODY, 0);
        LinearLayout actions = Nator.row(context);
        actions.setGravity(Gravity.END);
        TextView close = host.dialogTextButton("Close", host::dismissTopOverlay);
        actions.addView(close);
        card.addView(actions, Nator.margins(context, Nator.matchWidth(), 0, 18, 0, 0));
        host.showDialog(card, 520, null);
        close.requestFocus();
        host.catalog.loadAccountInfo(this::render);
    }

    private void render(XtreamAccountInfo info) {
        if (rows == null || rows.getChildCount() < 5) {
            return;
        }
        while (rows.getChildCount() > 3) {
            rows.removeViewAt(rows.getChildCount() - 1);
        }
        if (info == null) {
            row("Status", "Unavailable", Nator.ERROR, 0);
            return;
        }
        boolean active = "Active".equalsIgnoreCase(info.status());
        row("Status", info.status().isEmpty() ? "Unknown" : info.status(), active ? Nator.SUCCESS : 0xFFFF8F8F,
                active ? Nator.alpha(Nator.SUCCESS, 0.14f) : Nator.alpha(0xFFF44336, 0.14f));
        if (info.hasExpiry()) {
            long days = (info.expiryEpochSeconds() * 1000L - System.currentTimeMillis()) / 86_400_000L;
            String date = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(new Date(info.expiryEpochSeconds() * 1000L));
            int color = days < 0 ? 0xFFFF8F8F : days <= 14 ? 0xFFFBBF24 : Nator.HEADING;
            int tint = days < 0 ? Nator.alpha(0xFFF44336, 0.14f) : days <= 14 ? Nator.alpha(0xFFFBBF24, 0.16f) : 0;
            row("Expires", date + (days >= 0 ? "  ·  " + days + " days left" : "  ·  expired"), color, tint);
        } else {
            row("Expires", "Unlimited", Nator.HEADING, 0);
        }
    }

    private void row(String label, String value, int color, int tint) {
        if (rows.getChildCount() > 0) {
            rows.addView(Nator.hairline(context, 0x0FFFFFFF), new LinearLayout.LayoutParams(-1, Math.max(1, Nator.px(context, 1))));
        }
        LinearLayout line = Nator.row(context);
        Nator.pad(line, 16, 13, 16, 13);
        line.addView(Nator.text(context, label, 13.5f, Nator.W500, Nator.BODY), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView text = Nator.text(context, value, 13.5f, Nator.W500, color);
        if (tint != 0) {
            text.setBackground(Nator.pill(context, tint));
            Nator.pad(text, 10, 3, 10, 3);
        }
        line.addView(text);
        rows.addView(line);
    }
}
