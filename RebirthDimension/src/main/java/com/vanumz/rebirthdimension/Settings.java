package com.vanumz.rebirthdimension;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Snapshot konfigurasi yang sudah divalidasi. Nilai tidak masuk akal dikoreksi + dicatat di log. */
public final class Settings {

    public static final class EffectSettings {
        public final boolean enabled;
        public final int durationTicks;
        public final int density;
        public final List<SoundSpec> sounds;

        EffectSettings(boolean enabled, int durationTicks, int density, List<SoundSpec> sounds) {
            this.enabled = enabled;
            this.durationTicks = durationTicks;
            this.density = density;
            this.sounds = sounds;
        }
    }

    private final Logger log;

    // rebirth
    public final int durationSeconds;
    public final Set<String> enabledWorlds;
    public final String rebirthWorld;
    public final double centerX, centerY, centerZ, spawnScatter;
    public final World.Environment environment;
    public final String dimensionName;
    /** Hanya dipakai jika environment NORMAL; -1 = jangan ubah waktu. */
    public final long skyTime;

    // return
    public final String returnWorld;
    public final boolean returnUseWorldSpawn;
    public final boolean returnUseBedSpawn;
    public final double returnX, returnY, returnZ;
    public final float returnYaw, returnPitch;

    // flight
    public final boolean allowFlight, startFlying;
    public final float flySpeed;

    // boundary
    public final boolean boundaryEnabled;
    public final double radius, hardMargin, minY, maxY, pushStrength;
    public final int boundaryIntervalTicks, returnCooldownTicks;

    // protection
    public final boolean noDamage, noPvp, noHunger, noBuilding, noProjectiles, noItemDrop, blockMobSpawns;
    public final Set<String> blockedCommands;

    // bossbar
    public final boolean bossBarEnabled;
    public final String bossBarTitle;
    public final BossBar.Color bossBarColor;
    public final BossBar.Overlay bossBarOverlay;

    // aurora
    public final boolean auroraEnabled;
    public final int auroraIntervalTicks, auroraMaxPoints, auroraLayers, auroraRings;
    public final double auroraRenderDistance, auroraRingSpacing, auroraBaseHeight, auroraHeight,
            auroraSpacing, auroraParticleSize, auroraSpeed;
    public final ColorRamp auroraRamp;

    // effects
        public final int blinkTicks;
    public final int guiScaleDefault;
    public final boolean packSendOnJoin, packRequired;
    public final String packUrl, packSha1, packPrompt;
    public final boolean deathCosmic;
    public final int deathCosmicLead;
    public final String blinkMode;
    public final String whiteMode;
    public final boolean exitWhiteFlash;
    // death scene
    public final boolean deathInstantRespawn, deathSceneEnabled;
    public final int deathRespawnDelayTicks, deathSceneTicks, deathCloseTicks;
    public final double deathCameraDistance, deathCameraHeight, deathCameraSweepDeg;
    public final String deathCorpsePose;
    public final int exitFadeTicks;

    // stardust
    public final boolean stardustEnabled;
    public final int stardustIntervalTicks, stardustCount;
    public final double stardustRadius;
    public final List<Color> stardustColors;

    public final EffectSettings enter;
    public final EffectSettings exit;

    private final Map<String, String> messages = new HashMap<>();

