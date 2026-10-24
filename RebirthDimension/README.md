# RebirthDimension

Plugin Paper untuk Minecraft Java **1.21.11** (Java 21). Pemain yang mati dikirim ke **satu dunia Rebirth bersama**
(secara default dunia **The End** yang kosong/void + aurora partikel; jenis dimensi bisa diganti lewat config), menjalani **timer pribadi 30 menit** yang tetap berjalan saat offline atau server
restart, lalu dikembalikan ke spawn dunia utama dengan efek transisi.

> **Status verifikasi (jujur):** source ini ditulis dan dicek sintaks/tipenya terhadap *stub* API buatan sendiri.
> Belum pernah dikompilasi terhadap `paper-api` asli dan belum pernah dijalankan di server. Build pertama lewat
> GitHub Actions adalah langkah verifikasi sebenarnya (lihat bagian "Yang sudah dan belum diverifikasi").

## Persyaratan
- Java 21
- Paper 1.21.11 (plugin ini memakai API Paper: Adventure, `teleportAsync`, `ParticleBuilder`; tidak jalan di Spigot murni/Folia)
- Maven 3.9+ (hanya untuk build lokal; GitHub Actions sudah menyiapkannya)

## Struktur
```
<root repo>/
├── .github/workflows/build.yml
└── RebirthDimension/
    ├── pom.xml
    ├── README.md
    └── src/main/
        ├── java/com/vanumz/rebirthdimension/
        │   ├── Main.java                 # entry point, wiring, reload
        │   ├── Settings.java             # baca + validasi config
        │   ├── DimensionManager.java     # buat/muat world Rebirth (void), titik spawn/return
        │   ├── VoidChunkGenerator.java   # generator kosong (tanpa bedrock)
        │   ├── TimerManager.java         # timer persisten (players.yml, tulis atomik)
        │   ├── PlayerData.java
        │   ├── RebirthManager.java       # alur death -> rebirth -> keluar
        │   ├── RebirthListener.java      # death/respawn/join/quit/teleport/command
        │   ├── ProtectionListener.java   # perlindungan di dunia Rebirth
        │   ├── BoundaryTask.java         # batas area
        │   ├── BossBarManager.java
        │   ├── EffectManager.java        # efek masuk / keluar / tiba
        │   ├── AuroraManager.java        # aurora partikel
        │   ├── ColorRamp.java, SoundSpec.java
        │   └── RebirthCommand.java       # /rebirth (admin)
        └── resources/ plugin.yml, config.yml
```

## Cara kerja singkat
1. **Mati** di world yang ada di `rebirth.enabled-worlds` -> timer dimulai **saat kematian** dan langsung disimpan ke
   `plugins/RebirthDimension/data/players.yml` (timestamp waktu berakhir).
2. **Respawn**: lokasi respawn diganti langsung ke dunia Rebirth (tanpa teleport kedua, jadi tidak ada tabrakan
   event respawn/teleport). Efek masuk dimainkan sekali.
3. **Timer** dicek tiap 1 detik; BossBar `Rebirth In: MM:SS` diperbarui. Mati lagi saat timer aktif **tidak**
   mereset atau memperpanjang timer.
4. **Offline/restart**: karena yang disimpan adalah waktu berakhir, offline 20 menit dari 30 menit berarti sisa ~10 menit.
   Saat login: jika masih aktif pemain dikembalikan ke Rebirth (tanpa efek masuk jika memang sudah di sana); jika sudah
   habis pemain dikeluarkan.
5. **Selesai**: BossBar disembunyikan, efek keluar dimainkan, lalu teleport ke dunia utama. Data timer baru dihapus
   **setelah** teleport sukses; jika gagal akan dicoba lagi tanpa memutar ulang efek. Teleport tidak bergantung pada efek.
