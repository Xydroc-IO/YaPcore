package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parse YaPPerms YAML layers for the dashboard access editor. */
public final class DashboardPermsNodes {

    private DashboardPermsNodes() {
    }

    public static Map<String, List<String>> parseStarterGrants(Object grantsObj) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (!(grantsObj instanceof Map<?, ?> map)) {
            return out;
        }
        for (var e : map.entrySet()) {
            out.put(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT),
                    DashboardNetworkSnapshots.stringList(e.getValue(), List.of()));
        }
        return out;
    }

    /**
     * Explicit group nodes for the web editor.
     * starter-grants + editor-nodes are the dashboard truth for catalog commands.
     * Live dump adds only extra (non-catalog) nodes so a stale dump cannot undo a save.
     */
    public static Map<String, Map<String, Boolean>> mergeGroupNodes(Path root, Map<String, Object> yaml) {
        Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
        mergeNodeLayer(out, grantsAsAllows(yaml.get("starter-grants")));
        mergeNodeLayer(out, parseEditorNodes(yaml.get("editor-nodes")));
        Path snap = root.resolve("plugins").resolve("YaPPerms").resolve("editor-snapshot.yml");
        if (Files.isRegularFile(snap)) {
            try {
                mergeExtraSnapshotNodes(out, parseEditorNodes(DashboardNetworkSnapshots.loadYaml(snap).get("groups")));
            } catch (IOException ignored) {
                /* keep YAML layer */
            }
        }
        return out;
    }

    private static void mergeExtraSnapshotNodes(Map<String, Map<String, Boolean>> dest,
                                                Map<String, Map<String, Boolean>> snapshot) {
        java.util.Set<String> catalog = new java.util.HashSet<>(PermissionCatalog.allNodes());
        for (var e : snapshot.entrySet()) {
            Map<String, Boolean> nodes = dest.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>());
            for (var node : e.getValue().entrySet()) {
                if (!catalog.contains(node.getKey()) && !nodes.containsKey(node.getKey())) {
                    nodes.put(node.getKey(), node.getValue());
                }
            }
        }
    }

    private static Map<String, Map<String, Boolean>> grantsAsAllows(Object grantsObj) {
        Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
        for (var e : parseStarterGrants(grantsObj).entrySet()) {
            Map<String, Boolean> nodes = new LinkedHashMap<>();
            for (String node : e.getValue()) {
                if (node != null && !node.isBlank()) {
                    nodes.put(node, true);
                }
            }
            out.put(e.getKey(), nodes);
        }
        return out;
    }

    public static Map<String, Map<String, Boolean>> parseEditorNodes(Object editorObj) {
        Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
        if (!(editorObj instanceof Map<?, ?> map)) {
            return out;
        }
        for (var e : map.entrySet()) {
            String group = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            Map<String, Boolean> nodes = new LinkedHashMap<>();
            if (e.getValue() instanceof List<?> list) {
                for (Object item : list) {
                    putEditorRow(nodes, item);
                }
            } else if (e.getValue() instanceof Map<?, ?> indexed) {
                for (Object item : indexed.values()) {
                    putEditorRow(nodes, item);
                }
            }
            out.put(group, nodes);
        }
        return out;
    }

    private static void putEditorRow(Map<String, Boolean> nodes, Object item) {
        if (item instanceof Map<?, ?> row) {
            Object nodeObj = row.get("node");
            if (nodeObj == null) {
                return;
            }
            String node = String.valueOf(nodeObj).trim();
            if (node.isEmpty()) {
                return;
            }
            nodes.put(node, DashboardNetworkSnapshots.bool(row.get("value"), true));
        }
    }

    private static void mergeNodeLayer(Map<String, Map<String, Boolean>> dest,
                                       Map<String, Map<String, Boolean>> layer) {
        for (var e : layer.entrySet()) {
            dest.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>()).putAll(e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> parseGroupDetails(Object groupsObj) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(groupsObj instanceof Map<?, ?> map)) {
            return out;
        }
        map.entrySet().stream()
                .sorted((a, b) -> String.valueOf(a.getKey()).compareToIgnoreCase(String.valueOf(b.getKey())))
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", String.valueOf(e.getKey()));
                    if (e.getValue() instanceof Map<?, ?> g) {
                        Map<String, Object> gm = (Map<String, Object>) g;
                        row.put("weight", DashboardNetworkSnapshots.intVal(gm.get("weight"), 0));
                        row.put("prefix", DashboardNetworkSnapshots.str(gm.get("prefix"), ""));
                        row.put("suffix", DashboardNetworkSnapshots.str(gm.get("suffix"), ""));
                        row.put("nameColor", DashboardNetworkSnapshots.str(gm.get("name-color"), ""));
                        row.put("chatColor", DashboardNetworkSnapshots.str(gm.get("chat-color"), ""));
                        row.put("parents", DashboardNetworkSnapshots.stringList(gm.get("parents"), List.of()));
                    }
                    out.add(row);
                });
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<String>> parseTracks(Object tracksObj) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (!(tracksObj instanceof Map<?, ?> map)) {
            return out;
        }
        for (var e : map.entrySet()) {
            out.put(String.valueOf(e.getKey()), DashboardNetworkSnapshots.stringList(e.getValue(), List.of()));
        }
        return out;
    }

    static List<String> groupNames(Object groups) {
        if (!(groups instanceof Map<?, ?> map)) {
            return List.of("default", "vip", "mod", "admin");
        }
        List<String> names = new ArrayList<>();
        for (Object key : map.keySet()) {
            names.add(String.valueOf(key));
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }
}
