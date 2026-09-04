package com.yapcore.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Snapshot + writers for YaPDisasters dashboard tab. */
public final class DashboardDisastersSnapshot {

    private static final String[] TYPES = {
            "clear", "rain", "thunder", "hurricane", "tornado", "earthquake",
            "volcano", "blizzard", "drought", "meteor", "tsunami"
    };

    private DashboardDisastersSnapshot() {
    }

    public static Path configFile(Path root) {
        return root.resolve("plugins").resolve("YaPDisasters").resolve("config.yml");
    }

    public static Map<String, Object> snapshot(Path root) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path pluginsDir = root.resolve("plugins");
        out.put("installed", jarPresent(pluginsDir, "yap-disasters"));
        Path file = configFile(root);
        out.put("configPresent", Files.isRegularFile(file));
        Map<String, Object> yaml = Map.of();
        if (Files.isRegularFile(file)) {
            try {
                yaml = DashboardNetworkSnapshots.loadYaml(file);
            } catch (Exception e) {
                yaml = Map.of();
            }
        }
        out.put("enabled", bool(yaml.get("enabled"), true));
        out.put("broadcastStart", bool(yaml.get("broadcast-start"), true));
        out.put("broadcastEnd", bool(yaml.get("broadcast-end"), true));
        out.put("defaultDurationSeconds", intVal(yaml.get("default-duration-seconds"), 120));
        out.put("grief", bool(yaml.get("grief"), false));
        out.put("realLightning", bool(yaml.get("real-lightning"), false));
        out.put("protectClaims", bool(yaml.get("protect-claims"), true));
        out.put("protectRegions", bool(yaml.get("protect-regions"), true));
        out.put("allowedWorlds", joinList(yaml.get("allowed-worlds")));

        Map<String, Object> warnings = map(yaml.get("warnings"));
        out.put("warningsEnabled", bool(warnings.get("enabled"), true));

        Map<String, Object> random = map(yaml.get("random"));
        out.put("randomEnabled", bool(random.get("enabled"), false));
        out.put("randomRequirePlayers", bool(random.get("require-players"), true));
        out.put("randomMinIntervalSeconds", intVal(random.get("min-interval-seconds"), 900));
        out.put("randomMaxIntervalSeconds", intVal(random.get("max-interval-seconds"), 2400));
        out.put("randomWarningSeconds", intVal(random.get("warning-seconds"), 30));
        out.put("randomDurationSeconds", intVal(random.get("duration-seconds"), 120));

        Map<String, Object> weights = map(random.get("weights"));
        Map<String, Object> weightOut = new LinkedHashMap<>();
        for (String type : TYPES) {
            if ("clear".equals(type) || "rain".equals(type)) {
                continue;
            }
            weightOut.put(type, intVal(weights.get(type), defaultWeight(type)));
        }
        out.put("weights", weightOut);

        out.put("volcanoSitesAmbient", bool(yaml.get("volcano-sites-ambient"), true));
        out.put("volcanoSiteSnapBlocks", intVal(yaml.get("volcano-site-snap-blocks"), 48));