6. **Area kecil**: pemain hanya bisa terbang dalam radius **20 blok** dari pusat (`boundary.radius`); begitu keluar radius langsung di-teleport ke **tengah**. Tidak bisa terbang ke bawah karena ada **lantai Barrier tak terlihat** tepat di bawah `boundary.min-y`, dan batas atas `boundary.max-y`. Barrier hanya dibangun di world Rebirth. Semua pemain muncul di titik yang sama (`spawn-scatter: 0`).
7. **Tidak bisa kabur**: teleport keluar dari dunia Rebirth dibatalkan (termasuk portal, ender pearl, chorus fruit, command
   plugin lain) kecuali teleport internal plugin atau pemain ber-permission `rebirth.bypass`.

## Build
```bash
cd RebirthDimension
mvn -B clean package
# hasil: target/RebirthDimension.jar
```

## Upload ke GitHub & GitHub Actions
1. **Backup dulu** repo lama: di GitHub buat branch cadangan (mis. `prototype-lama`) dari `main`, atau salin repo ke folder lain.
2. Ekstrak ZIP, lalu salin isinya ke root repo: folder `RebirthDimension/` dan `.github/` (timpa `build.yml` lama jika perlu).
3. ```bash
   git add .
   git commit -m "RebirthDimension: dunia bersama, aurora, efek transisi"
   git push origin main
   ```
4. Buka tab **Actions** -> workflow **Build RebirthDimension** (jalan otomatis saat push; atau **Run workflow** manual).
5. Setelah hijau, buka run tersebut -> bagian **Artifacts** -> unduh `RebirthDimension` (ZIP berisi `RebirthDimension.jar`).
   Jika merah, buka log langkah *Build with Maven* dan kirim pesan error-nya.

## Memasang di server Magmanode
1. **Backup** folder `world*` dan `plugins/RebirthDimension/` (config/data prototype lama) dari File Manager/FTP.
2. Hentikan server. Hapus/pindahkan `RebirthDimension.jar` lama dari `plugins/`.
3. **Pindahkan `plugins/RebirthDimension/config.yml` lama** (supaya config baru dibuat otomatis). Folder world Rebirth lama
   (prototype) jangan dihapus; plugin baru memakai nama world baru `null_entity` sehingga blok bedrock lama tidak muncul.
4. Unggah `RebirthDimension.jar` baru ke `plugins/`, pastikan server memakai Java 21 dan Paper 1.21.11.
5. Start server, cek console: harus ada `RebirthDimension aktif.` dan world `null_entity` terbuat.
6. Sesuaikan `plugins/RebirthDimension/config.yml`, lalu `/rebirth reload`.

> Catatan penting: data `players.yml` prototype lama tidak dibaca (formatnya tidak diketahui). Pemain yang sedang menjalani
> timer prototype lama tidak akan otomatis punya timer baru; kirim manual dengan `/rebirth send <pemain>` jika perlu.


## Tampilan "Null Entity" (efek masuk / keluar)
Dimensi ini bernama **Null Entity** (`rebirth.dimension-name`, dipakai di pesan, judul, dan BossBar lewat `<dimension>`; nama plugin/jar tetap RebirthDimension).
- **BossBar:** `Terlahir Kembali : MM:SS` (label bergradasi, waktu **bold**), diatur di `bossbar.title`.
- **Masuk:** layar sudah hitam (kelopak tertutup dari adegan kematian), lalu **berkedip beberapa kali** dan **terbuka** (overlay resource pack; tanpa pack otomatis jatuh ke BLINDNESS; `effects.enter.blink-mode`).
  Saat mata mulai terbuka: pita-pita partikel **putih** berputar berlawanan arah mengitari tubuh, heliks cahaya naik, cincin di kaki menyempit, lalu ledakan cahaya, plus judul `NULL ENTITY / Terlahir Kembali`.
