package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Efek masuk/keluar Rebirth ("Null Entity"): kedip mata (BLINDNESS), pusaran partikel putih yang memutari tubuh,
 * layar memutih saat keluar lalu memudar (partikel FLASH), dan efek tiba.
 *
 * Aturan keselamatan: tiap animasi berbatas durasi, satu animasi aktif per pemain, berhenti otomatis jika pemain
 * offline/mati, dan efek visual tidak pernah menentukan keberhasilan timer atau teleportasi.
 */
public final class EffectManager {

    private static final double TAU = Math.PI * 2;
    private static final Color WHITE = Color.fromRGB(255, 255, 255);
    private static final Color CYAN = Color.fromRGB(0x67e8f9);
    private static final Color VIOLET = Color.fromRGB(0xc4b5fd);

    private final Main plugin;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Set<UUID> blinking = new HashSet<>();
    private boolean flashColorSupported = true;
    private boolean flashBroken;

    public EffectManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isPlaying(UUID id) {
        return tasks.containsKey(id);
    }

    public void cancel(UUID id) {
        BukkitTask t = tasks.remove(id);
        if (t != null) {
            t.cancel();
        }
        if (blinking.remove(id)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }
    }

    public void cancelAll() {
        for (UUID id : new HashSet<>(blinking)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }
        blinking.clear();
        Iterator<BukkitTask> it = tasks.values().iterator();
        while (it.hasNext()) {
            it.next().cancel();
            it.remove();
        }
    }

    /** Skala kepadatan: dikurangi saat banyak pemain mengalami efek bersamaan. */
    private int n(int base) {
        int active = tasks.size();
        double load = active >= 8 ? 0.25 : (active >= 4 ? 0.5 : 1.0);
        return Math.max(1, (int) Math.round(base * load));
    }

    // ------------------------------------------------------------------ masuk

    /** Layar berkedip -> membuka mata -> pusaran partikel putih mengitari tubuh -> ledakan cahaya. */
    public void playEnter(Player player) {
        Settings s = plugin.settings();
        Settings.EffectSettings es = s.enter;
        if (!es.enabled) {
            plugin.rebirth().announceEntry(player);
            return;
        }
        cancel(player.getUniqueId());
        int blink = s.blinkTicks;
        if (blink > 0) {
            startBlink(player, blink);
        }
        final int delay = Math.max(0, blink - 20);
        final int duration = es.durationTicks;
        final int density = es.density;

        run(player, delay, duration, () -> {
            playSounds(player, es);
            plugin.rebirth().announceEntry(player);
        }, new Frame() {
            @Override
            public void frame(Player p, int tick) {
                double prog = tick / (double) duration;
                double ease = prog * prog * (3 - 2 * prog);
                Location base = p.getLocation();
                World w = base.getWorld();
                double bx = base.getX();
                double by = base.getY();
                double bz = base.getZ();

                // Pita cincin putih yang berputar berlawanan arah, mengerucut ke tubuh.
                double radius = 1.9 - 1.0 * ease;
                int pts = n(8);
                for (int b = 0; b < 5; b++) {
                    double y = by + 0.1 + b * 0.45;
                    double dir = (b % 2 == 0) ? 1.0 : -1.0;
                    double a0 = tick * 0.30 * dir * (1.0 + ease) + b * 1.1;
                    double r = radius + 0.12 * Math.sin(tick * 0.25 + b);
                    for (int k = 0; k < pts; k++) {
                        double a = a0 + k * TAU / pts;
                        white(w, bx + Math.cos(a) * r, y, bz + Math.sin(a) * r, 0.9f);
                    }
                }

                // Heliks naik (putih dengan semburat cyan) memutari badan.
                int trail = n(density);
                for (int st = 0; st < 3; st++) {
                    for (int j = 0; j < trail; j++) {
                        double a = tick * 0.55 + st * (TAU / 3) - j * 0.22;
                        double y = by + ((tick * 0.085 + j * 0.10) % 2.2);
                        double r = 0.85 + 0.15 * Math.sin(tick * 0.3 + st);
                        double x = bx + Math.cos(a) * r;
                        double z = bz + Math.sin(a) * r;
                        if (j % 2 == 0) {
                            glow(w, x, y, z);
                        } else {
                            tint(w, x, y, z, WHITE, CYAN, 1.0f);
                        }
                    }
                }

                // Cincin di kaki yang menyempit (energi terkumpul).
                if (tick % 2 == 0) {
                    double fr = 2.6 * (1.0 - ease) + 0.4;
                    int fp = n(14);
                    for (int k = 0; k < fp; k++) {
                        double a = k * TAU / fp + tick * 0.2;
                        glow(w, bx + Math.cos(a) * fr, by + 0.05, bz + Math.sin(a) * fr);
                    }
                }
                if (tick % 5 == 0) {
                    w.spawnParticle(Particle.REVERSE_PORTAL, bx, by + 1.0, bz, n(6), 0.6, 0.9, 0.6, 0.05);
                }
            }

            @Override
            public void finish(Player p) {
                burst(p.getLocation(), 28, 14);
            }
        });
    }

