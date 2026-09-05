package com.yapcore.map;

import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.RegionService;
import com.yapcore.regions.RegionServices;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Builds public {@code /map/markers.json} for the flat Leaflet viewer. */
public final class MapMarkers {

    private static final Logger LOG = Logger.getLogger("YaPMap");

    private MapMarkers() {
    }

    public static String toJson(MapConfig config) {
        return toJson(config, null);
    }

    public static String toJson(MapConfig config, Path dataFolder) {
        boolean players = config == null || config.markersPlayers();
        boolean npcs = config != null && config.markersNpcs();
        boolean regions = config != null && config.markersRegions();
        boolean claims = config != null && config.markersClaims();
        boolean pois = config == null || config.markersPois();
        boolean factionColors = config != null && config.markersFactionColors();
        int poll = config == null ? 5 : config.markersPollSeconds();
        int ox = config == null ? 0 : config.originChunkX() * 16;
        int oz = config == null ? 0 : config.originChunkZ() * 16;
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"players\":");
        appendPlayers(sb, players);
        sb.append(",\"npcs\":");
        if (npcs) {
            appendServiceCollection(sb, "com.yapcore.npcs.NpcService", "listRecords", MapMarkers::appendNpcRecord);
        } else {
            sb.append("[]");
        }
        sb.append(",\"regions\":");
        if (regions) {
            appendRegions(sb);
        } else {
            sb.append("[]");
        }
        sb.append(",\"claims\":");
        if (claims) {
            appendClaims(sb, factionColors);
        } else {
            sb.append("[]");
        }
        sb.append(",\"pois\":");
        if (pois) {
            appendPois(sb, dataFolder);
        } else {
            sb.append("[]");
        }
        sb.append(",\"showPlayers\":").append(players);
        sb.append(",\"showNpcs\":").append(npcs);
        sb.append(",\"showRegions\":").append(regions);
        sb.append(",\"showClaims\":").append(claims);
        sb.append(",\"showPois\":").append(pois);
        sb.append(",\"pollSeconds\":").append(Math.max(2, poll));
        sb.append(",\"originBlockX\":").append(ox);
        sb.append(",\"originBlockZ\":").append(oz);
        if (config != null) {
            sb.append(",\"worlds\":[");
            boolean first = true;
            for (String w : config.worlds()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(q(w));
            }
            sb.append(']');
            sb.append(",\"sampleChunkRadius\":").append(config.sampleChunkRadius());
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendRegions(StringBuilder sb) {
        sb.append('[');
        try {
            Optional<RegionService> regions = RegionServices.find();
            if (regions.isEmpty()) {
                sb.append(']');
                return;
            }
            List<AdminRegion> list = regions.get().listRegions();
            boolean first = true;
            for (AdminRegion region : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('{')
                        .append("\"name\":").append(q(region.name())).append(',')
                        .append("\"world\":").append(q(region.world())).append(',')
                        .append("\"minX\":").append(region.minX()).append(',')
                        .append("\"maxX\":").append(region.maxX()).append(',')
                        .append("\"minZ\":").append(region.minZ()).append(',')
                        .append("\"maxZ\":").append(region.maxZ())
                        .append('}');
            }
        } catch (Throwable e) {
            LOG.log(Level.FINE, "Map markers regions soft-depend failed", e);
        }
        sb.append(']');
    }

    private static void appendClaims(StringBuilder sb, boolean factionColors) {
        sb.append('[');
        try {
            Plugin plug = Bukkit.getPluginManager().getPlugin("YaPPlayerData");
            if (plug == null) {
                sb.append(']');
                return;
            }
            Object claimsSvc = plug.getClass().getMethod("claims").invoke(plug);
            if (claimsSvc == null) {
                sb.append(']');
                return;
            }
            @SuppressWarnings("unchecked")
            Collection<Object> list = (Collection<Object>) claimsSvc.getClass().getMethod("localClaims").invoke(claimsSvc);
            boolean first = true;
            for (Object claim : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                long id = ((Number) invoke(claim, "id", 0L)).longValue();
                String name = String.valueOf(invoke(claim, "name", "claim#" + id));
                if (name == null || "null".equals(name)) {
                    name = "claim#" + id;
                }
                String world = String.valueOf(invoke(claim, "world", "world"));
                int minX = ((Number) invoke(claim, "minX", 0)).intValue();
                int maxX = ((Number) invoke(claim, "maxX", 0)).intValue();
                int minZ = ((Number) invoke(claim, "minZ", 0)).intValue();
                int maxZ = ((Number) invoke(claim, "maxZ", 0)).intValue();
                String color = factionColors ? factionColorForClaim(id) : "#c060e0";
                sb.append('{')
                        .append("\"name\":").append(q(name)).append(',')
                        .append("\"world\":").append(q(world)).append(',')
                        .append("\"minX\":").append(minX).append(',')
                        .append("\"maxX\":").append(maxX).append(',')
                        .append("\"minZ\":").append(minZ).append(',')
                        .append("\"maxZ\":").append(maxZ).append(',')
                        .append("\"color\":").append(q(color))
                        .append('}');
            }
        } catch (Throwable e) {
            LOG.log(Level.FINE, "Map markers claims soft-depend failed", e);
        }
        sb.append(']');
    }

    private static String factionColorForClaim(long claimId) {
        try {
            Class<?> services = Class.forName("com.yapcore.factions.FactionServices");
            @SuppressWarnings("unchecked")
            Optional<Object> fs = (Optional<Object>) services.getMethod("find").invoke(null);
            if (fs == null || fs.isEmpty()) {
                return "#c060e0";
            }
            Object overlay = fs.get().getClass().getMethod("overlayForClaim", long.class).invoke(fs.get(), claimId);
            if (overlay instanceof Optional<?> opt && opt.isPresent()) {
                Object o = opt.get();
                long factionId = ((Number) o.getClass().getMethod("factionId").invoke(o)).longValue();
                return hashedColor(factionId);
            }
        } catch (Throwable ignored) {
        }
        return "#c060e0";
    }

    private static String hashedColor(long seed) {
        int h = Long.hashCode(seed);
        int r = 80 + ((h >>> 16) & 0x7F);
        int g = 60 + ((h >>> 8) & 0x7F);
        int b = 90 + (h & 0x7F);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static void appendPois(StringBuilder sb, Path dataFolder) {
        if (dataFolder == null) {
            sb.append("[]");
            return;
        }
        Path poi = dataFolder.resolve("poi.json");
        if (!Files.isRegularFile(poi)) {
            sb.append("[]");
            return;
        }
        try {
            String raw = Files.readString(poi, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                sb.append("[]");
                return;
            }
            // Expect a JSON array; pass through when already an array.
            if (raw.startsWith("[")) {
                sb.append(raw);
            } else {
                sb.append("[]");
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Map markers poi.json read failed", e);
            sb.append("[]");
        }
    }

    @FunctionalInterface
    private interface RecordAppender {
        void append(StringBuilder sb, Object record) throws ReflectiveOperationException;
    }

    private static void appendServiceCollection(StringBuilder sb, String serviceClass, String listMethod,
                                                RecordAppender appender) {
        sb.append('[');
        try {
            Class<?> type = Class.forName(serviceClass);
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(type);
            if (reg == null) {
                sb.append(']');
                return;
            }
            Object provider = reg.getProvider();
            @SuppressWarnings("unchecked")
            Collection<Object> list = (Collection<Object>) provider.getClass().getMethod(listMethod).invoke(provider);
            boolean first = true;
            for (Object record : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appender.append(sb, record);
            }
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.FINE, "Map markers soft-depend failed for " + serviceClass, e);
        }
        sb.append(']');
    }

    private static void appendNpcRecord(StringBuilder sb, Object npc) throws ReflectiveOperationException {
        String name = String.valueOf(invoke(npc, "displayName", invoke(npc, "id", "npc")));
        String world = String.valueOf(invoke(npc, "world", "world"));
        double x = ((Number) invoke(npc, "x", 0)).doubleValue();
        double y = ((Number) invoke(npc, "y", 0)).doubleValue();
        double z = ((Number) invoke(npc, "z", 0)).doubleValue();
        sb.append('{')
                .append("\"name\":").append(q(name)).append(',')
                .append("\"world\":").append(q(world)).append(',')
                .append("\"x\":").append(round(x)).append(',')
                .append("\"y\":").append(round(y)).append(',')
                .append("\"z\":").append(round(z))
                .append('}');
    }

    private static Object invoke(Object target, String method, Object fallback) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
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
