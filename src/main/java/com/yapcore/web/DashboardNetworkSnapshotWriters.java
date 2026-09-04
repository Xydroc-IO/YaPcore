package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** YAML write helpers for dashboard network plugin configs. */
public final class DashboardNetworkSnapshotWriters {

    private DashboardNetworkSnapshotWriters() {
    }

    public static void savePlayerdataFeature(Path root, String key, boolean enabled) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPPlayerData").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> features = DashboardNetworkSnapshots.mapOrCreate(yaml, "features");
        features.put(key, enabled);
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveDiscordRelay(Path root, boolean mcToDiscord, Boolean discordToMc) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPDiscord").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> relay = DashboardNetworkSnapshots.mapOrCreate(yaml, "relay");
        relay.put("mc-to-discord", mcToDiscord);
        if (discordToMc != null) {
            relay.put("discord-to-mc", discordToMc);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveDiscordWebhook(Path root, String key, String url) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPDiscord").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> hooks = DashboardNetworkSnapshots.mapOrCreate(yaml, "webhooks");
        hooks.put(key, url == null ? "" : url.trim());
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveDiscordEvents(Path root, Boolean join, Boolean leave, Boolean death,
                                         Boolean advancement) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPDiscord").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> events = DashboardNetworkSnapshots.mapOrCreate(yaml, "events");
        if (join != null) {
            events.put("join", join);
        }
        if (leave != null) {
            events.put("leave", leave);
        }
        if (death != null) {
            events.put("death", death);
        }
        if (advancement != null) {
            events.put("advancement", advancement);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveTabHeader(Path root, List<String> lines) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPTab").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        yaml.put("header", lines);
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveTabFooter(Path root, List<String> lines) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPTab").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        yaml.put("footer", lines);
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveTabSidebar(Path root, List<String> lines, Boolean enabled) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPTab").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> sidebar = DashboardNetworkSnapshots.mapOrCreate(yaml, "sidebar");
        sidebar.put("lines", lines);
        if (enabled != null) {
            sidebar.put("enabled", enabled);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveTabSettings(Path root, Boolean sidebarEnabled, Boolean nametagTeams,
                                       Integer refreshSeconds, Boolean networkSyncEnabled) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPTab").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        if (sidebarEnabled != null) {
            DashboardNetworkSnapshots.mapOrCreate(yaml, "sidebar").put("enabled", sidebarEnabled);
        }
        if (nametagTeams != null) {
            yaml.put("nametag-teams", nametagTeams);
        }
        if (refreshSeconds != null) {
            yaml.put("refresh-seconds", refreshSeconds);
        }
        if (networkSyncEnabled != null) {
            DashboardNetworkSnapshots.mapOrCreate(yaml, "network-sync").put("enabled", networkSyncEnabled);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveTabBossBar(Path root, Boolean enabled, Boolean welcomeOnJoin, String title,
                                      String subtitle, String color, Integer durationSeconds) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPTab").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> bossbar = DashboardNetworkSnapshots.mapOrCreate(yaml, "bossbar");
        if (enabled != null) {
            bossbar.put("enabled", enabled);
        }
        if (welcomeOnJoin != null) {
            bossbar.put("welcome-on-join", welcomeOnJoin);
        }
        if (title != null) {
            bossbar.put("title", title);
        }
        if (subtitle != null) {
            bossbar.put("subtitle", subtitle);
        }
        if (color != null && !color.isBlank()) {
            bossbar.put("color", color.trim());
        }
        if (durationSeconds != null) {
            bossbar.put("duration-seconds", durationSeconds);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
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

    public static void saveChatChannelFormat(Path root, String channel, String format) throws IOException {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel required");
        }
        Path file = root.resolve("plugins").resolve("YaPChat").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> channels = DashboardNetworkSnapshots.mapOrCreate(yaml, "channels");
        Map<String, Object> ch = DashboardNetworkSnapshots.mapOrCreate(channels, channel.trim());
        ch.put("format", format == null ? "" : format);
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveChatSettings(Path root, String defaultChannel, Integer slowModeSeconds,
                                        Boolean filterEnabled, Boolean networkEnabled) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPChat").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        if (defaultChannel != null && !defaultChannel.isBlank()) {
            yaml.put("default-channel", defaultChannel.trim());
        }
        if (slowModeSeconds != null) {
            yaml.put("slow-mode-seconds", Math.max(0, slowModeSeconds));
        }
        if (filterEnabled != null) {
            DashboardNetworkSnapshots.mapOrCreate(yaml, "filter").put("enabled", filterEnabled);
        }
        if (networkEnabled != null) {
            DashboardNetworkSnapshots.mapOrCreate(yaml, "network").put("enabled", networkEnabled);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveGuardSettings(Path root, Boolean fly, Boolean speed, Boolean reach, Boolean scaffold,
                                         Integer maxViolations, Integer decaySeconds, Boolean alerts)
            throws IOException {
        Path file = root.resolve("plugins").resolve("YaPGuard").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> checks = DashboardNetworkSnapshots.mapOrCreate(yaml, "checks");
        if (fly != null) {
            DashboardNetworkSnapshots.mapOrCreate(checks, "fly").put("enabled", fly);
        }
        if (speed != null) {
            DashboardNetworkSnapshots.mapOrCreate(checks, "speed").put("enabled", speed);
        }
        if (reach != null) {
            DashboardNetworkSnapshots.mapOrCreate(checks, "reach").put("enabled", reach);
        }
        if (scaffold != null) {
            DashboardNetworkSnapshots.mapOrCreate(checks, "scaffold").put("enabled", scaffold);
        }
        if (maxViolations != null) {
            yaml.put("max-violations-before-kick", Math.max(1, maxViolations));
        }
        if (decaySeconds != null) {
            yaml.put("violation-decay-seconds", Math.max(5, decaySeconds));
        }
        if (alerts != null) {
            yaml.put("alerts-enabled", alerts);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveProtectSettings(Path root, Boolean loggingEnabled, Boolean logBlocks,
                                           Boolean logContainers, Integer pruneDays) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPProtect").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> logging = DashboardNetworkSnapshots.mapOrCreate(yaml, "logging");
        if (loggingEnabled != null) {
            logging.put("enabled", loggingEnabled);
        }
        if (logBlocks != null) {
            logging.put("block-break", logBlocks);
        }
        if (logContainers != null) {
            logging.put("container-inventory", logContainers);
        }
        if (pruneDays != null) {
            DashboardNetworkSnapshots.mapOrCreate(yaml, "retention").put("prune-days", Math.max(1, pruneDays));
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveMapSettings(Path root, Integer renderIntervalMinutes, List<String> worlds)
            throws IOException {
        saveMapSettings(root, renderIntervalMinutes, worlds, null, null, null, null);
    }

    public static void saveMapSettings(Path root, Integer renderIntervalMinutes, List<String> worlds,
                                       Boolean markersPlayers, Boolean markersNpcs, Boolean markersRegions,
                                       Integer markersPollSeconds) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPMap").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        if (renderIntervalMinutes != null) {
            yaml.put("render-interval-minutes", Math.max(1, renderIntervalMinutes));
        }
        if (worlds != null && !worlds.isEmpty()) {
            yaml.put("worlds", worlds);
        }
        if (markersPlayers != null || markersNpcs != null || markersRegions != null || markersPollSeconds != null) {
            Map<String, Object> markers = DashboardNetworkSnapshots.mapOrCreate(yaml, "markers");
            if (markersPlayers != null) {
                markers.put("players", markersPlayers);
            }
            if (markersNpcs != null) {
                markers.put("npcs", markersNpcs);
            }
            if (markersRegions != null) {
                markers.put("regions", markersRegions);
            }
            if (markersPollSeconds != null) {
                markers.put("poll-seconds", Math.max(2, markersPollSeconds));
            }
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveWorldBrushMax(Path root, int maxRadius) throws IOException {
        Path file = root.resolve("plugins").resolve("YaPWorld").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        DashboardNetworkSnapshots.mapOrCreate(yaml, "brush").put("max-radius", Math.max(1, maxRadius));
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveDiscordInbound(Path root, Boolean enabled, Integer port, String secret)
            throws IOException {
        Path file = root.resolve("plugins").resolve("YaPDiscord").resolve("config.yml");
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Map<String, Object> inbound = DashboardNetworkSnapshots.mapOrCreate(yaml, "inbound");
        if (enabled != null) {
            inbound.put("enabled", enabled);
        }
        if (port != null) {
            inbound.put("port", port);
        }
        if (secret != null) {
            inbound.put("secret", secret.trim());
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveTebexSecret(Path root, String secret) throws IOException {
        Path file = root.resolve("plugins").resolve("Tebex").resolve("config.yml");
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file)
                : new LinkedHashMap<>();
        Map<String, Object> server = DashboardNetworkSnapshots.mapOrCreate(yaml, "server");
        server.put("secret-key", secret == null ? "" : secret.trim());
        if (!yaml.containsKey("buy-command")) {
            Map<String, Object> buy = new LinkedHashMap<>();
            buy.put("enabled", true);
            buy.put("name", "buy");
            yaml.put("buy-command", buy);
        }
        if (!server.containsKey("proxy")) {
            server.put("proxy", false);
        }
        if (!yaml.containsKey("config-version")) {
            yaml.put("config-version", 2);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveTebexSettings(Path root, Boolean buyEnabled, String buyName,
                                        Boolean proxy, Boolean verbose) throws IOException {
        Path file = root.resolve("plugins").resolve("Tebex").resolve("config.yml");
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file)
                : new LinkedHashMap<>();
        Map<String, Object> buy = DashboardNetworkSnapshots.mapOrCreate(yaml, "buy-command");
        if (buyEnabled != null) {
            buy.put("enabled", buyEnabled);
        }
        if (buyName != null && !buyName.isBlank()) {
            buy.put("name", buyName.trim().replaceAll("\\s+", ""));
        }
        Map<String, Object> server = DashboardNetworkSnapshots.mapOrCreate(yaml, "server");
        if (proxy != null) {
            server.put("proxy", proxy);
        }
        if (verbose != null) {
            yaml.put("verbose", verbose);
        }
        if (!yaml.containsKey("config-version")) {
            yaml.put("config-version", 2);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

}
