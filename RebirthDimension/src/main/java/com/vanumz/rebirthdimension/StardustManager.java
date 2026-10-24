package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stardust angkasa (ungu/biru/putih) yang melayang di sekitar tiap pemain di dunia Rebirth.
 * Dikirim hanya ke pemain bersangkutan, dalam radius kecil (&lt;= 30 blok), dengan jumlah per update terbatas.
 */
public final class StardustManager {

    private final Main plugin;
    private BukkitTask task;
    private int failures;

    public StardustManager(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        Settings s = plugin.settings();
        if (!s.stardustEnabled) {
            return;
        }
        failures = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, s.stardustIntervalTicks);
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
            plugin.getLogger().warning("Render stardust gagal (" + failures + "/5): " + ex);
            if (failures >= 5) {
                plugin.getLogger().severe("Stardust dimatikan setelah 5 kegagalan berturut-turut.");
                stop();
            }
        }
    }

    private void render() {
        World w = plugin.dimension().getWorld();
        if (w == null) {
            return;
        }
        Settings s = plugin.settings();
        List<Color> colors = s.stardustColors;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : w.getPlayers()) {
            if (!p.isOnline() || p.isDead()) {
                continue;
            }
            Location c = p.getLocation();
            for (int i = 0; i < s.stardustCount; i++) {
                // Titik acak dalam bola (sebaran merata volume).
                double u = r.nextDouble();
                double radius = s.stardustRadius * Math.cbrt(u);
                double theta = r.nextDouble() * Math.PI * 2;
                double cosPhi = r.nextDouble() * 2 - 1;
                double sinPhi = Math.sqrt(1 - cosPhi * cosPhi);
                double x = c.getX() + radius * sinPhi * Math.cos(theta);
                double y = c.getY() + radius * cosPhi;
                double z = c.getZ() + radius * sinPhi * Math.sin(theta);

                double roll = r.nextDouble();
                Color a = colors.get(r.nextInt(colors.size()));
                if (roll < 0.55) {
                    // Butir debu kecil yang berkelip singkat.
                    float size = (float) (0.5 + r.nextDouble() * 0.7);
                    p.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(a, size));
                } else if (roll < 0.80) {
                    Color b = colors.get(r.nextInt(colors.size()));
                    float size = (float) (0.7 + r.nextDouble() * 0.6);
                    p.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0,
                            new Particle.DustTransition(a, b, size));
                } else {
                    // Kilau putih yang melayang pelan.
                    p.spawnParticle(Particle.END_ROD, x, y, z, 0,
                            (r.nextDouble() - 0.5), (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5), 0.015);
                }
            }
        }
    }
}
