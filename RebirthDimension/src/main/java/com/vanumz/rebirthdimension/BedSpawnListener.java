package com.vanumz.rebirthdimension;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Mencatat spawn bed pemain dan menghapus catatan saat bed hancur (block break / ledakan). */
public final class BedSpawnListener implements Listener {

    private final Main plugin;

    public BedSpawnListener(Main plugin) {
        this.plugin = plugin;
    }

    /** Terpicu tepat saat pemain berhasil menyetel respawn point lewat bed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSetSpawn(PlayerSetSpawnEvent e) {
        if (e.getCause() != PlayerSetSpawnEvent.Cause.BED) {
            return;
        }
        Location loc = e.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        World w = loc.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL || plugin.dimension().isRebirthWorld(w)) {
            return;
        }
        try {
            plugin.bedSpawns().record(e.getPlayer(), loc.getBlock());
        } catch (Throwable ex) {
            plugin.getLogger().warning("Gagal mencatat spawn bed: " + ex);
        }
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
