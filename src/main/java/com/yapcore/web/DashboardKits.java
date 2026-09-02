package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read/write {@code plugins/YaPPlayerData/kits.yml} for the dashboard kit builder. */
public final class DashboardKits {

    public static final Path RELATIVE = Path.of("plugins", "YaPPlayerData", "kits.yml");

    private DashboardKits() {
    }

    public static Path file(Path root) {
        return root.resolve(RELATIVE);
    }

    public static Map<String, Object> snapshot(Path root) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path file = file(root);
        out.put("configPresent", Files.isRegularFile(file));
        List<Map<String, Object>> kits = listKits(root);
        out.put("kits", kits);
        List<String> names = new ArrayList<>();
        for (Map<String, Object> kit : kits) {
            names.add(String.valueOf(kit.get("id")));
        }
        out.put("kitNames", names);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> listKits(Path root) {
        List<Map<String, Object>> out = new ArrayList<>();
        Path file = file(root);
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
            Object kitsObj = yaml.get("kits");
            if (!(kitsObj instanceof Map<?, ?> map)) {
                return out;
            }
            map.entrySet().stream()
                    .sorted((a, b) -> String.valueOf(a.getKey()).compareToIgnoreCase(String.valueOf(b.getKey())))
                    .forEach(e -> {
                        if (e.getValue() instanceof Map<?, ?> raw) {
                            out.add(DashboardKitItems.toKit(String.valueOf(e.getKey()), (Map<String, Object>) raw));
                        }
                    });
        } catch (IOException ignored) {
            return out;
        }
        return out;
    }

    public static void saveKit(Path root, Map<String, Object> kit) throws IOException {
        String id = normalizeId(String.valueOf(kit.getOrDefault("id", "")));
        if (id.isEmpty()) {
            throw new IllegalArgumentException("kit id required");
        }
        Path file = file(root);
        Files.createDirectories(file.getParent());
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file) : new LinkedHashMap<>();
        Map<String, Object> kits = DashboardNetworkSnapshots.mapOrCreate(yaml, "kits");
        kits.put(id, DashboardKitItems.fromKit(kit));
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    @SuppressWarnings("unchecked")
    public static void deleteKit(Path root, String id) throws IOException {
        String key = normalizeId(id);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("kit id required");
        }
        Path file = file(root);
        if (!Files.isRegularFile(file)) {
            return;
        }
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Object kitsObj = yaml.get("kits");
        if (kitsObj instanceof Map<?, ?> kits) {
            ((Map<String, Object>) kits).remove(key);
            DashboardNetworkSnapshots.dumpYaml(file, yaml);
        }
    }

    public static void cloneKit(Path root, String fromId, String toId) throws IOException {
        String from = normalizeId(fromId);
        String to = normalizeId(toId);
        if (from.isEmpty() || to.isEmpty()) {
            throw new IllegalArgumentException("from and to kit ids required");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("clone target must differ");
        }
        Map<String, Object> source = null;
        for (Map<String, Object> kit : listKits(root)) {
            if (from.equals(kit.get("id"))) {
                source = new LinkedHashMap<>(kit);
                break;
            }
        }
        if (source == null) {
            throw new IllegalArgumentException("unknown kit: " + from);
        }
        source.put("id", to);
        saveKit(root, source);
    }


    public static String normalizeId(String raw) {
        return DashboardKitItems.normalizeId(raw);
    }

    public static String encodeItems(List<Map<String, Object>> items) {
        return DashboardKitItems.encodeItems(items);
    }

    public static List<Map<String, Object>> decodeItems(String raw) {
        return DashboardKitItems.decodeItems(raw);
    }

    public static String encodeItem(Map<String, Object> item) {
        return DashboardKitItems.encodeItem(item);
    }

    public static Map<String, Object> decodeItem(String line) {
        return DashboardKitItems.decodeItem(line);
    }
}
