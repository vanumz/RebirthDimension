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
 * Penyimpanan timer persisten. Waktu berakhir disimpan sebagai timestamp (epoch ms),
 * sehingga timer tetap benar saat pemain offline atau server restart.
 * Hanya dipakai dari main thread.
 */
public final class TimerManager {

    private final Main plugin;
    private final File file;
    private final Map<UUID, PlayerData> data = new HashMap<>();

    public TimerManager(Main plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Tidak bisa membuat folder data: " + dir.getAbsolutePath());
        }
        this.file = new File(dir, "players.yml");
    }

    public void load() {
        data.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception ex) {
            File backup = new File(file.getParentFile(), "players.yml.corrupt-" + System.currentTimeMillis());
            plugin.getLogger().severe("players.yml rusak (" + ex.getMessage() + "). Disalin ke " + backup.getName()
                    + " dan dimulai dari data kosong.");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException io) {
                plugin.getLogger().severe("Gagal membuat backup players.yml: " + io.getMessage());
            }
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection e = players.getConfigurationSection(key);
                if (e == null) {
                    continue;
                }
                long end = e.getLong("end", 0L);
                long duration = e.getLong("duration", 0L);
                if (end <= 0L) {
                    plugin.getLogger().warning("Entri timer tanpa waktu berakhir dilewati: " + key);
                    continue;
                }
                if (duration <= 0L) {
                    duration = plugin.settings().durationSeconds * 1000L;
                }
                data.put(id, new PlayerData(id, end, duration, e.getString("prev-gamemode"), e.getBoolean("prev-allow-flight", false)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Entri timer dilewati (UUID tidak valid): " + key);
            }
        }
        plugin.getLogger().info("Memuat " + data.size() + " timer Rebirth.");
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerData d : data.values()) {
            String base = "players." + d.uuid + ".";
            yaml.set(base + "end", d.endMillis);
            yaml.set(base + "duration", d.durationMillis);
            if (d.prevGameMode != null) {
                yaml.set(base + "prev-gamemode", d.prevGameMode);
            }
            yaml.set(base + "prev-allow-flight", d.prevAllowFlight);
        }
        Path target = file.toPath();
        Path tmp = target.resolveSibling("players.yml.tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Gagal menyimpan players.yml: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }

    public PlayerData get(UUID id) {
        return data.get(id);
    }

    public PlayerData start(UUID id, long durationMillis, String prevGameMode, boolean prevAllowFlight) {
        PlayerData d = new PlayerData(id, System.currentTimeMillis() + durationMillis, durationMillis, prevGameMode, prevAllowFlight);
        data.put(id, d);
        save();
        return d;
    }

    /** Memajukan waktu berakhir ke sekarang (dipakai /rebirth release untuk pemain offline). */
    public PlayerData expireNow(UUID id) {
        PlayerData d = data.get(id);
        if (d == null) {
            return null;
        }
        PlayerData n = d.withEnd(System.currentTimeMillis());
        data.put(id, n);
        save();
        return n;
    }

    public void remove(UUID id) {
        if (data.remove(id) != null) {
            save();
        }
    }
}
