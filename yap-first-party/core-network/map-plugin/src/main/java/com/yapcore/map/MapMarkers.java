package com.yapcore.map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Builds public {@code /map/markers.json} for the flat Leaflet viewer. */
public final class MapMarkers {

    private MapMarkers() {
    }

    public static String toJson(MapConfig config) {
        boolean players = config == null || config.markersPlayers();
        boolean npcs = config != null && config.markersNpcs();
        boolean regions = config != null && config.markersRegions();
        int poll = config == null ? 5 : config.markersPollSeconds();
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"players\":");
        appendPlayers(sb, players);
        sb.append(",\"npcs\":[]");
        sb.append(",\"regions\":[]");
        sb.append(",\"showPlayers\":").append(players);
        sb.append(",\"showNpcs\":").append(npcs);
        sb.append(",\"showRegions\":").append(regions);
        sb.append(",\"pollSeconds\":").append(Math.max(2, poll));
        sb.append('}');
        return sb.toString();
    }

    private static void appendPlayers(StringBuilder sb, boolean enabled) {
        sb.append('[');
        if (!enabled) {
            sb.append(']');
            return;
        }
        boolean first = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            World world = player.getWorld();
            sb.append('{')
                    .append("\"name\":").append(q(player.getName())).append(',')
                    .append("\"world\":").append(q(world == null ? "world" : world.getName())).append(',')
                    .append("\"x\":").append(round(player.getLocation().getX())).append(',')
                    .append("\"y\":").append(round(player.getLocation().getY())).append(',')
                    .append("\"z\":").append(round(player.getLocation().getZ()))
                    .append('}');
        }
        sb.append(']');
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String q(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", "") + "\"";
    }
}
