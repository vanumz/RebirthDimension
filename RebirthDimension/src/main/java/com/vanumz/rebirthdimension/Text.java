package com.vanumz.rebirthdimension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Format teks bergaya MiniMessage (subset) yang diubah menjadi string legacy berwarna hex ("§x§r§r§g§g§b§b").
 * Tidak memakai Adventure sama sekali, sehingga jalan di Spigot/Arclight (yang tidak punya Adventure).
 *
 * Tag yang didukung: warna bernama (white, gray, dark_gray, ...), #RRGGBB, gradient:#a:#b[:#c...],
 * bold|b, italic|i|em, underlined|u, strikethrough|st, obfuscated|obf, reset, beserta penutupnya.
 * Placeholder berupa nama dalam kurung siku sudut diganti teks biasa lebih dulu. Tag tak dikenal ditampilkan apa adanya.
 */
public final class Text {

    private static final Map<String, Integer> NAMED = new HashMap<>();

    static {
        NAMED.put("black", 0x000000);
        NAMED.put("dark_blue", 0x0000aa);
        NAMED.put("dark_green", 0x00aa00);
        NAMED.put("dark_aqua", 0x00aaaa);
        NAMED.put("dark_red", 0xaa0000);
        NAMED.put("dark_purple", 0xaa00aa);
        NAMED.put("gold", 0xffaa00);
        NAMED.put("gray", 0xaaaaaa);
        NAMED.put("dark_gray", 0x555555);
        NAMED.put("blue", 0x5555ff);
        NAMED.put("green", 0x55ff55);
        NAMED.put("aqua", 0x55ffff);
        NAMED.put("red", 0xff5555);
        NAMED.put("light_purple", 0xff55ff);
        NAMED.put("yellow", 0xffff55);
        NAMED.put("white", 0xffffff);
    }

    private static final class Ch {
        final char c;
        int rgb;
        final boolean bold;
        final boolean italic;
        final boolean underlined;
        final boolean strike;
        final boolean obf;

        Ch(char c, int rgb, boolean bold, boolean italic, boolean underlined, boolean strike, boolean obf) {
            this.c = c;
            this.rgb = rgb;
            this.bold = bold;
            this.italic = italic;
            this.underlined = underlined;
            this.strike = strike;
            this.obf = obf;
        }

        boolean sameStyle(Ch o) {
            return o != null && rgb == o.rgb && bold == o.bold && italic == o.italic
                    && underlined == o.underlined && strike == o.strike && obf == o.obf;
        }
    }

    private static final class Grad {
        final List<Integer> colors;
        final int start;

        Grad(List<Integer> colors, int start) {
            this.colors = colors;
            this.start = start;
        }
    }

    private Text() {
    }

