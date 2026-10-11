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

import java.util.ArrayList;
import java.util.List;

/**
 * `app-dashboard-rail` cards: `rail__art` posters (172px, 12px radius, hairline) with a
 * 13.6px/600 title, channel tiles for the live rails, and the progress capsule on
 * Continue Watching.
 */
public final class RailCardAdapter extends RecyclerView.Adapter<RailCardAdapter.Holder> {
    public static final class Card {
        public final XtreamMediaItem item;
        public final String subtitle;
        public final int percent;

        public Card(XtreamMediaItem item, String subtitle, int percent) {
            this.item = item;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.percent = percent;
        }
    }

    public interface Listener {
        void onOpen(Card card, int position);

        default void onMenu(Card card, int position) {
        }
    }

    private final Context context;
    private final List<Card> cards = new ArrayList<>();
    private final Listener listener;
    private final boolean channels;
    private final int width;

    public RailCardAdapter(Context context, boolean channels, int cssWidth, Listener listener) {
        this.context = context;
        this.channels = channels;
        this.width = Nator.px(context, cssWidth);
        this.listener = listener;
    }

    public void setCards(List<Card> newCards) {
        cards.clear();
        cards.addAll(newCards);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new Holder(context, channels, width);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        Card card = cards.get(position);
        String url = PosterUrls.isUsable(card.item.imageUrl()) ? card.item.imageUrl() : "";
        holder.fallback.setVisibility(url.isEmpty() ? View.VISIBLE : View.GONE);
        Nator.setIcon(holder.fallback, channels ? "live_tv" : card.item.type() == XtreamContentType.SERIES
                || card.item.type() == XtreamContentType.EPISODE ? "tv" : "movie");
        PosterLoader.load(url, holder.image, false);
        holder.title.setText(card.item.title());
        holder.subtitle.setText(card.subtitle);
        holder.subtitle.setVisibility(card.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        holder.track.setVisibility(card.percent > 0 ? View.VISIBLE : View.GONE);
        if (card.percent > 0) {
            ((LinearLayout.LayoutParams) holder.fill.getLayoutParams()).weight = card.percent;
            ((LinearLayout.LayoutParams) holder.rest.getLayoutParams()).weight = 100 - card.percent;
            holder.fill.requestLayout();
        }
        holder.itemView.setOnClickListener(v -> listener.onOpen(card, holder.getBindingAdapterPosition()));
        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                listener.onMenu(card, holder.getBindingAdapterPosition());
                return true;
            }
            return false;
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView fallback;
        final TextView title;
        final TextView subtitle;
        final LinearLayout track;
        final View fill;
        final View rest;

        Holder(Context context, boolean channels, int width) {
            super(new LinearLayout(context));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setFocusable(true);
            root.setClickable(true);
            root.setClipChildren(false);

            FrameLayout art = new FrameLayout(context);
            float radius = 12;
            art.setBackground(Nator.roundedStroke(context, channels ? Nator.THUMB_BG : Nator.WIDGET_BG, radius, 1, 0x0DFFFFFF));
            art.setClipToOutline(true);
            image = new ImageView(context);
            image.setScaleType(channels ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
            if (channels) {
                int inset = Nator.px(context, 22);
                image.setPadding(inset, inset, inset, inset);
            }
            art.addView(image, new FrameLayout.LayoutParams(-1, -1));
            fallback = Nator.icon(context, channels ? "live_tv" : "movie", 36, Nator.alpha(Nator.ON_SURFACE, 0.3f));
            art.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
            track = Nator.row(context);
            track.setBackground(Nator.pill(context, 0x66000000));
            fill = new View(context);
            fill.setBackground(Nator.pill(context, Nator.SELECTION));
            rest = new View(context);
            track.addView(fill, new LinearLayout.LayoutParams(0, -1, 0));
            track.addView(rest, new LinearLayout.LayoutParams(0, -1, 100));
            FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams(-1, Nator.px(context, 4), Gravity.BOTTOM);
            trackParams.setMargins(Nator.px(context, 10), 0, Nator.px(context, 10), Nator.px(context, 10));
            art.addView(track, trackParams);
            int height = channels ? Math.round(width * 9f / 16f) : Math.round(width * 1.5f);
            root.addView(art, new LinearLayout.LayoutParams(width, height));

            title = Nator.line(context, "", 13.6f, Nator.W600, Nator.HEADING);
            root.addView(title, Nator.margins(context, new LinearLayout.LayoutParams(width, -2), 2, 8, 2, 0));
            subtitle = Nator.line(context, "", 11.5f, Nator.W400, Nator.EYEBROW);
            root.addView(subtitle, Nator.margins(context, new LinearLayout.LayoutParams(width, -2), 2, 3, 2, 0));

            Nator.liftOnFocus(root, root, 3);
            View.OnFocusChangeListener lift = root.getOnFocusChangeListener();
            root.setOnFocusChangeListener((v, focused) -> {
                art.setForeground(focused ? Nator.focusRing(context, radius) : null);
                if (lift != null) {
                    lift.onFocusChange(v, focused);
                }
            });
            root.setBackground(new GradientDrawable());
        }
    }
}
