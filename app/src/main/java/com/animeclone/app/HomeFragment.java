package com.animeclone.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Tab "Terbaru" (beranda) ala AnimeLovers:
 *   header avatar + sapaan + tamu + lonceng/cari, chip genre, kartu pengumuman,
 *   ticker sedang tayang, banner carousel, seksi Riwayat (kartu horizontal),
 *   seksi Genre, Ongoing Update + pil filter, grid Rilis Terbaru, Top Rating.
 *
 * Tanpa login, tanpa VIP — semua tombol berfungsi (cari, dialog info, resume).
 * Seksi tanpa data tidak pernah ditampilkan (tidak ada data karangan).
 */
public class HomeFragment extends Fragment {

    /** Jumlah kartu yang diperiksa untuk seksi Sedang Tayang (hemat jaringan). */
    private static final int PROBE_LIMIT = 6;

    /** Genre jalan pintas chip atas (ketuk = cari). */
    private static final String[] QUICK_GENRES = {
            "Action", "Adventure", "Comedy", "Romance", "Fantasy"};

    /** Indeks genre lengkap untuk dialog "Semua genre" (netral, 16 entri). */
    private static final String[] GENRE_INDEX = {
            "Action", "Adventure", "Avant Garde", "Award Winning",
            "Comedy", "Drama", "Fantasy", "Horror",
            "Mystery", "Romance", "Shoujo", "Shounen",
            "Slice of Life", "Sports", "Supernatural", "Suspense"};

    private HomeAdapter adapter;
    private BannerAdapter bannerAdapter;
    private SwipeRefreshLayout refresh;
    private ProgressBar progress;
    private View emptyBox;
    private TextView empty;
    private ViewPager2 banner;
    private LinearLayout dots;
    private View bannerBox;
    private TextView greeting;
    private TextView ticker;
    private RecyclerView rv;

    private HistoryStore historyStore;

    private final List<AnimeItem> all = new ArrayList<>();
    private final List<AnimeItem> ongoingAll = new ArrayList<>();
    /** Hasil probe mentah (studio/genre/sinopsis/tanggal) untuk Rekom + Jadwal. */
    private final List<Oploverz.Series> liteSeries = new ArrayList<>();
    private String filter = HomeAdapter.FILTER_ALL;
    private boolean loadedOnce;

    private final Handler auto = new Handler(Looper.getMainLooper());

