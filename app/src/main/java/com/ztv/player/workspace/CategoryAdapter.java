package com.ztv.player.workspace;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Context-panel category list (`.nav-item.category-item`): 16px labels, count badges,
 * the shared selection gradient on the selected row and the selection-surface hover.
 */
public final class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {
    public static final class Row {
        public final String key;
        public final String label;
        public final int count;
        public final String icon;

        public Row(String key, String label, int count) {
            this(key, label, count, null);
        }

        public Row(String key, String label, int count, String icon) {
            this.key = key;
            this.label = label;
            this.count = count;
            this.icon = icon;
        }
    }

    public interface Listener {
        void onPick(Row row, int position);
    }

    private final Context context;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private String selectedKey;
    private boolean showCounts = true;
    /** Settings/sources option rows use 14px/500 labels instead of 16px/400. */
    private final boolean compactOptions;

    public CategoryAdapter(Context context, boolean compactOptions, Listener listener) {
        this.context = context;
        this.compactOptions = compactOptions;
        this.listener = listener;
    }

    public void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    public List<Row> rows() {
        return rows;
    }

    public void setSelectedKey(String key) {
        String previous = selectedKey;
        selectedKey = key;
        int oldIndex = indexOf(previous);
        int newIndex = indexOf(key);
        if (oldIndex >= 0) {
            notifyItemChanged(oldIndex);
        }
        if (newIndex >= 0) {
            notifyItemChanged(newIndex);
        }
    }

    public String selectedKey() {
        return selectedKey;
    }

    public int indexOf(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (key.equals(rows.get(i).key)) {
                return i;
            }
        }
        return -1;
    }

    public void setShowCounts(boolean showCounts) {
        this.showCounts = showCounts;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        Holder holder = new Holder(context, compactOptions);
        // Rows span the list width; the layout manager supplies its own params type (pitfall 2).
        holder.itemView.setLayoutParams(((androidx.recyclerview.widget.RecyclerView) parent).getLayoutManager()
                .generateLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)));
        return holder;
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        Row row = rows.get(position);
        holder.label.setText(row.label);
        holder.badge.setText(String.valueOf(row.count));
        holder.badge.setVisibility(showCounts && row.count >= 0 ? View.VISIBLE : View.GONE);
        if (holder.icon != null) {
            holder.icon.setVisibility(row.icon == null ? View.GONE : View.VISIBLE);
            if (row.icon != null) {
                Nator.setIcon(holder.icon, row.icon);
            }
        }
        holder.selected = row.key != null && row.key.equals(selectedKey);
        holder.style(holder.itemView.isFocused());
        holder.itemView.setOnClickListener(v -> listener.onPick(row, holder.getBindingAdapterPosition()));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView label;
        final TextView badge;
        final TextView icon;
        final boolean compact;
        boolean selected;

        Holder(Context context, boolean compact) {
            super(new LinearLayout(context));
            this.compact = compact;
            LinearLayout row = (LinearLayout) itemView;
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            Nator.pad(row, 4, 6, 4, 6);
            row.setFocusable(true);
            row.setClickable(true);
            row.setMinimumHeight(Nator.px(context, compact ? 32 : 33));
            if (compact) {
                icon = Nator.icon(context, "layers", 18, Nator.ON_SURFACE_VARIANT);
                row.addView(icon, Nator.margins(context, Nator.size(context, 18, 18), 1, 0, 10, 0));
                label = Nator.line(context, "", 14, Nator.W500, Nator.ON_SURFACE_VARIANT);
                badge = Nator.text(context, "", 12.8f, Nator.W500, Nator.ON_SURFACE_VARIANT);
            } else {
                icon = null;
                label = Nator.line(context, "", 16, Nator.W400, Nator.ON_SURFACE_VARIANT);
                badge = Nator.countBadge(context, "");
            }
            row.addView(label, Nator.margins(context, new LinearLayout.LayoutParams(0, -2, 1f), 1, 0, 10, 0));
            row.addView(badge, Nator.margins(context, Nator.wrap(), 0, 0, 1, 0));
            row.setOnFocusChangeListener((v, focused) -> style(focused));
        }

        void style(boolean focused) {
            Context context = itemView.getContext();
            float radius = compact ? 12 : 10;
            if (selected) {
                itemView.setBackground(Nator.selection(context, radius));
                label.setTextColor(Nator.SELECTION);
                label.setTypeface(Nator.face(Nator.W600));
                if (compact) {
                    badge.setTextColor(Nator.SELECTION);
                    badge.setTypeface(Nator.face(Nator.W600));
                } else {
                    badge.setTextColor(Nator.SELECTION);
                    badge.setBackground(Nator.pill(context, Nator.SELECTION_SURFACE));
                }
                if (icon != null) {
                    icon.setTextColor(Nator.SELECTION);
                }
                itemView.setTranslationY(-Nator.fpx(context, 1));
            } else {
                itemView.setBackground(focused ? Nator.rounded(context, Nator.SELECTION_SURFACE, radius) : null);
                label.setTextColor(focused ? Nator.ON_SURFACE : Nator.ON_SURFACE_VARIANT);
                label.setTypeface(Nator.face(compact ? Nator.W500 : Nator.W400));
                badge.setTextColor(compact ? Nator.ON_SURFACE_VARIANT : Nator.BADGE_TEXT);
                badge.setTypeface(Nator.face(Nator.W500));
                if (!compact) {
                    badge.setBackground(Nator.pill(context, Nator.BADGE_BG));
                }
                if (icon != null) {
                    icon.setTextColor(Nator.ON_SURFACE_VARIANT);
                }
                itemView.setTranslationY(0f);
            }
            itemView.setForeground(focused ? Nator.focusRing(context, radius) : null);
        }
    }
}
