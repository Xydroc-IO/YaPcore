package com.yapcore.web;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads permission nodes declared in installed plugin.yml files. */
public final class PluginPermissionScanner {

    private PluginPermissionScanner() {
    }

    public static List<Map<String, Object>> scan(Path pluginsDir) {
        Map<String, Map<String, Object>> byNode = new LinkedHashMap<>();
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return List.of();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                Map<String, Object> yaml = readPluginYaml(jar);
                if (yaml.isEmpty()) {
                    continue;
                }
                String plugin = String.valueOf(yaml.getOrDefault("name", jar.getFileName()));
                collectFromCommands(byNode, plugin, yaml.get("commands"));
                collectFromPermissions(byNode, plugin, yaml.get("permissions"));
            }
        } catch (Exception ignored) {
            /* keep whatever we parsed */
        }
        return new ArrayList<>(byNode.values());
    }

    public static Map<String, Object> discoveredCategory(Path pluginsDir, Set<String> alreadyListed) {
        List<Map<String, Object>> extra = new ArrayList<>();
        for (Map<String, Object> row : scan(pluginsDir)) {
            String node = String.valueOf(row.get("node"));
            if (alreadyListed != null && alreadyListed.contains(node)) {
                continue;
            }
            extra.add(row);
        }
        Map<String, Object> cat = new LinkedHashMap<>();
        cat.put("id", "discovered");
        cat.put("title", "From installed plugins");
        cat.put("hint", "Every permission declared in plugin.yml on disk. Add any other node below.");
        cat.put("nodes", extra);
        return cat;
    }

    @SuppressWarnings("unchecked")
    static void collectFromCommands(Map<String, Map<String, Object>> out, String plugin, Object commands) {
        if (!(commands instanceof Map<?, ?> map)) {
            return;
        }
        for (var e : map.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> cmd)) {
                continue;
            }
            Object perm = cmd.get("permission");
            if (perm == null || String.valueOf(perm).isBlank()) {
                continue;
            }
            String node = String.valueOf(perm).trim();
            String label = "/" + e.getKey();
            Object rawDesc = cmd.get("description");
            String desc = rawDesc == null ? plugin : String.valueOf(rawDesc);
            put(out, node, label, desc, plugin);
        }
    }

    @SuppressWarnings("unchecked")
    static void collectFromPermissions(Map<String, Map<String, Object>> out, String plugin, Object perms) {
        if (!(perms instanceof Map<?, ?> map)) {
            return;
        }
        for (var e : map.entrySet()) {
            String node = String.valueOf(e.getKey()).trim();
            if (node.isEmpty()) {
                continue;
            }
            String desc = plugin;
            if (e.getValue() instanceof Map<?, ?> meta && meta.get("description") != null) {
                desc = String.valueOf(meta.get("description"));
            }
            put(out, node, node, desc, plugin);
        }
    }

    private static void put(Map<String, Map<String, Object>> out, String node, String label, String desc,
                            String plugin) {
        Map<String, Object> row = out.computeIfAbsent(node, k -> {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("node", node);
            created.put("label", label);
            created.put("desc", desc);
            created.put("plugin", plugin);
            return created;
        });
        if (label.startsWith("/") && !String.valueOf(row.get("label")).startsWith("/")) {
            row.put("label", label);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readPluginYaml(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("plugin.yml");
            if (entry == null) {
                entry = zip.getEntry("paper-plugin.yml");
            }
            if (entry == null) {
                return Map.of();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                Object loaded = new Yaml().load(in);
                if (loaded instanceof Map<?, ?> map) {
                    return new LinkedHashMap<>((Map<String, Object>) map);
                }
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return Map.of();
    }

    public static Set<String> uniqueNodes(Path pluginsDir) {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> row : scan(pluginsDir)) {
            out.add(String.valueOf(row.get("node")).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