    public Settings(FileConfiguration c, Logger log) {
        this.log = log;

        durationSeconds = clampInt(c, "rebirth.duration-seconds", 1800, 10, 604800);
        Set<String> worlds = new LinkedHashSet<>();
        for (String w : c.getStringList("rebirth.enabled-worlds")) {
            if (w != null && !w.isBlank()) {
                worlds.add(w.trim());
            }
        }
        if (!c.contains("rebirth.enabled-worlds")) {
            worlds.add("world");
            worlds.add("world_nether");
            worlds.add("world_the_end");
        }
        enabledWorlds = worlds;

        String rw = c.getString("rebirth.world-name", "null_entity");
        if (rw == null || rw.isBlank() || !rw.matches("[A-Za-z0-9_.\\-]+")) {
            log.warning("rebirth.world-name tidak valid, memakai 'null_entity'.");
            rw = "rebirth_dimension";
        }
        rebirthWorld = rw;
        centerX = c.getDouble("rebirth.center.x", 3000.5);
        centerY = clampDouble(c, "rebirth.center.y", 100.0, -32.0, 300.0);
        centerZ = c.getDouble("rebirth.center.z", 3000.5);
        spawnScatter = clampDouble(c, "rebirth.spawn-scatter", 0.0, 0.0, 100.0);
        skyTime = clampInt(c, "rebirth.sky-time", -1, -1, 23999);
        environment = parseEnvironment(c.getString("rebirth.environment", "THE_END"));
        String dn = c.getString("rebirth.dimension-name", "Null Entity");
        dimensionName = (dn == null || dn.isBlank()) ? "Null Entity" : dn.trim();

        String retW = c.getString("return.world", "world");
        returnWorld = (retW == null || retW.isBlank()) ? "world" : retW.trim();
        returnUseWorldSpawn = c.getBoolean("return.use-world-spawn", true);
        returnUseBedSpawn = c.getBoolean("return.use-bed-spawn", true);
        returnX = c.getDouble("return.x", 0.5);
        returnY = c.getDouble("return.y", 80.0);
        returnZ = c.getDouble("return.z", 0.5);
        returnYaw = (float) c.getDouble("return.yaw", 0.0);
        returnPitch = (float) c.getDouble("return.pitch", 0.0);

        allowFlight = c.getBoolean("flight.allow-flight", true);
        startFlying = c.getBoolean("flight.start-flying", true);
        flySpeed = (float) clampDouble(c, "flight.fly-speed", 0.1, 0.05, 0.5);

        boundaryEnabled = c.getBoolean("boundary.enabled", true);
        radius = clampDouble(c, "boundary.radius", 20.0, 5.0, 5000.0);
        hardMargin = clampDouble(c, "boundary.hard-margin", 0.0, 0.0, 500.0);
        double mn = clampDouble(c, "boundary.min-y", 95.0, -32.0, 300.0);
        double mx = clampDouble(c, "boundary.max-y", 140.0, -32.0, 310.0);
        if (mx - mn < 10.0) {
            log.warning("boundary.min-y / max-y terlalu sempit (minimal selisih 10). Memakai 95 dan 140.");
            mn = 95.0;
            mx = 140.0;
        }
        minY = mn;
        maxY = mx;
        pushStrength = clampDouble(c, "boundary.push-strength", 0.6, 0.1, 2.0);
        boundaryIntervalTicks = clampInt(c, "boundary.check-interval-ticks", 5, 5, 100);
        returnCooldownTicks = clampInt(c, "boundary.return-cooldown-ticks", 20, 5, 1200);

        noDamage = c.getBoolean("protection.no-damage", true);
        noPvp = c.getBoolean("protection.no-pvp", true);
        noHunger = c.getBoolean("protection.no-hunger", true);
        noBuilding = c.getBoolean("protection.no-building", true);
        noProjectiles = c.getBoolean("protection.no-projectiles", true);
        noItemDrop = c.getBoolean("protection.no-item-drop", true);
        blockMobSpawns = c.getBoolean("protection.block-mob-spawns", true);
        Set<String> blocked = new LinkedHashSet<>();
        for (String cmd : c.getStringList("protection.blocked-commands")) {
            if (cmd != null && !cmd.isBlank()) {
                String n = cmd.trim().toLowerCase(Locale.ROOT);
                blocked.add(n.startsWith("/") ? n.substring(1) : n);
            }
        }
        blockedCommands = blocked;

        bossBarEnabled = c.getBoolean("bossbar.enabled", true);
        bossBarTitle = c.getString("bossbar.title", "<gradient:#c4b5fd:#22d3ee>Terlahir Kembali</gradient> <dark_gray>:</dark_gray> <bold><white><time></white></bold>");
        bossBarColor = parseEnum(BossBar.Color.class, c.getString("bossbar.color", "PURPLE"), BossBar.Color.PURPLE, "bossbar.color");
        bossBarOverlay = parseEnum(BossBar.Overlay.class, c.getString("bossbar.overlay", "PROGRESS"), BossBar.Overlay.PROGRESS, "bossbar.overlay");

        auroraEnabled = c.getBoolean("aurora.enabled", true);
        auroraIntervalTicks = clampInt(c, "aurora.update-interval-ticks", 20, 10, 100);
        auroraRenderDistance = clampDouble(c, "aurora.render-distance", 64.0, 16.0, 128.0);
        auroraMaxPoints = clampInt(c, "aurora.max-points-per-update", 600, 50, 3000);
        auroraRingSpacing = clampDouble(c, "aurora.ring-spacing", 80.0, 30.0, 200.0);
        auroraLayers = clampInt(c, "aurora.layers", 3, 1, 3);
        auroraBaseHeight = clampDouble(c, "aurora.base-height", 40.0, 5.0, 150.0);
        auroraHeight = clampDouble(c, "aurora.height", 30.0, 5.0, 100.0);
        auroraSpacing = clampDouble(c, "aurora.point-spacing", 2.5, 1.0, 8.0);
        auroraParticleSize = clampDouble(c, "aurora.particle-size", 3.0, 0.5, 4.0);
        auroraSpeed = clampDouble(c, "aurora.speed", 1.0, 0.1, 3.0);
        auroraRings = (int) Math.max(1, Math.min(8, Math.ceil(radius / auroraRingSpacing)));

        List<Color> ramp = new ArrayList<>();
        for (String hex : c.getStringList("aurora.colors")) {
            Color col = parseColor(hex);
            if (col != null) {
                ramp.add(col);
            } else {
                log.warning("aurora.colors: warna tidak valid diabaikan: " + hex);
            }
        }
        if (ramp.isEmpty()) {
            ramp.add(Color.fromRGB(0x14b8a6));
            ramp.add(Color.fromRGB(0x22d3ee));
            ramp.add(Color.fromRGB(0x3b82f6));
            ramp.add(Color.fromRGB(0x7c3aed));
            ramp.add(Color.fromRGB(0x4c1d95));
        }
        auroraRamp = new ColorRamp(ramp);

        enter = readEffect(c, "effects.enter", 70, 10, 100, "minecraft:block.beacon.activate");
        blinkTicks = clampInt(c, "effects.enter.blink-ticks", 80, 0, 200);
        String purl = c.getString("resource-pack.url", "");
        purl = purl == null ? "" : purl.trim();
        String psha = c.getString("resource-pack.sha1", "");
        psha = psha == null ? "" : psha.trim().toLowerCase(Locale.ROOT);
        boolean psend = c.getBoolean("resource-pack.send-on-join", false);
        if (psend && (purl.isEmpty() || !psha.matches("[0-9a-f]{40}"))) {
            log.warning("resource-pack.send-on-join dimatikan: url kosong atau sha1 bukan 40 karakter hex.");
            psend = false;
        }
        packSendOnJoin = psend;
        packUrl = purl;
        packSha1 = psha;
        packRequired = c.getBoolean("resource-pack.required", false);
        String pprompt = c.getString("resource-pack.prompt", "<gradient:#a78bfa:#67e8f9>Resource pack Null Entity (kelopak mata & efek layar)</gradient>");
        packPrompt = pprompt == null ? "" : pprompt;
        guiScaleDefault = clampInt(c, "effects.gui-scale-default", 2, 1, 4);
        blinkMode = parseChoice(c.getString("effects.enter.blink-mode", "AUTO"), "effects.enter.blink-mode", "AUTO", "AUTO", "EYELID", "BLINDNESS", "NONE");
        exit = readEffect(c, "effects.exit", 60, 10, 80, "minecraft:entity.enderman.teleport");
        whiteMode = parseChoice(c.getString("effects.exit.white-mode", "AUTO"), "effects.exit.white-mode", "AUTO", "AUTO", "OVERLAY", "FLASH", "NONE");
        exitWhiteFlash = !whiteMode.equals("NONE");

        deathInstantRespawn = c.getBoolean("death.instant-respawn", true);
        deathRespawnDelayTicks = clampInt(c, "death.respawn-delay-ticks", 2, 0, 40);
        deathSceneEnabled = c.getBoolean("death.scene.enabled", true);
        int closeTicks = clampInt(c, "death.scene.close-ticks", 24, 8, 80);
        int sceneTicks = clampInt(c, "death.scene.duration-ticks", 130, 40, 400);
        if (sceneTicks < closeTicks + 20) {
            log.warning("death.scene.duration-ticks harus >= close-ticks + 20; dikoreksi menjadi " + (closeTicks + 20) + ".");
            sceneTicks = closeTicks + 20;
        }
        deathCloseTicks = closeTicks;
        deathCosmic = c.getBoolean("death.scene.cosmic-effect", true);
        deathCosmicLead = Math.min(clampInt(c, "death.scene.cosmic-lead-ticks", 50, 10, 120), Math.max(10, sceneTicks - closeTicks));
        deathSceneTicks = sceneTicks;
        deathCameraDistance = clampDouble(c, "death.scene.camera-distance", 2.2, 1.0, 12.0);
        deathCameraHeight = clampDouble(c, "death.scene.camera-height", 1.3, 0.5, 10.0);
        deathCameraSweepDeg = clampDouble(c, "death.scene.camera-sweep-degrees", 160.0, 0.0, 360.0);
        deathCorpsePose = parseChoice(c.getString("death.scene.corpse-pose", "SLEEPING"), "death.scene.corpse-pose", "SLEEPING", "SLEEPING", "SWIMMING");
        exitFadeTicks = clampInt(c, "effects.exit.fade-ticks", 40, 10, 100);

        stardustEnabled = c.getBoolean("stardust.enabled", true);
        stardustIntervalTicks = clampInt(c, "stardust.update-interval-ticks", 4, 2, 40);
        stardustCount = clampInt(c, "stardust.particles-per-update", 18, 1, 80);
        stardustRadius = clampDouble(c, "stardust.radius", 26.0, 8.0, 30.0);
        List<Color> stars = new ArrayList<>();
        for (String hex : c.getStringList("stardust.colors")) {
            Color col = parseColor(hex);
            if (col != null) {
                stars.add(col);
            } else {
                log.warning("stardust.colors: warna tidak valid diabaikan: " + hex);
            }
        }
        if (stars.isEmpty()) {
            stars.add(Color.fromRGB(0xffffff));
            stars.add(Color.fromRGB(0xa78bfa));
            stars.add(Color.fromRGB(0x60a5fa));
        }
        stardustColors = stars;

        String[][] defaults = {
                {"entered", "<gradient:#e9d5ff:#67e8f9>Kamu terlahir ke dalam <dimension>.</gradient>"},
                {"enter-title", "<bold><gradient:#e9d5ff:#67e8f9><dimension></gradient></bold>"},
                {"enter-subtitle", "<gradient:#a78bfa:#67e8f9>Terlahir Kembali</gradient>"},
                {"exiting", "<gradient:#67e8f9:#ffffff>Waktumu di <dimension> telah habis...</gradient>"},
                {"boundary-warning", "<gradient:#a78bfa:#67e8f9>Kembali ke tengah <dimension>...</gradient>"},
                {"boundary-return", "<gradient:#a78bfa:#67e8f9>Kamu terlalu jauh. Ditarik kembali ke pusat.</gradient>"},
                {"blocked-teleport", "<gradient:#f0abfc:#a78bfa>Kamu belum bisa meninggalkan <dimension>.</gradient>"},
                {"guiscale-hint", "<gradient:#a78bfa:#67e8f9>Efek layar Null Entity pas di GUI scale yang kamu pakai. Cek GUI scale di Esc > Options > Video Settings, lalu ketik /guiscale <1-4>.</gradient>"},
                {"bed-fallback", "<gradient:#a78bfa:#67e8f9>Tempat tidurmu hilang atau terhalang. Kamu kembali ke spawn dunia.</gradient>"},
                {"blocked-command", "<gradient:#f0abfc:#a78bfa>Perintah itu tidak bisa dipakai di <dimension>.</gradient>"}
        };
        for (String[] d : defaults) {
            String v = c.getString("messages." + d[0], d[1]);
            messages.put(d[0], v == null ? "" : v);
        }
    }

