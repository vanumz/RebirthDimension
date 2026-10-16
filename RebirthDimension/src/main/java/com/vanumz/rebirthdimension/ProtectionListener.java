package com.vanumz.rebirthdimension;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/** Perlindungan di dunia Rebirth saja. Dunia lain tidak disentuh. */
public final class ProtectionListener implements Listener {

    private final Main plugin;

    public ProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    private boolean inRebirth(Entity e) {
        return plugin.dimension().isRebirthWorld(e.getWorld());
    }

    private boolean bypass(Player p) {
        return p.hasPermission("rebirth.bypass");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !inRebirth(p)) {
            return;
        }
        Settings s = plugin.settings();
        if (s.noDamage) {
            e.setCancelled(true);
            return;
        }
        if (s.noPvp && e instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            if (damager instanceof Player) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && inRebirth(p) && plugin.settings().noHunger) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (inRebirth(e.getPlayer()) && plugin.settings().noBuilding && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (inRebirth(e.getPlayer()) && plugin.settings().noBuilding && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (inRebirth(e.getPlayer()) && plugin.settings().noBuilding && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (inRebirth(e.getPlayer()) && plugin.settings().noBuilding && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && inRebirth(p)
                && plugin.settings().noProjectiles && !bypass(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (inRebirth(e.getPlayer()) && plugin.settings().noItemDrop && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (inRebirth(e.getEntity()) && (plugin.settings().blockMobSpawns || e.getEntity() instanceof org.bukkit.entity.EnderDragon)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (plugin.dimension().isRebirthWorld(e.getWorld()) && e.toWeatherState()) {
            e.setCancelled(true);
        }
    }
}
