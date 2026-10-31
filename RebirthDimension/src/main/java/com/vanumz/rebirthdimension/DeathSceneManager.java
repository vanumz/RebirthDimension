package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adegan kematian: respawn dipaksa otomatis (death screen hilang), tubuh pemain muncul tergeletak (Mannequin
 * dengan skin pemain), lalu kamera orang ketiga mengorbit tubuh itu. Kamera dibuat dengan MEMINDAHKAN PEMAIN
 * (mode spectator, fly-speed 0) tiap tick ke titik orbit yang menghadap tubuh; tidak bergantung pada entity kamera.
 * Setelah itu kelopak mata menutup dan pemain diteruskan ke teleport dimensi.
 *
 * Entity tubuh non-persisten dan selalu dibersihkan (selesai, quit, disable). Jika tubuh gagal dibuat, kamera tetap
 * mengorbit titik kematian. Jika adegan terhenti, alur Rebirth tetap berjalan.
 */
public final class DeathSceneManager {

    /** Tinggi mata pemain (spectator berdiri) di atas posisi kaki. */
    private static final double EYE_HEIGHT = 1.62;

    private final Main plugin;
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Map<UUID, Scene> scenes = new HashMap<>();

    private static final class Scene {
        boolean vfxWarned;
        Entity corpse;
        BukkitTask task;
        Location focus;
        double theta0;
        boolean test;
        GameMode prevMode;
        float prevFly;
    }

    public DeathSceneManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isActive(UUID id) {
        return scenes.containsKey(id);
    }

