package com.ztv.player.workspace;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ztv.player.ui.Tr;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * IPTVnator's design system, ported value-for-value from its dark theme
 * (apps/web/src/m3-theme.scss + the component stylesheets, measured from the running app).
 *
 * <p>Every size in this class is written in IPTVnator CSS pixels as measured on a 1280x720
 * viewport. {@link #px} maps them to device pixels with K = 0.75 dp/px, so a 1080p TV
 * (960x540 dp) shows exactly the layout a 1280x720 browser window shows.
 */
public final class Nator {
    /** dp per IPTVnator CSS pixel (1280 css px -> 960 dp). */
    public static final float K = 0.75f;

    // ─── Surfaces (dark theme tokens) ────────────────────────────────────────
    public static final int SHELL_BG = 0xFF0D0F12;
    public static final int RAIL_BG = 0xFF111318;
    public static final int HEADER_BG = 0xFF111318;
    public static final int CONTENT_BG = 0xFF161A22;
    public static final int WIDGET_BG = 0xFF1E2330;
    public static final int WIDGET_HEADER_BG = 0xFF232840;
    public static final int CARD_HOVER_BG = 0xFF252B3A;
    public static final int THUMB_BG = 0xFF292A2C;
    public static final int REFINE_BG = 0xFF1F2022;
    public static final int MENU_BG = 0xFF272A31;

    // ─── Text ───────────────────────────────────────────────────────────────
    public static final int ON_SURFACE = 0xFFE3E2E6;
    public static final int ON_SURFACE_VARIANT = 0xFFE0E2EC;
    public static final int HEADING = 0xFFD8DCE8;
    public static final int BODY = 0xFF8B93A8;
    public static final int MUTED = 0xFF5C6478;
    public static final int EYEBROW = 0xFF6B7494;
    public static final int DESCRIPTION = 0xFFC7CCDA;

    // ─── Selection / focus ──────────────────────────────────────────────────
    public static final int SELECTION = 0xFF78ADFF;
    public static final int SELECTION_ON = 0xFF071225;
    public static final int SELECTION_SURFACE = alpha(SELECTION, 0.18f);
    public static final int SELECTION_SURFACE_STRONG = alpha(SELECTION, 0.28f);
    public static final int SELECTION_BORDER = alpha(SELECTION, 0.62f);
    public static final int SELECTION_GLOW = alpha(SELECTION, 0.44f);
    public static final int FOCUS_RING = 0xFF8CBAFF;
    public static final int HOVER_OVERLAY = 0x14FFFFFF;

    // ─── Lines ──────────────────────────────────────────────────────────────
    public static final int SEPARATOR = 0x24FFFFFF;
    public static final int OUTLINE_VARIANT = 0xFF44474E;
    public static final int OUTLINE = 0xFF8E9099;
    public static final int SEARCH_BG = 0x0DFFFFFF;
    public static final int SEARCH_BORDER = 0x17FFFFFF;

    // ─── Accents ────────────────────────────────────────────────────────────
    public static final int RATING_GREEN = 0xFF4ADE80;
    public static final int RATING_GOLD = 0xFFFFCF5C;
    public static final int CTA_BG = 0xFFF2F4F8;
    public static final int CTA_FG = 0xFF0A0E14;
    public static final int CTA_META = 0xFF5A6274;
    public static final int LIVE_COLOR = 0xFFFF8F8F;
    public static final int LIVE_BADGE = 0xFFD32F2F;
    public static final int PLAYER_ACCENT = 0xFF3474E8;
    public static final int PLAYER_TEXT = 0xFFE7ECF3;
    public static final int PLAYER_MUTED = 0xFF6B7384;
    public static final int TYPE_MOVIE = 0xFF42A5F5;
    public static final int TYPE_SERIES = 0xFFAB47BC;
    public static final int TYPE_LIVE = 0xFFEF5350;
    public static final int BADGE_TEXT = 0xFF5C6478;
    public static final int BADGE_BG = alpha(0xFF5C6478, 0.18f);
    public static final int CHIP_BG = alpha(0xFF292A2C, 0.76f);
    public static final int META_CHIP_BG = alpha(0xFFE6E1E5, 0.10f);
    public static final int GHOST_BG = alpha(0xFFE6E1E5, 0.12f);
    public static final int GHOST_BORDER = alpha(0xFFE6E1E5, 0.14f);
    public static final int SUCCESS = 0xFF4ADE80;
    public static final int ERROR = 0xFFFFB4AB;

    // ─── Typography ─────────────────────────────────────────────────────────
    public static final int W400 = 400;
    public static final int W500 = 500;
    public static final int W600 = 600;
    public static final int W700 = 700;

    private static Typeface regular;
    private static Typeface medium;
    private static Typeface semiBold;
    private static Typeface bold;
    private static Typeface icons;
    private static final Map<String, String> ICON_GLYPHS = new HashMap<>();

    private Nator() {
    }

    /** Loads the bundled DM Sans faces and the Material Icons font + ligature map once. */
    public static synchronized void init(Context context) {
        if (regular != null) {
            return;
        }
        AssetManager assets = context.getAssets();
        regular = Typeface.createFromAsset(assets, "fonts/DMSans-Regular.ttf");
        medium = Typeface.createFromAsset(assets, "fonts/DMSans-Medium.ttf");
        semiBold = Typeface.createFromAsset(assets, "fonts/DMSans-SemiBold.ttf");
        bold = Typeface.createFromAsset(assets, "fonts/DMSans-Bold.ttf");
        icons = Typeface.createFromAsset(assets, "fonts/MaterialIcons-Regular.ttf");
        try (InputStream in = assets.open("fonts/MaterialIcons-Regular.json")) {
            byte[] bytes = new byte[in.available()];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            JSONObject map = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
            Iterator<String> keys = map.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int code = Integer.parseInt(map.getString(key), 16);
                ICON_GLYPHS.put(key, new String(Character.toChars(code)));
            }
        } catch (Exception ignored) {
            // Icons fall back to blank glyphs; text still renders.
        }
    }

    public static Typeface face(int weight) {
        if (weight >= 700) {
            return bold;
        }
        if (weight >= 600) {
            return semiBold;
        }
        if (weight >= 500) {
            return medium;
        }
        return regular;
    }

    public static Typeface iconFace() {
        return icons;
    }

    // ─── Units ──────────────────────────────────────────────────────────────

    /** IPTVnator CSS px -> device px. */
    public static int px(Context context, float css) {
        return Math.round(css * K * context.getResources().getDisplayMetrics().density);
    }

    public static float fpx(Context context, float css) {
        return css * K * context.getResources().getDisplayMetrics().density;
    }

    public static int alpha(int color, float alpha) {
        return Color.argb(Math.round(Color.alpha(color) * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    // ─── Text & icons ───────────────────────────────────────────────────────

    public static TextView text(Context context, CharSequence value, float cssSize, int weight, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setIncludeFontPadding(false);
        view.setTypeface(face(weight));
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, cssSize * K);
        view.setTextColor(color);
        return view;
    }

    public static TextView line(Context context, CharSequence value, float cssSize, int weight, int color) {
        TextView view = text(context, value, cssSize, weight, color);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        // Labels start at the reading-order start edge, whatever script they are in.
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return view;
    }

    /** Sets a CSS line-height (px) on a text view. */
    public static void lineHeight(TextView view, float cssLineHeight) {
        Context context = view.getContext();
        float target = fpx(context, cssLineHeight);
        float font = view.getPaint().getFontMetrics(null);
        view.setLineSpacing(Math.max(0f, target - font), 1f);
    }

    public static void letterSpacing(TextView view, float cssEm) {
        view.setLetterSpacing(cssEm);
    }

    public static String glyph(String iconName) {
        String glyph = ICON_GLYPHS.get(iconName);
        return glyph == null ? "" : glyph;
    }

    /** A Material Icons glyph rendered exactly as IPTVnator's mat-icon (font-size = box size). */
    public static TextView icon(Context context, String name, float cssSize, int color) {
        TextView view = new TextView(context);
        view.setTypeface(icons);
        view.setText(glyph(name));
        view.setIncludeFontPadding(false);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, cssSize * K);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        mirror(view, name);
        return view;
    }

    public static void setIcon(TextView view, String name) {
        view.setText(glyph(name));
        mirror(view, name);
    }

    private static final Set<String> DIRECTIONAL = new HashSet<>(Arrays.asList(
            "arrow_back", "arrow_forward", "chevron_left", "chevron_right", "navigate_next", "navigate_before",
            "keyboard_arrow_left", "keyboard_arrow_right", "arrow_back_ios", "arrow_forward_ios", "open_in_new",
            "playlist_play", "logout", "login", "reply", "redo", "undo", "sort", "notes", "subject", "segment"));

    /** Arrows and other pointing icons face the reading direction. */
    private static void mirror(TextView view, String name) {
        view.setScaleX(Tr.arabic() && DIRECTIONAL.contains(name) ? -1f : 1f);
    }

    // ─── Shapes ─────────────────────────────────────────────────────────────

    public static GradientDrawable rounded(Context context, int fill, float cssRadius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(fpx(context, cssRadius));
        return drawable;
    }

    public static GradientDrawable roundedStroke(Context context, int fill, float cssRadius, float cssStroke, int stroke) {
        GradientDrawable drawable = rounded(context, fill, cssRadius);
        drawable.setStroke(Math.max(1, px(context, cssStroke)), stroke);
        return drawable;
    }

    public static GradientDrawable pill(Context context, int fill) {
        return rounded(context, fill, 999f);
    }

    public static GradientDrawable oval(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        return drawable;
    }

    /**
     * IPTVnator's shared selection recipe: 135deg gradient strong -> surface, a 1px
     * selection border, plus the inset 1px ring (drawn as a second stroke).
     */
    public static Drawable selection(Context context, float cssRadius) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{SELECTION_SURFACE_STRONG, SELECTION_SURFACE});
        fill.setCornerRadius(fpx(context, cssRadius));
        fill.setStroke(Math.max(1, px(context, 1.5f)), SELECTION_BORDER);
        return fill;
    }

    /** Rail-link active recipe (145deg instead of 135deg). */
    public static Drawable railSelection(Context context) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{SELECTION_SURFACE_STRONG, SELECTION_SURFACE});
        fill.setCornerRadius(fpx(context, 14));
        fill.setStroke(Math.max(1, px(context, 1.2f)), SELECTION_BORDER);
        return fill;
    }

    /** 2px --app-focus-ring outline, drawn inside the element (IPTVnator's negative-offset ring). */
    public static GradientDrawable focusRing(Context context, float cssRadius) {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(fpx(context, cssRadius));
        ring.setStroke(Math.max(2, px(context, 2.5f)), FOCUS_RING);
        return ring;
    }

    /** Foreground that shows the keyboard focus ring only while the view is focused. */
    public static Drawable focusForeground(Context context, float cssRadius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focusRing(context, cssRadius));
        states.addState(new int[]{}, new GradientDrawable());
        return states;
    }

    /** Background swap between rest and focused (IPTVnator :hover == TV focus). */
    public static Drawable states(Drawable rest, Drawable focused) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, rest);
        return states;
    }

    public static Drawable layers(Drawable... drawables) {
        return new LayerDrawable(drawables);
    }

    /** Makes a view a D-pad stop with IPTVnator's focus ring. */
    public static void focusable(View view, float cssRadius) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setClickable(true);
        view.setForeground(focusForeground(view.getContext(), cssRadius));
    }

    /** mat-card:hover — lift 3px and cast the soft card shadow, used as the TV focus state. */
    public static void liftOnFocus(View view, View target, float cssLift) {
        Context context = view.getContext();
        float lift = -fpx(context, cssLift);
        float z = fpx(context, 8);
        View.OnFocusChangeListener previous = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((v, focused) -> {
            target.animate().translationY(focused ? lift : 0f).translationZ(focused ? z : 0f)
                    .setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
            if (previous != null) {
                previous.onFocusChange(v, focused);
            }
        });
    }

    // ─── Layout helpers ─────────────────────────────────────────────────────

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams size(Context context, float cssWidth, float cssHeight) {
        return new LinearLayout.LayoutParams(
                cssWidth < 0 ? (int) cssWidth : px(context, cssWidth),
                cssHeight < 0 ? (int) cssHeight : px(context, cssHeight));
    }

    public static LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    public static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /** Margins by reading order: "left" is the start side, so Arabic mirrors them. */
    public static <T extends LinearLayout.LayoutParams> T margins(Context context, T params,
                                                               float left, float top, float right, float bottom) {
        params.setMargins(0, px(context, top), 0, px(context, bottom));
        params.setMarginStart(px(context, left));
        params.setMarginEnd(px(context, right));
        return params;
    }

    /** Padding by reading order: "left" is the start side, so Arabic mirrors it. */
    public static void pad(View view, float left, float top, float right, float bottom) {
        Context context = view.getContext();
        view.setPaddingRelative(px(context, left), px(context, top), px(context, right), px(context, bottom));
    }

    public static View spacer(Context context) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return view;
    }

    public static View hairline(Context context, int color) {
        View view = new View(context);
        view.setBackgroundColor(color);
        return view;
    }

    /** `app-count-badge`: 12.48px/500 muted pill. */
    public static TextView countBadge(Context context, CharSequence value) {
        TextView badge = text(context, value, 12.48f, W500, BADGE_TEXT);
        badge.setBackground(pill(context, BADGE_BG));
        pad(badge, 8, 2, 8, 2);
        badge.setGravity(Gravity.CENTER);
        return badge;
    }

    /** `category-subtitle`/`category-count` chip: 12.48px/500 on the refine surface. */
    public static TextView countChip(Context context, CharSequence value, int textColor) {
        TextView chip = text(context, value, 12.48f, W500, textColor);
        chip.setBackground(pill(context, REFINE_BG));
        pad(chip, 8, 3, 8, 3);
        chip.setGravity(Gravity.CENTER);
        return chip;
    }

    /** `app-meta-chip`: 12px/500 pill on a 10% on-surface wash. */
    public static TextView metaChip(Context context, CharSequence value, int color, int weight) {
        TextView chip = text(context, value, 12f, weight, color);
        chip.setBackground(pill(context, META_CHIP_BG));
        pad(chip, 9, 4, 9, 4);
        return chip;
    }

    public static String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    /** {@link #plural} with Arabic number agreement (see {@link Tr#count}). */
    public static String plural(int count, String singular, String plural,
                                String arOne, String arTwo, String arFew, String arMany) {
        return Tr.count(count, singular, plural, arOne, arTwo, arFew, arMany);
    }
}
