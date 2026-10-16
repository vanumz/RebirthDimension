package com.vanumz.rebirthdimension;

import org.bukkit.Color;

import java.util.List;

/** Gradasi warna; posisi di luar 0..1 dipantulkan (triangle wave) agar transisi selalu mulus. */
public final class ColorRamp {

    private final Color[] colors;

    public ColorRamp(List<Color> colors) {
        if (colors == null || colors.isEmpty()) {
            throw new IllegalArgumentException("ColorRamp butuh minimal 1 warna");
        }
        this.colors = colors.toArray(new Color[0]);
    }

    public Color at(double u) {
        if (colors.length == 1) {
            return colors[0];
        }
        double x = u % 2.0;
        if (x < 0) {
            x += 2.0;
        }
        double f = x <= 1.0 ? x : 2.0 - x;
        double pos = f * (colors.length - 1);
        int i = (int) Math.floor(pos);
        if (i >= colors.length - 1) {
            return colors[colors.length - 1];
        }
        double k = pos - i;
        Color a = colors[i];
        Color b = colors[i + 1];
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k);
        return Color.fromRGB(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