- **Keluar:** pita putih naik memutari tubuh makin cepat + pilar cahaya, **layar memutih** menjelang teleport, lalu putihnya **memudar** setelah tiba di dunia utama (`effects.exit.white-flash`, `fade-ticks`).
- **Stardust:** debu bintang ungu/biru/putih melayang di sekitar pemain (`stardust.*`, jumlah per update dan radius bisa diatur).
- Pesan notifikasi memakai gradien MiniMessage. (Tag `<shadow>` sengaja tidak dipakai karena belum dipastikan didukung versi MiniMessage di server.)

## Adegan kematian + kelopak mata (butuh resource pack untuk tampilan penuh)
Urutan saat pemain mati di world yang terdaftar:
1. **Respawn dipaksa otomatis** beberapa tick setelah mati (`death.instant-respawn`), jadi tombol Respawn/Title Screen tidak sempat dipakai. Death screen bisa muncul sekilas (1-2 tick); layar itu milik klien dan tidak bisa disembunyikan server.
2. Pemain muncul di tempat mati dalam **spectator**, dan **kamera orbit orang ketiga** (spectate sebuah entity kamera) berputar pelan melihat **tubuh pemain yang tergeletak** (entity Mannequin dengan skin pemain; pose `SLEEPING` atau `SWIMMING`). Ada partikel jiwa halus. Semua entity adegan non-persisten dan dibersihkan di akhir/quit/disable.
3. **Efek kosmik:** menjelang akhir (`death.scene.cosmic-lead-ticks`, default 50 tick) muncul pusaran warna-warni putih/biru/ungu/cyan/pink yang naik dan menyempit, pilar cahaya, awan debu bintang, kilau putih yang mengorbit, lalu **ledakan nova** tepat saat kelopak mulai menutup (`death.scene.cosmic-effect`).
4. **Kelopak mata menutup horizontal** (kelopak atas dan bawah bertemu di tengah, tepi lembut) lalu layar hitam tertahan.
5. Teleport ke Null Entity saat layar hitam, lalu kelopak **berkedip beberapa kali dan terbuka**, partikel putih mulai, judul muncul.
Timer tetap dimulai saat **kematian** (bukan setelah adegan), jadi adegan ~5 detik ikut menghitung. Jika adegan gagal di tengah jalan, pemain tetap dikirim ke dimensi (fallback).

### Resource pack NullEntity
Kelopak mata dan layar putih memakai **title dengan font kustom** dari resource pack (`resourcepack/NullEntity-ResourcePack.zip`, dibuat oleh `tools/generate_resourcepack.py`; format 75+ lewat `min_format`/`max_format`).
**SHA-1 pack saat ini:** `d867e46e1341276ba43dbe9acc740aba5160fcd7` (HARUS cocok dengan file yang kamu unggah; hitung ulang jika kamu membuat ulang pack).

Pasang dengan **salah satu** cara (jangan keduanya):
- **Cara A, server.properties:**
```
resource-pack=<link unduhan LANGSUNG ke zip>
resource-pack-sha1=d867e46e1341276ba43dbe9acc740aba5160fcd7
require-resource-pack=true
```
- **Cara B, plugin mengirim sendiri (tidak butuh server.properties):** di `config.yml`
```
resource-pack:
  send-on-join: true
  url: "<link unduhan LANGSUNG ke zip>"
  sha1: "d867e46e1341276ba43dbe9acc740aba5160fcd7"
  required: true
```
Link harus langsung ke file zip, contoh GitHub: `https://raw.githubusercontent.com/<user>/<repo>/main/resourcepack/NullEntity-ResourcePack.zip` (BUKAN link halaman `github.com/.../blob/...`). Jika kamu mengganti file di GitHub, ingat raw GitHub bisa tertahan cache beberapa menit.

**Deteksi pack:** pack dari `server.properties` dikirim di fase konfigurasi login sehingga event statusnya sering tidak sampai ke plugin. Karena itu mode `AUTO` menganggap pack ada jika server dikonfigurasi mengirim pack (server.properties terisi atau `send-on-join`) dan pemain tidak melaporkan gagal/menolak. Event status (jika ada) tercatat di console dan di `/rebirth debug`.

