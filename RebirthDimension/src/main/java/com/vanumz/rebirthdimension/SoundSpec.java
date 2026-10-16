package com.vanumz.rebirthdimension;

/** Satu suara yang dimainkan setelah delay tertentu. Key berupa resource location (mendukung suara resource pack). */
public record SoundSpec(String key, float volume, float pitch, int delayTicks) {
}
