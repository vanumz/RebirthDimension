package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spawn bed per pemain, disimpan terpisah dari timer di {@code bed-spawns.yml} (tahan restart).
 *
 * - Dicatat saat pemain berhasil menyetel respawn point lewat bed di dunia ber-environment NORMAL.
 * - Satu bed boleh dipakai banyak pemain (catatan per UUID).
 * - Catatan dihapus saat bed dihancurkan (block break / ledakan).
 * - Saat Rebirth selesai, bed diperiksa lagi (masih bed + ada tempat berdiri yang aman); jika tidak valid
 *   pemain memakai tujuan fallback dari config.
 * Hanya dipakai dari main thread.
 */
public final class BedSpawnManager {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final int[] STEP_Y = {0, 1, -1};

    private static final Set<Material> HAZARDS = EnumSet.of(
            Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.COBWEB, Material.POWDER_SNOW);

    private static final class Entry {
        final String world;
        final int x;
        final int y;
        final int z;

        Entry(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Main plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public BedSpawnManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bed-spawns.yml");
    }

    // ------------------------------------------------------------------ persistensi

    public void load() {
        entries.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception ex) {
            File backup = new File(file.getParentFile(), "bed-spawns.yml.corrupt-" + System.currentTimeMillis());
            plugin.getLogger().severe("bed-spawns.yml rusak (" + ex.getMessage() + "). Disalin ke " + backup.getName()
                    + " dan dimulai dari data kosong.");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException io) {
                plugin.getLogger().severe("Gagal membuat backup bed-spawns.yml: " + io.getMessage());
            }
            return;
        }
        ConfigurationSection beds = yaml.getConfigurationSection("beds");
        if (beds == null) {
            return;
        }
        for (String key : beds.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection e = beds.getConfigurationSection(key);
                if (e == null) {
                    continue;
                }
                String world = e.getString("world");
                if (world == null || world.isBlank()) {
                    continue;
                }
                entries.put(id, new Entry(world, e.getInt("x", 0), e.getInt("y", 0), e.getInt("z", 0)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Entri bed dilewati (UUID tidak valid): " + key);
            }
        }
        plugin.getLogger().info("Memuat " + entries.size() + " spawn bed pemain.");
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> me : entries.entrySet()) {
            String base = "beds." + me.getKey() + ".";
            Entry e = me.getValue();
            yaml.set(base + "world", e.world);
            yaml.set(base + "x", e.x);
            yaml.set(base + "y", e.y);
            yaml.set(base + "z", e.z);
        }
        Path target = file.toPath();
        Path tmp = target.resolveSibling("bed-spawns.yml.tmp");
        try {
            Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Gagal menyimpan bed-spawns.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ pencatatan

    public boolean has(UUID id) {
        return entries.containsKey(id);
    }

    /** Normalisasi ke bagian HEAD bed; null jika blok bukan bed. */
    private static Block headOf(Block b) {
        if (b == null || !Tag.BEDS.isTagged(b.getType())) {
            return null;
        }
        BlockData data = b.getBlockData();
        if (data instanceof Bed bed) {
            return bed.getPart() == Bed.Part.FOOT ? b.getRelative(bed.getFacing()) : b;
        }
        return null;
    }

    /** Mencatat bed milik pemain (menimpa catatan sebelumnya). */
    public void record(Player p, Block bedBlock) {
        Block head = headOf(bedBlock);
        if (head == null) {
            return;
        }
        Entry e = new Entry(head.getWorld().getName(), head.getX(), head.getY(), head.getZ());
        entries.put(p.getUniqueId(), e);
        save();
    }

    /** Menghapus semua catatan yang menunjuk bed ini (dipanggil saat bed hancur). */
    public void removeBed(Block broken) {
        Block head = headOf(broken);
        if (head == null) {
            return;
        }
        String world = head.getWorld().getName();
        int x = head.getX();
        int y = head.getY();
        int z = head.getZ();
        boolean removed = entries.values().removeIf(e -> e.world.equals(world) && e.x == x && e.y == y && e.z == z);
        if (removed) {
            save();
        }
    }

    public void forget(UUID id) {
        if (entries.remove(id) != null) {
            save();
        }
    }

    // ------------------------------------------------------------------ pemeriksaan

    /**
     * Lokasi berdiri aman di sebelah bed pemain, atau null jika tidak ada catatan / bed tidak valid.
     * Catatan dihapus jika blok di koordinat itu sudah bukan bed. Jika bed ada tapi terhalang, catatan dipertahankan.
     * Jika dunianya belum dimuat, catatan dipertahankan dan hasilnya null (pakai fallback).
     */
    public Location resolve(UUID id) {
        Entry e = entries.get(id);
        if (e == null) {
            return null;
        }
        World w = Bukkit.getWorld(e.world);
        if (w == null) {
            return null;
        }
        Block head = headOf(w.getBlockAt(e.x, e.y, e.z));
        if (head == null) {
            entries.remove(id);
            save();
            return null;
        }
        return findStandUp(head);
    }

    /** Ringkasan untuk /rebirth bed (tidak mengubah data). */
    public String describe(UUID id) {
        Entry e = entries.get(id);
        if (e == null) {
            return "tidak punya spawn bed tercatat";
        }
        World w = Bukkit.getWorld(e.world);
        String where = e.world + " " + e.x + " " + e.y + " " + e.z;
        if (w == null) {
            return "bed di " + where + " (dunia belum dimuat)";
        }
        Block head = headOf(w.getBlockAt(e.x, e.y, e.z));
        if (head == null) {
            return "bed di " + where + " SUDAH HILANG (akan memakai fallback)";
        }
        return "bed di " + where + (findStandUp(head) != null ? " (valid)" : " (terhalang, akan memakai fallback)");
    }

    private Location findStandUp(Block head) {
        BlockData data = head.getBlockData();
        Block foot = head;
        if (data instanceof Bed bed) {
            foot = head.getRelative(bed.getFacing().getOppositeFace());
        }
        Block[] parts = {head, foot};
        for (int dy : STEP_Y) {
            for (Block part : parts) {
                for (BlockFace side : SIDES) {
                    Block feet = part.getRelative(side.getModX(), dy, side.getModZ());
                    if (Tag.BEDS.isTagged(feet.getType())) {
                        continue;
                    }
                    if (isSafe(feet)) {
                        Location loc = new Location(feet.getWorld(), feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        loc.setDirection(new Vector(part.getX() + 0.5 - loc.getX(), 0.0, part.getZ() + 0.5 - loc.getZ()));
                        return loc;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafe(Block feet) {
        Block above = feet.getRelative(BlockFace.UP);
        Block below = feet.getRelative(BlockFace.DOWN);
        return feet.isPassable() && !feet.isLiquid() && !HAZARDS.contains(feet.getType())
                && above.isPassable() && !above.isLiquid() && !HAZARDS.contains(above.getType())
                && below.getType().isSolid() && !HAZARDS.contains(below.getType());
    }
}