### Jika kelopak mata tidak tampil (diagnosa)
1. Jalankan **`/rebirth packtest`**: memutar kelopak menutup, layar putih memudar, lalu kelopak membuka (~8 detik) TANPA bergantung deteksi.
   - Layar menghitam dengan tepi lembut = pack aktif dan benar.
   - Tidak ada perubahan atau muncul kotak/karakter aneh = pack belum aktif di kliennya. Cek: link langsung, SHA-1 cocok, pemain menerima pack (Esc > Options > Resource Packs harus ada "Null Entity" di server pack), dan format pack.
2. **`/rebirth debug`** menampilkan mode yang dipakai (`KELOPAK` / `BLINDNESS`), status event terakhir, dan apakah server.properties terisi.
3. Jika pack terpasang manual di klien dan kamu tetap mau memaksa: `effects.enter.blink-mode: EYELID` dan `effects.exit.white-mode: OVERLAY`.
4. Console mencatat tiap tahap beserta mode yang dipakai; kirim potongan log jika masih bermasalah.
Catatan teknis: tiap gambar glyph dibuat 240x240 px karena atlas font Minecraft 256x256 (gambar lebih besar tidak muat dan tidak akan tampil); ukuran tampil diatur lewat `height`.

### Kelopak & layar putih di semua GUI scale dan perangkat
Rancangan lama (satu glyph per frame, atau 9 potongan glyph yang disambung) bermasalah: ukuran tampil terikat GUI scale, potongan meninggalkan celah (jarak glyph bawaan 4-16 piksel di layar), dan 279 tekstur glyph membuat patah-patah. Sekarang:
- **Satu glyph tunggal per frame**, tanpa sambungan, sehingga tidak ada celah. Gambar 240x240 (muat di atlas font 256x256).
- **4 set (GUI scale 1, 2, 3, 4)** memakai gambar frame yang sama tapi `height` berbeda, sehingga ukuran akhir di layar sama (~3900 px, menutup layar 16:9 sampai 4K dan 21:9 sampai 3440 px). GUI scale 5 ke atas memakai set 4.
- **Layar putih** satu glyph putih raksasa (~6000 px); fade in/out dikerjakan fade title bawaan klien, jadi mulus per frame render di perangkat apa pun.
- **Bukaan kelopak eksponensial**: celah sangat halus di dekat tertutup, dan waktunya kira-kira seragam di semua resolusi.
- Cakupan diverifikasi lewat simulasi tata letak font (model buatan sendiri, bukan klien asli): frame tertutup menutup 100% layar pada 1366x768, 1920x1080, 2560x1440, 2560x1080, 3440x1440, dan 3840x2160 di GUI scale 1-4.

**Server tidak bisa membaca GUI scale klien** (tidak ada paket yang membawanya). Karena itu tiap pemain menyetel sekali: **`/guiscale <1-4>`** (cek di Esc > Options > Video Settings > GUI Scale), tersimpan di `gui-scales.yml`. Belum disetel = `effects.gui-scale-default` (2). `/guiscale test` memutar kelopak + layar putih. Pemain yang belum menyetel mendapat petunjuk saat join. Salah setel tidak merusak apa pun; hanya ukuran/ketebalan kelopak yang sedikit berbeda, layar tetap tertutup.

**Batas jujur:** kehalusan gerak kelopak dibatasi 20 pembaruan per detik (tick server); fade putih tidak dibatasi itu. Kehalusan 60 fps penuh untuk kelopak hanya bisa lewat mod klien atau shader core.
Regenerasi pack: `python3 tools/generate_resourcepack.py` (ubah `COVER_PX` untuk cakupan lain), lalu ganti SHA-1.

