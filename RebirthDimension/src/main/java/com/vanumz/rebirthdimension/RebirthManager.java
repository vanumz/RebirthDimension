package com.vanumz.rebirthdimension;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

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
                        && !internalTeleports.contains(p.getUniqueId()) && !plugin.deathScene().isActive(p.getUniqueId())) {
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
        return internalTeleports.contains(id) || plugin.deathScene().isActive(id);
    }

    /** Adegan kematian selesai: lanjut teleport ke dimensi (kelopak mata sudah menutup). */
    public void afterDeathScene(Player p) {
        if (p.isOnline()) {
            arrive(p, true);
        }
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
            plugin.deathScene().onRebirthDeath(p);
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
        plugin.deathScene().onRebirthDeath(p);
    }

    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!plugin.dimension().isReady() || !hasActive(p.getUniqueId())) {
            return;
        }
        Location sceneLoc = plugin.deathScene().prepareScene(p);
        if (sceneLoc != null) {
            // Adegan kematian: respawn di tempat mati, lihat tubuh tergeletak, kelopak menutup, lalu teleport ke dimensi.
            e.setRespawnLocation(sceneLoc);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                PlayerData d = plugin.timers().get(p.getUniqueId());
                if (d == null || d.expired(System.currentTimeMillis())) {
                    plugin.deathScene().discard(p);
                    return;
                }
                if (!plugin.deathScene().start(p)) {
                    arrive(p, true);
                }
            });
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
        plugin.effects().forget(id);
        plugin.deathScene().abort(p);
        plugin.bossBars().hide(p);
        plugin.bossBars().forget(id);
        finishing.remove(id);
        exitEffectPlayed.remove(id);
        internalTeleports.remove(id);
        lastRescue.remove(id);
        lastBlockedMessage.remove(id);
        plugin.boundary().forget(id);
    }

    /** Tujuan keluar: bed pemain jika valid, jika tidak tujuan fallback dari config. */
    private Location destinationFor(Player p) {
        if (plugin.settings().returnUseBedSpawn) {
            Location bed = plugin.bedSpawns().resolve(p.getUniqueId());
            if (bed == null) {
                bed = vanillaRespawn(p);
            }
            if (bed != null) {
                return bed;
            }
        }
        if (plugin.settings().rtpEnabled) {
            Location rtp = plugin.dimension().randomReturnLocation();
            if (rtp != null) {
                return rtp;
            }
        }
        return plugin.dimension().returnLocation();
    }

    /** Fallback: respawn point vanilla pemain (bed/anchor) bila tidak ada catatan di bed-spawns.yml. */
    private Location vanillaRespawn(Player p) {
        try {
            Location rs = p.getRespawnLocation();
            if (rs != null && rs.getWorld() != null && !plugin.dimension().isRebirthWorld(rs.getWorld())) {
                return rs;
            }
        } catch (Throwable ignored) {
            // API lama/Arclight: lewati
        }
        return null;
    }

    /** Pemain berada di dunia Rebirth tapi tidak punya timer (data hilang/admin menghapus): kembalikan ke dunia utama. */
    private void rescueStuck(Player p) {
        plugin.getLogger().warning(p.getName() + " berada di dunia Rebirth tanpa timer; dikembalikan ke dunia utama.");
        Location dest = destinationFor(p);
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
        java.util.Map<String, String> ph = new java.util.HashMap<>();
        ph.put("time", formatTime(rem));
        ph.put("dimension", s.dimensionName);
        String title = Text.render(s.bossBarTitle, ph);
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
        final boolean hadBed = plugin.settings().returnUseBedSpawn && plugin.bedSpawns().has(id);
        Location resolvedBed = plugin.settings().returnUseBedSpawn ? plugin.bedSpawns().resolve(id) : null;
        if (resolvedBed == null && plugin.settings().returnUseBedSpawn) {
            resolvedBed = vanillaRespawn(p);
        }
        final Location bedLoc = resolvedBed;
        Location rtpLoc = null;
        if (bedLoc == null && plugin.settings().rtpEnabled) {
            rtpLoc = plugin.dimension().randomReturnLocation();
        }
        final boolean usedRtp = rtpLoc != null;
        final Location dest = bedLoc != null ? bedLoc : (usedRtp ? rtpLoc : plugin.dimension().returnLocation());
        final boolean bedFallback = hadBed && bedLoc == null;
        teleportInternal(p, dest, ok -> {
            finishing.remove(id);
            if (!p.isOnline()) {
                return;
            }
            if (ok) {
                cleanup(p, d);
                plugin.timers().remove(id);
                exitEffectPlayed.remove(id);
                if (usedRtp) {
                    sendMessage(p, "rtp-return");
                } else if (bedFallback) {
                    sendMessage(p, "bed-fallback");
                }
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
        ClientLoadFix.apply(p, plugin.getLogger());
        plugin.bossBars().hide(p);
    }

    // ------------------------------------------------------------------ teleport helper

    /**
     * Teleport yang ditandai internal (tidak diblokir penjaga teleport). Spigot/Arclight tidak punya teleportAsync,
     * jadi chunk tujuan dimuat dulu lalu teleport dilakukan sinkron. Callback dijalankan di tick berikutnya.
     */
    public void teleportInternal(Player p, Location dest, Consumer<Boolean> done) {
        UUID id = p.getUniqueId();
        internalTeleports.add(id);
        boolean ok = false;
        try {
            if (dest.getWorld() != null) {
                dest.getWorld().getChunkAt(dest); // memuat chunk tujuan
            }
            ok = p.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Teleport gagal untuk " + p.getName() + ": " + ex);
        }
        final boolean result = ok;
        Bukkit.getScheduler().runTask(plugin, () -> {
            internalTeleports.remove(id);
            done.accept(result);
        });
    }

    // ------------------------------------------------------------------ messages

    public void sendMessage(Player p, String key) {
        String c = parse(key);
        if (c != null) {
            p.sendMessage(c);
        }
    }

    /** Pesan dari config diubah ke string legacy berwarna hex; null jika pesan dikosongkan. */
    public String parse(String key) {
        String raw = plugin.settings().message(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Text.render(raw, "dimension", plugin.settings().dimensionName);
    }

    /** Teks di atas hotbar (action bar) lewat API Spigot. */
    public void actionBar(Player p, String text) {
        if (text == null) {
            return;
        }
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
        } catch (Throwable ex) {
            plugin.getLogger().warning("Action bar gagal: " + ex);
        }
    }

    /** Pesan + judul saat pemain "membuka mata" di dunia Rebirth (dipanggil efek masuk, atau langsung jika efek mati). */
    public void announceEntry(Player p) {
        if (!p.isOnline()) {
            return;
        }
        sendMessage(p, "entered");
        String title = parse("enter-title");
        String sub = parse("enter-subtitle");
        if (title != null || sub != null) {
            p.sendTitle(title == null ? "" : title, sub == null ? "" : sub, 8, 48, 20);
        }
    }
}
