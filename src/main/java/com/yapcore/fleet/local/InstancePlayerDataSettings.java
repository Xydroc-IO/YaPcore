package com.yapcore.fleet.local;

import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.web.DashboardNetworkSnapshots;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-instance YaPPlayerData inventory sharing knobs for the fleet dashboard.
 * Intentionally not synced by {@link InstanceLayout#syncSharedCatalogData} — each backend
 * chooses whether to share the global profile or keep a private one.
 */
public final class InstancePlayerDataSettings {

    private static final String[] FOLDERS = {"YaPPlayerData", "yap-playerdata"};

    private InstancePlayerDataSettings() {
    }

    public static Map<String, Object> read(Path rootDir, FleetInstance inst) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inventoryProfile", "global");
        out.put("syncInventory", true);
        out.put("syncEnderchest", true);
        out.put("syncXp", true);
        out.put("syncVitals", true);
        out.put("syncEconomy", true);
        out.put("configPresent", false);
        Path cfg = resolveConfig(rootDir, inst);
        if (cfg == null) {
            return out;
        }
        try {
            Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(cfg);
            out.put("configPresent", true);
            out.put("inventoryProfile", str(yaml.get("inventory-profile"), "global"));
            Map<String, Object> sync = map(yaml.get("sync"));
            out.put("syncInventory", bool(sync.get("inventory"), true));
            out.put("syncEnderchest", bool(sync.get("enderchest"), true));
            out.put("syncXp", bool(sync.get("xp"), true));
            out.put("syncVitals", bool(sync.get("vitals"), true));
            out.put("syncEconomy", bool(sync.get("economy"), true));
        } catch (IOException ignored) {
            // defaults already set
        }
        return out;
    }

    /**
     * Apply inventory-profile / sync.* from a flat settings body. Returns true if the YAML changed.
     */
    public static boolean write(Path rootDir, FleetInstance inst, Map<String, String> body)
            throws IOException {
        if (body == null || !hasPlayerDataKeys(body)) {
            return false;
        }
        Path cfg = resolveConfig(rootDir, inst);
        if (cfg == null) {
            Path dir = rootDir.resolve(inst.relativeDir()).resolve("plugins").resolve("YaPPlayerData");
            Files.createDirectories(dir);
            cfg = dir.resolve("config.yml");
        }
        Map<String, Object> yaml = Files.isRegularFile(cfg)
                ? DashboardNetworkSnapshots.loadYaml(cfg)
                : new LinkedHashMap<>();

        boolean changed = false;
        String profile = firstPresent(body, "inventoryProfile", "inventory-profile");
        if (profile != null) {
            String mode = normalizeProfile(profile, body.get("inventoryProfileCustom"));
            Object prev = yaml.get("inventory-profile");
            if (!mode.equals(String.valueOf(prev == null ? "" : prev))) {
                yaml.put("inventory-profile", mode);
                changed = true;
            }
        }

        Map<String, Object> sync = mapOrCreate(yaml, "sync");
        changed |= putBool(sync, "inventory", firstPresent(body, "syncInventory", "sync.inventory"));
        changed |= putBool(sync, "enderchest", firstPresent(body, "syncEnderchest", "sync.enderchest"));
        changed |= putBool(sync, "xp", firstPresent(body, "syncXp", "sync.xp"));
        changed |= putBool(sync, "vitals", firstPresent(body, "syncVitals", "sync.vitals"));
        changed |= putBool(sync, "economy", firstPresent(body, "syncEconomy", "sync.economy"));

        if (changed) {
            DashboardNetworkSnapshots.dumpYaml(cfg, yaml);
        }
        return changed;
    }

    static Path resolveConfig(Path rootDir, FleetInstance inst) {
        Path base = rootDir.resolve(inst.relativeDir()).resolve("plugins");
        for (String folder : FOLDERS) {
            Path cfg = base.resolve(folder).resolve("config.yml");
            if (Files.isRegularFile(cfg)) {
                return cfg;
            }
        }
        return null;
    }

    private static boolean hasPlayerDataKeys(Map<String, String> body) {
        return firstPresent(body,
                "inventoryProfile", "inventory-profile",
                "inventoryProfileCustom",
                "syncInventory", "sync.inventory",
                "syncEnderchest", "sync.enderchest",
                "syncXp", "sync.xp",
                "syncVitals", "sync.vitals",
                "syncEconomy", "sync.economy") != null;
    }

    private static String normalizeProfile(String raw, String custom) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return "global";
        }
        String lower = v.toLowerCase(Locale.ROOT);
        if ("global".equals(lower) || "server".equals(lower)) {
            return lower;
        }
        if ("custom".equals(lower)) {
            String c = custom == null ? "" : custom.trim();
            return c.isEmpty() ? "global" : c;
        }
        return v;
    }

    private static boolean putBool(Map<String, Object> sync, String key, String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        boolean next = parseBool(raw);
        Object prev = sync.get(key);
        boolean prevBool = bool(prev, !next);
        if (prev instanceof Boolean && prevBool == next) {
            return false;
        }
        if (!(prev instanceof Boolean) && String.valueOf(prev).equalsIgnoreCase(String.valueOf(next))) {
            return false;
        }
        sync.put(key, next);
        return true;
    }

    private static boolean parseBool(String raw) {
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return "true".equals(t) || "1".equals(t) || "yes".equals(t) || "on".equals(t);
    }

    private static String firstPresent(Map<String, String> src, String... keys) {
        for (String key : keys) {
            if (src.containsKey(key)) {
                String v = src.get(key);
                return v == null ? "" : v;
            }
        }
        return null;
    }

    private static String str(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static boolean bool(Object v, boolean def) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "yes".equals(s) || "1".equals(s) || "on".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "no".equals(s) || "0".equals(s) || "off".equals(s)) {
            return false;
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object val) {
        if (val instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOrCreate(Map<String, Object> root, String key) {
        Object val = root.get(key);
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        root.put(key, created);
        return created;
    }
}