**Soal "loading terrain":** saat pindah dimensi klien menampilkan layar *Loading terrain* bawaan Minecraft, dan itu **layar GUI milik klien yang tidak bisa ditimpa plugin**. Yang bisa dilakukan plugin: menahan layar tetap hitam sebelum teleport dan langsung melanjutkan hitam begitu HUD kembali, plus chunk tujuan sudah dimuat (spawn chunk) supaya layar loading singkat. Apakah layar itu terlihat sekilas di klien kamu perlu dicek di game.

**Catatan jujur tentang layar putih:** mode `FLASH` (tanpa pack) memakai partikel `FLASH` di depan mata dan belum teruji di klien; mode overlay (pack) lebih andal.

## Spawn bed (kembali ke bed setelah Rebirth)
- **Tersimpan per pemain:** saat pemain berhasil menyetel respawn point lewat bed di Overworld (event `PlayerSetSpawnEvent` dengan cause `BED`; dunia ber-environment NORMAL, bukan dunia Rebirth), koordinat bed dicatat per UUID di `plugins/RebirthDimension/bed-spawns.yml`. Menyetel bed baru menimpa catatan lama.
- **Satu bed untuk banyak pemain:** boleh. Setiap pemain punya catatan sendiri, tidak ada batas satu bed satu pemain.
- **Diperiksa lagi saat Rebirth selesai:** bed harus masih berupa bed dan harus ada tempat berdiri yang aman di sebelahnya. Jika valid, pemain muncul di sebelah bed itu.
- **Bed hancur = fallback:** catatan dihapus saat bed dihancurkan lewat block break atau ledakan (entity maupun blok). Jika bed hilang/terhalang saat timer selesai, atau pemain tidak punya bed, dipakai tujuan fallback `return.*` (default spawn dunia utama) dan muncul pesan `bed-fallback`.
- **Tahan restart:** data bed dipisah dari timer (`bed-spawns.yml` vs `data/players.yml`), ditulis atomik, dan file rusak dicadangkan otomatis.
- **Config:** `return.use-bed-spawn: true` (set `false` untuk selalu memakai tujuan fallback).
- **Cek:** `/rebirth bed [pemain]` menampilkan bed tercatat dan apakah masih valid.

Catatan jujur:
- Pemain yang **sudah punya bed sebelum plugin ini dipasang** belum tercatat; mereka perlu klik kanan bed itu sekali lagi (cukup sampai muncul pesan respawn point). Sampai itu, mereka kembali ke fallback.
- Bed yang hilang lewat cara lain (piston, WorldEdit, plugin lain) tidak memicu penghapusan, tetapi tertangkap oleh pemeriksaan saat keluar (catatan dihapus saat itu). Bed yang hanya **terhalang** tidak dihapus; pemain memakai fallback kali ini saja.
- Jika dunia bed belum dimuat, catatan dipertahankan dan fallback dipakai.
- Respawn Anchor tidak didukung. Posisi berdiri di sebelah bed dihitung oleh plugin (mirip vanilla, belum tentu identik).
- Titik respawn bed asli milik vanilla tidak diubah plugin; mati lalu "respawn" ke bed tidak dipakai karena respawn selalu dialihkan ke adegan kematian dan dimensi.

## Perintah & permission
| Perintah | Fungsi |
|---|---|
| `/rebirth status [pemain]` | Sisa waktu |
| `/rebirth send <pemain> [menit]` | Kirim pemain online ke Rebirth |
| `/rebirth release <pemain>` | Keluarkan pemain (offline: ditandai selesai, keluar saat login) |
| `/rebirth reload` | Muat ulang config |
| `/rebirth debug [pemain]` | Mode kedip/layar putih yang dipakai + status resource pack |
| `/rebirth scene [pemain]` | Tes adegan kamera + tubuh tanpa harus mati |
| `/rebirth blink [pemain]` | Tes kelopak menutup lalu membuka |
| `/guiscale <1-4\|test>` | (pemain) Setel GUI scale untuk overlay kelopak/putih; `test` memutar tes |
| `/rebirth bed [pemain]` | Spawn bed tercatat dan statusnya |
| `/rebirth packtest [pemain]` | Tes overlay kelopak + layar putih tanpa bergantung deteksi pack |

