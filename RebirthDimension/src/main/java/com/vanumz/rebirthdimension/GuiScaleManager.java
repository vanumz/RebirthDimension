package com.vanumz.rebirthdimension;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GUI scale (1-4) tiap pemain untuk overlay kelopak/putih. Server TIDAK bisa membaca GUI scale klien, jadi pemain
 * menyetelnya sekali lewat /guiscale; nilainya disimpan di gui-scales.yml. Belum disetel = effects.gui-scale-default.
 */
public final class GuiScaleManager {

    private final Main plugin;
    private final File file;
    private final Map<UUID, Integer> scales = new HashMap<>();

    public GuiScaleManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gui-scales.yml");
    }

    public void load() {
        scales.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("gui-scales.yml tidak bisa dibaca (" + ex.getMessage() + "); memakai default untuk semua pemain.");
            return;
        }
        ConfigurationSection sec = yaml.getConfigurationSection("scales");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            try {
                scales.put(UUID.fromString(key), clamp(sec.getInt(key, 2)));
            } catch (IllegalArgumentException ignored) {
                // entri rusak dilewati
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : scales.entrySet()) {
            yaml.set("scales." + e.getKey(), e.getValue());
        }
        Path target = file.toPath();
        Path tmp = target.resolveSibling("gui-scales.yml.tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Gagal menyimpan gui-scales.yml: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }

    public boolean has(UUID id) {
        return scales.containsKey(id);
    }

    /** GUI scale efektif 1..4 (belum disetel -> default config). GUI scale >= 4 memakai set 4. */
    public int get(UUID id) {
        Integer v = scales.get(id);
        return v != null ? v : clamp(plugin.settings().guiScaleDefault);
    }

    public void set(UUID id, int scale) {
        scales.put(id, clamp(scale));
        save();
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(4, v));
    }
}
