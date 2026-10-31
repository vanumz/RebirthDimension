package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Batas area horizontal & vertikal. Urutan: dorongan halus -> (jika terus melewati margin) pindah ke titik aman
 * di dalam area dengan cooldown, supaya pemain tidak bolak-balik diteleportasi.
 */
public final class BoundaryTask {

    private final Main plugin;
    private BukkitTask task;
    private final Map<UUID, Long> lastReturn = new HashMap<>();
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public BoundaryTask(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        Settings s = plugin.settings();
        if (!s.boundaryEnabled) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, s.boundaryIntervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void forget(UUID id) {
        lastReturn.remove(id);
        lastWarn.remove(id);
    }

    private void tick() {
        try {
            if (!plugin.dimension().isReady()) {
                return;
            }
            for (Player p : plugin.dimension().getWorld().getPlayers()) {
                check(p);
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Error pada pemeriksaan batas area: " + ex);
        }
    }

    private void check(Player p) {
        UUID id = p.getUniqueId();
        RebirthManager rm = plugin.rebirth();
        if (p.isDead() || rm.isFinishing(id) || rm.isInternalTeleport(id) || p.hasPermission("rebirth.bypass")) {
            return;
        }
        Settings s = plugin.settings();
        Location l = p.getLocation();
        double dx = l.getX() - s.centerX;
        double dz = l.getZ() - s.centerZ;
        double hd = Math.sqrt(dx * dx + dz * dz);
        double y = l.getY();

        boolean outH = hd > s.radius;
        boolean outY = y < s.minY || y > s.maxY;
        if (!outH && !outY) {
            return;
        }

        boolean hard = hd > s.radius + s.hardMargin || y < s.minY - s.hardMargin || y > s.maxY + s.hardMargin;
        long now = System.currentTimeMillis();

        if (!hard) {
            Vector push = new Vector(0, 0, 0);
            if (outH && hd > 0.0001) {
                push.setX(-dx / hd * s.pushStrength);
                push.setZ(-dz / hd * s.pushStrength);
            }
            if (y < s.minY) {
                push.setY(s.pushStrength);
            } else if (y > s.maxY) {
                push.setY(-s.pushStrength);
            }
            p.setVelocity(push);
            Long w = lastWarn.get(id);
            if (w == null || now - w > 4000L) {
                lastWarn.put(id, now);
                rm.actionBar(p, rm.parse("boundary-warning"));
            }
            return;
        }

        Long last = lastReturn.get(id);
        if (last != null && now - last < s.returnCooldownTicks * 50L) {
            return;
        }
        lastReturn.put(id, now);

        double tx = l.getX();
        double tz = l.getZ();
        if (hd > s.radius + s.hardMargin) {
            tx = s.centerX;
            tz = s.centerZ;
        }
        double ty = Math.max(s.minY + 2.0, Math.min(s.maxY - 2.0, y));
        Location from = l.clone();
        Location dest = new Location(l.getWorld(), tx, ty, tz, l.getYaw(), l.getPitch());
        try {
            Particles.spawn(l.getWorld(), Particle.REVERSE_PORTAL, from.getX(), from.getY() + 1.0, from.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
        } catch (Throwable ignored) {
            // efek tidak boleh mengganggu pengembalian
        }
        rm.teleportInternal(p, dest, ok -> {
            if (ok) {
                rm.actionBar(p, rm.parse("boundary-return"));
                try {
                    Particles.spawn(dest.getWorld(), Particle.REVERSE_PORTAL, dest.getX(), dest.getY() + 1.0, dest.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
                } catch (Throwable ignored) {
                    // abaikan
                }
            }
        });
    }
}
