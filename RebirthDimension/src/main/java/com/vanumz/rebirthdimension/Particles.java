package com.vanumz.rebirthdimension;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Pengganti World#spawnParticle. Di Arclight, World#spawnParticle memanggil method NMS
 * (ServerLevel#sendParticlesSource) yang tidak ada sehingga melempar NoSuchMethodError.
 * Di sini partikel dikirim langsung ke tiap pemain lewat Player#spawnParticle, dengan
 * jangkauan yang sama seperti vanilla (32 blok normal, 512 blok jika force).
 */
final class Particles {

    private static final double NORMAL_RANGE_SQ = 32.0 * 32.0;
    private static final double FORCE_RANGE_SQ = 512.0 * 512.0;

    private static volatile boolean disabled;

    private Particles() {
    }

    static void spawn(World w, Particle particle, double x, double y, double z, int count,
                      double ox, double oy, double oz, double extra) {
        spawn(w, particle, x, y, z, count, ox, oy, oz, extra, null, false);
    }

    static <T> void spawn(World w, Particle particle, double x, double y, double z, int count,
                          double ox, double oy, double oz, double extra, T data) {
        spawn(w, particle, x, y, z, count, ox, oy, oz, extra, data, false);
    }

    static <T> void spawn(World w, Particle particle, double x, double y, double z, int count,
                          double ox, double oy, double oz, double extra, T data, boolean force) {
        if (disabled || w == null) {
            return;
        }
        double rangeSq = force ? FORCE_RANGE_SQ : NORMAL_RANGE_SQ;
        try {
            for (Player p : w.getPlayers()) {
                Location pl = p.getLocation();
                double dx = pl.getX() - x;
                double dy = pl.getY() - y;
                double dz = pl.getZ() - z;
                if (dx * dx + dy * dy + dz * dz > rangeSq) {
                    continue;
                }
                p.spawnParticle(particle, x, y, z, count, ox, oy, oz, extra, data, force);
            }
        } catch (LinkageError ex) {
            disabled = true;
            JavaPlugin.getPlugin(Main.class).getLogger()
                    .warning("Partikel dimatikan karena server tidak mendukung API partikel: " + ex);
        }
    }
}
