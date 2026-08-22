package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Path;
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


}
