package com.animeclone.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter beranda ala AnimeLovers: satu RecyclerView dengan baris-baris —
 * seksi Riwayat + kartu horizontal, seksi Genre (pil warna statis),
 * kepala Ongoing + pil filter, grid Rilis Terbaru, dan baris horizontal
 * Sedang Tayang serta Top Rating.
 *
 * Seksi yang datanya kosong tidak pernah ditampilkan (tanpa data karangan).
 * Grid memakai GridLayoutManager; {@link #isFullSpan(int)} memberi tahu
 * LayoutManager baris mana yang harus melebar penuh.
 */
public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnClick { void onPick(AnimeItem item); }
    public interface OnHistoryPick { void onPick(HistoryItem item); }
    public interface OnSectionClick { void onSection(String key); }
    public interface OnGenrePick { void onGenre(String genre); }
    public interface OnFilterPick { void onFilter(String mode); }

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_POSTER = 1;
    private static final int TYPE_ROW = 2;
    private static final int TYPE_HISTORY_ROW = 3;
    private static final int TYPE_GENRE = 4;
    private static final int TYPE_ONGOING_HEAD = 5;

    /** Mode filter ongoing: "all" / "anime" / "donghua". */
    public static final String FILTER_ALL = "all";
    public static final String FILTER_ANIME = "anime";
    public static final String FILTER_DONGHUA = "donghua";

    /** Genre statis ala AL (nama + warna pil). */
    private static final String[] GENRES = {
            "Action", "Adventure", "Comedy", "Romance", "Fantasy", "Horror"};
    private static final String[] GENRE_COLORS = {
            "#E85D5D", "#2EC4B6", "#E9C46A", "#EC6AA5", "#5B8DEF", "#6B7280"};

    /** Satu baris daftar; key = aksi klik hint seksi (mis. "history"). */
    private static final class Row {
        final int type, titleRes, hintRes;
        final String key;
        final AnimeItem item;
        final List<AnimeItem> items;

        Row(int type, int titleRes, int hintRes, String key,
            AnimeItem item, List<AnimeItem> items) {
            this.type = type;
            this.titleRes = titleRes;
            this.hintRes = hintRes;
            this.key = key;
            this.item = item;
            this.items = items;
        }
    }

    private final OnClick click;
    private final List<Row> rows = new ArrayList<>();

    private final List<AnimeItem> latest = new ArrayList<>();
    private final List<AnimeItem> ongoing = new ArrayList<>();
    private final List<AnimeItem> top = new ArrayList<>();
    private final List<HistoryItem> history = new ArrayList<>();
    private boolean hasOngoing, hasTop, hasHistory;
    private String filter = FILTER_ALL;
    private int latestPos;

    private OnHistoryPick historyPick;
    private OnSectionClick sectionClick;
    private OnGenrePick genrePick;
    private OnFilterPick filterPick;

    public HomeAdapter(OnClick click) { this.click = click; }

    public void setOnHistoryPick(OnHistoryPick l) { historyPick = l; }
    public void setOnSectionClick(OnSectionClick l) { sectionClick = l; }
    public void setOnGenrePick(OnGenrePick l) { genrePick = l; }
    public void setOnFilterPick(OnFilterPick l) { filterPick = l; }

    // ------------------------------------------------------------- data

    public void setLatest(List<AnimeItem> items) {
        latest.clear();
        if (items != null) latest.addAll(items);
        rebake();
    }

    /** Seksi Sedang Tayang — kosong/berisi, tampil hanya bila ada datanya. */
    public void setOngoing(List<AnimeItem> items) {
        ongoing.clear();
        if (items != null) ongoing.addAll(items);
        hasOngoing = !ongoing.isEmpty();
        rebake();
    }

    /** Seksi Top Rating — urutan situs, tanpa angka rating. */
    public void setTop(List<AnimeItem> items) {
        top.clear();
        if (items != null) top.addAll(items);
        hasTop = !top.isEmpty();
        rebake();
    }

    /** Seksi Riwayat — 10 tontonan terakhir, sembunyi bila kosong. */
    public void setHistory(List<HistoryItem> items) {
        history.clear();
        if (items != null) {
            int n = Math.min(items.size(), 10);
            for (int i = 0; i < n; i++) history.add(items.get(i));
        }
        hasHistory = !history.isEmpty();
        rebake();
    }

    /** Pil filter aktif pada kepala Ongoing (disorot). */
    public void setFilter(String mode) {
        filter = mode == null ? FILTER_ALL : mode;
        rebake();
    }

    /** Posisi baris judul Rilis Terbaru (untuk lompat dari kepala Ongoing). */
    public int latestPosition() { return latestPos; }

    private void rebake() {
        rows.clear();
        if (hasHistory) {
            rows.add(new Row(TYPE_SECTION, R.string.section_history, R.string.see_all,
                    "history", null, null));
            rows.add(new Row(TYPE_HISTORY_ROW, 0, 0, null, null, null));
        }
        rows.add(new Row(TYPE_GENRE, 0, 0, null, null, null));
        if (hasOngoing) {
            rows.add(new Row(TYPE_ONGOING_HEAD, 0, 0, null, null, null));
            rows.add(new Row(TYPE_ROW, 0, 0, null, null, new ArrayList<>(ongoing)));
        }
        latestPos = rows.size();
        if (!latest.isEmpty()) {
            rows.add(new Row(TYPE_SECTION, R.string.section_latest, R.string.ui_refresh_hint,
                    null, null, null));
            for (AnimeItem a : latest) {
                rows.add(new Row(TYPE_POSTER, 0, 0, null, a, null));
            }
        }
        if (hasTop) {
            rows.add(new Row(TYPE_SECTION, R.string.section_top, 0, null, null, null));
            rows.add(new Row(TYPE_ROW, 0, 0, null, null, new ArrayList<>(top)));
        }
        notifyDataSetChanged();
    }

    /** Baris mana yang melebar penuh (semua kecuali kartu poster). */
    public boolean isFullSpan(int position) {
        return position < 0 || position >= rows.size()
                || rows.get(position).type != TYPE_POSTER;
    }

    // -------------------------------------------------------- recycling

    @Override public int getItemCount() { return rows.size(); }

    @Override public int getItemViewType(int position) { return rows.get(position).type; }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (type == TYPE_SECTION) {
            return new SectionVH(inf.inflate(R.layout.ui_item_section, parent, false));
        }
        if (type == TYPE_ROW || type == TYPE_HISTORY_ROW) {
            return new RowVH(inf.inflate(R.layout.ui_item_row, parent, false));
        }
        if (type == TYPE_GENRE) {
            return new GenreVH(inf.inflate(R.layout.ui_item_genre, parent, false));
        }
        if (type == TYPE_ONGOING_HEAD) {
            return new OngoingHeadVH(
                    inf.inflate(R.layout.ui_item_ongoing_head, parent, false));
        }
        return new PosterVH(inf.inflate(R.layout.item_anime, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.type == TYPE_SECTION) {
            SectionVH h = (SectionVH) holder;
            h.title.setText(row.titleRes);
            if (row.hintRes != 0) {
                h.hint.setText(row.hintRes);
                h.hint.setVisibility(View.VISIBLE);
            } else {
                h.hint.setVisibility(View.GONE);
            }
            final String key = row.key;
            h.hint.setOnClickListener(key != null && sectionClick != null
                    ? v -> sectionClick.onSection(key) : null);
            h.hint.setClickable(key != null && sectionClick != null);
            return;
        }

        if (row.type == TYPE_ROW) {
            ((RowVH) holder).rv.setAdapter(new RowAdapter(row.items, click));
            return;
        }

        if (row.type == TYPE_HISTORY_ROW) {
            HistoryCardAdapter ha = new HistoryCardAdapter(
                    it -> { if (historyPick != null) historyPick.onPick(it); });
            ha.setItems(history);
            ((RowVH) holder).rv.setAdapter(ha);
            return;
        }

        if (row.type == TYPE_GENRE) {
            ((GenreVH) holder).bind(genrePick);
            return;
        }

        if (row.type == TYPE_ONGOING_HEAD) {
            ((OngoingHeadVH) holder).bind(filter, filterPick);
            return;
        }

        bindPoster((PosterVH) holder, row.item);
    }

    private void bindPoster(final PosterVH h, final AnimeItem it) {
        if (it == null) return;
        h.title.setText(it.title);
        h.meta.setText(it.meta);
        h.badge.setText(badgeOf(it.meta));
        h.badge.setVisibility(it.meta.isEmpty() ? View.GONE : View.VISIBLE);
        ImageLoader.load(it.thumb, h.thumb);
        h.itemView.setOnClickListener(v -> { if (click != null) click.onPick(it); });
    }

    /** Lencana episode diambil dari metadata ("… · Ep 13" → "Ep 13"). */
    static String badgeOf(String meta) {
        if (meta == null || meta.trim().isEmpty()) return "";
        String m = meta.trim();
        int idx = m.lastIndexOf(" · ");
        String last = (idx >= 0) ? m.substring(idx + 3).trim() : m;
        if (last.length() > 16) last = m;
        return last;
    }

    // ------------------------------------------------------------ VH

    static class SectionVH extends RecyclerView.ViewHolder {
        final TextView title, hint;
        SectionVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.uiSectionTitle);
            hint = v.findViewById(R.id.uiSectionHint);
        }
    }

    static class PosterVH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, meta, badge;
        PosterVH(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            meta = v.findViewById(R.id.meta);
            badge = v.findViewById(R.id.badge);
        }
    }

    static class RowVH extends RecyclerView.ViewHolder {
        final RecyclerView rv;
        RowVH(@NonNull View v) {
            super(v);
            rv = (RecyclerView) v;
            rv.setLayoutManager(new LinearLayoutManager(
                    v.getContext(), LinearLayoutManager.HORIZONTAL, false));
            rv.setNestedScrollingEnabled(false);
        }
    }

    /** Deretan pil genre warna-warni (statis, ala AL). */
    static class GenreVH extends RecyclerView.ViewHolder {
        private final MaterialButton[] btns = new MaterialButton[6];
        GenreVH(@NonNull View v) {
            super(v);
            btns[0] = v.findViewById(R.id.uiGenre0);
            btns[1] = v.findViewById(R.id.uiGenre1);
            btns[2] = v.findViewById(R.id.uiGenre2);
            btns[3] = v.findViewById(R.id.uiGenre3);
            btns[4] = v.findViewById(R.id.uiGenre4);
            btns[5] = v.findViewById(R.id.uiGenre5);
        }
        void bind(final OnGenrePick pick) {
            for (int i = 0; i < 6; i++) {
                btns[i].setText(GENRES[i]);
                btns[i].setBackgroundColor(
                        android.graphics.Color.parseColor(GENRE_COLORS[i]));
                final String g = GENRES[i];
                btns[i].setOnClickListener(
                        pick != null ? v -> pick.onGenre(g) : null);
            }
        }
    }

    /** Kepala Ongoing Update + pil filter Semua/Anime/Donghua. */
    static class OngoingHeadVH extends RecyclerView.ViewHolder {
        private final Chip fAll, fAnime, fDonghua;
        OngoingHeadVH(@NonNull View v) {
            super(v);
            fAll = v.findViewById(R.id.uiFilterAll);
            fAnime = v.findViewById(R.id.uiFilterAnime);
            fDonghua = v.findViewById(R.id.uiFilterDonghua);
        }
        void bind(String filter, final OnFilterPick pick) {
            fAll.setChecked(FILTER_ALL.equals(filter));
            fAnime.setChecked(FILTER_ANIME.equals(filter));
            fDonghua.setChecked(FILTER_DONGHUA.equals(filter));
            fAll.setOnClickListener(pick != null ? v -> pick.onFilter(FILTER_ALL) : null);
            fAnime.setOnClickListener(
                    pick != null ? v -> pick.onFilter(FILTER_ANIME) : null);
            fDonghua.setOnClickListener(
                    pick != null ? v -> pick.onFilter(FILTER_DONGHUA) : null);
        }
    }

    /** Adapter baris horizontal: kartu poster berlebar tetap. */
    private static class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {
        private final List<AnimeItem> data;
        private final OnClick click;

        RowAdapter(List<AnimeItem> data, OnClick click) {
            this.data = data == null ? new ArrayList<>() : data;
            this.click = click;
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.ui_item_serie, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int i) {
            final AnimeItem it = data.get(i);
            h.title.setText(it.title);
            h.meta.setText(it.meta);
            h.badge.setText(badgeOf(it.meta));
            h.badge.setVisibility(it.meta.isEmpty() ? View.GONE : View.VISIBLE);
            ImageLoader.load(it.thumb, h.thumb);
            h.itemView.setOnClickListener(v -> { if (click != null) click.onPick(it); });
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title, meta, badge;
            VH(@NonNull View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                title = v.findViewById(R.id.title);
                meta = v.findViewById(R.id.meta);
                badge = v.findViewById(R.id.badge);
            }
        }
    }
}
