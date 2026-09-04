package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** YAML write helpers for YaPPerms dashboard configs. */
public final class DashboardPermsSnapshotWriters {

    private DashboardPermsSnapshotWriters() {
    }

    public static void savePermsDefaultGroup(Path root, String group) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPPerms").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        yaml.put("default-group", group == null ? "default" : group.trim().toLowerCase());
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    @SuppressWarnings("unchecked")
    public static void savePermsGroup(Path root, String name, Integer weight, String prefix, String suffix,
                                      String nameColor, String chatColor, List<String> parents) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("group name required");
        }
        String key = name.trim().toLowerCase();
        Path file = root.resolve("plugins").resolve("YaPPerms").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> groups = DashboardNetworkSnapshots.mapOrCreate(yaml, "groups");
        Map<String, Object> group = new LinkedHashMap<>();
        if (groups.get(key) instanceof Map<?, ?> existing) {
            group.putAll((Map<String, Object>) existing);
        } else {
            group.put("weight", 0);
            group.put("prefix", "");
            group.put("suffix", "");
            group.put("name-color", "&f");
            group.put("chat-color", "&f");
            group.put("parents", new ArrayList<String>());
        }
        if (weight != null) {
            group.put("weight", weight);
        }
        if (prefix != null) {
            group.put("prefix", prefix);
        }
        if (suffix != null) {
            group.put("suffix", suffix);
        }
        if (nameColor != null) {
            group.put("name-color", nameColor);
        }
        if (chatColor != null) {
            group.put("chat-color", chatColor);
        }
        if (parents != null) {
            group.put("parents", new ArrayList<>(parents));
        }
        groups.put(key, group);
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    @SuppressWarnings("unchecked")
    public static void deletePermsGroup(Path root, String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("group name required");
        }
        String key = name.trim().toLowerCase();
        Path file = root.resolve("plugins").resolve("YaPPerms").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Object groupsObj = yaml.get("groups");
        if (groupsObj instanceof Map<?, ?> groups) {
            ((Map<String, Object>) groups).remove(key);
        }
        Object tracksObj = yaml.get("tracks");
        if (tracksObj instanceof Map<?, ?> tracks) {
            Map<String, Object> trackMap = (Map<String, Object>) tracks;
            for (Map.Entry<String, Object> entry : trackMap.entrySet()) {
                if (entry.getValue() instanceof List<?> list) {
                    List<Object> kept = new ArrayList<>();
                    for (Object item : list) {
                        if (!key.equalsIgnoreCase(String.valueOf(item))) {
                            kept.add(item);
                        }
                    }
                    trackMap.put(entry.getKey(), kept);
                }
            }
        }
        Object grantsObj = yaml.get("starter-grants");
        if (grantsObj instanceof Map<?, ?> grants) {
            ((Map<String, Object>) grants).remove(key);
        }
        Object editorObj = yaml.get("editor-nodes");
        if (editorObj instanceof Map<?, ?> editor) {
            ((Map<String, Object>) editor).remove(key);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
        removeSnapshotGroup(root, key);
    }

    /**
     * Persist rank editor toggles: allow/deny on the selected group, plus a pending
     * apply file the live {@code yapperm editor-apply} command consumes.
     */
    public static Map<String, Integer> savePermsGroupNodes(Path root, String groupName,
                                                           List<String> allow, List<String> deny,
                                                           List<String> unset) throws IOException {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("group name required");
        }
        String group = groupName.trim().toLowerCase();
        List<String> allowN = normalizeNodes(allow);
        List<String> denyN = normalizeNodes(deny);
        List<String> unsetN = normalizeNodes(unset);
        denyN.removeAll(allowN);
        unsetN.removeAll(allowN);
        unsetN.removeAll(denyN);

        Path file = root.resolve("plugins").resolve("YaPPerms").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> grants = DashboardNetworkSnapshots.mapOrCreate(yaml, "starter-grants");
        grants.put(group, new ArrayList<>(allowN));

        Map<String, Object> editor = DashboardNetworkSnapshots.mapOrCreate(yaml, "editor-nodes");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String node : allowN) {
            rows.add(editorRow(node, true));
        }
        for (String node : denyN) {
            rows.add(editorRow(node, false));
        }
        editor.put(group, rows);
        DashboardNetworkSnapshots.dumpYaml(file, yaml);

        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("group", group);
        pending.put("allow", new ArrayList<>(allowN));
        pending.put("deny", new ArrayList<>(denyN));
        pending.put("unset", new ArrayList<>(unsetN));
        DashboardNetworkSnapshots.dumpYaml(
                root.resolve("plugins").resolve("YaPPerms").resolve("editor-apply.yml"), pending);

        writeSnapshotGroup(root, group, allowN, denyN);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("allow", allowN.size());
        counts.put("deny", denyN.size());
        counts.put("unset", unsetN.size());
        return counts;
    }

    private static Map<String, Object> editorRow(String node, boolean value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("node", node);
        row.put("value", value);
        return row;
    }

    private static List<String> normalizeNodes(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String node = item.trim();
            if (node.isEmpty() || out.contains(node)) {
                continue;
            }
            out.add(node);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void writeSnapshotGroup(Path root, String group, List<String> allow, List<String> deny)
            throws IOException {
        Path snap = root.resolve("plugins").resolve("YaPPerms").resolve("editor-snapshot.yml");
        Map<String, Object> yaml = Files.isRegularFile(snap)
                ? DashboardNetworkSnapshots.loadYaml(snap) : new LinkedHashMap<>();
        Map<String, Object> groups = DashboardNetworkSnapshots.mapOrCreate(yaml, "groups");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String node : allow) {
            rows.add(editorRow(node, true));
        }
        for (String node : deny) {
            rows.add(editorRow(node, false));
        }
        groups.put(group, rows);
        yaml.put("exported-at", java.time.Instant.now().toString());
        yaml.put("source", "dashboard");
        DashboardNetworkSnapshots.dumpYaml(snap, yaml);
    }

    @SuppressWarnings("unchecked")
    private static void removeSnapshotGroup(Path root, String group) throws IOException {
        Path snap = root.resolve("plugins").resolve("YaPPerms").resolve("editor-snapshot.yml");
        if (!Files.isRegularFile(snap)) {
            return;
        }
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(snap);
        Object groupsObj = yaml.get("groups");
        if (groupsObj instanceof Map<?, ?> groups) {
            ((Map<String, Object>) groups).remove(group);
            DashboardNetworkSnapshots.dumpYaml(snap, yaml);
        }
    }

    @SuppressWarnings("unchecked")
    public static void appendGroupToTrack(Path root, String track, String groupName) throws IOException {
        if (track == null || track.isBlank() || groupName == null || groupName.isBlank()) {
            return;
        }
        String group = groupName.trim().toLowerCase();
        String trackKey = track.trim().toLowerCase();
        Path file = root.resolve("plugins").resolve("YaPPerms").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> tracks = DashboardNetworkSnapshots.mapOrCreate(yaml, "tracks");
        List<String> order = new ArrayList<>();
        Object existing = tracks.get(trackKey);
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                order.add(String.valueOf(item).toLowerCase());
            }
        }
        if (!order.contains(group)) {
            order.add(group);
        }
        tracks.put(trackKey, order);
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }
}
