package com.animeclone.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Penyimpanan bookmark (Tersimpan): SQLite `bookmark.db`, VER 2.
 *
 * Skema (animelovers.md §3.3):
 *   bookmark(id INTEGER PK, series_url TEXT UNIQUE, title TEXT, thumb TEXT,
 *            status TEXT DEFAULT '', last_seen_ep INTEGER DEFAULT 0,
 *            cat_id INTEGER DEFAULT 0,
 *            added_at INTEGER, updated_at INTEGER);  -- idx updated_at DESC
 *   cat(id INTEGER PK, name TEXT UNIQUE)  -- kategori buatan pengguna
 *
 * Alur "Ada Episode Baru!":
 *   - Saat Series membuka sebuah judul, jumlah episode situs (asli, dari
 *     daftar episode) ditulis ke kolom `status` lewat {@link #refresh};
 *     `last_seen_ep` TIDAK diubah sehingga bila jumlahnya bertambah, kartu
 *     di tab Tersimpan menampilkan lencana.
 *   - Saat pengguna benar-benar membuka sebuah episode, {@link #touchEpisodes}
 *     menaikkan `last_seen_ep` dan lencana hilang (sudah dilihat).
 *
 * Meniru pola {@link HistoryStore} (SQLiteOpenHelper).
 */
public class BookmarkStore extends SQLiteOpenHelper {

    private static final String DB = "bookmark.db";
    private static final int VER = 2;
    private static final String TABLE = "bookmark";

    /** Satu kategori bookmark (baris tabel `cat`); id 0 = virtual "Semua". */
    public static class Cat {
        public long id;
        public String name = "";
    }

    /** Panjang maksimum nama kategori buatan pengguna. */
    private static final int MAX_CAT_LEN = 20;

    /** Urutan pengurutan daftar bookmark. */
    public static final int SORT_ALPHA = 0;     // title COLLATE NOCASE
    public static final int SORT_ADDED = 1;     // added_at DESC
    public static final int SORT_UPDATED = 2;   // updated_at DESC

    private static final Pattern EP_COUNT = Pattern.compile("(\\d+)\\s*Episode");

    public BookmarkStore(Context c) {
        super(c.getApplicationContext(), DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmark ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "series_url TEXT NOT NULL UNIQUE,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "thumb TEXT NOT NULL DEFAULT '',"
                + "status TEXT NOT NULL DEFAULT '',"
                + "last_seen_ep INTEGER NOT NULL DEFAULT 0,"
                + "cat_id INTEGER NOT NULL DEFAULT 0,"
                + "added_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmark_updated "
                + "ON bookmark(updated_at DESC)");
        ensureCats(db);
    }

    /**
     * Migrasi VER 1 → 2: tabel bookmark TIDAK di-drop (data pengguna aman,
     * riwayat di history.db tidak tersentuh) — hanya tambah tabel `cat`,
     * kolom `cat_id`, dan seed 3 kategori bawaan.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS cat("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT UNIQUE)");
            try {
                db.execSQL("ALTER TABLE bookmark "
                        + "ADD COLUMN cat_id INTEGER NOT NULL DEFAULT 0");
            } catch (Throwable ignored) {
                // Kolom sudah ada (migrasi berjalan dua kali) — lanjutkan.
            }
            db.execSQL("INSERT OR IGNORE INTO cat(name) VALUES ('Favorit')");
            db.execSQL("INSERT OR IGNORE INTO cat(name) VALUES ('Mau Nonton')");
            db.execSQL("INSERT OR IGNORE INTO cat(name) VALUES ('Arsip')");
        }
    }

    /** Buat tabel `cat` + seed bawaan (dipakai instalasi baru). */
    private void ensureCats(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cat("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT UNIQUE)");
        db.execSQL("INSERT OR IGNORE INTO cat(name) VALUES ('Favorit')");
        db.execSQL("INSERT OR IGNORE INTO cat(name) VALUES ('Mau Nonton')");
        db.execSQL("INSERT OR IGNORE INTO cat(name) VALUES ('Arsip')");
    }

    // -------------------------------------------------------------- kategori

    /**
     * Daftar kategori: baris pertama selalu virtual id 0 = "Semua",
     * sisanya kategori dari tabel `cat` urut nama.
     */
    public List<Cat> cats() {
        List<Cat> out = new ArrayList<>();
        Cat semua = new Cat();
        semua.id = 0;
        semua.name = "Semua";
        out.add(semua);
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id,name FROM cat ORDER BY name COLLATE NOCASE ASC", null);
        try {
            while (c.moveToNext()) {
                Cat k = new Cat();
                k.id = c.getLong(0);
                k.name = c.getString(1) == null ? "" : c.getString(1);
                out.add(k);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /**
     * Tambah kategori; nama dipangkas (maks 20 karakter), kosong/duplikat
     * diabaikan. Kembalikan id baris (yang sudah ada bila duplikat),
     * atau -1 bila nama kosong.
     */
    public long addCat(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return -1;
        if (n.length() > MAX_CAT_LEN) n = n.substring(0, MAX_CAT_LEN).trim();
        if (n.isEmpty()) return -1;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO cat(name) VALUES (?)", new Object[]{n});
        Cursor c = db.rawQuery("SELECT id FROM cat WHERE name=?", new String[]{n});
        try {
            return c.moveToFirst() ? c.getLong(0) : -1;
        } finally {
            c.close();
        }
    }

    /** Pindahkan bookmark ke kategori lain. */
    public void setCat(String url, long catId) {
        if (url == null || url.isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("cat_id", Math.max(0, catId));
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update(TABLE, v, "series_url=?", new String[]{url});
    }

    /** Nama kategori; id 0/tidak dikenal = "Semua". */
    public String catName(long id) {
        if (id <= 0) return "Semua";
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT name FROM cat WHERE id=?", new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) {
                String n = c.getString(0);
                return n == null ? "" : n;
            }
        } finally {
            c.close();
        }
        return "";
    }

    // ------------------------------------------------------------------ tulis

    /**
     * Tambah bookmark. Judul yang sudah ada diperbarui datanya, `last_seen_ep`
     * lama tidak disentuh. `siteEp` dipakai sebagai jumlah yang sudah dilihat
     * sehingga tidak ada lencana palsu tepat setelah menyimpan.
     */
    public void add(String url, String title, String thumb, String status, int siteEp) {
        save(url, title, thumb, status, siteEp, 0);
    }

    /**
     * Simpan judul ke kategori tertentu (dipakai sheet "Simpan ke").
     * Perilaku lencana sama seperti {@link #add}: `last_seen_ep` diisi
     * jumlah episode situs saat ini agar tidak muncul lencana palsu.
     */
    public void save(String url, String title, String thumb,
                     String status, int siteEp, long catId) {
        if (url == null || url.isEmpty()) return;
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();

        BookmarkItem old = get(url);
        if (old != null) {
            refresh(url, status);
            return;
        }

        ContentValues v = new ContentValues();
        v.put("series_url", url);
        v.put("title", nz(title));
        v.put("thumb", nz(thumb));
        v.put("status", nz(status));
        v.put("last_seen_ep", Math.max(0, siteEp));
        v.put("cat_id", Math.max(0, catId));
        v.put("added_at", now);
        v.put("updated_at", now);
        db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Simpan/hapus; kembalikan true bila berakhir dalam keadaan tersimpan. */
    public boolean toggle(String url, String title, String thumb, String status, int siteEp) {
        if (has(url)) {
            removeByUrl(url);
            return false;
        }
        add(url, title, thumb, status, siteEp);
        return true;
    }

    public void remove(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public void removeByUrl(String url) {
        if (url == null || url.isEmpty()) return;
        getWritableDatabase().delete(TABLE, "series_url=?", new String[]{url});
    }

    public void clear() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    /**
     * Perbarui status terbaru dari situs tanpa mengubah `last_seen_ep` —
     * dipanggil tiap kali Series dibuka sehingga jumlah episode yang lebih
     * banyak langsung terlihat sebagai lencana di tab Tersimpan.
     */
    public void refresh(String url, String status) {
        if (url == null || url.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        String s = nz(status);
        ContentValues v = new ContentValues();
        v.put("status", s);
        v.put("updated_at", System.currentTimeMillis());
        db.update(TABLE, v, "series_url=?", new String[]{url});
    }

    /** Tandai pengguna sudah melihat `count` episode (lencana hilang). */
    public void touchEpisodes(String url, int count) {
        if (url == null || url.isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("last_seen_ep", Math.max(0, count));
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update(TABLE, v, "series_url=?", new String[]{url});
    }

    // ------------------------------------------------------------------ baca

    public boolean has(String url) {
        return get(url) != null;
    }

    /** Satu baris berdasarkan URL series; null bila tidak ada. */
    public BookmarkItem get(String url) {
        if (url == null || url.isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id,series_url,title,thumb,status,last_seen_ep,cat_id,added_at,updated_at "
              + "FROM bookmark WHERE series_url=?", new String[]{url});
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    /** Daftar bookmark sesuai urutan yang dipilih. */
    public List<BookmarkItem> all(int sortMode) {
        String order;
        switch (sortMode) {
            case SORT_ALPHA:   order = "title COLLATE NOCASE ASC"; break;
            case SORT_ADDED:   order = "added_at DESC"; break;
            case SORT_UPDATED:
            default:           order = "updated_at DESC"; break;
        }

        List<BookmarkItem> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id,series_url,title,thumb,status,last_seen_ep,cat_id,added_at,updated_at "
              + "FROM bookmark ORDER BY " + order, null);
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private BookmarkItem read(Cursor c) {
        BookmarkItem b = new BookmarkItem();
        b.id = c.getLong(0);
        b.seriesUrl = c.getString(1);
        b.title = c.getString(2);
        b.thumb = c.getString(3);
        b.status = c.getString(4);
        b.lastSeenEp = c.getInt(5);
        int ci = c.getColumnIndex("cat_id");
        b.catId = ci >= 0 ? c.getLong(ci) : 0;
        b.addedAt = c.getLong(ci >= 0 ? 7 : 6);
        b.updatedAt = c.getLong(ci >= 0 ? 8 : 7);
        b.siteEp = parseCount(b.status);
        return b;
    }

    // ------------------------------------------------------------------ util

    /**
     * Susun teks status yang selalu memuat jumlah episode terbaru,
     * contoh: "Ongoing · 13 Episode" / "13 Episode". Jumlah berasal dari
     * daftar episode situs (data asli, bukan angka karangan).
     */
    public static String buildStatus(String siteStatus, int count) {
        String s = siteStatus == null ? "" : siteStatus.trim();
        if (count <= 0) return s;
        // Buang jumlah episode lama bila sudah ada, lalu ganti dengan terbaru.
        s = s.replaceAll("(?i)\\s*·?\\s*\\d+\\s*Episode\\s*$", "").trim();
        String fresh = count + " Episode";
        return s.isEmpty() ? fresh : s + " · " + fresh;
    }

    /** Baca jumlah episode dari teks status; 0 bila tidak terbaca. */
    public static int parseCount(String status) {
        if (status == null || status.isEmpty()) return 0;
        Matcher m = EP_COUNT.matcher(status);
        int best = 0;
        while (m.find()) {
            try {
                best = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                // angka tidak wajar — abaikan potongan ini
            }
        }
        return best;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
