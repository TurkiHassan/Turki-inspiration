package com.ztv.player.workspace;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ztv.player.data.KidsPolicy;
import com.ztv.player.data.KidsStore;
import com.ztv.player.ui.Tr;

import java.util.Calendar;

/**
 * Runs Kids mode inside the workspace: counts watch time while a preview plays, keeps the
 * header badge's "minutes left" current, and covers the app with a lock screen at bedtime
 * or when today's time is used up. Only the parent PIN lifts the lock.
 */
final class KidsGuard {
    private static final long TICK_MS = 15_000L;
    private static final int BONUS_MINUTES = 30;

    private final WorkspaceActivity host;
    private final KidsStore kids;
    private final FrameLayout layer;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView badgeText;
    private long lastTick;
    private boolean running;
    private KidsPolicy.Block shown;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            Page page = host.top();
            if (kids.isActive() && page != null && page.playingMedia()) {
                kids.addWatched(KidsStore.dayKey(Calendar.getInstance()), now - lastTick);
            }
            lastTick = now;
            check();
            ui.postDelayed(this, TICK_MS);
        }
    };

    KidsGuard(WorkspaceActivity host, KidsStore kids, FrameLayout layer) {
        this.host = host;
        this.kids = kids;
        this.layer = layer;
    }

    void bindBadge(TextView text) {
        badgeText = text;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        lastTick = SystemClock.elapsedRealtime();
        check();
        ui.postDelayed(tick, TICK_MS);
    }

    void stop() {
        running = false;
        ui.removeCallbacks(tick);
    }

    /** Shows or lifts the lock screen and refreshes the badge. */
    void check() {
        KidsPolicy.Block block = kids.isActive() ? kids.block(Calendar.getInstance()) : null;
        updateBadge();
        if (block == shown) {
            return;
        }
        shown = block;
        layer.removeAllViews();
        if (block == null) {
            layer.setVisibility(View.GONE);
            return;
        }
        Page page = host.top();
        if (page != null) {
            page.onHostStopped();
        }
        layer.addView(buildLock(block), new FrameLayout.LayoutParams(-1, -1));
        layer.setVisibility(View.VISIBLE);
        layer.post(() -> {
            View first = layer.findViewWithTag("first");
            if (first != null) {
                first.requestFocus();
            }
        });
    }

    boolean locked() {
        return shown != null;
    }

    private void updateBadge() {
        if (badgeText == null) {
            return;
        }
        long left = kids.remainingMs(Calendar.getInstance());
        if (left == Long.MAX_VALUE) {
            badgeText.setText(Tr.t("Kids", "الأطفال"));
        } else {
            long minutes = (left + 59_999) / 60_000;
            badgeText.setText(Tr.t("Kids · " + minutes + " min left", "الأطفال · باقي " + minutes + " د"));
        }
    }

    private View buildLock(KidsPolicy.Block block) {
        boolean bedtime = block == KidsPolicy.Block.BEDTIME;
        FrameLayout screen = new FrameLayout(host);
        screen.setBackgroundColor(Nator.alpha(Nator.SHELL_BG, 0.97f));
        screen.setClickable(true);
        LinearLayout column = Nator.column(host);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(Nator.icon(host, bedtime ? "bedtime" : "hourglass_empty", 72, Nator.SELECTION),
                Nator.size(host, 84, 84));
        TextView title = Nator.text(host, bedtime ? Tr.t("It's bedtime", "حان وقت النوم")
                : Tr.t("Time's up for today", "انتهى وقت المشاهدة لليوم"), 30, Nator.W600, Nator.HEADING);
        title.setGravity(Gravity.CENTER);
        column.addView(title, Nator.margins(host, Nator.wrap(), 0, 18, 0, 0));
        TextView body = Nator.text(host, bedtime
                        ? Tr.t("Watching opens again in the morning.", "تعود المشاهدة في الصباح.")
                        : Tr.t("See you tomorrow! Ask a parent if you need more time.",
                        "نراك غدًا! اطلب من أحد والديك إن احتجت وقتًا إضافيًا."),
                17, Nator.W400, Nator.BODY);
        body.setGravity(Gravity.CENTER);
        column.addView(body, Nator.margins(host, Nator.size(host, 560, -2), 0, 10, 0, 0));

        LinearLayout actions = Nator.row(host);
        actions.setGravity(Gravity.CENTER);
        TextView extend = host.dialogFilledButton(bedtime
                ? Tr.t("Parent: allow tonight", "للوالدين: السماح الليلة")
                : Tr.t("Parent: 30 more minutes", "للوالدين: 30 دقيقة إضافية"), () ->
                PinDialog.verify(host, Tr.t("Parent PIN", "رمز الوالدين"), () -> {
                    Calendar now = Calendar.getInstance();
                    if (bedtime) {
                        kids.passBedtime(now);
                    } else {
                        kids.addBonusMinutes(KidsStore.dayKey(now), BONUS_MINUTES);
                    }
                    check();
                }));
        extend.setTag("first");
        TextView exit = host.dialogTextButton(Tr.t("Parent: exit Kids mode", "للوالدين: الخروج من وضع الأطفال"),
                host::exitKidsMode);
        Widgets.trapFocus(extend, true, true, false, true);
        Widgets.trapFocus(exit, true, false, true, true);
        actions.addView(extend, Nator.margins(host, Nator.wrap(), 8, 0, 8, 0));
        actions.addView(exit, Nator.margins(host, Nator.wrap(), 8, 0, 8, 0));
        column.addView(actions, Nator.margins(host, Nator.wrap(), 0, 28, 0, 0));
        screen.addView(column, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return screen;
    }
}