`rebirth.admin` (default op) untuk perintah; `rebirth.bypass` (default false) untuk melewati blok teleport/bangun/command.

## Konfigurasi
Semua opsi ada di `config.yml` dengan komentar. Nilai di luar batas (mis. interval aurora terlalu kecil, radius negatif)
dikoreksi otomatis dan dicatat sebagai warning di console. Bagian utama: `rebirth` (durasi, world, pusat), `return`
(tujuan keluar), `flight`, `boundary`, `protection`, `bossbar`, `aurora`, `effects.enter/exit` (durasi, kepadatan, suara),
`messages`. Suara memakai resource location (`minecraft:...`), jadi bisa memakai suara dari resource pack.

## Cara menguji
**Timer & penyimpanan**
1. Matikan keepInventory kalau perlu, bunuh diri di `world` (`/kill`). Pastikan respawn di Rebirth, BossBar muncul, dan `data/players.yml` terbentuk.
2. `/rebirth status` -> sisa waktu berkurang.
3. Logout 1-2 menit, login: sisa waktu harus berkurang sebanyak waktu offline (bukan reset).
4. Restart server saat timer jalan: setelah login sisa waktu tetap benar.
5. Untuk tes cepat set `rebirth.duration-seconds: 60` atau pakai `/rebirth send <pemain> 1`. Mati lagi di Rebirth (admin `/kill` dengan bypass) tidak boleh memperpanjang timer.
6. Biarkan timer habis (online dan offline): pemain kembali ke spawn dunia utama, gamemode & kemampuan terbang kembali seperti semula.

**Aurora**: terbang di Rebirth; pita muncul di atas dalam jarak `aurora.render-distance`. Coba ubah `colors`, `height`, `speed`,
`max-points-per-update`, lalu `/rebirth reload`. Perhatikan TPS/MSPT dengan beberapa pemain.

**Efek masuk/keluar**: masuk lewat kematian (efek heliks naik) dan keluar saat timer habis (efek mengerucut naik). Efek masuk
tidak boleh muncul saat reconnect di dalam Rebirth. Tes beberapa pemain mati bersamaan, dan quit saat animasi (tidak boleh ada error di console).

**Batas area & teleportasi**: terbang melewati `boundary.radius` (default 20 blok) -> langsung di-teleport ke tengah (maks sekali per `return-cooldown-ticks`); `hard-margin` > 0 menambah zona dorongan halus dulu. Coba terbang ke bawah: harus tertahan lantai tak terlihat. Coba `/spawn`/`/home`, ender pearl, chorus fruit, dan portal: semua harus diblokir.

## Keterbatasan aurora (baca ini)
Aurora di plugin ini adalah **partikel server-side**, bukan tekstur langit:
- Partikel `DUST` berbentuk kotak kecil, tanpa transparansi bertingkat. Dari dekat terlihat seperti "tirai butir cahaya", bukan kain mulus.
- Klien hanya menggambar partikel jauh jika paket ditandai *force*; plugin memakainya, tetapi partikel kecil di jarak >60 blok sulit terlihat. Karena itu aurora digambar hanya di sekitar pemain (`render-distance`) dan dianimasikan dengan pola yang tetap di dunia.
- Pemain yang mengatur *Particles: Decreased/Minimal* akan melihat lebih sedikit/tidak ada aurora.
- Langit adalah langit vanilla milik environment yang dipilih (The End secara default); `sky-time` hanya mengunci jam untuk environment NORMAL. Bukan skybox custom.

