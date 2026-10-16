package com.vanumz.rebirthdimension;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Orkestrasi alur Rebirth: kematian -> respawn ke dunia Rebirth -> timer -> keluar.
 *
 * Aturan yang dipakai:
 *  - Timer dimulai saat KEMATIAN (bukan saat respawn), dan disimpan langsung ke disk.
 *  - Kematian saat timer masih aktif TIDAK mereset/memperpanjang timer (keep).
 *  - Semua teleport internal ditandai agar tidak diblokir oleh penjaga teleport.
 *  - Data timer baru dihapus setelah teleport keluar benar-benar berhasil.
 */
public final class RebirthManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Main plugin;
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final Set<UUID> finishing = new HashSet<>();
    private final Set<UUID> exitEffectPlayed = new HashSet<>();
    private final Map<UUID, Long> lastRescue = new HashMap<>();
    private final Map<UUID, Long> lastBlockedMessage = new HashMap<>();
    private BukkitTask timerTask;

    public RebirthManager(Main plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ tasks

    public void startTimerTask() {
        stopTimerTask();
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stopTimerTask() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData d = plugin.timers().get(p.getUniqueId());
                if (d == null) {
                    continue;
                }
                if (finishing.contains(p.getUniqueId())) {
                    continue;
                }
                if (d.expired(now)) {
                    finish(p);
                    continue;
                }
                if (plugin.dimension().isRebirthWorld(p.getWorld())) {
                    updateBar(p, d, now);
                } else if (!p.isDead() && plugin.dimension().isReady() && !p.hasPermission("rebirth.bypass")
                        && !internalTeleports.contains(p.getUniqueId())) {
                    Long last = lastRescue.get(p.getUniqueId());
                    if (last == null || now - last > 5000L) {
                        lastRescue.put(p.getUniqueId(), now);
                        arrive(p, true);
                    }
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().severe("Error pada tick timer Rebirth: " + ex);
        }
    }

    // ------------------------------------------------------------------ query

    /** Aktif = ada data timer dan belum habis. */
    public boolean hasActive(UUID id) {
        PlayerData d = plugin.timers().get(id);
        return d != null && !d.expired(System.currentTimeMillis());
    }

    public boolean isInternalTeleport(UUID id) {
        return internalTeleports.contains(id);
    }

    public boolean isFinishing(UUID id) {
        return finishing.contains(id);
    }

    public boolean throttleMessage(UUID id) {
        long now = System.currentTimeMillis();
        Long last = lastBlockedMessage.get(id);
        if (last != null && now - last < 2000L) {
            return false;
        }
        lastBlockedMessage.put(id, now);
        return true;
    }

    public static String formatTime(long millis) {
        long totalSeconds = (millis + 999L) / 1000L;
        long m = totalSeconds / 60L;
        long s = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    // ------------------------------------------------------------------ death / respawn

    public void onDeath(Player p) {
        if (!plugin.dimension().isReady()) {
            return;
        }
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        PlayerData existing = plugin.timers().get(id);
        if (existing != null) {
            // Aturan: timer yang sedang berjalan dipertahankan, tidak diperpanjang/direset.
            return;
        }
        boolean inRebirthWorld = plugin.dimension().isRebirthWorld(p.getWorld());
        if (!inRebirthWorld && !plugin.settings().enabledWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (inRebirthWorld) {
            return; // mati di dunia Rebirth tanpa timer (mis. dunia dipakai admin): bukan alur Rebirth
        }
        plugin.timers().start(id, plugin.settings().durationSeconds * 1000L,
                p.getGameMode().name(), p.getAllowFlight());
        plugin.getLogger().info("Rebirth dimulai untuk " + p.getName() + " (" + plugin.settings().durationSeconds + " detik, mulai " + now + ").");
    }

    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!plugin.dimension().isReady() || !hasActive(p.getUniqueId())) {
            return;
        }
        e.setRespawnLocation(plugin.dimension().spawnLocation());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                PlayerData d = plugin.timers().get(p.getUniqueId());
                if (d != null && !d.expired(System.currentTimeMillis())) {
                    applyState(p, d, true);
                }
            }
        });
    }

    // ------------------------------------------------------------------ join / quit

    public void onJoin(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            UUID id = p.getUniqueId();
            PlayerData d = plugin.timers().get(id);
            boolean inRebirth = plugin.dimension().isRebirthWorld(p.getWorld());
            if (d == null) {
                if (inRebirth) {
                    rescueStuck(p);
                }
                return;
            }
            if (d.expired(System.currentTimeMillis())) {
                finish(p);
                return;
            }
            if (!plugin.dimension().isReady()) {
                return;
            }
            if (inRebirth) {
                applyState(p, d, false); // reconnect: tanpa efek masuk
            } else {
                arrive(p, true);
            }
        });
    }

    public void onQuit(Player p) {
        UUID id = p.getUniqueId();
        plugin.effects().cancel(id);
        plugin.bossBars().hide(p);
        plugin.bossBars().forget(id);
        finishing.remove(id);
        exitEffectPlayed.remove(id);
        internalTeleports.remove(id);
        lastRescue.remove(id);
        lastBlockedMessage.remove(id);
        plugin.boundary().forget(id);
    }

    /** Pemain berada di dunia Rebirth tapi tidak punya timer (data hilang/admin menghapus): kembalikan ke dunia utama. */
    private void rescueStuck(Player p) {
        plugin.getLogger().warning(p.getName() + " berada di dunia Rebirth tanpa timer; dikembalikan ke dunia utama.");
        Location dest = plugin.dimension().returnLocation(p);
        teleportInternal(p, dest, ok -> {
            if (ok) {
                if (p.getGameMode() == GameMode.ADVENTURE) {
                    p.setGameMode(GameMode.SURVIVAL);
                }
                p.setAllowFlight(false);
                p.setFlySpeed(0.1f);
                p.resetPlayerTime();
                p.resetPlayerWeather();
            }
        });
    }

    // ------------------------------------------------------------------ enter

    /** Admin: mulai Rebirth manual untuk pemain online. */
    public boolean startManual(Player p, int seconds) {
        if (!plugin.dimension().isReady()) {
            return false;
        }
        UUID id = p.getUniqueId();
        if (plugin.timers().get(id) == null) {
            plugin.timers().start(id, seconds * 1000L, p.getGameMode().name(), p.getAllowFlight());
        }
        arrive(p, true);
        return true;
    }

    /** Pastikan pemain berada di dunia Rebirth; jika belum, teleport (dengan efek masuk). */
    private void arrive(Player p, boolean withEffect) {
        if (!p.isOnline() || !plugin.dimension().isReady()) {
            return;
        }
        PlayerData d = plugin.timers().get(p.getUniqueId());
        if (d == null || d.expired(System.currentTimeMillis())) {
            return;
        }
        if (plugin.dimension().isRebirthWorld(p.getWorld())) {
            applyState(p, d, withEffect);
            return;
        }
        Location dest = plugin.dimension().spawnLocation();
        teleportInternal(p, dest, ok -> {
            if (ok) {
                PlayerData cur = plugin.timers().get(p.getUniqueId());
                if (cur != null && !cur.expired(System.currentTimeMillis())) {
                    applyState(p, cur, withEffect);
                }
            } else {
                plugin.getLogger().warning("Teleport masuk Rebirth gagal untuk " + p.getName() + "; akan dicoba lagi.");
            }
        });
    }

    private void applyState(Player p, PlayerData d, boolean withEffect) {
        if (!p.isOnline()) {
            return;
        }
        Settings s = plugin.settings();
        try {
            p.setGameMode(GameMode.ADVENTURE);
            if (s.allowFlight) {
                p.setAllowFlight(true);
                p.setFlySpeed(s.flySpeed);
                if (s.startFlying) {
                    p.setFlying(true);
                }
            }
            p.setFireTicks(0);
            p.setFallDistance(0f);
            if (s.environment == World.Environment.NORMAL && s.skyTime >= 0) {
                p.setPlayerTime(s.skyTime, false);
                p.setPlayerWeather(WeatherType.CLEAR);
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Gagal menerapkan status Rebirth ke " + p.getName() + ": " + ex);
        }
        updateBar(p, d, System.currentTimeMillis());
        if (withEffect) {
            try {
                plugin.effects().playEnter(p);
            } catch (Throwable ex) {
                plugin.getLogger().warning("Efek masuk gagal (diabaikan): " + ex);
                announceEntry(p);
            }
        }
    }

    private void updateBar(Player p, PlayerData d, long now) {
        Settings s = plugin.settings();
        if (!s.bossBarEnabled) {
            return;
        }
        long rem = d.remaining(now);
        Component title = MM.deserialize(s.bossBarTitle, Placeholder.unparsed("time", formatTime(rem)),
                Placeholder.unparsed("dimension", s.dimensionName));
        plugin.bossBars().show(p, title, rem / (double) d.durationMillis, s.bossBarColor, s.bossBarOverlay);
    }

    // ------------------------------------------------------------------ exit

    /** Mengakhiri Rebirth (timer habis atau dilepas admin). */
    public void finish(Player p) {
        UUID id = p.getUniqueId();
        PlayerData d = plugin.timers().get(id);
        if (d == null || finishing.contains(id)) {
            return;
        }
        if (!plugin.dimension().isRebirthWorld(p.getWorld())) {
            // Tidak sedang di dunia Rebirth: cukup bersihkan status.
            cleanup(p, d);
            plugin.timers().remove(id);
            exitEffectPlayed.remove(id);
            return;
        }
        finishing.add(id);
        plugin.bossBars().hide(p);

        int delay = 1;
        if (exitEffectPlayed.add(id)) {
            sendMessage(p, "exiting");
            try {
                plugin.effects().playExit(p);
            } catch (Throwable ex) {
                plugin.getLogger().warning("Efek keluar gagal (teleport tetap dilanjutkan): " + ex);
            }
            delay = plugin.settings().exit.enabled ? plugin.settings().exit.durationTicks : 1;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> completeExit(p), delay);
    }

    private void completeExit(Player p) {
        UUID id = p.getUniqueId();
        if (!p.isOnline() || !finishing.contains(id)) {
            finishing.remove(id);
            return;
        }
        PlayerData d = plugin.timers().get(id);
        if (d == null) {
            finishing.remove(id);
            return;
        }
        Location dest = plugin.dimension().returnLocation(p);
        teleportInternal(p, dest, ok -> {
            finishing.remove(id);
            if (!p.isOnline()) {
                return;
            }
            if (ok) {
                cleanup(p, d);
                plugin.timers().remove(id);
                exitEffectPlayed.remove(id);
                try {
                    plugin.effects().playArrival(p);
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Efek tiba gagal (diabaikan): " + ex);
                }
            } else {
                plugin.getLogger().warning("Teleport keluar Rebirth gagal untuk " + p.getName() + "; dicoba lagi pada tick berikutnya.");
            }
        });
    }

    /** Mengembalikan gamemode/flight/waktu/cuaca pemain ke kondisi sebelum Rebirth. */
    private void cleanup(Player p, PlayerData d) {
        try {
            GameMode gm = GameMode.SURVIVAL;
            if (d.prevGameMode != null) {
                try {
                    gm = GameMode.valueOf(d.prevGameMode);
                } catch (IllegalArgumentException ignored) {
                    gm = GameMode.SURVIVAL;
                }
            }
            p.setGameMode(gm);
            boolean fly = gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || d.prevAllowFlight;
            if (!fly) {
                p.setFlying(false);
            }
            p.setAllowFlight(fly);
            p.setFlySpeed(0.1f);
            p.setFallDistance(0f);
            p.resetPlayerTime();
            p.resetPlayerWeather();
        } catch (Throwable ex) {
            plugin.getLogger().warning("Gagal memulihkan status pemain " + p.getName() + ": " + ex);
        }
        plugin.bossBars().hide(p);
    }

    // ------------------------------------------------------------------ teleport helper

    /** Teleport yang ditandai internal (tidak diblokir penjaga teleport). Callback dijalankan di main thread. */
    public void teleportInternal(Player p, Location dest, Consumer<Boolean> done) {
        UUID id = p.getUniqueId();
        internalTeleports.add(id);
        try {
            p.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((ok, err) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        internalTeleports.remove(id);
                        if (err != null) {
                            plugin.getLogger().warning("Teleport error untuk " + p.getName() + ": " + err);
                        }
                        done.accept(err == null && Boolean.TRUE.equals(ok));
                    }));
        } catch (Throwable ex) {
            internalTeleports.remove(id);
            plugin.getLogger().warning("Teleport gagal dijadwalkan untuk " + p.getName() + ": " + ex);
            done.accept(false);
        }
    }

    // ------------------------------------------------------------------ messages

    public void sendMessage(Player p, String key, TagResolver... resolvers) {
        Component c = parse(key, resolvers);
        if (c != null) {
            p.sendMessage(c);
        }
    }

    public Component parse(String key, TagResolver... resolvers) {
        String raw = plugin.settings().message(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        TagResolver dim = Placeholder.unparsed("dimension", plugin.settings().dimensionName);
        TagResolver[] all = new TagResolver[resolvers.length + 1];
        all[0] = dim;
        System.arraycopy(resolvers, 0, all, 1, resolvers.length);
        return MM.deserialize(raw, all);
    }

    /** Pesan + judul saat pemain "membuka mata" di dunia Rebirth (dipanggil efek masuk, atau langsung jika efek mati). */
    public void announceEntry(Player p) {
        if (!p.isOnline()) {
            return;
        }
        sendMessage(p, "entered");
        Component title = parse("enter-title");
        Component sub = parse("enter-subtitle");
        if (title != null || sub != null) {
            p.showTitle(Title.title(title == null ? Component.empty() : title, sub == null ? Component.empty() : sub,
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2400), Duration.ofMillis(1000))));
        }
    }
}
