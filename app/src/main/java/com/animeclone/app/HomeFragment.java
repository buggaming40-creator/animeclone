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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
        new AlertDialog.Builder(requireContext())
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
            for (AnimeItem a : probe) {
                try {
                    Oploverz.Series s = Oploverz.loadSeriesLite(a.url);
                    String st = s == null || s.status == null ? "" : s.status;
                    if (st.toLowerCase(Locale.ROOT).contains("ongoing")) found.add(a);
                } catch (Throwable ignored) {
                    // Gagal memuat satu judul bukan alasan membatalkan seluruh seksi.
                }
                if (found.size() >= 5) break;
            }
            return found;
        }, new Async.Done<List<AnimeItem>>() {
            @Override public void ok(List<AnimeItem> result) {
                if (!isAdded() || adapter == null) return;
                ongoingAll.clear();
                if (result != null) ongoingAll.addAll(result);
                adapter.setOngoing(filterList(ongoingAll));
            }

            @Override public void err(Throwable t) {
                if (isAdded() && adapter != null) adapter.setOngoing(null);
            }
        });
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
