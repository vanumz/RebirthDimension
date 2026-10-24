#!/usr/bin/env python3
"""
Membuat resource pack "NullEntity-ResourcePack" untuk plugin RebirthDimension.

Isi:
  - Kelopak mata: 47 frame (level 0 = tertutup .. 46 = hampir terbuka; level 47 = terbuka penuh -> title dihapus).
    Satu GLYPH TUNGGAL per frame (tanpa potongan/sambungan, jadi tidak ada celah di layar).
  - Layar putih: satu glyph putih raksasa (fade in/out ditangani fade title bawaan klien, mulus per frame render).

Kenapa ada 4 set (GUI scale 1-4):
  Ukuran glyph di layar = 4 x height x GUI-scale piksel. Server tidak bisa membaca GUI scale klien, jadi tiap
  set memakai `height` berbeda sehingga ukuran akhirnya sama (~COVER_PX piksel) di GUI scale 1, 2, 3, atau 4.
  Gambar frame SAMA dipakai ke-4 set (hanya `height`/`ascent` yang beda), jadi pack tetap kecil.
  Plugin memilih set sesuai pengaturan GUI scale pemain (/guiscale).

Batas teknis yang dijaga:
  - gambar glyph 240x240 (< atlas font 256x256),
  - tinggi tepi kelopak diskalakan dalam baris gambar, jadi tampilannya konsisten di semua GUI scale.

Pemakaian:  python3 tools/generate_resourcepack.py [folder_output]
Butuh: pip install pillow numpy
"""
import hashlib
import json
import math
import os
import shutil
import sys
import zipfile

import numpy as np
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "NullEntity-ResourcePack"
NAMESPACE = "rebirth"
FONT_ID = "eyelid"

IMG = 240                 # gambar persegi 240x240 (muat di atlas font 256x256)
LEVELS = 48               # level 0..47; 47 = terbuka penuh (tidak digambar)
SCALES = (1, 2, 3, 4)     # GUI scale yang didukung
COVER_PX = 3900           # lebar/tinggi glyph di layar (piksel); menutup layar 16:9 sampai 4K dan 21:9 sampai 3440 px
SET_STRIDE = 0x40         # kode karakter set s: 0xE000 + (s-1) * 0x40 + level
WHITE_CHAR = 0xE100
WHITE_COVER_PX = 6000     # overlay putih jauh lebih besar (menutup 4K pada GUI scale 1)

FEATHER = 8.0             # kelembutan tepi (baris gambar)
OPEN_HALF = 75.0          # setengah tinggi celah saat terbuka penuh (baris gambar; 75 baris ~ 1220 px)
CURVE = 6.0               # lengkung bentuk mata (baris gambar)
EXP_A = 3.0               # kelengkungan eksponensial: celah halus di dekat tertutup


def smoothstep(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def edge_rows(o):
    """Posisi tepi celah (baris dari tengah) untuk bukaan o 0..1, eksponensial."""
    return -FEATHER + (OPEN_HALF + FEATHER) * (math.exp(EXP_A * o) - 1.0) / (math.exp(EXP_A) - 1.0)


def make_frame(level):
    o = level / (LEVELS - 1)
    ys = (np.arange(IMG) + 0.5) - IMG / 2.0
    xs = ((np.arange(IMG) + 0.5) - IMG / 2.0) / (IMG / 2.0)
    dy = np.abs(ys)[:, None]
    xn2 = (xs ** 2)[None, :]
    edge = edge_rows(o) - CURVE * (1.0 - o) ** 1.2 * xn2
    alpha = smoothstep((dy - edge) / FEATHER)
    rgba = np.zeros((IMG, IMG, 4), dtype=np.uint8)
    rgba[..., 3] = np.round(alpha * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def glyph_height(cover_px, scale):
    # ukuran di layar = 4 (skala title) * height * scale  ->  height = cover_px / (4 * scale)
    return int(math.ceil(cover_px / (4.0 * scale)))


def main():
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    tex_dir = os.path.join(OUT, "assets", NAMESPACE, "textures", "font", FONT_ID)
    font_dir = os.path.join(OUT, "assets", NAMESPACE, "font")
    os.makedirs(tex_dir)
    os.makedirs(font_dir)

    providers = []
    for level in range(LEVELS - 1):          # 0..46
        name = "f%02d" % level
        make_frame(level).save(os.path.join(tex_dir, name + ".png"), optimize=True)
        for s in SCALES:
            h = glyph_height(COVER_PX, s)
            providers.append({
                "type": "bitmap",
                "file": "%s:font/%s/%s.png" % (NAMESPACE, FONT_ID, name),
                "ascent": h // 2 - 3,
                "height": h,
                "chars": [chr(0xE000 + (s - 1) * SET_STRIDE + level)],
            })

    Image.new("RGBA", (16, 16), (255, 255, 255, 255)).save(os.path.join(tex_dir, "white.png"), optimize=True)
    wh = glyph_height(WHITE_COVER_PX, 1)
    providers.append({
        "type": "bitmap",
        "file": "%s:font/%s/white.png" % (NAMESPACE, FONT_ID),
        "ascent": wh // 2 - 3,
        "height": wh,
        "chars": [chr(WHITE_CHAR)],
    })

    with open(os.path.join(font_dir, FONT_ID + ".json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, indent=1)      # ensure_ascii: karakter ditulis sebagai \uXXXX

    mcmeta = {"pack": {
        "description": "Null Entity - kelopak mata & layar putih (RebirthDimension)",
        "min_format": [75, 0],
        "max_format": [100, 0],
    }}
    with open(os.path.join(OUT, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump(mcmeta, f, indent=2)

    zip_path = OUT.rstrip("/\\") + ".zip"
    if os.path.exists(zip_path):
        os.remove(zip_path)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(OUT):
            for fn in sorted(files):
                full = os.path.join(root, fn)
                z.write(full, os.path.relpath(full, OUT).replace(os.sep, "/"))
    sha1 = hashlib.sha1(open(zip_path, "rb").read()).hexdigest()
    print("Selesai:", zip_path)
    print("SHA-1  :", sha1)


if __name__ == "__main__":
    main()
