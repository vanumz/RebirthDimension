package com.vanumz.rebirthdimension;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Sejak MC 1.21.4 server mengabaikan paket serang/interaksi entity (handleInteract) selama koneksi pemain belum
 * "client loaded". Saat pemain mati, koneksi ditandai waitingForRespawn=true dan baru dilepas ketika CLIENT
 * mengirim paket respawn. Respawn paksa dari server (Player.Spigot#respawn) tidak pernah melepas flag itu, sehingga
 * pemain tidak bisa memukul entity sampai relog. Kelas ini melepas flag tersebut lewat refleksi (nama Mojang, dipakai
 * Paper dan Arclight/NeoForge saat runtime).
 */
final class ClientLoadFix {

    private static boolean warned;

    private ClientLoadFix() {
    }

    static void apply(Player p, Logger log) {
        try {
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            Object conn = findConnection(handle);
            if (conn == null) {
                warnOnce(log, "koneksi pemain tidak ditemukan");
                return;
            }
            boolean any = setField(conn, "waitingForRespawn", false);
            Method mark = findMethod(conn.getClass(), "markClientLoaded");
            if (mark != null) {
                mark.invoke(conn);
                any = true;
            }
            any |= setField(conn, "clientLoadedTimeoutTimer", 0);
            if (!any) {
                warnOnce(log, "field client-loaded tidak dikenali di " + conn.getClass().getName());
            }
        } catch (Throwable ex) {
            warnOnce(log, ex.toString());
        }
    }

    private static void warnOnce(Logger log, String why) {
        if (!warned) {
            warned = true;
            log.warning("ClientLoadFix tidak bisa diterapkan (" + why + "). Jika pemain tidak bisa memukul entity "
                    + "setelah respawn, matikan death.instant-respawn di config.");
        }
    }

    private static Object findConnection(Object handle) throws IllegalAccessException {
        for (Class<?> c = handle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                String type = f.getType().getSimpleName();
                if (f.getName().equals("connection") || type.equals("ServerGamePacketListenerImpl")
                        || type.equals("PlayerConnection")) {
                    f.setAccessible(true);
                    Object v = f.get(handle);
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private static boolean setField(Object target, String name, Object value) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // cek superclass
            } catch (Throwable ex) {
                return false;
            }
        }
        return false;
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }
}
