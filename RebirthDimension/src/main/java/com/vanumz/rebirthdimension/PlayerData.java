package com.vanumz.rebirthdimension;

import java.util.UUID;

/** Status Rebirth satu pemain. Immutable; timer disimpan sebagai timestamp berakhir (epoch ms). */
public final class PlayerData {

    public final UUID uuid;
    public final long endMillis;
    public final long durationMillis;
    /** Nama GameMode sebelum Rebirth (null = anggap SURVIVAL). */
    public final String prevGameMode;
    public final boolean prevAllowFlight;

    public PlayerData(UUID uuid, long endMillis, long durationMillis, String prevGameMode, boolean prevAllowFlight) {
        this.uuid = uuid;
        this.endMillis = endMillis;
        this.durationMillis = Math.max(1L, durationMillis);
        this.prevGameMode = prevGameMode;
        this.prevAllowFlight = prevAllowFlight;
    }

    public long remaining(long now) {
        return Math.max(0L, endMillis - now);
    }

    public boolean expired(long now) {
        return now >= endMillis;
    }

    public PlayerData withEnd(long newEnd) {
        return new PlayerData(uuid, newEnd, durationMillis, prevGameMode, prevAllowFlight);
    }
}
