package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Locale;

/** Event siklus hidup Rebirth: death, respawn, join, quit, teleport, command. */
public final class RebirthListener implements Listener {

    private final Main plugin;

    public RebirthListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        try {
            plugin.rebirth().onDeath(e.getEntity());
        } catch (Throwable ex) {
            plugin.getLogger().severe("Error pada onDeath: " + ex);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        try {
            plugin.rebirth().onRespawn(e);
        } catch (Throwable ex) {
            plugin.getLogger().severe("Error pada onRespawn: " + ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joined.isOnline()) {
                plugin.effects().sendPack(joined);
                if (!plugin.guiScales().has(joined.getUniqueId()) && plugin.effects().isPackExpected(joined)) {
                    plugin.rebirth().sendMessage(joined, "guiscale-hint");
                }
            }
        }, 40L);
        plugin.rebirth().onJoin(e.getPlayer());
    }

    /** Melacak apakah resource pack NullEntity (overlay kelopak/putih) sudah dimuat pemain. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent e) {
        String st = e.getStatus().name();
        plugin.effects().setPackStatus(e.getPlayer().getUniqueId(), st);
        plugin.getLogger().info("Status resource pack " + e.getPlayer().getName() + ": " + st);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.rebirth().onQuit(e.getPlayer());
    }

    /** Pemain dengan Rebirth aktif tidak boleh keluar dari dunia Rebirth kecuali lewat plugin ini. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        RebirthManager rm = plugin.rebirth();
        if (!rm.hasActive(p.getUniqueId()) || rm.isInternalTeleport(p.getUniqueId())) {
            return;
        }
        if (p.hasPermission("rebirth.bypass")) {
            return;
        }
        if (e.getTo() == null) {
            return;
        }
        boolean toRebirth = plugin.dimension().isRebirthWorld(e.getTo().getWorld());
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        boolean illegalCause = cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT;
        if (!toRebirth || illegalCause) {
            e.setCancelled(true);
            if (rm.throttleMessage(p.getUniqueId())) {
                rm.sendMessage(p, "blocked-teleport");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.dimension().isRebirthWorld(p.getWorld()) || !plugin.rebirth().hasActive(p.getUniqueId())) {
            return;
        }
        if (p.hasPermission("rebirth.bypass")) {
            return;
        }
        String msg = e.getMessage();
        if (msg.length() < 2) {
            return;
        }
        String label = msg.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (plugin.settings().blockedCommands.contains(label)) {
            e.setCancelled(true);
            if (plugin.rebirth().throttleMessage(p.getUniqueId())) {
                plugin.rebirth().sendMessage(p, "blocked-command");
            }
        }
    }
}
