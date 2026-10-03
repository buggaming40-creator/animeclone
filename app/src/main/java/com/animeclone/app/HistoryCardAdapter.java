package com.animeclone.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kartu riwayat horizontal di Beranda: ketuk = lanjutkan nonton (resume). */
public class HistoryCardAdapter extends RecyclerView.Adapter<HistoryCardAdapter.VH> {

    public interface OnPick { void onPick(HistoryItem item); }

    private static final Pattern EP_NUM = Pattern.compile("(\\d+)");

    private final List<HistoryItem> items = new ArrayList<>();
    private final OnPick pick;

    public HistoryCardAdapter(OnPick pick) { this.pick = pick; }

    public void setItems(List<HistoryItem> list) {
        items.clear();
        if (list != null) {
            int n = Math.min(list.size(), 10);
            for (int i = 0; i < n; i++) items.add(list.get(i));
        }
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HistoryItem it = items.get(position);
        h.title.setText(it.title == null ? "" : it.title);
        h.badge.setText(epLabel(it.epTitle));
        ImageLoader.load(it.thumb, h.thumb);
        // Sisa/total waktu + bilah kemajuan — hanya bila durasi tercatat.
        if (it.durMs > 0) {
            h.time.setText(Utils.clock(it.posMs) + " / " + Utils.clock(it.durMs));
            h.time.setVisibility(View.VISIBLE);
            h.progress.setProgress(it.percent());
            h.progress.setVisibility(View.VISIBLE);
        } else {
            h.time.setVisibility(View.GONE);
            h.progress.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(x -> { if (pick != null) pick.onPick(it); });
    }

    @Override public int getItemCount() { return items.size(); }

    /** "Episode 13 Subtitle Indonesia" -> "Eps 13". */
    private static String epLabel(String epTitle) {
        if (epTitle == null) return "Eps";
        Matcher m = EP_NUM.matcher(epTitle);
        return m.find() ? "Eps " + m.group(1) : "Eps";
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView badge, title, time;
        final android.widget.ProgressBar progress;
        VH(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.cardThumb);
            badge = v.findViewById(R.id.cardBadge);
            title = v.findViewById(R.id.cardTitle);
            time = v.findViewById(R.id.cardTime);
            progress = v.findViewById(R.id.cardProgress);
        }
    }
}
