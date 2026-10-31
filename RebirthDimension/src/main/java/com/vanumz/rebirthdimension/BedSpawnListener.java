package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;

/** Mencatat spawn bed pemain dan menghapus catatan saat bed hancur (block break / ledakan). */
public final class BedSpawnListener implements Listener {

    private final Main plugin;

    public BedSpawnListener(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Spigot menganggap PlayerBedEnterEvent "cancelled" untuk semua hasil selain OK (mis. siang hari =
     * NOT_POSSIBLE_NOW), padahal vanilla tetap menyetel respawn point. Karena itu ignoreCancelled harus false,
     * dan yang dijadikan acuan adalah respawn point asli pemain satu tick kemudian.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedEnter(PlayerBedEnterEvent e) {
        Player p = e.getPlayer();
        Block bed = e.getBed();
        World w = bed.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL || plugin.dimension().isRebirthWorld(w)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            Location rs = p.getRespawnLocation();
            if (rs == null || rs.getWorld() == null || !rs.getWorld().equals(w)
                    || rs.distanceSquared(bed.getLocation()) > 16.0) {
                return;
            }
            try {
                plugin.bedSpawns().record(p, bed);
            } catch (Throwable ex) {
                plugin.getLogger().warning("Gagal mencatat spawn bed: " + ex);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        plugin.bedSpawns().removeBed(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) {
            plugin.bedSpawns().removeBed(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) {
            plugin.bedSpawns().removeBed(b);
        }
    }
}
