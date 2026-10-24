package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/** Membuat/memuat satu world Rebirth bersama. Tidak pernah menyentuh world utama. */
public final class DimensionManager {

    private final Main plugin;
    private World world;
    private BukkitTask fightTask;
    private boolean fightErrorLogged;

    public DimensionManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isReady() {
        return world != null;
    }

    public World getWorld() {
        return world;
    }

    public boolean isRebirthWorld(World w) {
        return w != null && world != null && w.getUID().equals(world.getUID());
    }

    /** @return true jika world Rebirth siap dipakai. */
    public boolean ensureLoaded() {
        Settings s = plugin.settings();
        String name = s.rebirthWorld;

        if (!Bukkit.getWorlds().isEmpty() && Bukkit.getWorlds().get(0).getName().equalsIgnoreCase(name)) {
            plugin.getLogger().severe("rebirth.world-name ('" + name + "') sama dengan world utama. Ditolak agar world utama aman. Rebirth dinonaktifkan.");
            world = null;
            return false;
        }

        World w = Bukkit.getWorld(name);
        if (w == null) {
            try {
                WorldCreator creator = new WorldCreator(name);
                creator.environment(s.environment);
                creator.generator(new VoidChunkGenerator(s.centerX, s.centerY, s.centerZ));
                creator.generateStructures(false);
                w = Bukkit.createWorld(creator);
            } catch (Exception ex) {
                plugin.getLogger().severe("Gagal membuat world Rebirth '" + name + "': " + ex);
            }
        }
        if (w == null) {
            plugin.getLogger().severe("World Rebirth '" + name + "' tidak tersedia. Rebirth dinonaktifkan.");
            world = null;
            return false;
        }
        world = w;
        if (w.getEnvironment() != s.environment) {
            plugin.getLogger().warning("World '" + name + "' sudah ada dengan environment " + w.getEnvironment()
                    + " (config minta " + s.environment + "). Environment world tidak bisa diubah; pakai rebirth.world-name baru.");
        }
        applyWorldSettings();
        return true;
    }

    public void applyWorldSettings() {
        if (world == null) {
            return;
        }
        Settings s = plugin.settings();
        try {
            world.setStorm(false);
            world.setThundering(false);
            if (world.getEnvironment() == World.Environment.NORMAL && s.skyTime >= 0) {
                world.setTime(s.skyTime);
            }
            world.setDifficulty(Difficulty.PEACEFUL);
            world.setSpawnLocation((int) Math.floor(s.centerX), (int) Math.floor(s.centerY), (int) Math.floor(s.centerZ));
        } catch (Exception ex) {
            plugin.getLogger().warning("Gagal menerapkan pengaturan world Rebirth: " + ex);
        }
        startFightSuppression();
        buildFloor();
    }