    /** Pesan MiniMessage mentah; string kosong berarti dinonaktifkan. */
    public String message(String key) {
        return messages.getOrDefault(key, "");
    }

    private EffectSettings readEffect(FileConfiguration c, String path, int defDuration, int minDur, int maxDur, String defSound) {
        boolean enabled = c.getBoolean(path + ".enabled", true);
        int dur = clampInt(c, path + ".duration-ticks", defDuration, minDur, maxDur);
        int density = clampInt(c, path + ".density", 6, 1, 8);
        List<SoundSpec> sounds = new ArrayList<>();
        List<Map<?, ?>> raw = c.getMapList(path + ".sounds");
        for (Map<?, ?> m : raw) {
            Object key = m.get("sound");
            if (key == null || key.toString().isBlank()) {
                continue;
            }
            float vol = (float) Math.max(0.0, Math.min(4.0, toDouble(m.get("volume"), 1.0)));
            float pitch = (float) Math.max(0.5, Math.min(2.0, toDouble(m.get("pitch"), 1.0)));
            int delay = (int) Math.max(0, Math.min(200, toDouble(m.get("delay"), 0.0)));
            sounds.add(new SoundSpec(key.toString().trim(), vol, pitch, delay));
        }
        if (raw.isEmpty() && !c.contains(path + ".sounds")) {
            sounds.add(new SoundSpec(defSound, 0.6f, 1.2f, 0));
        }
        return new EffectSettings(enabled, dur, density, sounds);
    }

