# AGENTS.md — AnimeClone (`/home/nansoffc/AnimeClone`)

Petunjuk kerja untuk agen coding di repo ini. Baca sebelum mengubah kode.
Repo ini klon dari NsNime (`com.anistream.app`) dengan package
`com.animeclone.app` — bisa terinstal berdampingan. Fitur fungsional
(detail, player, scraper) diusahakan tetap sejalan dengan NsNime; bedanya
hanya gaya beranda + profil + palet malam ungu.

## Perintah

```bash
cd /home/nansoffc/AnimeClone && ./gradlew assembleDebug
/usr/bin/adb -s emulator-5554 install -r app/build/outputs/apk/debug/AnimeClone-debug.apk
/usr/bin/adb -s emulator-5554 shell "am start -n com.animeclone.app/.MainActivity"
```

- APK: `AnimeClone-debug.apk`. Aturan paralelisme & bahasa sama seperti NsNime.
- Bila mem-port perubahan NsNime ↔ AnimeClone: salin file + `sed
  s/anistream/animeclone/g` (atau sebaliknya), lalu cek file yang TIDAK boleh
  ikut: `HomeFragment/HomeAdapter/fragment_home*/fragment_settings*`
  (beda gaya), `SettingsFragment` (header profil vs identitas).

## Arsitektur (delta dari NsNime)

- Beranda AL: `HomeFragment` (sapaan, lonceng→dialog, chip genre→
  `SearchFragment.requestQuery` + `MainActivity.EXTRA_TAB`, ticker marquee,
  seksi Riwayat via `HistoryStore.all()` di `onResume`).
- `HomeAdapter` baris: Riwayat (section+`HistoryCardAdapter`), Genre
  (`ui_item_genre`, 6 pil warna statis), Ongoing-head (`ui_item_ongoing_head`,
  pil filter Semua/Anime/Donghua → filter client-side dari `meta`), Rilis
  grid, Top row. Seksi kosong disembunyikan; header Rilis hanya bila ada item.
- `MainActivity.EXTRA_TAB` + `onNewIntent`/`applyExtraTab` untuk lompat tab
  dari SeriesActivity (chip genre). `SearchFragment.pendingQuery` dikonsumsi
  sekali di `onResume`.
- Setelan memakai header profil tamu (`uiProfileGreet` + avatar + badge versi).
- Palet malam ungu (`window #141222`, aksen bawaan periwinkle); nama resource
  warna sama seperti NsNime.

## Scraper & player

Sama seperti NsNime (lihat `AGENTS.md` di sana): `Oploverz` (sinopsis, genre,
studio, mirror, GoFile), `loadSeriesLite` untuk probe, rel player
(speed/daftar/server, mengikuti visibilitas kontrol), sheet episode
Putar/Unduh. Episode lengkap: sinopsis/genre hanya ada di halaman series —
`loadSeries` selalu follow + merge field kosong.

## Jebakan emulator

Sama seperti NsNime. Tambahan: dump `uiautomator` tidak bisa diandalkan untuk
memotret kontrol player (lambat + node GONE hilang) — uji tombol rel dengan
rantai tap cepat dalam SATU perintah shell lalu screenshot.

## Pelajaran build
- Build incremental di sini TIDAK bisa dipercaya (pernah hasilkan Frankenstein:
  UI campur kode lama/baru). Untuk APK rilis/uji SELALU `clean assembleDebug
  --no-build-cache`, lalu verifikasi isi dex (`unzip -p ... classes*.dex |
  grep -c <simbol-baru>`) + `force-stop` sebelum install/uji.
- Setelah `install -r`, proses app kadang STALE — selalu `force-stop` dulu.
- Dump uiautomator hanya memuat baris RecyclerView yang menempel (tidak bisa
  dipakai membuktikan seksi tidak ada); screenshot lebih terpercaya. Dump juga
  melewatkan node GONE dan lambat (kontrol player keburu hide).

## Pelajaran build 2
- JANGAN percaya "BUILD SUCCESSFUL in 1s": up-to-date check sering bohong di
  sini. Rilis/uji SELALU `--rerun-tasks --no-build-cache`, lalu verifikasi
  simbol baru di dex + timestamp/size APK berubah.