    /**
     * World End buatan plugin otomatis punya Ender Dragon fight bawaan (naga, BossBar, podium portal bedrock).
     * Fight itu diinisialisasi belakangan saat chunk pusat aktif, jadi dibersihkan berkala (tiap 10 tick):
     * ditandai sudah pernah dibunuh (tidak spawn naga), naga dihapus, BossBar disembunyikan, podium dibongkar.
     */
    private void startFightSuppression() {
        stopFightSuppression();
        if (world == null || world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        suppressFight();
        fightTask = Bukkit.getScheduler().runTaskTimer(plugin, this::suppressFight, 10L, 10L);
    }

    public void stopFightSuppression() {
        if (fightTask != null) {
            fightTask.cancel();
            fightTask = null;
        }
    }

    private void suppressFight() {
        try {
            if (world == null) {
                return;
            }
            for (EnderDragon d : world.getEntitiesByClass(EnderDragon.class)) {
                d.remove();
            }
            DragonBattle battle = world.getEnderDragonBattle();
            if (battle == null) {
                return;
            }
            if (!battle.hasBeenPreviouslyKilled()) {
                battle.setPreviouslyKilled(true);
            }
            EnderDragon dragon = battle.getEnderDragon();
            if (dragon != null) {
                dragon.remove();
            }
            BossBar bar = battle.getBossBar();
            bar.setVisible(false);
            bar.removeAll();
            clearPodium(battle.getEndPortalLocation());
        } catch (Throwable ex) {
            if (!fightErrorLogged) {
                fightErrorLogged = true;
                plugin.getLogger().warning("Pembersihan dragon fight gagal (hanya dicatat sekali): " + ex);
            }
        }
    }

    private static boolean isPodiumMaterial(Material m) {
        return m == Material.BEDROCK || m == Material.END_STONE || m == Material.END_PORTAL
                || m == Material.TORCH || m == Material.WALL_TORCH || m == Material.DRAGON_EGG;
    }

    /** Membongkar podium portal End (hanya material podium; lantai Barrier tidak tersentuh). */
    private void clearPodium(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        World w = loc.getWorld();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        // Jangan memuat chunk arena hanya untuk memeriksa podium: itu justru mengaktifkan dragon fight.
        if (!w.isChunkLoaded((bx - 6) >> 4, (bz - 6) >> 4) || !w.isChunkLoaded((bx + 6) >> 4, (bz - 6) >> 4)
                || !w.isChunkLoaded((bx - 6) >> 4, (bz + 6) >> 4) || !w.isChunkLoaded((bx + 6) >> 4, (bz + 6) >> 4)) {
            return;
        }
        boolean present = false;
        for (int dy = -2; dy <= 4 && !present; dy++) {
            present = isPodiumMaterial(w.getBlockAt(bx, by + dy, bz).getType());
        }
        if (!present) {
            return;
        }
        int removed = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -4; dy <= 8; dy++) {
                    Block b = w.getBlockAt(bx + dx, by + dy, bz + dz);
                    if (isPodiumMaterial(b.getType())) {
                        b.setType(Material.AIR, false);
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Podium portal End dibongkar (" + removed + " blok).");
        }
    }

    /**
     * Lantai Barrier (tak terlihat) tepat di bawah min-y, hanya di world Rebirth, agar pemain tidak bisa terbang ke bawah.
     * Dilewati jika radius batas &gt; 150; batas vertikal tetap menjaga pemain.
     */
    public void buildFloor() {
        Settings s = plugin.settings();
        if (world == null || !s.boundaryEnabled) {
            return;
        }
        if (s.radius > 150.0) {
            plugin.getLogger().warning("boundary.radius > 150: lantai Barrier dilewati, batas vertikal tetap aktif.");
            return;
        }
        int y = (int) Math.floor(s.minY) - 1;
        int r = (int) Math.ceil(s.radius) + 6;
        int cx = (int) Math.floor(s.centerX);
        int cz = (int) Math.floor(s.centerZ);
        int placed = 0;
        try {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) {
                        continue;
                    }
                    Block b = world.getBlockAt(cx + dx, y, cz + dz);
                    if (b.getType() != Material.BARRIER) {
                        b.setType(Material.BARRIER, false);
                        placed++;
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Gagal membangun lantai Barrier: " + ex);
        }
        if (placed > 0) {
            plugin.getLogger().info("Lantai Barrier dibangun di y=" + y + " (" + placed + " blok).");
        }
    }

    public Location center() {
        Settings s = plugin.settings();
        return new Location(world, s.centerX, s.centerY, s.centerZ);
    }

    /** Titik muncul bersama; scatter 0 berarti koordinat dan arah yang sama persis. */
    public Location spawnLocation() {
        Settings s = plugin.settings();
        if (s.spawnScatter <= 0.0) {
            return new Location(world, s.centerX, s.centerY, s.centerZ, 0f, 0f);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble(Math.PI * 2);
        double dist = Math.sqrt(r.nextDouble()) * s.spawnScatter;
        return new Location(world,
                s.centerX + Math.cos(angle) * dist,
                s.centerY,
                s.centerZ + Math.sin(angle) * dist,
                r.nextFloat() * 360f - 180f, 0f);
    }

    /** Tujuan setelah Rebirth selesai. Tidak pernah world Rebirth itu sendiri. */
    public Location returnLocation() {
        Settings s = plugin.settings();
        World target = Bukkit.getWorld(s.returnWorld);
        if (target == null || isRebirthWorld(target)) {
            target = Bukkit.getWorlds().get(0);
            plugin.getLogger().warning("return.world tidak valid/ sama dengan world Rebirth; memakai world utama '" + target.getName() + "'.");
        }
        if (s.returnUseWorldSpawn) {
            Location sp = target.getSpawnLocation();
            return new Location(target, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, sp.getYaw(), sp.getPitch());
        }
        return new Location(target, s.returnX, s.returnY, s.returnZ, s.returnYaw, s.returnPitch);
    }
}
