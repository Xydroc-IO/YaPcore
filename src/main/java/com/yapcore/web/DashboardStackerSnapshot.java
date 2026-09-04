package com.yapcore.web;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** YAML + live-command snapshot for YaPStacker dashboard tab. */
public final class DashboardStackerSnapshot {

    private DashboardStackerSnapshot() {
    }

    public static Map<String, Object> snapshot(Path root) {
        Map<String, Object> out = new LinkedHashMap<>(DashboardNetworkSnapshots.base(root, "yap-stacker", "YaPStacker"));
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPStacker", "config.yml");
        out.put("enabled", DashboardNetworkSnapshots.bool(yaml.get("enabled"), true));
        Map<String, Object> mobs = DashboardNetworkSnapshots.map(yaml.get("mobs"));
        Map<String, Object> items = DashboardNetworkSnapshots.map(yaml.get("items"));
        Map<String, Object> spawners = DashboardNetworkSnapshots.map(yaml.get("spawners"));
        out.put("mobsEnabled", DashboardNetworkSnapshots.bool(mobs.get("enabled"), true));
        out.put("itemsEnabled", DashboardNetworkSnapshots.bool(items.get("enabled"), true));
        out.put("spawnersEnabled", DashboardNetworkSnapshots.bool(spawners.get("enabled"), true));
        out.put("killMode", DashboardNetworkSnapshots.str(mobs.get("kill-mode"), "DECREMENT"));
        out.put("mobMaxStack", DashboardNetworkSnapshots.intVal(mobs.get("max-stack"), 100));
        out.put("itemMaxStack", DashboardNetworkSnapshots.intVal(items.get("max-stack"), 1000));
        out.put("spawnerMaxStack", DashboardNetworkSnapshots.intVal(spawners.get("max-stack"), 64));
        return out;
    }

    public static void saveSettings(Path root, Map<String, String> body) throws Exception {
        Path file = root.resolve("plugins").resolve("YaPStacker").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        if (body.containsKey("enabled")) {
            yaml.put("enabled", !"false".equalsIgnoreCase(body.get("enabled")));
        }
        applyNestedBool(yaml, "mobs", "enabled", body, "mobsEnabled");
        applyNestedBool(yaml, "items", "enabled", body, "itemsEnabled");
        applyNestedBool(yaml, "spawners", "enabled", body, "spawnersEnabled");
        if (body.containsKey("killMode")) {
            Map<String, Object> mobs = DashboardNetworkSnapshots.mapOrCreate(yaml, "mobs");
            mobs.put("kill-mode", body.get("killMode").trim().toUpperCase());
        }
        if (body.containsKey("mobMaxStack")) {
            Map<String, Object> mobs = DashboardNetworkSnapshots.mapOrCreate(yaml, "mobs");
            mobs.put("max-stack", Integer.parseInt(body.get("mobMaxStack")));
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    private static void applyNestedBool(Map<String, Object> yaml, String section, String key,
                                        Map<String, String> body, String bodyKey) {
        if (!body.containsKey(bodyKey)) {
            return;
        }
        Map<String, Object> nested = DashboardNetworkSnapshots.mapOrCreate(yaml, section);
        nested.put(key, !"false".equalsIgnoreCase(body.get(bodyKey)));
    }
}
