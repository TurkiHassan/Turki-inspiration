package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.ztv.player.ui.Tr;

/** Shared IPTVnator building blocks used by several pages. */
public final class Widgets {
    private Widgets() {
    }

    /** Leanback vertical grid configured the way every scroll owner here needs it. */
    public static VerticalGridView verticalGrid(Context context, int columns, float cssRowGap, float cssColumnGap) {
        VerticalGridView grid = new VerticalGridView(context);
        grid.setNumColumns(columns);
        grid.setVerticalSpacing(Nator.px(context, cssRowGap));
        grid.setHorizontalSpacing(Nator.px(context, cssColumnGap));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setItemAnimator(null);
        grid.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ALIGNED);
        grid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        grid.setWindowAlignmentOffsetPercent(35f);
        grid.setItemAlignmentOffsetPercent(0f);
        grid.setHasFixedSize(true);
        grid.setItemViewCacheSize(24);
        // UP from the first row leaves the grid (header / band above); DOWN at the end stays.
        grid.setOnKeyInterceptListener(event -> escapeEdge(grid, event, columns, false));
        return grid;
    }

    public static HorizontalGridView horizontalRail(Context context, float cssGap) {
        HorizontalGridView rail = new HorizontalGridView(context);
        rail.setHorizontalSpacing(Nator.px(context, cssGap));
        rail.setClipChildren(false);
        rail.setClipToPadding(false);
        rail.setItemAnimator(null);
        rail.setWindowAlignment(BaseGridView.WINDOW_ALIGN_LOW_EDGE);
        rail.setWindowAlignmentOffsetPercent(0f);
        rail.setItemAlignmentOffsetPercent(0f);
        rail.setHasFixedSize(false);
        // LEFT from the first card returns to the side rail (leanback traps it by default).
        rail.setOnKeyInterceptListener(event -> escapeEdge(rail, event, 1, true));
        return rail;
    }

    /** The D-pad key that moves toward the content: → in English, ← in Arabic (right to left). */
    public static int forwardKey() {
        return Tr.arabic() ? android.view.KeyEvent.KEYCODE_DPAD_LEFT : android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    /** The D-pad key that moves back toward the side panels: ← in English, → in Arabic. */
    public static int backKey() {
        return Tr.arabic() ? android.view.KeyEvent.KEYCODE_DPAD_RIGHT : android.view.KeyEvent.KEYCODE_DPAD_LEFT;
    }

    /**
     * leanback consumes the D-pad at a grid's leading edge; hand that key to the normal
     * focus search instead so focus can leave (LEFT out of a rail, UP out of a grid).
     */
    static boolean escapeEdge(BaseGridView grid, android.view.KeyEvent event, int columns, boolean horizontal) {
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
            return false;
        }
        int key = event.getKeyCode();
        int direction;
        if (horizontal && key == backKey()) {
            direction = Tr.arabic() ? View.FOCUS_RIGHT : View.FOCUS_LEFT;
        } else if (!horizontal && key == android.view.KeyEvent.KEYCODE_DPAD_UP) {
            direction = View.FOCUS_UP;
        } else {
            return false;
        }
        int position = grid.getSelectedPosition();
        if (position < 0 || position >= columns) {
            return false;
        }
        View focused = grid.findFocus();
        if (focused == null) {
            return false;
        }
        View next = android.view.FocusFinder.getInstance().findNextFocus((android.view.ViewGroup) grid.getRootView(), focused, direction);
        if (next == null || isInside(next, grid)) {
            return false;
        }
        next.requestFocus();
        return true;
    }

    private static boolean isInside(View view, View ancestor) {
        View current = view;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    /**
     * D-pad DOWN on a partial last row: leanback refuses to move when no cell sits
     * directly below; jump to the last item instead (pitfall 3).
     */
    public static void enablePartialRowDown(VerticalGridView grid, int columns) {
        gridKeys(grid, columns, null);
    }

    /**
     * Grid D-pad rules: UP out of the first row, LEFT out of the first column to
     * {@code onLeftEdge} (the selected category, like IPTVnator's ArrowLeft handoff), and
     * DOWN on a partial last row jumps to the last item instead of dying (pitfall 3).
     */
    public static void gridKeys(VerticalGridView grid, int columns, Runnable onLeftEdge) {
        grid.setOnKeyInterceptListener(event -> {
            if (escapeEdge(grid, event, columns, false)) {
                return true;
            }
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
                return false;
            }
            int position = grid.getSelectedPosition();
            if (event.getKeyCode() == backKey() && onLeftEdge != null
                    && position >= 0 && position % columns == 0) {
                onLeftEdge.run();
                return true;
            }
            if (event.getKeyCode() != android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return false;
            }
            RecyclerView.Adapter<?> adapter = grid.getAdapter();
            if (adapter == null) {
                return false;
            }
            int count = adapter.getItemCount();
            int lastRowStart = ((count - 1) / columns) * columns;
            if (position < lastRowStart && position + columns >= count) {
                grid.setSelectedPosition(count - 1);
                return true;
            }
            return false;
        });
    }

    /** The empty / error view IPTVnator shows in panes (icon, title, description). */
    public static LinearLayout emptyState(Context context, String icon, String title, String description) {
        LinearLayout box = Nator.column(context);
        box.setGravity(Gravity.CENTER);
        Nator.pad(box, 24, 24, 24, 24);
        if (icon != null) {
            box.addView(Nator.icon(context, icon, 64, Nator.alpha(Nator.ON_SURFACE, 0.9f)), Nator.size(context, 64, 64));
        }
        TextView heading = Nator.text(context, title, 16.8f, Nator.W500, Nator.ON_SURFACE);
        heading.setGravity(Gravity.CENTER);
        box.addView(heading, Nator.margins(context, Nator.wrap(), 0, 24, 0, 0));
        if (description != null && !description.isEmpty()) {
            TextView body = Nator.text(context, description, 13.6f, Nator.W400, Nator.alpha(Nator.ON_SURFACE, 0.72f));
            body.setGravity(Gravity.CENTER);
            box.addView(body, Nator.margins(context, Nator.size(context, 440, -2), 0, 10, 0, 0));
        }
        return box;
    }

    /** Outlined pill action used by the error views ("Home", "Retry"). */
    public static TextView outlinedPill(Context context, String icon, String label, Runnable action) {
        TextView button = Nator.text(context, (icon == null ? "" : "") + label, 14, Nator.W500, 0xFFABC7FF);
        button.setGravity(Gravity.CENTER);
        Nator.pad(button, 16, 0, 16, 0);
        button.setMinHeight(Nator.px(context, 40));
        button.setBackground(Nator.states(
                Nator.roundedStroke(context, 0, 999, 1, Nator.OUTLINE),
                Nator.roundedStroke(context, Nator.alpha(0xFFABC7FF, 0.12f), 999, 1, Nator.OUTLINE)));
        Nator.focusable(button, 999);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    /** Ghost loading bar used by skeleton rows. */
    public static View skeletonBar(Context context, float cssWidth, float cssHeight) {
        View bar = new View(context);
        bar.setBackground(Nator.pill(context, Nator.alpha(Nator.ON_SURFACE, 0.08f)));
        bar.setLayoutParams(Nator.size(context, cssWidth, cssHeight));
        return bar;
    }

    /** Category-panel skeleton: rows with a label bar and a badge bar. */
    public static LinearLayout categorySkeleton(Context context, int rows) {
        LinearLayout list = Nator.column(context);
        Nator.pad(list, 8, 6, 8, 0);
        for (int i = 0; i < rows; i++) {
            LinearLayout row = Nator.row(context);
            row.setBackground(Nator.rounded(context, Nator.alpha(0xFF2A3360, 0.55f), 10));
            Nator.pad(row, 10, 0, 10, 0);
            View dot = new View(context);
            dot.setBackground(Nator.oval(Nator.alpha(Nator.ON_SURFACE, 0.12f)));
            row.addView(dot, Nator.margins(context, Nator.size(context, 8, 8), 0, 0, 10, 0));
            row.addView(skeletonBar(context, 90 + (i * 37) % 70, 8));
            row.addView(Nator.spacer(context));
            row.addView(skeletonBar(context, 18, 8));
            list.addView(row, Nator.margins(context, new LinearLayout.LayoutParams(-1, Nator.px(context, 24)), 0, 0, 0, 4));
        }
        return list;
    }

    /** Rail header: label 18px/600 + count badge + "See all ›" on the right. */
    public static LinearLayout railHeader(Context context, String label, int count, String seeAll, Runnable onSeeAll) {
        LinearLayout header = Nator.row(context);
        TextView title = Nator.text(context, label, 18, Nator.W600, Nator.HEADING);
        header.addView(title);
        if (count >= 0) {
            header.addView(Nator.countBadge(context, String.valueOf(count)), Nator.margins(context, Nator.wrap(), 8, 0, 0, 0));
        }
        header.addView(Nator.spacer(context));
        if (seeAll != null && onSeeAll != null) {
            LinearLayout link = Nator.row(context);
            link.addView(Nator.text(context, seeAll, 12.48f, Nator.W500, Nator.BADGE_TEXT));
            link.addView(Nator.icon(context, "chevron_right", 16, Nator.BADGE_TEXT), Nator.margins(context, Nator.size(context, 16, 16), 2, 0, 0, 0));
            Nator.pad(link, 6, 4, 6, 4);
            Nator.focusable(link, 6);
            link.setBackground(Nator.states(new GradientDrawable(), Nator.rounded(context, Nator.HOVER_OVERLAY, 6)));
            link.setOnClickListener(v -> onSeeAll.run());
            header.addView(link);
        }
        return header;
    }

    /** IPTVnator's light primary button (play-btn / hero__button--primary). */
    public static LinearLayout lightPrimary(Context context, String icon, String label, String meta, Runnable action) {
        LinearLayout button = Nator.row(context);
        button.setBackground(Nator.states(Nator.rounded(context, Nator.CTA_BG, 10), Nator.rounded(context, 0xFFFFFFFF, 10)));
        Nator.pad(button, 16, 0, 20, 0);
        Nator.focusable(button, 10);
        button.addView(Nator.icon(context, icon, 20, Nator.CTA_FG), Nator.margins(context, Nator.size(context, 20, 20), 0, 0, 10, 0));
        LinearLayout texts = Nator.column(context);
        texts.addView(Nator.line(context, label, 13.5f, Nator.W600, Nator.CTA_FG));
        if (meta != null && !meta.isEmpty()) {
            TextView metaView = Nator.line(context, meta, 11, Nator.W500, Nator.CTA_META);
            texts.addView(metaView, Nator.margins(context, Nator.wrap(), 0, 2, 0, 0));
        }
        button.addView(texts);
        button.setOnClickListener(v -> action.run());
        button.setMinimumHeight(Nator.px(context, 44));
        return button;
    }

    /** 44px ghost icon button (detail actions): 12% wash, 14% hairline, 10px radius. */
    public static FrameLayout ghostIconButton(Context context, String icon, String label, Runnable action) {
        FrameLayout button = new FrameLayout(context);
        button.setContentDescription(label);
        button.setBackground(Nator.states(
                Nator.roundedStroke(context, Nator.GHOST_BG, 10, 1, Nator.GHOST_BORDER),
                Nator.roundedStroke(context, Nator.alpha(0xFFE6E1E5, 0.2f), 10, 1, Nator.alpha(0xFFE6E1E5, 0.24f))));
        Nator.focusable(button, 10);
        TextView glyph = Nator.icon(context, icon, 20, Nator.HEADING);
        button.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    public static TextView glyphOf(FrameLayout iconButton) {
        return (TextView) iconButton.getChildAt(0);
    }

    /** Keeps D-pad focus inside the card: the views behind it stay focusable. */
    public static void trapFocus(View view, boolean up, boolean left, boolean right, boolean down) {
        if (view.getId() == View.NO_ID) {
            view.setId(View.generateViewId());
        }
        int self = view.getId();
        if (up) {
            view.setNextFocusUpId(self);
        }
        if (left) {
            view.setNextFocusLeftId(self);
        }
        if (right) {
            view.setNextFocusRightId(self);
        }
        if (down) {
            view.setNextFocusDownId(self);
        }
    }
}
