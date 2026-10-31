package com.vanumz.rebirthdimension;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /guiscale <1-4|test> : pemain menyetel GUI scale-nya agar overlay kelopak/putih pas di layarnya. */
public final class GuiScaleCommand implements TabExecutor {

    private final Main plugin;

    public GuiScaleCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Perintah ini hanya untuk pemain.");
            return true;
        }
        int current = plugin.guiScales().get(p.getUniqueId());
        if (args.length == 0) {
            p.sendMessage("GUI scale overlay kamu: " + current + (plugin.guiScales().has(p.getUniqueId()) ? "" : " (default)")
                    + ". Cek GUI scale di Esc > Options > Video Settings > GUI Scale, lalu: /guiscale <1-4>. "
                    + "Tes efek: /guiscale test. (Auto di layar besar bisa 5 ke atas: pakai 4.)");
            return true;
        }
        String a = args[0].toLowerCase(Locale.ROOT);
        if (a.equals("test")) {
            plugin.effects().testOverlay(p);
            p.sendMessage("Tes overlay (GUI scale " + current + "): kelopak menutup, layar putih memudar, lalu kelopak membuka.");
            return true;
        }
        int v;
        try {
            v = Integer.parseInt(a);
        } catch (NumberFormatException ex) {
            p.sendMessage("Pakai: /guiscale <1|2|3|4|test>");
            return true;
        }
        if (v < 1) {
            p.sendMessage("GUI scale minimal 1.");
            return true;
        }
        int applied = Math.min(4, v);
        plugin.guiScales().set(p.getUniqueId(), applied);
        p.sendMessage("GUI scale overlay disetel ke " + applied + (v > 4 ? " (GUI scale " + v + " memakai set 4)" : "") + ". Coba: /guiscale test");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("1", "2", "3", "4", "test")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