**Pendekatan B (belum diimplementasikan):** aurora sebagai model/tekstur dari **resource pack** yang ditampilkan lewat
*Display Entity* (mis. ItemDisplay dengan model quad transparan bergradasi) hanya di dunia Rebirth. Ini bisa dilihat pemain vanilla
yang memasang resource pack, mendukung transparansi sungguhan, dan bisa dibatasi ke dunia Rebirth karena entity-nya hanya ada di sana.
Syaratnya ada aset (model + tekstur aurora) dan resource pack dikirim ke pemain. Skybox sungguhan di vanilla untuk dimensi
overworld **tidak bisa** hanya dengan resource pack; itu butuh shader/mod klien (mis. OptiFine/Iris/FabricSkyBoxes) dan tidak
berlaku untuk pemain vanilla. Kalau kamu punya resource pack aurora sendiri, kirim struktur modelnya dan integrasinya bisa ditambahkan.

## Yang sudah dan belum diverifikasi
**Sudah:** seluruh source dikompilasi bersih dengan `javac` Java 21 terhadap stub API buatan sendiri (memeriksa sintaks dan konsistensi antar-kelas).

**Belum:**
- Kompilasi terhadap `paper-api 1.21.11-R0.1-SNAPSHOT` asli (nama method yang kupakai berdasarkan pengetahuanku tentang API; kalau ada yang berbeda, GitHub Actions akan menampilkannya sebagai error kompilasi).
- Seluruh perilaku runtime di server (death/respawn, teleport, timer offline, aurora, efek, batas area).
- Tampilan visual aurora dan performa dengan banyak pemain. Parameter default adalah tebakan awal yang perlu disetel di server.

## Catatan desain
- Lantai Barrier tak terlihat (hanya di world Rebirth, tepat di bawah `min-y`) mencegah terbang ke bawah; jika `boundary.radius` > 150 lantai dilewati dan batas vertikal yang menjaga pemain. Pemain yang memegang item Barrier akan melihat blok-blok itu.
- **Mengganti dimensi:** ubah `rebirth.environment` (`THE_END`, `NORMAL`, `NETHER`) **dan** `rebirth.world-name` ke nama baru, lalu `/rebirth reload`. World baru dibuat otomatis (void kosong); pemain dengan timer aktif yang masih di world lama otomatis dipindah ke world baru. Environment world yang sudah ada tidak bisa diubah (plugin hanya memberi warning), dan world lama tidak dihapus.
- **Pusat jauh dari 0,0 (wajib untuk THE_END):** dragon fight bawaan berpusat di koordinat 0,0 dan menampilkan BossBar "Ender Dragon" ke pemain dalam ~192 blok darinya. Karena itu `rebirth.center` default 3000.5 / 3000.5. Jika config lama masih memakai 0.5, ubah manual lalu restart.
- **End flash:** kilatan ungu besar di langit End (sejak 1.21.9) dihitung di sisi klien dan tidak bisa dimatikan dari plugin; hanya ada di environment THE_END.
- **Ender Dragon fight:** world End buatan plugin otomatis mendapat dragon fight bawaan Minecraft (naga, BossBar "Ender Dragon", podium portal bedrock). Plugin membersihkannya tiap 10 tick: fight ditandai sudah pernah dibunuh, naga dihapus, BossBar disembunyikan, dan podium (bedrock/end stone/obor) dibongkar. Lantai Barrier tidak ikut terhapus. Jika terlihat sekilas saat world baru dibuat, itu normal (maksimal beberapa detik).
- Item tidak bisa dibuang di Rebirth (`protection.no-item-drop`) agar tidak terbuang ke void. Kematian lewat command yang mem-bypass proteksi tetap menjatuhkan item sesuai aturan vanilla/server.
- Jika plugin dihapus saat pemain sedang menjalani Rebirth, gamemode ADVENTURE dan kemampuan terbang mereka tidak dipulihkan otomatis; pasang kembali plugin lalu `/rebirth release`.
