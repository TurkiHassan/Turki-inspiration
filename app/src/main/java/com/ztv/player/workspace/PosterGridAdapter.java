package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.ztv.player.data.XtreamContentType;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.ui.PosterLoader;
import com.ztv.player.ui.PosterUrls;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * IPTVnator's cover cards: `app-grid-list` mat-card (catalog grids, poster or logo variant)
 * and `app-content-card` (favorites / recently viewed / search, with type badge + date).
 */
public final class PosterGridAdapter extends RecyclerView.Adapter<PosterGridAdapter.Holder> {
    public enum Style {
        /** Catalog grid: rounded 10px thumb, rating badge, muted title under the cover. */
        GRID,
        /** Collection/search card: type badge, title + date line, optional remove button. */
        CONTENT
    }

    public interface Listener {
        void onOpen(XtreamMediaItem item, int position);

        default void onRemove(XtreamMediaItem item, int position) {
        }

        default void onMenu(XtreamMediaItem item, int position) {
        }
    }

    public interface ProgressSource {
        int percent(XtreamMediaItem item);
    }

    private final Context context;
    private final Style style;
    private final Listener listener;
    private final List<XtreamMediaItem> items = new ArrayList<>();
    private int cardWidth;
    private boolean showTitles = true;
    private boolean removable;
    private ProgressSource progressSource;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH);

    public PosterGridAdapter(Context context, Style style, int cardWidthPx, Listener listener) {
        this.context = context;
        this.style = style;
        this.cardWidth = cardWidthPx;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setItems(List<XtreamMediaItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public List<XtreamMediaItem> items() {
        return items;
    }

    public void setShowTitles(boolean showTitles) {
        this.showTitles = showTitles;
    }

    public void setRemovable(boolean removable) {
        this.removable = removable;
    }

    public void setProgressSource(ProgressSource progressSource) {
        this.progressSource = progressSource;
    }

    public void removeAt(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    @Override
    public long getItemId(int position) {
        XtreamMediaItem item = items.get(position);
        return ((long) item.type().ordinal() << 40) ^ item.id();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new Holder(context, style);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        XtreamMediaItem item = items.get(position);
        boolean logo = item.type() == XtreamContentType.LIVE;
        int thumbHeight = logo ? Math.round(cardWidth * 89f / 159f) : Math.round(cardWidth * 1.5f);
        holder.art.setLayoutParams(new LinearLayout.LayoutParams(cardWidth, thumbHeight));

        holder.image.setScaleType(logo ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        int inset = logo ? Nator.px(context, 18) : 0;
        holder.image.setPadding(inset, inset, inset, inset);
        String url = PosterUrls.isUsable(item.imageUrl()) ? item.imageUrl() : "";
        holder.placeholder.setVisibility(url.isEmpty() ? View.VISIBLE : View.GONE);
        Nator.setIcon(holder.placeholder, logo ? "live_tv" : item.type() == XtreamContentType.SERIES ? "tv" : "movie");
        PosterLoader.load(url, holder.image, false);

        boolean hasRating = style == Style.GRID && !item.rating().isEmpty();
        holder.rating.setVisibility(hasRating ? View.VISIBLE : View.GONE);
        holder.ratingText.setText(item.rating());

        if (style == Style.CONTENT) {
            holder.typeBadge.setVisibility(View.VISIBLE);
            String type = item.type() == XtreamContentType.LIVE ? "live"
                    : item.type() == XtreamContentType.SERIES ? "series" : "movie";
            holder.typeBadge.setText(type);
            holder.typeBadge.setTextColor(item.type() == XtreamContentType.LIVE ? Nator.TYPE_LIVE
                    : item.type() == XtreamContentType.SERIES ? Nator.TYPE_SERIES : Nator.TYPE_MOVIE);
            holder.remove.setVisibility(removable ? View.VISIBLE : View.GONE);
            holder.date.setVisibility(item.addedAt() > 0 ? View.VISIBLE : View.GONE);
            holder.date.setText(item.addedAt() > 0 ? dateFormat.format(new Date(item.addedAt() * 1000L)) : "");
        }

        int percent = progressSource == null ? 0 : progressSource.percent(item);
        holder.progressTrack.setVisibility(percent > 0 ? View.VISIBLE : View.GONE);
        if (percent > 0) {
            LinearLayout.LayoutParams fill = (LinearLayout.LayoutParams) holder.progressFill.getLayoutParams();
            fill.weight = percent;
            holder.progressFill.setLayoutParams(fill);
            LinearLayout.LayoutParams rest = (LinearLayout.LayoutParams) holder.progressRest.getLayoutParams();
            rest.weight = Math.max(0, 100 - percent);
            holder.progressRest.setLayoutParams(rest);
        }

        holder.title.setText(item.title());
        holder.titleBlock.setVisibility(showTitles || logo || url.isEmpty() ? View.VISIBLE : View.GONE);
        holder.titleBlock.setLayoutParams(new LinearLayout.LayoutParams(cardWidth, -2));

        holder.card.setOnClickListener(v -> listener.onOpen(item, holder.getBindingAdapterPosition()));
        holder.remove.setOnClickListener(v -> listener.onRemove(item, holder.getBindingAdapterPosition()));
        holder.card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                listener.onMenu(item, holder.getBindingAdapterPosition());
                return true;
            }
            return false;
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final FrameLayout art;
        final ImageView image;
        final TextView placeholder;
        final LinearLayout rating;
        final TextView ratingText;
        final TextView typeBadge;
        final View remove;
        final LinearLayout progressTrack;
        final View progressFill;
        final View progressRest;
        final LinearLayout titleBlock;
        final TextView title;
        final TextView date;

        Holder(Context context, Style style) {
            super(new FrameLayout(context));
            FrameLayout root = (FrameLayout) itemView;
            root.setClipChildren(false);
            root.setClipToPadding(false);

            card = Nator.column(context);
            card.setFocusable(true);
            card.setClickable(true);
            card.setClipChildren(false);
            root.addView(card, new FrameLayout.LayoutParams(-2, -2));

            art = new FrameLayout(context);
            float radius = style == Style.GRID ? 10 : 8;
            art.setBackground(Nator.rounded(context, Nator.THUMB_BG, radius));
            art.setClipToOutline(true);
            art.setForeground(Nator.focusForeground(context, radius));
            image = new ImageView(context);
            art.addView(image, new FrameLayout.LayoutParams(-1, -1));
            placeholder = Nator.icon(context, "movie", 40, Nator.alpha(Nator.ON_SURFACE, 0.28f));
            art.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));

            // .rating: top-right, 6px inset, black 85% pill with a green star.
            rating = Nator.row(context);
            rating.setBackground(Nator.rounded(context, 0xD9000000, 6));
            Nator.pad(rating, 5, 2, 5, 2);
            rating.addView(Nator.icon(context, "star", 13, Nator.RATING_GREEN), Nator.margins(context, Nator.size(context, 14, 14), 0, 0, 2, 0));
            ratingText = Nator.text(context, "", 11.52f, Nator.W600, Nator.RATING_GREEN);
            rating.addView(ratingText);
            FrameLayout.LayoutParams ratingParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            ratingParams.setMargins(0, Nator.px(context, 6), 0, 0);
            ratingParams.setMarginStart(0);
            ratingParams.setMarginEnd(Nator.px(context, 6));
            art.addView(rating, ratingParams);

            // .type-badge (content cards): top-left, uppercase 9px/700.
            typeBadge = Nator.text(context, "", 9, Nator.W700, Nator.TYPE_MOVIE);
            typeBadge.setAllCaps(true);
            typeBadge.setBackground(Nator.rounded(context, 0xD9000000, 4));
            Nator.pad(typeBadge, 6, 3, 6, 3);
            typeBadge.setVisibility(View.GONE);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            badgeParams.setMargins(0, Nator.px(context, 6), 0, 0);
            badgeParams.setMarginStart(Nator.px(context, 6));
            badgeParams.setMarginEnd(0);
            art.addView(typeBadge, badgeParams);

            // Remove (favorites): 28px dark circle with a close glyph, top-right.
            FrameLayout removeButton = new FrameLayout(context);
            removeButton.setBackground(Nator.oval(0x80000000));
            removeButton.addView(Nator.icon(context, "close", 18, 0xFFFFFFFF), new FrameLayout.LayoutParams(-1, -1));
            removeButton.setVisibility(View.GONE);
            FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(Nator.px(context, 28), Nator.px(context, 28), Gravity.TOP | Gravity.END);
            removeParams.setMargins(0, Nator.px(context, 4), 0, 0);
            removeParams.setMarginStart(0);
            removeParams.setMarginEnd(Nator.px(context, 4));
            art.addView(removeButton, removeParams);
            remove = removeButton;

            // app-progress-capsule: bottom track with the selection-blue fill.
            progressTrack = Nator.row(context);
            progressTrack.setBackground(Nator.pill(context, 0x66000000));
            progressFill = new View(context);
            progressFill.setBackground(Nator.pill(context, Nator.SELECTION));
            progressRest = new View(context);
            progressTrack.addView(progressFill, new LinearLayout.LayoutParams(0, -1, 0));
            progressTrack.addView(progressRest, new LinearLayout.LayoutParams(0, -1, 100));
            FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams(-1, Nator.px(context, 4), Gravity.BOTTOM);
            trackParams.setMargins(Nator.px(context, 8), 0, Nator.px(context, 8), Nator.px(context, 8));
            art.addView(progressTrack, trackParams);
            progressTrack.setVisibility(View.GONE);

            card.addView(art);

            titleBlock = Nator.column(context);
            if (style == Style.GRID) {
                title = Nator.line(context, "", 12.8f, Nator.W400, Nator.BODY);
                Nator.pad(titleBlock, 8, 21, 8, 14);
                titleBlock.addView(title);
                date = new TextView(context);
            } else {
                title = Nator.line(context, "", 12.8f, Nator.W500, Nator.ON_SURFACE);
                date = Nator.line(context, "", 11.2f, Nator.W400, Nator.alpha(Nator.ON_SURFACE, 0.7f));
                Nator.pad(titleBlock, 10, 8, 10, 10);
                titleBlock.addView(title);
                titleBlock.addView(date, Nator.margins(context, Nator.wrap(), 0, 4, 0, 0));
            }
            card.addView(titleBlock);

            // mat-card:hover — lift + shadow; the title brightens like the hovered card.
            Nator.liftOnFocus(card, card, 3);
            View.OnFocusChangeListener lift = card.getOnFocusChangeListener();
            card.setOnFocusChangeListener((v, focused) -> {
                art.setSelected(focused);
                art.refreshDrawableState();
                art.setForeground(focused ? Nator.focusRing(context, radius) : null);
                if (style == Style.GRID) {
                    title.setTextColor(focused ? Nator.HEADING : Nator.BODY);
                }
                if (lift != null) {
                    lift.onFocusChange(v, focused);
                }
            });
            GradientDrawable none = new GradientDrawable();
            card.setBackground(none);
        }
    }
}
