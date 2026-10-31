package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Satu BossBar per pemain (API Bukkit murni, kompatibel Spigot/Arclight); selalu dibersihkan saat selesai/quit/disable. */
public final class BossBarManager {

    private final Map<UUID, BossBar> bars = new HashMap<>();

    public void show(Player p, String title, double progress, BarColor color, BarStyle style) {
        double prog = Math.max(0.0, Math.min(1.0, progress));
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(title, color, style);
            bar.setProgress(prog);
            bar.addPlayer(p);
            bars.put(p.getUniqueId(), bar);
            return;
        }
        bar.setTitle(title);
        bar.setProgress(prog);
        bar.setColor(color);
        bar.setStyle(style);
        if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
        }
    }

    public void hide(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removePlayer(p);
            bar.removeAll();
        }
    }

    public void forget(UUID id) {
        BossBar bar = bars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void hideAll() {
        Iterator<Map.Entry<UUID, BossBar>> it = bars.entrySet().iterator();
        while (it.hasNext()) {
            it.next().getValue().removeAll();
            it.remove();
        }
    }
}