        List<Map<String, Object>> sites = new ArrayList<>();
        Map<String, Object> siteMap = map(yaml.get("volcano-sites"));
        for (Map.Entry<String, Object> e : siteMap.entrySet()) {
            Map<String, Object> node = map(e.getValue());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getKey());
            row.put("world", str(node.get("world"), "world"));
            row.put("x", num(node.get("x"), 0));
            row.put("y", num(node.get("y"), 64));
            row.put("z", num(node.get("z"), 0));
            row.put("dormant", bool(node.get("dormant"), false));
            sites.add(row);
        }
        sites.sort((a, b) -> String.valueOf(a.get("id")).compareToIgnoreCase(String.valueOf(b.get("id"))));
        out.put("volcanoSites", sites);

        List<Map<String, Object>> disasters = new ArrayList<>();
        Map<String, Object> disastersYaml = map(yaml.get("disasters"));
        for (String type : TYPES) {
            Map<String, Object> node = map(disastersYaml.get(type));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", type);
            row.put("enabled", bool(node.get("enabled"), true));
            if (node.containsKey("period-ticks")) {
                row.put("periodTicks", intVal(node.get("period-ticks"), 20));
            }
            putIfPresent(row, node, "temporary-lava-ticks", "temporaryLavaTicks");
            putIfPresent(row, node, "temporary-snow-ticks", "temporarySnowTicks");
            putIfPresent(row, node, "temporary-dry-ticks", "temporaryDryTicks");
            putIfPresent(row, node, "temporary-fire-ticks", "temporaryFireTicks");
            putIfPresent(row, node, "temporary-water-ticks", "temporaryWaterTicks");
            putIfPresent(row, node, "wave-radius", "waveRadius");
            putIfPresent(row, node, "flood-height", "floodHeight");
            disasters.add(row);
        }
        out.put("disasters", disasters);
        out.put("types", List.of(TYPES));
        return out;
    }

    public static void saveSettings(Path root, Map<String, String> body) throws Exception {
        Path file = configFile(root);
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file)
                : new LinkedHashMap<>();

        applyBool(yaml, "enabled", body, "enabled");
        applyBool(yaml, "broadcast-start", body, "broadcastStart");
        applyBool(yaml, "broadcast-end", body, "broadcastEnd");
        applyInt(yaml, "default-duration-seconds", body, "defaultDurationSeconds");
        applyBool(yaml, "grief", body, "grief");
        applyBool(yaml, "real-lightning", body, "realLightning");
        applyBool(yaml, "protect-claims", body, "protectClaims");
        applyBool(yaml, "protect-regions", body, "protectRegions");
        if (body.containsKey("allowedWorlds")) {
            yaml.put("allowed-worlds", splitList(body.get("allowedWorlds")));
        }

        Map<String, Object> warnings = DashboardNetworkSnapshots.mapOrCreate(yaml, "warnings");
        applyBool(warnings, "enabled", body, "warningsEnabled");

        Map<String, Object> random = DashboardNetworkSnapshots.mapOrCreate(yaml, "random");
        applyBool(random, "enabled", body, "randomEnabled");
        applyBool(random, "require-players", body, "randomRequirePlayers");
        applyInt(random, "min-interval-seconds", body, "randomMinIntervalSeconds");
        applyInt(random, "max-interval-seconds", body, "randomMaxIntervalSeconds");
        applyInt(random, "warning-seconds", body, "randomWarningSeconds");
        applyInt(random, "duration-seconds", body, "randomDurationSeconds");

        Map<String, Object> weights = DashboardNetworkSnapshots.mapOrCreate(random, "weights");
        for (String type : TYPES) {
            if ("clear".equals(type) || "rain".equals(type)) {
                continue;
            }
            String key = "weight." + type;
            if (body.containsKey(key)) {
                weights.put(type, parseInt(body.get(key), defaultWeight(type)));
            }
        }

        applyBool(yaml, "volcano-sites-ambient", body, "volcanoSitesAmbient");
        applyInt(yaml, "volcano-site-snap-blocks", body, "volcanoSiteSnapBlocks");

        Map<String, Object> disasters = DashboardNetworkSnapshots.mapOrCreate(yaml, "disasters");
        for (String type : TYPES) {
            Map<String, Object> node = DashboardNetworkSnapshots.mapOrCreate(disasters, type);
            String enKey = "disaster." + type + ".enabled";
            if (body.containsKey(enKey)) {
                node.put("enabled", "true".equalsIgnoreCase(body.get(enKey)));
            }
            applyTypedInt(node, "period-ticks", body, "disaster." + type + ".periodTicks");
            applyTypedInt(node, "temporary-lava-ticks", body, "disaster." + type + ".temporaryLavaTicks");
            applyTypedInt(node, "temporary-snow-ticks", body, "disaster." + type + ".temporarySnowTicks");
            applyTypedInt(node, "temporary-dry-ticks", body, "disaster." + type + ".temporaryDryTicks");
            applyTypedInt(node, "temporary-fire-ticks", body, "disaster." + type + ".temporaryFireTicks");
            applyTypedInt(node, "temporary-water-ticks", body, "disaster." + type + ".temporaryWaterTicks");
            applyTypedInt(node, "wave-radius", body, "disaster." + type + ".waveRadius");
            applyTypedInt(node, "flood-height", body, "disaster." + type + ".floodHeight");
        }

        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void upsertSite(Path root, String id, String world, double x, double y, double z, boolean dormant)
            throws Exception {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("site id required");
        }
        Path file = configFile(root);
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file)
                : new LinkedHashMap<>();
        Map<String, Object> sites = DashboardNetworkSnapshots.mapOrCreate(yaml, "volcano-sites");
        String key = id.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("world", world == null || world.isBlank() ? "world" : world);
        node.put("x", x);
        node.put("y", y);
        node.put("z", z);
        if (dormant) {
            node.put("dormant", true);
        }
        sites.put(key, node);
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void removeSite(Path root, String id) throws Exception {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("site id required");
        }
        Path file = configFile(root);
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file)
                : new LinkedHashMap<>();
        Map<String, Object> sites = DashboardNetworkSnapshots.mapOrCreate(yaml, "volcano-sites");
        sites.remove(id.trim().toLowerCase(Locale.ROOT));
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    private static void putIfPresent(Map<String, Object> row, Map<String, Object> node, String yamlKey, String outKey) {
        if (node.containsKey(yamlKey)) {
            row.put(outKey, intVal(node.get(yamlKey), 0));
        }
    }

    private static int defaultWeight(String type) {
        return switch (type) {
            case "thunder" -> 10;
            case "hurricane", "blizzard" -> 6;
            case "tornado", "earthquake" -> 5;
            case "volcano", "drought" -> 4;
            case "meteor" -> 5;
            case "tsunami" -> 3;
            default -> 1;
        };
    }

    private static void applyBool(Map<String, Object> yaml, String yamlKey, Map<String, String> body, String bodyKey) {
        if (body.containsKey(bodyKey)) {
            yaml.put(yamlKey, "true".equalsIgnoreCase(body.get(bodyKey)));
        }
    }

    private static void applyInt(Map<String, Object> yaml, String yamlKey, Map<String, String> body, String bodyKey) {
        if (body.containsKey(bodyKey)) {
            yaml.put(yamlKey, parseInt(body.get(bodyKey), intVal(yaml.get(yamlKey), 0)));
        }
    }

    private static void applyTypedInt(Map<String, Object> node, String yamlKey, Map<String, String> body, String bodyKey) {
        if (body.containsKey(bodyKey)) {
            node.put(yamlKey, parseInt(body.get(bodyKey), intVal(node.get(yamlKey), 0)));
        }
    }

    private static List<String> splitList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("[,\\n]")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String joinList(Object val) {
        if (!(val instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(item);
        }
        return sb.toString();
    }

    private static boolean jarPresent(Path pluginsDir, String token) {
        if (!Files.isDirectory(pluginsDir)) {
            return false;
        }
        String needle = token.toLowerCase(Locale.ROOT);
        try (var stream = Files.list(pluginsDir)) {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".jar") && name.contains(needle);
            });
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object val) {
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new LinkedHashMap<>();
    }

    private static boolean bool(Object val, boolean fallback) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val != null) {
            String s = String.valueOf(val);
            if ("true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "1".equals(s)) {
                return true;
            }
            if ("false".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s) || "0".equals(s)) {
                return false;
            }
        }
        return fallback;
    }

    private static int intVal(Object val, int fallback) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static double num(Object val, double fallback) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val != null) {
            try {
                return Double.parseDouble(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String str(Object val, String fallback) {
        return val == null ? fallback : String.valueOf(val);
    }
}
