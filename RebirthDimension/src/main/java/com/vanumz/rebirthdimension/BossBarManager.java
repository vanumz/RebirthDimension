package com.vanumz.rebirthdimension;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Satu BossBar per pemain; selalu dibersihkan saat selesai/quit/disable. */
public final class BossBarManager {

    private final Map<UUID, BossBar> bars = new HashMap<>();

    public void show(Player p, Component title, double progress, BossBar.Color color, BossBar.Overlay overlay) {
        float prog = (float) Math.max(0.0, Math.min(1.0, progress));
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(title, prog, color, overlay);
            bars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(prog);
            bar.color(color);
            bar.overlay(overlay);
        }
    }

    public void hide(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
    }

    public void forget(UUID id) {
        bars.remove(id);
    }

    public void hideAll() {
        Iterator<Map.Entry<UUID, BossBar>> it = bars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BossBar> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
            it.remove();
        }
    }
}
