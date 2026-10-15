package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaSection;
import com.ztv.player.ui.Tr;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Category management dialog: a checkbox per category (checked = visible) + Show all / Hide all. */
final class ManageCategoriesDialog {
    private final WorkspaceActivity host;
    private final Context context;
    private final XtreamContentType type;
    private final List<XtreamMediaSection> sections;
    private final Runnable onSaved;
    private final Set<String> hidden;
    private LinearLayout list;

    ManageCategoriesDialog(WorkspaceActivity host, XtreamContentType type, List<XtreamMediaSection> sections, Runnable onSaved) {
        this.host = host;
        this.context = host;
        this.type = type;
        this.sections = sections;
        this.onSaved = onSaved;
        this.hidden = new HashSet<>(host.settings.hiddenSections(type));
    }

    void show() {
        LinearLayout card = Nator.column(context);
        card.setBackground(Nator.rounded(context, 0xFF121316, 28));
        Nator.pad(card, 24, 22, 24, 18);
        String label = type == XtreamContentType.LIVE ? Tr.t("Live TV", "القنوات") : type == XtreamContentType.SERIES ? Tr.t("Series", "المسلسلات") : Tr.t("Movies", "الأفلام");
        card.addView(Nator.text(context, Tr.t("Manage categories", "إدارة الفئات"), 20, Nator.W600, Nator.HEADING));
        card.addView(Nator.text(context, label + " · uncheck a category to hide it", 13, Nator.W400, Nator.BODY),
                Nator.margins(context, Nator.wrap(), 0, 4, 0, 0));
        LinearLayout bulk = Nator.row(context);
        bulk.addView(host.dialogTextButton(Tr.t("Show all", "إظهار الكل"), () -> {
            hidden.clear();
            render();
        }), Nator.margins(context, Nator.wrap(), 0, 0, 8, 0));
        bulk.addView(host.dialogTextButton(Tr.t("Hide all", "إخفاء الكل"), () -> {
            for (XtreamMediaSection section : sections) {
                hidden.add(section.title());
            }
            render();
        }));
        card.addView(bulk, Nator.margins(context, Nator.wrap(), -12, 10, 0, 4));
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        list = Nator.column(context);
        scroll.addView(list);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, Nator.px(context, 360)));
        LinearLayout actions = Nator.row(context);
        actions.setGravity(Gravity.END);
        actions.addView(host.dialogTextButton(Tr.t("Cancel", "إلغاء"), host::dismissTopOverlay), Nator.margins(context, Nator.wrap(), 0, 0, 8, 0));
        TextView save = host.dialogFilledButton(Tr.t("Save", "حفظ"), () -> {
            host.settings.setHiddenSections(type, hidden);
            host.dismissTopOverlay();
            if (onSaved != null) {
                onSaved.run();
            }
        });
        actions.addView(save);
        card.addView(actions, Nator.margins(context, Nator.matchWidth(), 0, 16, 0, 0));
        render();
        host.showDialog(card, 560, null);
        if (list.getChildCount() > 0) {
            list.getChildAt(0).requestFocus();
        } else {
            save.requestFocus();
        }
    }

    private void render() {
        list.removeAllViews();
        for (XtreamMediaSection section : sections) {
            String title = section.title();
            LinearLayout row = Nator.row(context);
            Nator.pad(row, 8, 8, 10, 8);
            Nator.focusable(row, 8);
            row.setBackground(Nator.states(new GradientDrawable(), Nator.rounded(context, Nator.HOVER_OVERLAY, 8)));
            TextView box = Nator.icon(context, "check", 14, 0xFF002F65);
            row.addView(box, Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 12, 0));
            row.addView(Nator.line(context, title, 14, Nator.W400, Nator.ON_SURFACE), new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(Nator.countBadge(context, String.valueOf(section.items().size())));
            Runnable style = () -> {
                boolean visible = !hidden.contains(title);
                box.setBackground(visible ? Nator.rounded(context, 0xFFABC7FF, 2) : Nator.roundedStroke(context, 0, 2, 2, Nator.ON_SURFACE_VARIANT));
                box.setText(visible ? Nator.glyph("check") : "");
            };
            style.run();
            row.setOnClickListener(v -> {
                if (!hidden.remove(title)) {
                    hidden.add(title);
                }
                style.run();
            });
            list.addView(row, Nator.matchWidth());
        }
        if (sections.isEmpty()) {
            View empty = Widgets.emptyState(context, null, Tr.t("No categories", "لا فئات"), null);
            list.addView(empty);
        }
    }
}