    /** Dipanggil saat kematian yang memicu/melanjutkan Rebirth. */
    public void onRebirthDeath(Player p) {
        UUID id = p.getUniqueId();
        if (!plugin.dimension().isRebirthWorld(p.getWorld())) {
            deathLocations.put(id, p.getLocation().clone());
        } else {
            deathLocations.remove(id);
        }
        Settings s = plugin.settings();
        if (s.deathInstantRespawn) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.isDead()) {
                    try {
                        p.spigot().respawn();
                    } catch (Throwable ex) {
                        // Arclight bisa melempar NPE internal setelah respawn sebenarnya sudah terjadi.
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (p.isOnline() && p.isDead()) {
                                plugin.getLogger().warning("Respawn otomatis gagal untuk " + p.getName() + ": " + ex);
                            }
                        });
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (p.isOnline() && !p.isDead()) {
                            ClientLoadFix.apply(p, plugin.getLogger());
                        }
                    });
                }
            }, s.deathRespawnDelayTicks);
        }
    }

    /** Lokasi respawn untuk adegan (di tempat mati), atau null jika adegan tidak dimainkan. */
    public Location prepareScene(Player p) {
        if (!plugin.settings().deathSceneEnabled) {
            deathLocations.remove(p.getUniqueId());
            return null;
        }
        Location death = deathLocations.get(p.getUniqueId());
        if (death == null || death.getWorld() == null) {
            deathLocations.remove(p.getUniqueId());
            return null;
        }
        Location loc = death.clone();
        loc.setY(Math.max(loc.getY(), death.getWorld().getMinHeight() + 5));
        return loc;
    }

    public void discard(Player p) {
        deathLocations.remove(p.getUniqueId());
    }

    /** Memulai adegan setelah pemain respawn di lokasi kematian. @return false jika gagal (pakai fallback). */
    public boolean start(Player p) {
        Location death = deathLocations.remove(p.getUniqueId());
        if (death == null) {
            return false;
        }
        return begin(p, death, false);
    }

    /** Untuk /rebirth scene: memutar adegan di posisi pemain tanpa harus mati (tidak teleport ke dimensi). */
    public boolean startTest(Player p) {
        if (scenes.containsKey(p.getUniqueId())) {
            return false;
        }
        return begin(p, p.getLocation().clone(), true);
    }

    private boolean begin(Player p, Location death, boolean test) {
        if (!p.isOnline() || death.getWorld() == null) {
            return false;
        }
        UUID id = p.getUniqueId();
        World w = death.getWorld();
        Scene sc = new Scene();
        sc.test = test;
        sc.prevMode = p.getGameMode();
        sc.prevFly = p.getFlySpeed();

        Location ground = findGround(death);
        ground.setYaw(death.getYaw());
        ground.setPitch(0f);
        sc.focus = ground;
        sc.theta0 = Math.toRadians(death.getYaw() + 90.0);

        try {
            sc.corpse = spawnCorpse(w, ground, p);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Tubuh adegan gagal dibuat (kamera tetap mengorbit titik kematian): " + ex);
            removeCorpse(sc);
            sc.corpse = null;
        }

        scenes.put(id, sc);
        try {
            p.setGameMode(GameMode.SPECTATOR);
            p.setFlySpeed(0f);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Gagal menyiapkan spectator untuk adegan: " + ex);
        }
        playStartSound(sc);
        plugin.getLogger().info("Adegan kematian dimulai untuk " + p.getName() + (test ? " (tes)" : "")
                + " | tubuh=" + (sc.corpse != null) + " | " + plugin.effects().describe(p));

        sc.task = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                try {
                    if (!p.isOnline()) {
                        end(p, true);
                        return;
                    }
                    Settings cur = plugin.settings();
                    int total = cur.deathSceneTicks;
                    int closeAt = total - cur.deathCloseTicks;
                    if (p.getGameMode() != GameMode.SPECTATOR) {
                        p.setGameMode(GameMode.SPECTATOR);
                    }
                    p.teleport(cameraAt(sc, Math.min(1.0, tick / (double) total)));
                    try {
                        ambient(sc, tick);
                        if (cur.deathCosmic) {
                            cosmic(sc, tick, closeAt, cur.deathCosmicLead);
                        }
                    } catch (Throwable vfx) {
                        // Partikel gagal tidak boleh menghentikan kamera, tubuh, dan title adegan.
                        if (!sc.vfxWarned) {
                            sc.vfxWarned = true;
                            plugin.getLogger().warning("Partikel adegan gagal (adegan tetap lanjut): " + vfx);
                        }
                    }
                    if (tick == closeAt) {
                        plugin.effects().playClose(p, cur.deathCloseTicks);
                    }
                    if (tick >= total) {
                        end(p, false);
                        return;
                    }
                    tick++;
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Adegan kematian dihentikan karena error: " + ex);
                    end(p, true);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Mengakhiri adegan lalu meneruskan ke teleport dimensi (alur nyata) atau membuka mata di tempat (tes). */
    private void end(Player p, boolean aborted) {
        UUID id = p.getUniqueId();
        Scene sc = scenes.remove(id);
        if (sc == null) {
            return;
        }
        if (sc.task != null) {
            sc.task.cancel();
        }
        boolean online = p.isOnline();
        if (online) {
            restorePlayer(p, sc);
        }
        removeCorpse(sc);
        plugin.getLogger().info("Adegan kematian selesai untuk " + p.getName() + (aborted ? " (dihentikan)" : ""));
        if (!online) {
            return;
        }
        if (sc.test) {
            plugin.effects().playEnter(p);
        } else {
            plugin.rebirth().afterDeathScene(p);
        }
    }

    private void restorePlayer(Player p, Scene sc) {
        try {
            p.setFlySpeed(sc.prevFly);
            p.setGameMode(sc.test ? sc.prevMode : GameMode.ADVENTURE);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Gagal memulihkan pemain setelah adegan: " + ex);
        }
    }

    /**
     * Tubuh tergeletak = entity Mannequin berskin pemain bila API server menyediakannya (dicari lewat refleksi agar
     * tidak crash di server tanpa kelas itu, mis. Arclight). Jika tidak ada/gagal, adegan lanjut tanpa tubuh.
     */
    @SuppressWarnings("unchecked")
    private Entity spawnCorpse(World w, Location ground, Player p) throws Exception {
        Class<?> mq;
        try {
            mq = Class.forName("org.bukkit.entity.Mannequin");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Entity Mannequin tidak tersedia di server ini");
        }
        Entity e = w.spawn(ground, (Class<? extends Entity>) mq);
        try {
            e.setGravity(false);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setPersistent(false);
            callIfPresent(e, "setImmovable", new Class<?>[]{boolean.class}, true);
            callIfPresent(e, "setDescription", new Class<?>[]{net.md_5.bungee.api.chat.BaseComponent[].class}, (Object) null);
            applySkin(e, p);
            Pose pose = "SWIMMING".equals(plugin.settings().deathCorpsePose) ? Pose.SWIMMING : Pose.SLEEPING;
            if (!callIfPresent(e, "setPose", new Class<?>[]{Pose.class, boolean.class}, pose, true)) {
                callIfPresent(e, "setPose", new Class<?>[]{Pose.class}, pose);
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Sebagian pengaturan tubuh adegan gagal (diabaikan): " + ex);
        }
        return e;
    }

    /** Skin: coba setProfile dengan PlayerProfile Bukkit; tipe profil lain (khusus Paper) dilewati. */
    private void applySkin(Entity corpse, Player p) {
        try {
            Object profile = p.getPlayerProfile();
            for (java.lang.reflect.Method m : corpse.getClass().getMethods()) {
                if (m.getName().equals("setProfile") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(profile)) {
                    m.invoke(corpse, profile);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // tubuh tanpa skin tetap berfungsi
        }
    }

    private static boolean callIfPresent(Object target, String name, Class<?>[] types, Object... args) {
        try {
            target.getClass().getMethod(name, types).invoke(target, args);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    /** Dipanggil saat pemain quit: bersihkan tanpa meneruskan alur (login berikutnya akan menangani). */
    public void abort(Player p) {
        UUID id = p.getUniqueId();
        deathLocations.remove(id);
        Scene sc = scenes.remove(id);
        if (sc != null) {
            if (sc.task != null) {
                sc.task.cancel();
            }
            try {
                p.setFlySpeed(sc.prevFly);
            } catch (Throwable ignored) {
                // abaikan
            }
            removeCorpse(sc);
        }
    }

    public void abortAll() {
        for (UUID id : new ArrayList<>(scenes.keySet())) {
            Scene sc = scenes.remove(id);
            if (sc == null) {
                continue;
            }
            if (sc.task != null) {
                sc.task.cancel();
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                try {
                    p.setFlySpeed(sc.prevFly);
                    p.setGameMode(sc.test ? sc.prevMode : GameMode.ADVENTURE);
                } catch (Throwable ignored) {
                    // abaikan
                }
            }
            removeCorpse(sc);
        }
        deathLocations.clear();
    }

    private void removeCorpse(Scene sc) {
        try {
            if (sc.corpse != null && sc.corpse.isValid()) {
                sc.corpse.remove();
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Gagal menghapus tubuh adegan: " + ex);
        }
    }

    // ------------------------------------------------------------------ geometri

    /** Menaruh tubuh di tanah terdekat di bawah titik kematian (maks 12 blok); jika tidak ada, tetap di titik itu. */
    private Location findGround(Location death) {
        World w = death.getWorld();
        Location start = death.clone().add(0, 0.5, 0);
        RayTraceResult hit = w.rayTraceBlocks(start, new Vector(0, -1, 0), 12.0, FluidCollisionMode.NEVER, true);
        Location out = death.clone();
        if (hit != null && hit.getHitPosition() != null) {
            out.setY(hit.getHitPosition().getY());
        }
        out.setY(Math.max(out.getY(), w.getMinHeight() + 2));
        return out;
    }

    /**
     * Posisi PEMAIN (kaki) agar matanya berada di titik orbit pada progres u (0..1) dan menghadap tubuh.
     * Orbit berkecepatan sudut tetap supaya putarannya terlihat jelas; jarak/tinggi hanya sedikit membesar.
     */
    private Location cameraAt(Scene sc, double u) {
        Settings s = plugin.settings();
        World w = sc.focus.getWorld();
        double theta = sc.theta0 + Math.toRadians(s.deathCameraSweepDeg) * u;
        double r = s.deathCameraDistance * (1.0 + 0.15 * u);
        double h = s.deathCameraHeight * (1.0 + 0.2 * u);

        Vector target = new Vector(sc.focus.getX(), sc.focus.getY() + 0.3, sc.focus.getZ());
        Vector eye = new Vector(target.getX() + Math.cos(theta) * r, target.getY() + h, target.getZ() + Math.sin(theta) * r);

        Vector dir = eye.clone().subtract(target);
        double len = dir.length();
        if (len > 0.001) {
            dir.multiply(1.0 / len);
            RayTraceResult hit = w.rayTraceBlocks(new Location(w, target.getX(), target.getY(), target.getZ()), dir, len,
                    FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitPosition() != null) {
                double d = Math.max(0.9, hit.getHitPosition().distance(target) - 0.3);
                eye = target.clone().add(dir.clone().multiply(d));
            }
        }
        Location cam = new Location(w, eye.getX(), eye.getY() - EYE_HEIGHT, eye.getZ());
        cam.setDirection(target.clone().subtract(eye));
        return cam;
    }

    private void ambient(Scene sc, int tick) {
        World w = sc.focus.getWorld();
        double x = sc.focus.getX();
        double y = sc.focus.getY();
        double z = sc.focus.getZ();
        if (tick % 3 == 0) {
            Particles.spawn(w, Particle.SOUL, x, y + 0.5, z, 1, 0.3, 0.1, 0.3, 0.01);
        }
        if (tick % 5 == 0) {
            Particles.spawn(w, Particle.END_ROD, x, y + 0.4, z, 0, 0.0, 1.0, 0.0, 0.03);
        }
    }

    private static final int[] COSMIC = {0x7c3aed, 0x3b82f6, 0x22d3ee, 0xffffff, 0xf0abfc, 0x14b8a6, 0xa78bfa, 0xffffff};
    private static final double TAU = Math.PI * 2;

    private static Color cosmicColor(int i) {
        return Color.fromRGB(COSMIC[Math.floorMod(i, COSMIC.length)]);
    }

    /**
     * Efek kosmik menjelang kelopak menutup: pusaran warna-warni (putih/biru/ungu/cyan/pink) yang naik dan menyempit,
     * pilar cahaya, awan debu bintang, dan kilau putih yang mengorbit; intensitas naik sampai closeAt, lalu nova.
     */
    private void cosmic(Scene sc, int tick, int closeAt, int lead) {
        int start = Math.max(0, closeAt - lead);
        if (tick < start) {
            return;
        }
        World w = sc.focus.getWorld();
        double q = Math.min(1.0, (tick - start) / (double) Math.max(1, closeAt - start));
        double x = sc.focus.getX();
        double y = sc.focus.getY() + 0.15;
        double z = sc.focus.getZ();

        int arms = 5;
        int pts = 6 + (int) Math.round(6 * q);
        for (int a = 0; a < arms; a++) {
            for (int j = 0; j < pts; j++) {
                double f = j / (double) pts;
                double ang = tick * 0.22 + a * TAU / arms + f * 5.0;
                double r = (1.9 - 1.1 * f) * (1.0 - 0.25 * q);
                double yy = y + f * (0.6 + 2.4 * q);
                Particles.spawn(w, Particle.DUST_COLOR_TRANSITION, x + Math.cos(ang) * r, yy, z + Math.sin(ang) * r, 1, 0.0, 0.0, 0.0, 0.0,
                        new Particle.DustTransition(cosmicColor(a + j), cosmicColor(a + j + 3), 1.0f + 0.4f * (float) (1.0 - f)));
            }
        }
        Particles.spawn(w, Particle.END_ROD, x, y + 0.1, z, 0, 0.0, 1.0, 0.0, 0.10 + 0.18 * q);
        Particles.spawn(w, Particle.END_ROD, x, y + 0.1, z, 0, 0.0, 1.0, 0.0, 0.05 + 0.10 * q);
        Particles.spawn(w, Particle.DUST_COLOR_TRANSITION, x, y + 1.0, z, 4 + (int) (8 * q), 1.5, 1.0, 1.5, 0.0,
                new Particle.DustTransition(cosmicColor(tick / 2), cosmicColor(tick / 2 + 2), 0.8f));
        if (tick % 2 == 0) {
            double rr = 1.1 + 0.5 * q;
            for (int k = 0; k < 8; k++) {
                double ang = -tick * 0.3 + k * TAU / 8;
                Particles.spawn(w, Particle.END_ROD, x + Math.cos(ang) * rr, y + 0.5 + 0.4 * Math.sin(tick * 0.2 + k), z + Math.sin(ang) * rr,
                        0, 0.0, 1.0, 0.0, 0.01);
            }
        }
        if (tick == closeAt) {
            nova(sc);
        }
    }

    /** Ledakan kosmik tepat saat kelopak mulai menutup. */
    private void nova(Scene sc) {
        World w = sc.focus.getWorld();
        double x = sc.focus.getX();
        double y = sc.focus.getY();
        double z = sc.focus.getZ();
        for (int i = 0; i < 40; i++) {
            double ang = i * TAU / 40;
            Particles.spawn(w, Particle.END_ROD, x, y + 0.3, z, 0, Math.cos(ang), 0.05, Math.sin(ang), 0.34);
            Particles.spawn(w, Particle.END_ROD, x, y + 1.0, z, 0, Math.cos(ang), 0.10, Math.sin(ang), 0.22);
            Particles.spawn(w, Particle.DUST_COLOR_TRANSITION, x + Math.cos(ang) * 0.8, y + 0.6, z + Math.sin(ang) * 0.8, 1, 0.0, 0.0, 0.0, 0.0,
                    new Particle.DustTransition(cosmicColor(i), cosmicColor(i + 2), 1.4f));
        }
        for (int i = 0; i < 16; i++) {
            double ang = i * TAU / 16;
            Particles.spawn(w, Particle.END_ROD, x + Math.cos(ang) * 0.4, y + 0.1, z + Math.sin(ang) * 0.4, 0, 0.0, 1.0, 0.0, 0.3);
        }
        try {
            Location l = sc.focus;
            w.playSound(l, "minecraft:block.amethyst_block_resonate", SoundCategory.AMBIENT, 1.0f, 0.6f);
            w.playSound(l, "minecraft:block.beacon.activate", SoundCategory.AMBIENT, 0.8f, 1.8f);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Gagal memainkan suara nova: " + ex);
        }
    }

    private void playStartSound(Scene sc) {
        try {
            Location l = sc.focus;
            l.getWorld().playSound(l, "minecraft:particle.soul_escape", SoundCategory.AMBIENT, 0.8f, 0.8f);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Gagal memainkan suara adegan: " + ex);
        }
    }
}