    private String parseChoice(String raw, String path, String def, String... allowed) {
        if (raw == null) {
            return def;
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        for (String a : allowed) {
            if (a.equals(v)) {
                return v;
            }
        }
        log.warning(path + " = '" + raw + "' tidak dikenal, memakai " + def + ".");
        return def;
    }

    private World.Environment parseEnvironment(String raw) {
        String v = raw == null ? "THE_END" : raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        switch (v) {
            case "THE_END":
            case "END":
                return World.Environment.THE_END;
            case "NORMAL":
            case "OVERWORLD":
                return World.Environment.NORMAL;
            case "NETHER":
                return World.Environment.NETHER;
            default:
                log.warning("rebirth.environment = '" + raw + "' tidak dikenal (NORMAL / NETHER / THE_END), memakai THE_END.");
                return World.Environment.THE_END;
        }
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
                // pakai default
            }
        }
        return def;
    }

    private int clampInt(FileConfiguration c, String path, int def, int min, int max) {
        int v = c.getInt(path, def);
        if (v < min || v > max) {
            int fixed = Math.max(min, Math.min(max, v));
            log.warning(path + " = " + v + " di luar batas (" + min + " - " + max + "), dikoreksi menjadi " + fixed + ".");
            return fixed;
        }
        return v;
    }

    private double clampDouble(FileConfiguration c, String path, double def, double min, double max) {
        double v = c.getDouble(path, def);
        if (Double.isNaN(v) || v < min || v > max) {
            double fixed = Double.isNaN(v) ? def : Math.max(min, Math.min(max, v));
            log.warning(path + " = " + v + " di luar batas (" + min + " - " + max + "), dikoreksi menjadi " + fixed + ".");
            return fixed;
        }
        return v;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E def, String path) {
        if (raw == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning(path + " = '" + raw + "' tidak dikenal, memakai " + def.name() + ".");
            return def;
        }
    }

    private static Color parseColor(String s) {
        if (s == null) {
            return null;
        }
        String h = s.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        if (h.length() != 6) {
            return null;
        }
        try {
            return Color.fromRGB(Integer.parseInt(h, 16) & 0xFFFFFF);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
