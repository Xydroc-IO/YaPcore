package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Flatten / write plugin YAML for the generic dashboard editor. */
public final class PluginConfigIo {

    private PluginConfigIo() {
    }

    public static List<Map<String, Object>> flatten(Map<String, Object> yaml) {
        List<Map<String, Object>> out = new ArrayList<>();
        walk("", yaml, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(String prefix, Map<String, Object> map, List<Map<String, Object>> out) {
        if (map == null) {
            return;
        }
        for (var e : map.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.startsWith("_") || key.startsWith("#")) {
                continue;
            }
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object val = e.getValue();
            if (val instanceof Map<?, ?> nested) {
                walk(path, (Map<String, Object>) nested, out);
                continue;
            }
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("key", path);
            field.put("secret", isSecret(path));
            if (val instanceof Boolean) {
                field.put("type", "bool");
                field.put("value", val);
            } else if (val instanceof Number n) {
                field.put("type", "number");
                field.put("value", n);
            } else if (val instanceof List<?> list) {
                if (list.stream().anyMatch(item -> item instanceof Map<?, ?>)) {
                    field.put("type", "complex");
                    field.put("value", list.size() + " entries (edit YAML on disk)");
                    field.put("readonly", true);
                } else {
                    field.put("type", "list");
                    field.put("value", joinList(list));
                }
            } else {
                field.put("type", "text");
                field.put("value", val == null ? "" : String.valueOf(val));
            }
            out.add(field);
        }
    }

    public static void apply(Map<String, Object> yaml, String dottedKey, String raw) {
        if (dottedKey == null || dottedKey.isBlank()) {
            return;
        }
        String[] parts = dottedKey.split("\\.");
        Map<String, Object> cur = yaml;
        for (int i = 0; i < parts.length - 1; i++) {
            cur = DashboardNetworkSnapshots.mapOrCreate(cur, parts[i]);
        }
        String leaf = parts[parts.length - 1];
        Object existing = cur.get(leaf);
        cur.put(leaf, coerce(existing, raw));
    }

    public static Object coerce(Object existing, String raw) {
        if (existing instanceof Boolean) {
            return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
        }
        if (existing instanceof Integer) {
            try {
                return Integer.parseInt(raw == null ? "0" : raw.trim());
            } catch (NumberFormatException e) {
                return existing;
            }
        }
        if (existing instanceof Long) {
            try {
                return Long.parseLong(raw == null ? "0" : raw.trim());
            } catch (NumberFormatException e) {
                return existing;
            }
        }
        if (existing instanceof Double || existing instanceof Float) {
            try {
                return Double.parseDouble(raw == null ? "0" : raw.trim());
            } catch (NumberFormatException e) {
                return existing;
            }
        }
        if (existing instanceof List<?>) {
            List<String> out = new ArrayList<>();
            if (raw != null && !raw.isBlank()) {
                for (String part : raw.split("[,\\n]")) {
                    String t = part.trim();
                    if (!t.isEmpty()) {
                        out.add(t);
                    }
                }
            }
            return out;
        }
        return raw == null ? "" : raw;
    }

    public static boolean isSecret(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("secret") || lower.endsWith(".token")
                || lower.contains("api-key") || lower.contains("apikey");
    }

    public static Path configPath(Path root, PluginConfigCatalog.Entry entry) {
        return root.resolve("plugins").resolve(entry.dataDir()).resolve(entry.file());
    }

    public static Map<String, Object> load(Path root, PluginConfigCatalog.Entry entry) throws IOException {
        Path file = configPath(root, entry);
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        return DashboardNetworkSnapshots.loadYaml(file);
    }

    public static void save(Path root, PluginConfigCatalog.Entry entry, Map<String, String> fields)
            throws IOException {
        Path file = configPath(root, entry);
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file) : new LinkedHashMap<>();
        for (var e : fields.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank() || "action".equals(key) || "plugin".equals(key)) {
                continue;
            }
            apply(yaml, key, e.getValue());
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    private static String joinList(List<?> list) {
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
}