    /** Auto-scroll banner: lanjut ke halaman berikutnya, berputar di ujung. */
    private final Runnable bannerTick = new Runnable() {
        @Override public void run() {
            int n = bannerAdapter == null ? 0 : bannerAdapter.getItemCount();
            if (banner != null && n > 1) {
                banner.setCurrentItem((banner.getCurrentItem() + 1) % n, true);
            }
            auto.postDelayed(this, 4500);
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup grp,
                             @Nullable Bundle st) {
        View v = inf.inflate(R.layout.fragment_home, grp, false);

        refresh = v.findViewById(R.id.refresh);
        progress = v.findViewById(R.id.progress);
        emptyBox = v.findViewById(R.id.emptyBox);
        empty = v.findViewById(R.id.empty);
        ImageView emptyIcon = v.findViewById(R.id.emptyIcon);
        rv = v.findViewById(R.id.recycler);
        greeting = v.findViewById(R.id.uiGreeting);
        ticker = v.findViewById(R.id.uiTicker);

        if (getContext() != null) historyStore = new HistoryStore(requireContext());

        emptyIcon.setImageResource(R.drawable.ic_empty_state);

        // ---- grid + baris horizontal ----
        adapter = new HomeAdapter(this::open);
        adapter.setOnHistoryPick(this::playHistory);
        adapter.setOnSectionClick(key -> {
            if ("history".equals(key)) gotoTab(PagerAdapter.PAGE_HISTORY);
            else if ("genres".equals(key)) showGenreIndex();
            else if ("schedule".equals(key)) showSchedule();
        });
        adapter.setOnGenrePick(this::searchGenre);
        adapter.setOnFilterPick(this::applyFilter);
        final GridLayoutManager glm = grid();
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return adapter.isFullSpan(position) ? glm.getSpanCount() : 1;
            }
        });
        rv.setLayoutManager(glm);
        rv.setAdapter(adapter);

        // ---- banner ----
        banner = v.findViewById(R.id.uiBanner);
        dots = v.findViewById(R.id.uiDots);
        bannerBox = v.findViewById(R.id.uiBannerBox);
        bannerAdapter = new BannerAdapter(this::open);
        banner.setAdapter(bannerAdapter);
        banner.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { buildDots(position); }
        });

        // ---- header AL: sapaan + lonceng + cari ----
        if (greeting != null) greeting.setText(greetingText());
        TextView ver = v.findViewById(R.id.uiVerBadge);
        if (ver != null) ver.setText("v" + installedVersion());
        View search = v.findViewById(R.id.uiSearchButton);
        search.setOnClickListener(x -> gotoTab(PagerAdapter.PAGE_SEARCH));
        View bell = v.findViewById(R.id.uiBellButton);
        bell.setOnClickListener(x -> showAnnouncement());

        // ---- chip genre jalan pintas ----
        int[] qcIds = {R.id.uiQuick0, R.id.uiQuick1, R.id.uiQuick2,
                R.id.uiQuick3, R.id.uiQuick4};
        for (int i = 0; i < qcIds.length && i < QUICK_GENRES.length; i++) {
            View c = v.findViewById(qcIds[i]);
            if (c instanceof TextView) ((TextView) c).setText(QUICK_GENRES[i]);
            final String g = QUICK_GENRES[i];
            if (c != null) c.setOnClickListener(x -> searchGenre(g));
        }

        // ---- kartu pengumuman ----
        View announce = v.findViewById(R.id.uiAnnounce);
        if (announce != null) announce.setOnClickListener(x -> showAnnouncement());

        refresh.setColorSchemeColors(
                MaterialColors.getColor(v, com.google.android.material.R.attr.colorPrimary),
                MaterialColors.getColor(v, com.google.android.material.R.attr.colorSecondary));
        refresh.setOnRefreshListener(this::load);

        load();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bannerAdapter != null && bannerAdapter.getItemCount() > 0) {
            auto.removeCallbacks(bannerTick);
            auto.postDelayed(bannerTick, 4500);
        }
        // Riwayat bisa berubah dari Player — segarkan seksi Riwayat
        // (satu kartu per judul: representatif terbaru tiap grup).
        if (adapter != null && historyStore != null && isAdded()) {
            try {
                List<HistoryItem> latest = new ArrayList<>();
                for (HistoryGroup g
                        : HistoryFragment.groupByTitle(historyStore.all())) {
                    if (g.latest() != null) latest.add(g.latest());
                }
                adapter.setHistory(latest);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onPause() {
        auto.removeCallbacks(bannerTick);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        auto.removeCallbacks(bannerTick);
        super.onDestroyView();
    }

    /** Jumlah kolom grid mengikuti orientasi layar. */
    private GridLayoutManager grid() {
        int orientation = getResources().getConfiguration().orientation;
        int span = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 5 : 3;
        return new GridLayoutManager(requireContext(), span);
    }

    /** Tampilkan tautan Jadwal hanya bila ada tanggal rilis terurai. */
    private void updateScheduleLink() {
        if (adapter != null) adapter.setHasSchedule(hasDated(liteSeries));
    }

    private static boolean hasDated(List<Oploverz.Series> list) {
        if (list == null) return false;
        for (Oploverz.Series s : list) {
            if (s != null && !s.episodes.isEmpty()
                    && !Utils.weekdayOf(s.episodes.get(0).date).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Jadwal rilis ala AL: kelompokkan judul menurut hari episode
     * terbarunya (dari tanggal situs). Tanpa tanggal = tidak ditampilkan.
     */
    private void showSchedule() {
        if (getContext() == null) return;
        String[] order = {"Senin", "Selasa", "Rabu", "Kamis",
                "Jumat", "Sabtu", "Minggu"};
        java.util.LinkedHashMap<String, List<Oploverz.Series>> byDay =
                new java.util.LinkedHashMap<>();
        for (String d : order) byDay.put(d, new ArrayList<>());
        for (Oploverz.Series s : liteSeries) {
            if (s == null || s.episodes.isEmpty()) continue;
            String day = Utils.weekdayOf(s.episodes.get(0).date);
            if (!day.isEmpty() && byDay.containsKey(day)) byDay.get(day).add(s);
        }

        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(4);
        root.setPadding(pad, pad, pad, pad);
        int count = 0;
        for (String day : order) {
            List<Oploverz.Series> items = byDay.get(day);
            if (items == null || items.isEmpty()) continue;
            TextView dh = new TextView(requireContext());
            dh.setText(day);
            dh.setTextSize(15);
            dh.setTypeface(null, android.graphics.Typeface.BOLD);
            dh.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    root, com.google.android.material.R.attr.colorPrimary));
            dh.setPadding(dp(8), dp(10), dp(8), dp(4));
            root.addView(dh);
            for (Oploverz.Series s : items) {
                TextView row = new TextView(requireContext());
                String ep = s.episodes.isEmpty() ? ""
                        : (" • " + Utils.relDate(s.episodes.get(0).date));
                row.setText("•  " + s.title + ep);
                row.setTextSize(14);
                row.setTextColor(androidx.core.content.ContextCompat.getColor(
                        requireContext(), R.color.text_primary));
                row.setPadding(dp(8), dp(7), dp(8), dp(7));
                row.setClickable(true);
                row.setFocusable(true);
                final Oploverz.Series pick = s;
                row.setOnClickListener(x -> open(new AnimeItem(
                        pick.title,
                        pick.seriesUrl.isEmpty() ? "" : pick.seriesUrl,
                        pick.thumb, "")));
                root.addView(row);
                count++;
            }
        }
        if (count == 0) {
            Toast.makeText(requireContext(),
                    R.string.no_schedule, Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(requireContext());
        sv.addView(root);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.schedule_title)
                .setView(sv)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Sapaan menurut jam: Pagi/Siang/Sore/Malam. */
    private String greetingText() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 4 && h < 11) return getString(R.string.greet_morning);
        if (h >= 11 && h < 15) return getString(R.string.greet_day);
        if (h >= 15 && h < 19) return getString(R.string.greet_afternoon);
        return getString(R.string.greet_night);
    }

    private String installedVersion() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1.0";
        }
    }

    /** Lonceng/pengumuman: dialog info aplikasi (tanpa login). */
    private void showAnnouncement() {
        if (getContext() == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.announce_title)
                .setMessage(R.string.announce_dialog)
                .setPositiveButton(R.string.ok_label, null)
                .show();
    }

    /** Chip/genre: titip query ke tab Cari lalu pindah. */
    private void searchGenre(String genre) {
        if (getContext() == null) return;
        SearchFragment.requestQuery(genre);
        gotoTab(PagerAdapter.PAGE_SEARCH);
    }

    /** Indeks genre: daftar netral — ketuk = cari genre tersebut. */
    private void showGenreIndex() {
        if (getContext() == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.genre_index_title)
                .setItems(GENRE_INDEX, (d, which) -> searchGenre(GENRE_INDEX[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------- muat data

    private void load() {
        if (!loadedOnce && refresh != null) {
            progress.setVisibility(View.VISIBLE);
            emptyBox.setVisibility(View.GONE);
        }
        Async.go(Oploverz::latest, new Async.Done<List<AnimeItem>>() {
            @Override public void ok(List<AnimeItem> items) {
                if (!isAdded() || adapter == null) return;
                loadedOnce = true;
                refresh.setRefreshing(false);
                progress.setVisibility(View.GONE);

                all.clear();
                if (items != null) all.addAll(items);

                refreshContent();
                syncTicker();
                syncEmpty();

                if (!filtered().isEmpty()) probeOngoing(filtered());
            }

            @Override public void err(Throwable t) {
                if (!isAdded() || adapter == null) return;
                loadedOnce = true;
                refresh.setRefreshing(false);
                progress.setVisibility(View.GONE);
                all.clear();
                adapter.setLatest(new ArrayList<>());
                adapter.setTop(new ArrayList<>());
                adapter.setOngoing(new ArrayList<>());
                adapter.setHistory(new ArrayList<>());
                adapter.setRekom(new ArrayList<Oploverz.Series>());
                setupBanner(new ArrayList<>());
                showEmpty(true, R.string.err_net);
            }
        });
    }

    /** H-6: urutan situs (bukan angka rating) — ambil beberapa judul teratas. */
    private List<AnimeItem> topOf(List<AnimeItem> items) {
        List<AnimeItem> out = new ArrayList<>();
        int n = Math.min(items.size(), 8);
        for (int i = 0; i < n; i++) out.add(items.get(i));
        return out;
    }

    /** H-5: best-effort — cek status tiap halaman series; bila gagal, seksi disembunyikan. */
    private void probeOngoing(final List<AnimeItem> items) {
        final List<AnimeItem> probe = new ArrayList<>();
        for (int i = 0; i < items.size() && i < PROBE_LIMIT; i++) probe.add(items.get(i));

        Async.go(() -> {
            List<AnimeItem> found = new ArrayList<>();
            List<Oploverz.Series> lites = new ArrayList<>();
            for (AnimeItem a : probe) {
                try {
                    Oploverz.Series s = Oploverz.loadSeriesLite(a.url);
                    if (s == null) continue;
                    lites.add(s);
                    String st = s.status == null ? "" : s.status;
                    if (st.toLowerCase(Locale.ROOT).contains("ongoing")) found.add(a);
                } catch (Throwable ignored) {
                    // Gagal memuat satu judul bukan alasan membatalkan seluruh seksi.
                }
                if (found.size() >= 5) break;
            }
            // Kembalikan keduanya: ongoing untuk seksi Ongoing, lite untuk
            // Rekomendasi + Jadwal (studio/genre/sinopsis/tanggal riil).
            ArrayList<Object> out = new ArrayList<>();
            out.add(found);
            out.add(lites);
            return out;
        }, new Async.Done<ArrayList<Object>>() {
            @Override public void ok(ArrayList<Object> result) {
                if (!isAdded() || adapter == null) return;
                ongoingAll.clear();
                liteSeries.clear();
                if (result != null && result.size() == 2) {
                    @SuppressWarnings("unchecked")
                    List<AnimeItem> found = (List<AnimeItem>) result.get(0);
                    @SuppressWarnings("unchecked")
                    List<Oploverz.Series> lites = (List<Oploverz.Series>) result.get(1);
                    if (found != null) ongoingAll.addAll(found);
                    if (lites != null) liteSeries.addAll(lites);
                }
                adapter.setOngoing(filterList(ongoingAll));
                adapter.setRekom(filterRekom());
                updateScheduleLink();
            }

            @Override public void err(Throwable t) {
                if (isAdded() && adapter != null) adapter.setOngoing(null);
            }
        });
    }

    /** Rekomendasi mengikuti filter aktif (cocok judul + meta). */
    private List<Oploverz.Series> filterRekom() {
        if (HomeAdapter.FILTER_ALL.equals(filter)) return new ArrayList<>(liteSeries);
        List<Oploverz.Series> out = new ArrayList<>();
        for (Oploverz.Series s : liteSeries) {
            if (s == null) continue;
            String hay = ((s.title == null ? "" : s.title) + " "
                    + String.join(" ", s.genres)).toLowerCase(Locale.ROOT);
            if (hay.contains(filter)) out.add(s);
        }
        return out;
    }

    // ---------------------------------------------------------------- filter

    private void applyFilter(String mode) {
        filter = mode == null ? HomeAdapter.FILTER_ALL : mode;
        if (adapter == null) return;
        adapter.setFilter(filter);
        refreshContent();
        syncEmpty();
    }

    /** Terapkan filter aktif ke seluruh konten (grid + semua seksi + banner). */
    private void refreshContent() {
        if (adapter == null) return;
        List<AnimeItem> f = filtered();
        adapter.setLatest(f);
        adapter.setTop(topOf(f));
        adapter.setOngoing(filterList(ongoingAll));
        adapter.setRekom(filterRekom());
        setupBanner(f);
    }

    /** Saring dari metadata kartu (mis. "Anime · Sub · Ep 1"). */
    private List<AnimeItem> filtered() {
        return filterList(all);
    }

    /** Saring daftar mana pun dengan filter aktif. */
    private List<AnimeItem> filterList(List<AnimeItem> src) {
        if (HomeAdapter.FILTER_ALL.equals(filter)) return new ArrayList<>(src);
        List<AnimeItem> out = new ArrayList<>();
        for (AnimeItem a : src) {
            if (a == null) continue;
            String meta = a.meta == null ? "" : a.meta.toLowerCase(Locale.ROOT);
            if (meta.contains(filter)) out.add(a);
        }
        return out;
    }

    // --------------------------------------------------------- keadaan kosong

    private void syncEmpty() {
        if (all.isEmpty()) {
            showEmpty(true, loadedOnce ? R.string.empty_latest : 0);
        } else {
            showEmpty(false, 0);
        }
    }

    private void showEmpty(boolean show, int msg) {
        if (emptyBox == null) return;
        emptyBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && msg != 0) empty.setText(msg);
    }

    // ---------------------------------------------------------------- ticker

    /** Teks berjalan "Sedang tayang: …" dari rilisan terbaru. */
    private void syncTicker() {
        if (ticker == null) return;
        if (all.isEmpty()) {
            ticker.setVisibility(View.GONE);
            return;
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < all.size() && i < 8; i++) {
            String t = all.get(i).title;
            if (t != null && !t.trim().isEmpty()) names.add(t.trim());
        }
        if (names.isEmpty()) {
            ticker.setVisibility(View.GONE);
            return;
        }
        ticker.setText(getString(R.string.ticker_fmt,
                TextUtils.join("  •  ", names)));
        ticker.setVisibility(View.VISIBLE);
        ticker.setSelected(true);
    }

    // -------------------------------------------------------------- banner

    private void setupBanner(List<AnimeItem> items) {
        if (banner == null || getContext() == null) return;
        List<AnimeItem> top5 = new ArrayList<>();
        for (int i = 0; i < items.size() && i < 5; i++) top5.add(items.get(i));

        bannerAdapter.submit(top5);
        if (bannerBox != null) {
            bannerBox.setVisibility(top5.isEmpty() ? View.GONE : View.VISIBLE);
        }
        buildDots(0);

        if (!top5.isEmpty()) {
            auto.removeCallbacks(bannerTick);
            auto.postDelayed(bannerTick, 4500);
        }
    }

    /** Susun indikator dot sesuai jumlah halaman banner dan halaman aktif. */
    private void buildDots(int active) {
        if (dots == null || getContext() == null) return;
        int count = bannerAdapter == null ? 0 : bannerAdapter.getItemCount();
        dots.removeAllViews();
        if (count <= 1) return;

        int accent = MaterialColors.getColor(dots, com.google.android.material.R.attr.colorPrimary);
        for (int i = 0; i < count; i++) {
            boolean on = i == active;
            View d = new View(requireContext());
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setCornerRadius(dp(3));
            g.setColor(on ? accent : 0x80FFFFFF);
            d.setBackground(g);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    on ? dp(18) : dp(6), dp(6));
            lp.setMargins(dp(3), 0, dp(3), 0);
            dots.addView(d, lp);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // -------------------------------------------------------------- umum

    /** Tombol header membuka tab lain tanpa menyentuh logika geser halaman. */
    private void gotoTab(int page) {
        if (getContext() == null) return;
        ViewPager2 pager = requireActivity().findViewById(R.id.pager);
        if (pager != null) pager.setCurrentItem(page, true);
    }

    private void open(AnimeItem item) {
        if (getContext() == null) return;
        Intent i = new Intent(requireContext(), SeriesActivity.class);
        i.putExtra("url", item.url);
        i.putExtra("title", item.title);
        i.putExtra("thumb", item.thumb);
        startActivity(i);
    }

    /** Kartu riwayat di Beranda: lanjutkan nonton persis seperti tab Riwayat. */
    private void playHistory(HistoryItem item) {
        if (getContext() == null || item == null) return;
        Intent i = new Intent(requireContext(), PlayerActivity.class);
        i.putExtra("epUrl", item.epUrl);
        i.putExtra("epTitle", item.epTitle);
        i.putExtra("title", item.title);
        i.putExtra("thumb", item.thumb);
        i.putExtra("seriesUrl", item.seriesUrl);
        i.putExtra("pos", item.finished() ? 0L : item.posMs);
        startActivity(i);
    }
}
