package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /rebirth status [pemain] | release <pemain> | send <pemain> [menit] | reload */
public final class RebirthCommand implements TabExecutor {

    private final Main plugin;

    public RebirthCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rebirth.admin")) {
            sender.sendMessage("Kamu tidak punya izin.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Pemakaian: /rebirth <status [pemain]|release <pemain>|send <pemain> [menit]|reload|debug [pemain]|scene [pemain]|blink [pemain]|bed [pemain]|packtest [pemain]>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender, args);
            case "release" -> release(sender, args);
            case "send" -> send(sender, args);
            case "packtest" -> {
                Player t = resolveOnline(sender, args);
                if (t == null) {
                    sender.sendMessage("Pemain harus online.");
                } else {
                    plugin.effects().testOverlay(t);
                    sender.sendMessage("Tes overlay: kelopak menutup, layar putih memudar, lalu kelopak membuka (~8 detik). "
                            + "Jika kamu melihat kotak/karakter aneh atau tidak ada perubahan, resource pack belum aktif di klienmu. " + plugin.effects().describe(t));
                }
            }
            case "bed" -> {
                OfflinePlayer t = resolveTarget(sender, args);
                if (t == null) {
                    sender.sendMessage("Pemain tidak ditemukan.");
                } else {
                    sender.sendMessage((t.getName() == null ? t.getUniqueId().toString() : t.getName()) + ": " + plugin.bedSpawns().describe(t.getUniqueId()));
                }
            }
            case "debug" -> {
                Player t = resolveOnline(sender, args);
                if (t == null) {
                    sender.sendMessage("Pemain harus online.");
                } else {
                    sender.sendMessage(t.getName() + ": " + plugin.effects().describe(t));
                }
            }
            case "scene" -> {
                Player t = resolveOnline(sender, args);
                if (t == null) {
                    sender.sendMessage("Pemain harus online.");
                } else if (plugin.deathScene().startTest(t)) {
                    sender.sendMessage("Memutar adegan kematian (tes) untuk " + t.getName() + ". Tidak ada teleport ke dimensi.");
                } else {
                    sender.sendMessage("Adegan tidak bisa dimulai (sudah berjalan?). Cek console.");
                }
            }
            case "blink" -> {
                Player t = resolveOnline(sender, args);
                if (t == null) {
                    sender.sendMessage("Pemain harus online.");
                } else {
                    plugin.effects().playClose(t, plugin.settings().deathCloseTicks);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (t.isOnline()) {
                            plugin.effects().playEnter(t);
                        }
                    }, plugin.settings().deathCloseTicks + 20L);
                    sender.sendMessage("Tes kelopak: menutup lalu membuka untuk " + t.getName() + ". " + plugin.effects().describe(t));
                }
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage("Konfigurasi dimuat ulang. (Perubahan world-name butuh restart server.)");
            }
            default -> sender.sendMessage("Subperintah tidak dikenal.");
        }
        return true;
    }

    private void status(CommandSender sender, String[] args) {
        OfflinePlayer target = resolveTarget(sender, args);
        if (target == null) {
            sender.sendMessage("Pemain tidak ditemukan.");
            return;
        }
        PlayerData d = plugin.timers().get(target.getUniqueId());
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        if (d == null) {
            sender.sendMessage(name + " tidak sedang menjalani Rebirth.");
            return;
        }
        long rem = d.remaining(System.currentTimeMillis());
        sender.sendMessage(name + ": sisa " + RebirthManager.formatTime(rem) + (target.isOnline() ? " (online)" : " (offline)"));
    }

    private void release(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Pemakaian: /rebirth release <pemain>");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            if (plugin.timers().get(online.getUniqueId()) == null) {
                sender.sendMessage(online.getName() + " tidak sedang menjalani Rebirth.");
                return;
            }
            plugin.rebirth().finish(online);
            sender.sendMessage(online.getName() + " dilepaskan dari Rebirth.");
            return;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (off == null || plugin.timers().get(off.getUniqueId()) == null) {
            sender.sendMessage("Pemain tidak ditemukan atau tidak sedang menjalani Rebirth.");
            return;
        }
        plugin.timers().expireNow(off.getUniqueId());
        sender.sendMessage(args[1] + " (offline) ditandai selesai; akan dikeluarkan saat login berikutnya.");
    }

    private void send(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Pemakaian: /rebirth send <pemain> [menit]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("Pemain harus online.");
            return;
        }
        int seconds = plugin.settings().durationSeconds;
        if (args.length >= 3) {
            try {
                int minutes = Integer.parseInt(args[2]);
                seconds = Math.max(1, Math.min(10080, minutes)) * 60;
            } catch (NumberFormatException ex) {
                sender.sendMessage("Menit harus berupa angka.");
                return;
            }
        }
        if (!plugin.rebirth().startManual(target, seconds)) {
            sender.sendMessage("Dunia Rebirth belum siap; cek log server.");
            return;
        }
        sender.sendMessage(target.getName() + " dikirim ke Rebirth.");
    }

    private Player resolveOnline(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            return Bukkit.getPlayerExact(args[1]);
        }
        return sender instanceof Player p ? p : null;
    }

    private OfflinePlayer resolveTarget(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null) {
                return online;
            }
            return Bukkit.getOfflinePlayerIfCached(args[1]);
        }
        if (sender instanceof Player p) {
            return p;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("rebirth.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("status", "release", "send", "reload", "debug", "scene", "blink", "bed", "packtest")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
