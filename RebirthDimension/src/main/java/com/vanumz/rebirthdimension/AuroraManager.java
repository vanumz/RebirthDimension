package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Aurora berbasis partikel (sisi server) di dunia Rebirth.
 *
 * Bentuk: cincin pita bergelombang yang mengelilingi pusat dunia pada beberapa radius, masing-masing
 * dengan beberapa lapisan. Pada tiap titik pita digambar "tirai" vertikal (beberapa sampel dari bawah ke atas)
 * dengan warna bergradasi (teal -> cyan -> biru -> ungu). Tutupan pita, tinggi tirai, dan sinar vertikal
 * bergeser perlahan terhadap waktu sehingga terlihat mengalir.
 *
 * Performa: hanya kolom dalam jarak render dari minimal satu pemain yang digambar, jumlah paket per update
 * dibatasi (max-points-per-update), dan tiap partikel hanya dikirim ke pemain yang cukup dekat.
 *
 * Catatan jujur: ini partikel, bukan tekstur langit. Ukuran maksimum partikel dust kecil, tidak ada transparansi
 * sungguhan, dan klien hanya merender partikel sesuai pengaturan "Particles" miliknya.
 */
public final class AuroraManager {

    private static final double TAU = Math.PI * 2;
    private static final int SAMPLES = 6;

    private final Main plugin;
    private BukkitTask task;
    private long startMillis;
    private long counter;
    private int failures;

    public AuroraManager(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        Settings s = plugin.settings();
        if (!s.auroraEnabled) {
            return;
        }
        startMillis = System.currentTimeMillis();
        failures = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, s.auroraIntervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        try {
            render();
            failures = 0;
        } catch (Throwable ex) {
            failures++;
            plugin.getLogger().warning("Render aurora gagal (" + failures + "/5): " + ex);
            if (failures >= 5) {
                plugin.getLogger().severe("Aurora dimatikan setelah 5 kegagalan berturut-turut.");
                stop();
            }
        }
    }

    private void render() {
        World w = plugin.dimension().getWorld();
        if (w == null) {
            return;
        }
        List<Player> viewers = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (p.isOnline() && !p.isDead()) {
                viewers.add(p);
            }
        }
        if (viewers.isEmpty()) {
            return;
        }

        Settings s = plugin.settings();
        double t = (System.currentTimeMillis() - startMillis) / 1000.0 * s.auroraSpeed;
        double rd2 = s.auroraRenderDistance * s.auroraRenderDistance;
        double cx = s.centerX;
        double cy = s.centerY;
        double cz = s.centerZ;

        // Tahap 1: kumpulkan kolom yang terlihat oleh minimal satu pemain.
        // Kolom: {x, z, baseY, tinggi, ringIdx, layerIdx, sudut, cover, ray}
        List<double[]> columns = new ArrayList<>();
        for (int ring = 0; ring < s.auroraRings; ring++) {
            for (int layer = 0; layer < s.auroraLayers; layer++) {
                double baseR = (ring + 0.5) * s.auroraRingSpacing + layer * 9.0;
                int n = (int) Math.max(24, Math.floor(TAU * baseR / s.auroraSpacing));
                double phase = ring * 1.7 + layer * 2.9;
                int coverFreq = 3 + (ring % 3) + layer;
                int rayFreq = (int) Math.max(8, Math.round(TAU * baseR / 12.0));
                for (int i = 0; i < n; i++) {
                    double th = TAU * i / n;
                    double r = baseR + 7.0 * Math.sin(3 * th + t * 0.30 + phase)
                            + 3.0 * Math.sin(7 * th - t * 0.50 + phase * 0.5);
                    double x = cx + Math.cos(th) * r;
                    double z = cz + Math.sin(th) * r;
                    if (!anyViewerNear(viewers, x, z, rd2)) {
                        continue;
                    }
                    double cover = 0.5 + 0.5 * Math.sin(coverFreq * th + t * 0.20 + phase);
                    if (cover < 0.30) {
                        continue;
                    }
                    double baseY = cy + s.auroraBaseHeight + ring * 2.0 + layer * 3.0
                            + 5.0 * Math.sin(2 * th + t * 0.25 + phase);
                    double h = s.auroraHeight * (0.45 + 0.55 * cover)
                            * (0.85 + 0.15 * Math.sin(5 * th - t * 0.35 + phase));
                    double ray = 0.5 + 0.5 * Math.sin(rayFreq * th + t * 0.9);
                    columns.add(new double[]{x, z, baseY, h, ring, layer, th, cover, ray});
                }
            }
        }
        if (columns.isEmpty()) {
            return;
        }

        // Tahap 2: terapkan anggaran paket per update (stride berputar agar semua kolom kebagian giliran).
        int allowedColumns = Math.max(1, s.auroraMaxPoints / SAMPLES);
        int stride = columns.size() > allowedColumns ? (int) Math.ceil(columns.size() / (double) allowedColumns) : 1;
        int offset = (int) (counter++ % stride);

        for (int idx = offset; idx < columns.size(); idx += stride) {
            emitColumn(w, viewers, s, columns.get(idx), t, rd2);
        }
    }

    private void emitColumn(World w, List<Player> viewers, Settings s, double[] col, double t, double rd2) {
        double x = col[0];
        double z = col[1];
        double baseY = col[2];
        double h = col[3];
        int ring = (int) col[4];
        int layer = (int) col[5];
        double cover = col[7];
        double ray = col[8];

        List<Player> receivers = new ArrayList<>();
        for (Player p : viewers) {
            Location pl = p.getLocation();
            double dx = pl.getX() - x;
            double dz = pl.getZ() - z;
            if (dx * dx + dz * dz <= rd2) {
                receivers.add(p);
            }
        }
        if (receivers.isEmpty()) {
            return;
        }

        double hueShift = ring * 0.17 + layer * 0.13 + 0.12 * Math.sin(t * 0.08 + ring);
        double top = 0.40 + 0.60 * ray;
        for (int k = 0; k < SAMPLES; k++) {
            double v = (k + 0.5) / SAMPLES;
            if (v > top) {
                continue;
            }
            double y = baseY + v * h;
            double u = 0.75 * v + hueShift;
            Color from = s.auroraRamp.at(u);
            Color to = s.auroraRamp.at(u + 0.22);
            double sizeD = s.auroraParticleSize * (1.15 - 0.45 * v) * (0.7 + 0.5 * cover);
            float size = (float) Math.max(0.5, Math.min(4.0, sizeD));
            // API Bukkit: dikirim ke pemain dalam jangkauan (force = terlihat jauh); dust kecil di jauh tetap tampak.
            Particles.spawn(w, Particle.DUST_COLOR_TRANSITION, x, y, z, 2,
                    0.7, Math.max(0.2, h / SAMPLES * 0.4), 0.7, 0.0,
                    new Particle.DustTransition(from, to, size), true);
        }
    }

    private static boolean anyViewerNear(List<Player> viewers, double x, double z, double rd2) {
        for (Player p : viewers) {
            Location pl = p.getLocation();
            double dx = pl.getX() - x;
            double dz = pl.getZ() - z;
            if (dx * dx + dz * dz <= rd2) {
                return true;
            }
        }
        return false;
    }
}
