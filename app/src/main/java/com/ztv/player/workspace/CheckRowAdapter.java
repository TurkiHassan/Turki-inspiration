package com.ztv.player.workspace;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Checkbox rows for the Kids mode picker: label, a small count, and on / partial / off. */
final class CheckRowAdapter extends RecyclerView.Adapter<CheckRowAdapter.Holder> {
    enum State { ON, PARTIAL, OFF }

    static final class Row {
        final String key;
        final String label;
        final String meta;
        final State state;

        Row(String key, String label, String meta, State state) {
            this.key = key;
            this.label = label;
            this.meta = meta;
            this.state = state;
        }
    }

    interface Listener {
        void onRow(Row row, int position);
    }

    private final Context context;
    private final Listener onClick;
    private final Listener onFocus;
    private final List<Row> rows = new ArrayList<>();
    private String selectedKey;

    CheckRowAdapter(Context context, Listener onClick, Listener onFocus) {
        this.context = context;
        this.onClick = onClick;
        this.onFocus = onFocus;
    }

    void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    void replace(int position, Row row) {
        rows.set(position, row);
        notifyItemChanged(position);
    }

    List<Row> rows() {
        return rows;
    }

    void setSelectedKey(String key) {
        int previous = indexOf(selectedKey);
        selectedKey = key;
        int next = indexOf(key);
        if (previous >= 0) {
            notifyItemChanged(previous);
        }
        if (next >= 0 && next != previous) {
            notifyItemChanged(next);
        }
    }

    private int indexOf(String key) {
        for (int i = 0; key != null && i < rows.size(); i++) {
            if (rows.get(i).key.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        LinearLayout row = Nator.row(context);
        Nator.pad(row, 10, 0, 12, 0);
        Nator.focusable(row, 8);
        row.setMinimumHeight(Nator.px(context, 40));
        TextView box = Nator.icon(context, "check", 14, 0xFF002F65);
        row.addView(box, Nator.margins(context, Nator.size(context, 18, 18), 0, 0, 12, 0));
        TextView label = Nator.line(context, "", 14, Nator.W500, Nator.ON_SURFACE_VARIANT);
        label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView meta = Nator.line(context, "", 12, Nator.W500, Nator.MUTED);
        row.addView(meta, Nator.margins(context, Nator.wrap(), 10, 0, 0, 0));
        return new Holder(row, box, label, meta);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        Row row = rows.get(position);
        holder.label.setText(row.label);
        holder.meta.setText(row.meta == null ? "" : row.meta);
        holder.box.setText(row.state == State.ON ? Nator.glyph("check") : row.state == State.PARTIAL ? Nator.glyph("remove") : "");
        holder.box.setBackground(row.state == State.OFF
                ? Nator.roundedStroke(context, 0, 2, 2, Nator.ON_SURFACE_VARIANT)
                : Nator.rounded(context, 0xFFABC7FF, 2));
        boolean selected = row.key.equals(selectedKey);
        holder.itemView.setBackground(Nator.states(
                selected ? Nator.rounded(context, Nator.SELECTION_SURFACE, 8) : new GradientDrawable(),
                Nator.rounded(context, selected ? Nator.SELECTION_SURFACE_STRONG : Nator.HOVER_OVERLAY, 8)));
        holder.label.setTextColor(selected ? Nator.SELECTION : Nator.ON_SURFACE_VARIANT);
        holder.itemView.setOnClickListener(v -> {
            int at = holder.getBindingAdapterPosition();
            if (at >= 0) {
                onClick.onRow(rows.get(at), at);
            }
        });
        holder.itemView.setOnFocusChangeListener((v, focused) -> {
            if (!focused || onFocus == null) {
                return;
            }
            // Focus can move during a layout pass; change the adapters after it.
            v.post(() -> {
                int at = holder.getBindingAdapterPosition();
                if (v.hasFocus() && at >= 0) {
                    onFocus.onRow(rows.get(at), at);
                }
            });
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView box;
        final TextView label;
        final TextView meta;

        Holder(View itemView, TextView box, TextView label, TextView meta) {
            super(itemView);
            this.box = box;
            this.label = label;
            this.meta = meta;
        }
    }
}
