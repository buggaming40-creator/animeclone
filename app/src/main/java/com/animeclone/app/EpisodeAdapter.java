package com.animeclone.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Adapter daftar episode: baris daftar atau sel grid (pil nomor). */
public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {

    public interface OnClick { void onPick(EpisodeItem item); }

    private static final int TYPE_LIST = 0;
    private static final int TYPE_GRID = 1;

    private final List<EpisodeItem> data = new ArrayList<>();
    private final OnClick click;
    private boolean grid;

    public EpisodeAdapter(OnClick click) { this.click = click; }

    /** Ganti mode grid/daftar; holder lama tidak dipakai ulang lintas tipe. */
    public void setGrid(boolean grid) {
        if (this.grid == grid) return;
        this.grid = grid;
        notifyDataSetChanged();
    }

    public boolean isGrid() { return grid; }

    public void submit(List<EpisodeItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) {
        return grid ? TYPE_GRID : TYPE_LIST;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        int layout = (t == TYPE_GRID) ? R.layout.item_episode_grid : R.layout.item_episode;
        View v = LayoutInflater.from(p.getContext()).inflate(layout, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        final EpisodeItem it = data.get(i);
        String num = it.num == null || it.num.isEmpty() ? "EP" : it.num;
        h.num.setText(num);
        if (h.title != null) h.title.setText(it.title);
        if (h.date != null) {
            h.date.setText(it.date);
            // Sel grid: tanggal mungil hanya bila ada datanya.
            if (h.title == null) {
                h.date.setVisibility(it.date == null || it.date.isEmpty()
                        ? View.GONE : View.VISIBLE);
            }
        }
        h.itemView.setOnClickListener(v -> { if (click != null) click.onPick(it); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView num, title, date;
        VH(@NonNull View v) {
            super(v);
            num = v.findViewById(R.id.num);
            // Tata letak grid tidak punya judul — boleh null.
            TextView t;
            try { t = v.findViewById(R.id.title); } catch (Throwable e) { t = null; }
            title = t;
            date = v.findViewById(R.id.date);
        }
    }
}
