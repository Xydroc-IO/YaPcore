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
