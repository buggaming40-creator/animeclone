# AnimeClone — Klon gaya AnimeLovers v3 (scraper oploverz)

Aplikasi Android untuk streaming anime subtitle Indonesia dengan sumber
`oploverz.ch`, dengan tampilan meniru **AnimeLovers v3** (header tamu + sapaan,
chip genre, pengumuman, ticker, banner, seksi Riwayat/Genre/Ongoing, nav
mengambang). Tanpa login, tanpa VIP/EXP/chat/komentar. Dibuat oleh
**nansoffc**.

> Hanya gaya & alur yang ditiru (tata letak, komponen). Nama, teks, logo, dan
> semua aset milik AnimeLovers tidak disalin.

## Fitur

- **Beranda AL** — sapaan pagi/siang/sore/malam + lonceng pengumuman, chip genre
  (ketuk = cari), kartu pengumuman, ticker sedang tayang, banner auto-scroll,
  seksi Riwayat (kartu 16:9 + tombol play + resume), Genre Pilihan warna-warni,
  Ongoing Update + pil filter Semua/Anime/Donghua, grid Rilis Terbaru.
- **Cari / Tersimpan / Riwayat** — sama seperti NsNime (bookmark + lencana
  episode baru, progress menit, resume).
- **Detail anime** — poster tengah, meta, chip genre, sinopsis, urut episode,
  Putar Sekarang / Unduh (GoFile).
- **Player** — kecepatan, daftar episode, ganti server, prev/next, info 2 baris,
  kontrol ala YouTube.
- **Setelan** — header profil tamu + tema, 8 warna aksen (bawaan periwinkle),
  preferensi player, penyimpanan, tentang.

## Kebutuhan

Sama seperti NsNime: JDK 17, SDK 36, minSdk 26. Package
`com.animeclone.app` (bisa terinstal berdampingan dengan NsNime).

## Build & install

```bash
cd /home/nansoffc/AnimeClone
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/AnimeClone-debug.apk
/usr/bin/adb -s emulator-5554 install -r app/build/outputs/apk/debug/AnimeClone-debug.apk
```

## Struktur

Sama seperti NsNime (`java/com/animeclone/app/`, `res/...`), plus:

```
  HomeAdapter.java     # baris Riwayat/Genre/Ongoing-head/Rilis/Top
  HistoryCardAdapter.java + item_history_card.xml
  ui_item_genre.xml / ui_item_ongoing_head.xml
  ic_bell.xml ic_person.xml ic_list.xml ic_sort.xml ui_bg_avatar.xml ui_bg_dot.xml
```

## Catatan

Proyek pribadi untuk pemakaian sendiri. Tidak berafiliasi dengan AnimeLovers
maupun oploverz.ch. Hormati hak cipta pemilik konten.
