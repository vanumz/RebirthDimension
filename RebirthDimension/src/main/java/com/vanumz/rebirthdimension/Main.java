package com.vanumz.rebirthdimension;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private Settings settings;
    private TimerManager timers;
    private DimensionManager dimension;
    private BossBarManager bossBars;
    private EffectManager effects;
    private RebirthManager rebirth;
    private AuroraManager aurora;
    private StardustManager stardust;
    private DeathSceneManager deathScene;
    private BedSpawnManager bedSpawns;
    private GuiScaleManager guiScales;
    private BoundaryTask boundary;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig(), getLogger());

        timers = new TimerManager(this);
        timers.load();
        bedSpawns = new BedSpawnManager(this);
        bedSpawns.load();
        guiScales = new GuiScaleManager(this);
        guiScales.load();
        dimension = new DimensionManager(this);
        bossBars = new BossBarManager();
        effects = new EffectManager(this);
        rebirth = new RebirthManager(this);
        aurora = new AuroraManager(this);
        stardust = new StardustManager(this);
        deathScene = new DeathSceneManager(this);
        boundary = new BoundaryTask(this);

        if (!dimension.ensureLoaded()) {
            getLogger().severe("Dunia Rebirth tidak siap. Kematian tidak akan memicu Rebirth sampai masalah ini diperbaiki.");
        }

        getServer().getPluginManager().registerEvents(new RebirthListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new BedSpawnListener(this), this);

        PluginCommand gcmd = getCommand("guiscale");
        if (gcmd != null) {
            GuiScaleCommand gh = new GuiScaleCommand(this);
            gcmd.setExecutor(gh);
            gcmd.setTabCompleter(gh);
        }
        PluginCommand cmd = getCommand("rebirth");
        if (cmd != null) {
            RebirthCommand handler = new RebirthCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        startTasks();

        // Pemain yang sudah online (mis. setelah /reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            rebirth.onJoin(p);
        }
        getLogger().info("RebirthDimension aktif.");
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (dimension != null) {
            dimension.stopFightSuppression();
        }
        if (deathScene != null) {
            deathScene.abortAll();
        }
        if (effects != null) {
            effects.cancelAll();
        }
        if (bossBars != null) {
            bossBars.hideAll();
        }
        if (timers != null) {
            timers.save();
        }
        if (bedSpawns != null) {
            bedSpawns.save();
        }
    }

    private void startTasks() {
        rebirth.startTimerTask();
        aurora.start();
        stardust.start();
        boundary.start();
    }

    private void stopTasks() {
        if (rebirth != null) {
            rebirth.stopTimerTask();
        }
        if (aurora != null) {
            aurora.stop();
        }
        if (stardust != null) {
            stardust.stop();
        }
        if (boundary != null) {
            boundary.stop();
        }
    }

    public void reloadAll() {
        stopTasks();
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        // Ganti world-name/environment di config lalu /rebirth reload: world baru dibuat/dimuat,
        // pemain dengan timer aktif yang masih di world lama otomatis dipindah oleh tick timer.
        if (!dimension.isReady() || !dimension.getWorld().getName().equals(settings.rebirthWorld)) {
            dimension.ensureLoaded();
        } else {
            dimension.applyWorldSettings();
        }
        startTasks();
    }

    public Settings settings() {
        return settings;
    }

    public TimerManager timers() {
        return timers;
    }

    public DimensionManager dimension() {
        return dimension;
    }

    public BossBarManager bossBars() {
        return bossBars;
    }

    public EffectManager effects() {
        return effects;
    }

    public RebirthManager rebirth() {
        return rebirth;
    }

    public GuiScaleManager guiScales() {
        return guiScales;
    }

    public BedSpawnManager bedSpawns() {
        return bedSpawns;
    }

    public DeathSceneManager deathScene() {
        return deathScene;
    }

    public BoundaryTask boundary() {
        return boundary;
    }
}