    /** Render dengan placeholder (nilai disisipkan sebagai teks biasa). */
    public static String render(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String t = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                String v = e.getValue() == null ? "" : e.getValue().replace("<", "").replace(">", "");
                t = t.replace("<" + e.getKey() + ">", v);
            }
        }
        return toLegacy(parse(t));
    }

    public static String render(String template) {
        return render(template, null);
    }

    public static String render(String template, String key, String value) {
        Map<String, String> m = new HashMap<>();
        m.put(key, value);
        return render(template, m);
    }

    private static List<Ch> parse(String t) {
        List<Ch> out = new ArrayList<>();
        Deque<Integer> colors = new ArrayDeque<>();
        Deque<Grad> grads = new ArrayDeque<>();
        int bold = 0;
        int italic = 0;
        int underlined = 0;
        int strike = 0;
        int obf = 0;

        int i = 0;
        int n = t.length();
        while (i < n) {
            char c = t.charAt(i);
            if (c == '<') {
                int j = t.indexOf('>', i);
                if (j > i) {
                    String tag = t.substring(i + 1, j).trim().toLowerCase(Locale.ROOT);
                    boolean closing = tag.startsWith("/");
                    String name = closing ? tag.substring(1) : tag;
                    boolean handled = true;
                    if (closing) {
                        if (name.equals("gradient")) {
                            if (!grads.isEmpty()) {
                                finishGradient(out, grads.pop());
                            }
                        } else if (colorOf(name) != null) {
                            if (!colors.isEmpty()) {
                                colors.pop();
                            }
                        } else if (name.equals("bold") || name.equals("b")) {
                            bold = Math.max(0, bold - 1);
                        } else if (name.equals("italic") || name.equals("i") || name.equals("em")) {
                            italic = Math.max(0, italic - 1);
                        } else if (name.equals("underlined") || name.equals("u")) {
                            underlined = Math.max(0, underlined - 1);
                        } else if (name.equals("strikethrough") || name.equals("st")) {
                            strike = Math.max(0, strike - 1);
                        } else if (name.equals("obfuscated") || name.equals("obf")) {
                            obf = Math.max(0, obf - 1);
                        } else {
                            handled = false;
                        }
                    } else if (name.equals("reset")) {
                        colors.clear();
                        while (!grads.isEmpty()) {
                            finishGradient(out, grads.pop());
                        }
                        bold = 0;
                        italic = 0;
                        underlined = 0;
                        strike = 0;
                        obf = 0;
                    } else if (name.startsWith("gradient")) {
                        List<Integer> cols = new ArrayList<>();
                        String[] parts = name.split(":");
                        for (int k = 1; k < parts.length; k++) {
                            Integer v = colorOf(parts[k]);
                            if (v != null) {
                                cols.add(v);
                            }
                        }
                        if (cols.size() >= 2) {
                            grads.push(new Grad(cols, out.size()));
                        } else {
                            handled = false;
                        }
                    } else if (name.equals("bold") || name.equals("b")) {
                        bold++;
                    } else if (name.equals("italic") || name.equals("i") || name.equals("em")) {
                        italic++;
                    } else if (name.equals("underlined") || name.equals("u")) {
                        underlined++;
                    } else if (name.equals("strikethrough") || name.equals("st")) {
                        strike++;
                    } else if (name.equals("obfuscated") || name.equals("obf")) {
                        obf++;
                    } else {
                        Integer v = colorOf(name);
                        if (v != null) {
                            colors.push(v);
                        } else {
                            handled = false;
                        }
                    }
                    if (handled) {
                        i = j + 1;
                        continue;
                    }
                }
            }
            out.add(new Ch(c, colors.isEmpty() ? -1 : colors.peek(), bold > 0, italic > 0, underlined > 0, strike > 0, obf > 0));
            i++;
        }
        while (!grads.isEmpty()) {
            finishGradient(out, grads.pop());
        }
        return out;
    }

    private static Integer colorOf(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        if (s.startsWith("#") && s.length() == 7) {
            try {
                return Integer.parseInt(s.substring(1), 16);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return NAMED.get(s);
    }

    private static void finishGradient(List<Ch> out, Grad g) {
        int from = g.start;
        int count = out.size() - from;
        if (count <= 0) {
            return;
        }
        List<Integer> cols = g.colors;
        for (int k = 0; k < count; k++) {
            Ch ch = out.get(from + k);
            if (ch.rgb != -1) {
                continue;
            }
            double pos = count == 1 ? 0.0 : k / (double) (count - 1);
            double scaled = pos * (cols.size() - 1);
            int idx = Math.min((int) Math.floor(scaled), cols.size() - 2);
            ch.rgb = lerp(cols.get(idx), cols.get(idx + 1), scaled - idx);
        }
    }

    private static int lerp(int a, int b, double f) {
        int r = (int) Math.round(((a >> 16) & 0xff) + (((b >> 16) & 0xff) - ((a >> 16) & 0xff)) * f);
        int g = (int) Math.round(((a >> 8) & 0xff) + (((b >> 8) & 0xff) - ((a >> 8) & 0xff)) * f);
        int bl = (int) Math.round((a & 0xff) + ((b & 0xff) - (a & 0xff)) * f);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static String hex(int rgb) {
        String h = String.format(Locale.ROOT, "%06x", rgb & 0xffffff);
        StringBuilder sb = new StringBuilder("\u00a7x");
        for (int k = 0; k < 6; k++) {
            sb.append('\u00a7').append(h.charAt(k));
        }
        return sb.toString();
    }

    private static String toLegacy(List<Ch> chars) {
        StringBuilder sb = new StringBuilder();
        Ch prev = null;
        for (Ch ch : chars) {
            if (!ch.sameStyle(prev)) {
                sb.append(ch.rgb == -1 ? "\u00a7r" : hex(ch.rgb));
                if (ch.bold) {
                    sb.append("\u00a7l");
                }
                if (ch.italic) {
                    sb.append("\u00a7o");
                }
                if (ch.underlined) {
                    sb.append("\u00a7n");
                }
                if (ch.strike) {
                    sb.append("\u00a7m");
                }
                if (ch.obf) {
                    sb.append("\u00a7k");
                }
            }
            sb.append(ch.c);
            prev = ch;
        }
        return sb.toString();
    }
}
