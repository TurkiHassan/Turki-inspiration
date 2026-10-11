package com.ztv.player.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ztv.player.R;

public final class ZtvUi {
    public static final int BACKGROUND = Color.rgb(4, 8, 16);
    public static final int SURFACE = Color.rgb(12, 21, 38);
    public static final int SURFACE_FOCUSED = Color.rgb(22, 48, 84);
    public static final int ACCENT = Color.rgb(47, 155, 255);
    public static final int TEXT_PRIMARY = Color.rgb(242, 246, 252);
    public static final int TEXT_SECONDARY = Color.rgb(176, 188, 206);
    public static final int TEXT_MUTED = Color.rgb(116, 130, 150);
    public static final int ERROR = Color.rgb(255, 107, 107);
    public static final int WARNING = Color.rgb(255, 183, 77);

    /** Roboto Medium reads crisper than faux-bold on TV panels. */
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private ZtvUi() {
    }

    public static TextView text(Context context, String text, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) {
            view.setTypeface(MEDIUM);
        }
        return view;
    }

    /** Small, letter-spaced ALL-CAPS label — section headers, badges, hints. */
    public static TextView caps(Context context, String label, int sp, int color) {
        TextView view = text(context, label == null ? "" : label.toUpperCase(), sp, color, true);
        view.setLetterSpacing(0.14f);
        return view;
    }

    /** The circular emblem on its dark disc (login screen). */
    public static ImageView logo(Context context, int heightDp) {
        return brandImage(context, R.drawable.ztv_logo, heightDp);
    }

    /** The circular emblem with its dark disc keyed to transparency, so the luminous graphic
     *  blends into any background with no visible plate. Used in the hub header + sidebar. */
    public static ImageView emblemGlow(Context context, int heightDp) {
        return brandImage(context, R.drawable.ztv_logo_glow, heightDp);
    }

    private static ImageView brandImage(Context context, int drawableRes, int heightDp) {
        ImageView image = new ImageView(context);
        image.setImageResource(drawableRes);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("ZTV");
        image.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, heightDp)));
        return image;
    }

    public static EditText input(Context context, String hint) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setHintTextColor(TEXT_MUTED);
        input.setTextColor(TEXT_PRIMARY);
        input.setTextSize(18);
        input.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        input.setMinHeight(dp(context, 56));
        input.setBackground(inputBackground());
        return input;
    }

    public static Button primaryButton(Context context, String text) {
        Button button = baseButton(context, text);
        button.setTextColor(Color.rgb(2, 9, 18));
        button.setBackground(buttonBackground(ACCENT, Color.WHITE));
        return button;
    }

    public static Button secondaryButton(Context context, String text) {
        Button button = baseButton(context, text);
        button.setTextColor(TEXT_PRIMARY);
        button.setBackground(buttonBackground(SURFACE, SURFACE_FOCUSED));
        return button;
    }

    public static GradientDrawable appBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(4, 8, 16), Color.rgb(8, 17, 36), Color.rgb(5, 10, 22)}
        );
    }

    public static GradientDrawable panelBackground() {
        GradientDrawable drawable = rounded(SURFACE, 22);
        drawable.setStroke(1, Color.rgb(36, 50, 74));
        return drawable;
    }

    public static GradientDrawable overlayBackground() {
        GradientDrawable drawable = rounded(Color.argb(216, 7, 12, 24), 18);
        drawable.setStroke(1, Color.argb(70, 130, 180, 255));
        return drawable;
    }

    public static GradientDrawable pillBackground() {
        GradientDrawable drawable = rounded(Color.argb(46, 47, 155, 255), 999);
        drawable.setStroke(1, Color.argb(120, 47, 155, 255));
        return drawable;
    }

    public static GradientDrawable sidebarBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(8, 16, 32), Color.rgb(6, 12, 24)}
        );
    }

    public static GradientDrawable groupPaneBackground() {
        return rounded(Color.argb(150, 7, 14, 28), 0);
    }

    /** Bottom scrim laid over a poster so the title stays readable on any artwork. */
    public static GradientDrawable posterScrim() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(120, 2, 4, 10), Color.argb(226, 2, 4, 10)}
        );
    }

    /** Neutral rounded block used while real content is loading. */
    public static View skeletonBlock(Context context, int radiusDp) {
        View block = new View(context);
        GradientDrawable drawable = rounded(Color.rgb(15, 24, 42), radiusDp);
        block.setBackground(drawable);
        return block;
    }

    /** Slow breathing pulse for skeleton placeholders. */
    public static void attachPulse(View view) {
        android.animation.ObjectAnimator pulse =
                android.animation.ObjectAnimator.ofFloat(view, View.ALPHA, 0.45f, 0.9f);
        pulse.setDuration(700);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.start();
    }

    /** Shared focus scale animation — one timing curve everywhere keeps motion coherent. */
    public static void animateFocusScale(View view, boolean focused, float focusedScale) {
        animateFocusScale(view, focused, focusedScale, 0f);
    }

    public static void animateFocusScale(View view, boolean focused, float focusedScale, float focusedTranslationZ) {
        view.animate().cancel();
        view.animate()
                .scaleX(focused ? focusedScale : 1f)
                .scaleY(focused ? focusedScale : 1f)
                .translationZ(focused ? focusedTranslationZ : 0f)
                .setDuration(160)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                .start();
    }

    /** Large home-hub tile: subtle diagonal gradient at rest, accent-lit when focused. */
    public static StateListDrawable homeCardBackground() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(24, 54, 98), Color.rgb(11, 24, 46)});
        focused.setCornerRadius(dpF(22));
        focused.setStroke(dpF(2), ACCENT);
        GradientDrawable pressed = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(24, 54, 98), Color.rgb(11, 24, 46)});
        pressed.setCornerRadius(dpF(22));
        pressed.setStroke(dpF(2), ACCENT);
        GradientDrawable normal = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(16, 29, 52), Color.rgb(9, 17, 33)});
        normal.setCornerRadius(dpF(22));
        normal.setStroke(1, Color.argb(56, 130, 180, 255));
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    /** Soft radial glow behind a home-tile icon -- a halo, not a hard circle. */
    public static GradientDrawable iconGlow(int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        // Radius must stay within half the view, or the square bounds show as a hard edge.
        drawable.setGradientRadius(dpF(radiusDp));
        drawable.setColors(new int[]{Color.argb(70, 47, 155, 255), Color.argb(0, 47, 155, 255)});
        return drawable;
    }

    /** Selector for pill chips (seasons etc): filled when focused, outlined when selected. */
    public static StateListDrawable chipSelector() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = rounded(ACCENT, 999);
        GradientDrawable selected = rounded(Color.argb(56, 47, 155, 255), 999);
        selected.setStroke(dpF(2), ACCENT);
        GradientDrawable normal = rounded(Color.argb(160, 13, 22, 40), 999);
        normal.setStroke(1, Color.argb(70, 130, 180, 255));
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_selected}, selected);
        states.addState(new int[]{}, normal);
        return states;
    }

    public static int dpF(int value) {
        return Math.round(value * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }

    public static GradientDrawable posterPlaceholder() {
        GradientDrawable drawable = rounded(Color.rgb(17, 24, 34), 10);
        drawable.setStroke(1, Color.rgb(35, 47, 62));
        return drawable;
    }

    /** A small rounded metadata pill, e.g. a genre / year / rating badge. */
    public static TextView chip(Context context, String label) {
        TextView chip = text(context, label, 12, TEXT_SECONDARY, true);
        chip.setLetterSpacing(0.04f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, 13), dp(context, 6), dp(context, 13), dp(context, 6));
        chip.setBackground(chipBackground());
        return chip;
    }

    /** Clips a view (typically a poster) to rounded corners and gives it a drop shadow. */
    public static void roundWithShadow(View view, int radiusDp, int elevationDp) {
        final float radius = dp(view.getContext(), radiusDp);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
        view.setElevation(dp(view.getContext(), elevationDp));
    }

    /** Thin rounded progress meter: a faint track with an accent fill scaled from the left. */
    public static final class Meter {
        public final FrameLayout track;
        private final View fill;

        private Meter(FrameLayout track, View fill) {
            this.track = track;
            this.fill = fill;
        }

        /** 0-100; anything outside that range hides the meter. */
        public void set(int percent) {
            if (percent < 0 || percent > 100) {
                track.setVisibility(View.GONE);
                return;
            }
            track.setVisibility(View.VISIBLE);
            fill.setScaleX(Math.max(0.02f, percent / 100f));
        }
    }

    public static Meter meter(Context context, int trackColor) {
        FrameLayout track = new FrameLayout(context);
        GradientDrawable trackShape = rounded(trackColor, 999);
        track.setBackground(trackShape);
        track.setClipToOutline(true);
        track.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getHeight() / 2f);
            }
        });
        View fill = new View(context);
        fill.setBackground(rounded(ACCENT, 999));
        fill.setPivotX(0f);
        track.addView(fill, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return new Meter(track, fill);
    }

    /** Darkens a full-bleed artwork backdrop so text on the left stays readable. */
    public static GradientDrawable backdropSideScrim() {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{
                Color.argb(250, 4, 8, 16), Color.argb(226, 5, 10, 22), Color.argb(156, 5, 10, 22)});
    }

    public static GradientDrawable backdropBottomScrim() {
        return new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{
                Color.argb(255, 4, 8, 16), Color.argb(0, 4, 8, 16)});
    }

    /** Small accent star on a dark disc, pinned to a poster corner for favorites. */
    public static ImageView favoriteBadge(Context context) {
        ImageView badge = new ImageView(context);
        badge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(context, 4);
        badge.setPadding(pad, pad, pad, pad);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(Color.argb(200, 4, 8, 16));
        badge.setBackground(disc);
        android.graphics.drawable.Drawable star = context.getDrawable(R.drawable.ic_favorite_24);
        if (star != null) {
            star = star.mutate();
            star.setTint(ACCENT);
            badge.setImageDrawable(star);
        }
        badge.setContentDescription("Favorite");
        return badge;
    }

    /** Transient hint pill (e.g. "Press Back again to exit"). */
    public static TextView hintPill(Context context, String message) {
        TextView pill = text(context, message, 14, TEXT_PRIMARY, true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(context, 22), dp(context, 12), dp(context, 22), dp(context, 12));
        GradientDrawable shape = rounded(Color.argb(236, 12, 21, 38), 999);
        shape.setStroke(1, Color.argb(110, 47, 155, 255));
        pill.setBackground(shape);
        pill.setElevation(dp(context, 8));
        return pill;
    }

    public static GradientDrawable heroBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(9, 16, 34), Color.rgb(5, 9, 20)}
        );
    }

    private static GradientDrawable chipBackground() {
        GradientDrawable drawable = rounded(Color.argb(40, 47, 155, 255), 999);
        drawable.setStroke(1, Color.argb(110, 47, 155, 255));
        return drawable;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout.LayoutParams wrapTop(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = topDp;
        return params;
    }

    public static LinearLayout.LayoutParams matchTop(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = topDp;
        return params;
    }

    public static LinearLayout.LayoutParams fixedTop(Context context, int widthDp, int heightDp, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, widthDp), dp(context, heightDp));
        params.topMargin = dp(context, topDp);
        return params;
    }

    private static Button baseButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(16);
        button.setTypeface(MEDIUM);
        button.setLetterSpacing(0.03f);
        button.setAllCaps(false);
        button.setMinHeight(dp(context, 52));
        button.setFocusable(true);
        button.setOnFocusChangeListener((view, hasFocus) -> animateFocusScale(view, hasFocus, 1.025f, dp(context, 3)));
        return button;
    }

    private static StateListDrawable inputBackground() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = rounded(SURFACE_FOCUSED, 12);
        focused.setStroke(2, ACCENT);
        GradientDrawable normal = rounded(Color.rgb(10, 14, 24), 12);
        normal.setStroke(1, Color.rgb(49, 61, 81));
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    public static StateListDrawable buttonBackground(int normalColor, int focusedColor) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, rounded(focusedColor, 999));
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(focusedColor, 999));
        GradientDrawable normal = rounded(normalColor, 999);
        if (normalColor == SURFACE) {
            normal.setStroke(1, Color.argb(60, 130, 180, 255));
        }
        states.addState(new int[]{}, normal);
        return states;
    }

    private static GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp);
        return drawable;
    }
}
