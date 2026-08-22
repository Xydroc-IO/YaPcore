package com.yapcore.web;

import com.yapcore.paper.PaperCommandBridge;
import com.yapcore.server.YaPcoreServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live Bukkit player list for the admin dashboard (via Paper/Folia classloader). */
public final class DashboardPlayerList {

    private DashboardPlayerList() {
    }

    public static List<Map<String, Object>> onlinePlayers(YaPcoreServer server) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            ClassLoader cl = PaperCommandBridge.resolvePaperLoader(null);
            if (cl == null) {
                return out;
            }
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object liveServer = bukkit.getMethod("getServer").invoke(null);
            if (liveServer == null) {
                return out;
            }
            @SuppressWarnings("unchecked")
            Collection<Object> players = (Collection<Object>) bukkit.getMethod("getOnlinePlayers").invoke(null);
            for (Object player : players) {
                out.add(row(player));
            }
            out.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        } catch (Exception ignored) {
        }
        return out;
    }

    private static Map<String, Object> row(Object player) throws ReflectiveOperationException {
        Map<String, Object> m = new LinkedHashMap<>();
        String name = String.valueOf(player.getClass().getMethod("getName").invoke(player));
        Object uuid = player.getClass().getMethod("getUniqueId").invoke(player);
        m.put("name", name);
        m.put("uuid", uuid == null ? "" : uuid.toString());
        m.put("displayName", stripColor(String.valueOf(
                player.getClass().getMethod("getDisplayName").invoke(player))));
        try {
            Object addr = player.getClass().getMethod("getAddress").invoke(player);
            if (addr != null) {
                Object inet = addr.getClass().getMethod("getAddress").invoke(addr);
                if (inet != null) {
                    m.put("ip", inet.getClass().getMethod("getHostAddress").invoke(inet));
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        if (!m.containsKey("ip")) {
            m.put("ip", "");
        }
        Object loc = player.getClass().getMethod("getLocation").invoke(player);
        if (loc != null) {
            Object world = loc.getClass().getMethod("getWorld").invoke(loc);
            if (world != null) {
                m.put("world", world.getClass().getMethod("getName").invoke(world));
            }
            m.put("x", round(loc.getClass().getMethod("getX").invoke(loc)));
            m.put("y", round(loc.getClass().getMethod("getY").invoke(loc)));
            m.put("z", round(loc.getClass().getMethod("getZ").invoke(loc)));
        }
        try {
            m.put("gamemode", String.valueOf(player.getClass().getMethod("getGameMode").invoke(player)));
        } catch (ReflectiveOperationException ignored) {
        }
        return m;
    }

    private static double round(Object val) {
        if (val instanceof Number n) {
            return Math.round(n.doubleValue() * 10.0) / 10.0;
        }
        return 0;
    }

    private static String stripColor(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\u00a7.", "");
    }
}