    // ----------------------------------------------------------------- keluar

    /** Pita putih naik memutari tubuh makin cepat, pilar cahaya, layar memutih menjelang teleport. */
    public void playExit(Player player) {
        Settings s = plugin.settings();
        Settings.EffectSettings es = s.exit;
        if (!es.enabled) {
            return;
        }
        cancel(player.getUniqueId());
        final int duration = es.durationTicks;
        final int density = es.density;
        final boolean whiteFlash = s.exitWhiteFlash;
        final int ramp = Math.min(duration, 26);

        run(player, 0, duration, () -> playSounds(player, es), new Frame() {
            @Override
            public void frame(Player p, int tick) {
                double prog = tick / (double) duration;
                double ease = prog * prog * (3 - 2 * prog);
                Location base = p.getLocation();
                World w = base.getWorld();
                double bx = base.getX();
                double by = base.getY();
                double bz = base.getZ();

                // Pita putih naik dari kaki melewati kepala, berputar makin cepat.
                int pts = n(10);
                for (int b = 0; b < 6; b++) {
                    double y = by + ((tick * (0.07 + 0.05 * ease) + b * 0.45) % 2.7);
                    double dir = (b % 2 == 0) ? 1.0 : -1.0;
                    double a0 = tick * 0.45 * dir * (1.0 + ease) + b * 0.9;
                    double r = 0.95 + 0.5 * ease + 0.1 * Math.sin(tick * 0.3 + b);
                    for (int k = 0; k < pts; k++) {
                        double a = a0 + k * TAU / pts;
                        white(w, bx + Math.cos(a) * r, y, bz + Math.sin(a) * r, 1.0f);
                    }
                }

                // Empat heliks cahaya.
                int trail = n(density);
                for (int st = 0; st < 4; st++) {
                    for (int j = 0; j < trail; j++) {
                        double a = -tick * 0.6 + st * (TAU / 4) - j * 0.2;
                        double y = by + ((tick * 0.11 + j * 0.12) % 2.6);
                        double r = 0.8 + 0.2 * Math.sin(tick * 0.35 + st);
                        double x = bx + Math.cos(a) * r;
                        double z = bz + Math.sin(a) * r;
                        if (j % 2 == 0) {
                            glow(w, x, y, z);
                        } else {
                            tint(w, x, y, z, WHITE, VIOLET, 1.0f);
                        }
                    }
                }

                // Pilar cahaya naik dari kaki.
                w.spawnParticle(Particle.END_ROD, bx, by + 0.1, bz, 0, 0.0, 1.0, 0.0, 0.2);
                int sparks = n(6);
                for (int k = 0; k < sparks; k++) {
                    double a = k * TAU / sparks + tick * 0.4;
                    w.spawnParticle(Particle.END_ROD, bx + Math.cos(a) * 0.6, by + 0.1, bz + Math.sin(a) * 0.6,
                            0, 0.0, 1.0, 0.0, 0.12 + 0.06 * ease);
                }

                // Layar memutih bertahap menjelang teleport.
                if (whiteFlash && tick >= duration - ramp && tick % 2 == 0) {
                    double k = (tick - (duration - ramp)) / (double) ramp;
                    flash(p, (int) Math.round(40 + 215 * Math.min(1.0, k)));
                }
            }

            @Override
            public void finish(Player p) {
                burst(p.getLocation(), 24, 16);
                if (whiteFlash) {
                    flash(p, 255);
                }
            }
        });
    }

    /** Tiba di dunia utama: putih penuh memudar, pita putih ringan naik memudar. */
    public void playArrival(Player player) {
        Settings s = plugin.settings();
        if (!s.exit.enabled) {
            return;
        }
        cancel(player.getUniqueId());
        final boolean whiteFlash = s.exitWhiteFlash;
        final int duration = whiteFlash ? s.exitFadeTicks : 20;

        run(player, 0, duration, () -> { }, new Frame() {
            @Override
            public void frame(Player p, int tick) {
                double prog = tick / (double) duration;
                if (whiteFlash && tick % 2 == 0) {
                    flash(p, (int) Math.round(255 * (1.0 - prog)));
                }
                Location base = p.getLocation();
                World w = base.getWorld();
                int pts = n(6);
                for (int b = 0; b < 3; b++) {
                    double y = base.getY() + ((tick * 0.08 + b * 0.8) % 2.4);
                    double dir = (b % 2 == 0) ? 1.0 : -1.0;
                    double a0 = tick * 0.4 * dir + b * 1.7;
                    double r = 1.1 + 0.5 * prog;
                    for (int k = 0; k < pts; k++) {
                        double a = a0 + k * TAU / pts;
                        white(w, base.getX() + Math.cos(a) * r, y, base.getZ() + Math.sin(a) * r, 0.9f);
                    }
                }
                if (tick % 3 == 0) {
                    w.spawnParticle(Particle.END_ROD, base.getX(), base.getY() + 0.1, base.getZ(), 0, 0.0, 1.0, 0.0, 0.14);
                }
            }

            @Override
            public void finish(Player p) {
                // tidak ada akhir khusus
            }
        });
    }

