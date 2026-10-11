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

import com.ztv.player.data.EpgNowNext;
import com.ztv.player.data.XtreamEpgProgram;
import com.ztv.player.data.XtreamMediaItem;
import com.ztv.player.ui.PosterLoader;
import com.ztv.player.ui.PosterUrls;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * `app-channel-list-item`: 38px logo shell, 15.36px/600 name, the EPG line + timeline
 * when the provider has one, the trailing favorite star, and the shared selection recipe
 * on the playing channel.
 */
public final class ChannelRowAdapter extends RecyclerView.Adapter<ChannelRowAdapter.Holder> {
    public interface Listener {
        void onChannel(XtreamMediaItem item, int position);

        void onToggleFavorite(XtreamMediaItem item, int position);

        void onRowFocused(XtreamMediaItem item, int position);
    }

    private final Context context;
    private final Catalog catalog;
    private final Listener listener;
    private final List<XtreamMediaItem> items = new ArrayList<>();
    private Set<Integer> favoriteIds;
    private int playingId = -1;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChannelRowAdapter(Context context, Catalog catalog, Set<Integer> favoriteIds, Listener listener) {
        this.context = context;
        this.catalog = catalog;
        this.favoriteIds = favoriteIds;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setItems(List<XtreamMediaItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public List<XtreamMediaItem> items() {
        return items;
    }

    public void setFavoriteIds(Set<Integer> ids) {
        favoriteIds = ids;
        notifyDataSetChanged();
    }

    public void setPlaying(int id) {
        int old = indexOf(playingId);
        playingId = id;
        if (old >= 0) {
            notifyItemChanged(old);
        }
        int now = indexOf(id);
        if (now >= 0) {
            notifyItemChanged(now);
        }
    }

    public int playingId() {
        return playingId;
    }

    public int indexOf(int id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id() == id) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        Holder holder = new Holder(context);
        // Rows span the list width; the layout manager supplies its own params type (pitfall 2).
        holder.itemView.setLayoutParams(((androidx.recyclerview.widget.RecyclerView) parent).getLayoutManager()
                .generateLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)));
        return holder;
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        XtreamMediaItem item = items.get(position);
        holder.boundId = item.id();
        holder.name.setText(item.title());
        String url = PosterUrls.isUsable(item.imageUrl()) ? item.imageUrl() : "";
        holder.fallback.setVisibility(url.isEmpty() ? View.VISIBLE : View.GONE);
        PosterLoader.load(url, holder.logo, false);
        boolean favorite = favoriteIds != null && favoriteIds.contains(item.id());
        Nator.setIcon(holder.star, favorite ? "star" : "star_outline");
        holder.active = item.id() == playingId;
        holder.style(holder.itemView.isFocused());
        bindEpg(holder, catalog.cachedEpg(item.id()));
        if (catalog.cachedEpg(item.id()) == null) {
            int id = item.id();
            catalog.epg(id, programs -> {
                if (holder.boundId == id) {
                    bindEpg(holder, programs);
                }
            });
        }
        holder.itemView.setOnClickListener(v -> listener.onChannel(item, holder.getBindingAdapterPosition()));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onToggleFavorite(item, holder.getBindingAdapterPosition());
            return true;
        });
        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                listener.onToggleFavorite(item, holder.getBindingAdapterPosition());
                return true;
            }
            return false;
        });
        holder.focusHook = () -> listener.onRowFocused(item, holder.getBindingAdapterPosition());
    }

    private void bindEpg(Holder holder, List<XtreamEpgProgram> programs) {
        long now = System.currentTimeMillis() / 1000L;
        int index = programs == null ? -1 : EpgNowNext.nowIndex(programs, now);
        if (index < 0) {
            holder.program.setVisibility(View.GONE);
            holder.timeline.setVisibility(View.GONE);
            return;
        }
        XtreamEpgProgram current = programs.get(index);
        holder.program.setVisibility(View.VISIBLE);
        holder.program.setText(current.title());
        if (current.hasTimestamps()) {
            holder.timeline.setVisibility(View.VISIBLE);
            holder.start.setText(clock.format(new Date(current.startEpochSeconds() * 1000L)));
            holder.end.setText(clock.format(new Date(current.endEpochSeconds() * 1000L)));
            int percent = EpgNowNext.progressPercent(current, now);
            ((LinearLayout.LayoutParams) holder.fill.getLayoutParams()).weight = percent;
            ((LinearLayout.LayoutParams) holder.rest.getLayoutParams()).weight = Math.max(0, 100 - percent);
            holder.fill.requestLayout();
        } else {
            holder.timeline.setVisibility(View.GONE);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView fallback;
        final TextView name;
        final TextView program;
        final LinearLayout timeline;
        final TextView start;
        final TextView end;
        final View fill;
        final View rest;
        final TextView star;
        boolean active;
        int boundId;
        Runnable focusHook;

        Holder(Context context) {
            super(new LinearLayout(context));
            LinearLayout row = (LinearLayout) itemView;
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Nator.pad(row, 12, 8, 10, 8);
            row.setMinimumHeight(Nator.px(context, 52));
            row.setFocusable(true);
            row.setClickable(true);

            // .channel-logo-shell: 38px, 10px radius, grey 12% wash + inner hairline.
            FrameLayout shell = new FrameLayout(context);
            GradientDrawable shellBg = Nator.roundedStroke(context, 0x1F7F7F7F, 10, 1, 0x14FFFFFF);
            shell.setBackground(shellBg);
            shell.setClipToOutline(true);
            logo = new ImageView(context);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int inset = Nator.px(context, 6);
            logo.setPadding(inset, inset, inset, inset);
            shell.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            fallback = Nator.icon(context, "live_tv", 20, Nator.alpha(Nator.ON_SURFACE, 0.5f));
            shell.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
            row.addView(shell, Nator.margins(context, Nator.size(context, 38, 38), 0, 0, 12, 0));

            LinearLayout texts = Nator.column(context);
            name = Nator.line(context, "", 15.36f, Nator.W600, Nator.ON_SURFACE);
            texts.addView(name);
            program = Nator.line(context, "", 12.8f, Nator.W400, Nator.BODY);
            program.setVisibility(View.GONE);
            texts.addView(program, Nator.margins(context, Nator.wrap(), 0, 3, 0, 0));
            timeline = Nator.row(context);
            start = Nator.text(context, "", 11, Nator.W500, Nator.BODY);
            end = Nator.text(context, "", 11, Nator.W500, Nator.BODY);
            LinearLayout track = Nator.row(context);
            track.setBackground(Nator.pill(context, 0x477F7F7F));
            fill = new View(context);
            fill.setBackground(Nator.pill(context, Nator.SELECTION));
            rest = new View(context);
            track.addView(fill, new LinearLayout.LayoutParams(0, -1, 0));
            track.addView(rest, new LinearLayout.LayoutParams(0, -1, 100));
            timeline.addView(start, Nator.margins(context, Nator.wrap(), 0, 0, 8, 0));
            timeline.addView(track, new LinearLayout.LayoutParams(0, Nator.px(context, 4), 1f));
            timeline.addView(end, Nator.margins(context, Nator.wrap(), 8, 0, 0, 0));
            timeline.setVisibility(View.GONE);
            texts.addView(timeline, Nator.margins(context, Nator.matchWidth(), 0, 4, 0, 0));
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

            star = Nator.icon(context, "star_outline", 18, Nator.ON_SURFACE);
            row.addView(star, Nator.margins(context, Nator.size(context, 30, 30), 8, 0, 0, 0));

            row.setOnFocusChangeListener((v, focused) -> {
                style(focused);
                if (focused && focusHook != null) {
                    focusHook.run();
                }
            });
        }

        void style(boolean focused) {
            Context context = itemView.getContext();
            if (active) {
                itemView.setBackground(Nator.selection(context, 12));
                name.setTextColor(Nator.SELECTION);
                star.setTextColor(Nator.SELECTION);
                itemView.setTranslationY(-Nator.fpx(context, 1));
            } else {
                itemView.setBackground(focused ? Nator.rounded(context, Nator.alpha(Nator.ON_SURFACE, 0.08f), 12) : null);
                name.setTextColor(Nator.ON_SURFACE);
                star.setTextColor(Nator.ON_SURFACE);
                itemView.setTranslationY(0f);
            }
            itemView.setForeground(focused ? Nator.focusRing(context, 12) : null);
        }
    }
}