    // ---------------------------------------------------------------- blink

    /**
     * Kedip mata: gelap -> sekilas terang (kedipan) -> gelap -> memudar terbuka.
     * Memakai BLINDNESS (fade-out bawaan 1 detik terakhir sebagai "membuka mata").
     */
    private void startBlink(Player p, int total) {
        final UUID id = p.getUniqueId();
        blinking.add(id);
        applyBlind(p, total);
        if (total >= 30) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (blinking.contains(id) && p.isOnline()) {
                    p.removePotionEffect(PotionEffectType.BLINDNESS);
                }
            }, 14L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (blinking.contains(id) && p.isOnline()) {
                    applyBlind(p, total - 17);
                }
            }, 17L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> blinking.remove(id), total + 2L);
    }

    private void applyBlind(Player p, int duration) {
        try {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.max(1, duration), 0, false, false, false));
        } catch (Throwable ex) {
            plugin.getLogger().warning("Efek kedip (BLINDNESS) gagal diterapkan: " + ex);
        }
    }

    // ---------------------------------------------------------------- flash putih

    /** Kilat putih di depan mata pemain (hanya dikirim ke pemain itu). Alpha 0-255. */
    private void flash(Player p, int alpha) {
        if (flashBroken || alpha <= 0) {
            return;
        }
        try {
            Location eye = p.getEyeLocation();
            Vector d = eye.getDirection();
            Location at = eye.clone().add(d.getX() * 0.9, d.getY() * 0.9, d.getZ() * 0.9);
            int a = Math.max(0, Math.min(255, alpha));
            if (flashColorSupported) {
                try {
                    p.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, Color.fromARGB(a, 255, 255, 255));
                    return;
                } catch (IllegalArgumentException ex) {
                    flashColorSupported = false;
                    plugin.getLogger().warning("Particle.FLASH tidak menerima data Color; memakai tanpa data (tanpa fade alpha).");
                }
            }
            p.spawnParticle(Particle.FLASH, at, 1);
        } catch (Throwable ex) {
            flashBroken = true;
            plugin.getLogger().warning("Efek layar putih dimatikan karena error: " + ex);
        }
    }

    // ---------------------------------------------------------------- internal

    private interface Frame {
        void frame(Player p, int tick);

        void finish(Player p);
    }

    private void run(Player player, int startDelay, int duration, Runnable onStart, Frame frame) {
        final UUID id = player.getUniqueId();
        BukkitTask task = new BukkitRunnable() {
            int tick = -startDelay;
            boolean started = false;

            @Override
            public void run() {
                try {
                    if (!player.isOnline() || player.isDead()) {
                        stop();
                        return;
                    }
                    if (tick < 0) {
                        tick++;
                        return;
                    }
                    if (!started) {
                        started = true;
                        onStart.run();
                    }
                    if (tick >= duration) {
                        frame.finish(player);
                        stop();
                        return;
                    }
                    frame.frame(player, tick);
                    tick++;
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Efek dibatalkan karena error: " + ex);
                    stop();
                }
            }

            private void stop() {
                cancel();
                BukkitTask cur = tasks.get(id);
                if (cur != null && cur.getTaskId() == getTaskId()) {
                    tasks.remove(id);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        tasks.put(id, task);
    }

    /** Ledakan cahaya: cincin keluar di kaki + semburan naik. */
    private void burst(Location l, int ring, int up) {
        World w = l.getWorld();
        double x = l.getX();
        double y = l.getY();
        double z = l.getZ();
        for (int i = 0; i < ring; i++) {
            double a = i * TAU / ring;
            w.spawnParticle(Particle.END_ROD, x, y + 0.2, z, 0, Math.cos(a), 0.05, Math.sin(a), 0.28);
        }
        for (int i = 0; i < up; i++) {
            double a = i * TAU / up;
            w.spawnParticle(Particle.END_ROD, x + Math.cos(a) * 0.4, y + 0.1, z + Math.sin(a) * 0.4, 0, 0.0, 1.0, 0.0, 0.18);
        }
    }

    private void playSounds(Player player, Settings.EffectSettings es) {
        for (SoundSpec spec : es.sounds) {
            if (spec.delayTicks() <= 0) {
                playOne(player, spec);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        playOne(player, spec);
                    }
                }, spec.delayTicks());
            }
        }
    }

    private void playOne(Player player, SoundSpec spec) {
        try {
            Location l = player.getLocation();
            l.getWorld().playSound(l, spec.key(), SoundCategory.AMBIENT, spec.volume(), spec.pitch());
        } catch (Throwable ex) {
            plugin.getLogger().warning("Gagal memainkan suara '" + spec.key() + "': " + ex);
        }
    }

    private static void white(World w, double x, double y, double z, float size) {
        w.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(WHITE, size));
    }

    private static void glow(World w, double x, double y, double z) {
        w.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static void tint(World w, double x, double y, double z, Color from, Color to, float size) {
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0,
                new Particle.DustTransition(from, to, size));
    }
}
