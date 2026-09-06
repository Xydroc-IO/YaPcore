package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Config snapshots for core network plugins (protect, world, chat, discord, guard, …).
 * Delegated from {@link DashboardNetworkSnapshots}.
 */
public final class DashboardNetworkPluginSnapshots {

    private DashboardNetworkPluginSnapshots() {
    }

    public static Map<String, Object> protect(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-protect", "YaPProtect");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPProtect", "config.yml");
        Map<String, Object> logging = DashboardNetworkSnapshots.map(yaml.get("logging"));
        out.put("loggingEnabled", DashboardNetworkSnapshots.bool(logging.get("enabled"), true));
        out.put("logBlocks", DashboardNetworkSnapshots.bool(logging.get("block-break"), true));
        out.put("logContainers", DashboardNetworkSnapshots.bool(logging.get("container-inventory"), true));
        out.put("pruneDays", DashboardNetworkSnapshots.intVal(
                DashboardNetworkSnapshots.nested(yaml, "retention", "prune-days"), 30));
        out.put("serverId", DashboardNetworkSnapshots.str(yaml.get("server-id"), "default"));
        return out;
    }

    public static Map<String, Object> world(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-world", "YaPWorld");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPWorld", "config.yml");
        Map<String, Object> worlds = DashboardNetworkSnapshots.map(yaml.get("worlds"));
        Map<String, Object> schem = DashboardNetworkSnapshots.map(yaml.get("schematics"));
        out.put("allowLoad", DashboardNetworkSnapshots.bool(worlds.get("allow-load"), true));
        out.put("allowUnload", DashboardNetworkSnapshots.bool(worlds.get("allow-unload"), true));
        out.put("schematicsEnabled", DashboardNetworkSnapshots.bool(schem.get("enabled"), true));
        out.put("schematicsFolder", DashboardNetworkSnapshots.str(schem.get("folder"), "schematics"));
        out.put("brushMaxRadius", DashboardNetworkSnapshots.intVal(
                DashboardNetworkSnapshots.nested(yaml, "brush", "max-radius"), 16));
        out.put("serverId", DashboardNetworkSnapshots.str(yaml.get("server-id"), "default"));
        Path schemDir = root.resolve("plugins").resolve("YaPWorld")
                .resolve(DashboardNetworkSnapshots.str(schem.get("folder"), "schematics"));
        out.put("schematicCount", DashboardNetworkSnapshots.countFiles(schemDir, ".yschem", ".schem"));
        return out;
    }

    public static Map<String, Object> chat(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-chat", "YaPChat");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPChat", "config.yml");
        out.put("defaultChannel", DashboardNetworkSnapshots.str(yaml.get("default-channel"), "global"));
        out.put("slowModeSeconds", DashboardNetworkSnapshots.intVal(yaml.get("slow-mode-seconds"), 0));
        out.put("unsignedSystemChat", DashboardNetworkSnapshots.bool(yaml.get("unsigned-system-chat"), true));
        out.put("networkEnabled", DashboardNetworkSnapshots.bool(
                DashboardNetworkSnapshots.nestedBool(yaml, "network", "enabled"), true));
        Map<String, Object> filter = DashboardNetworkSnapshots.map(yaml.get("filter"));
        out.put("filterEnabled", DashboardNetworkSnapshots.bool(filter.get("enabled"), true));
        out.put("channels", DashboardNetworkSnapshots.channelNames(yaml.get("channels")));
        out.put("channelFormats", DashboardNetworkSnapshots.parseChannelFormats(yaml.get("channels")));
        out.put("serverId", DashboardNetworkSnapshots.str(yaml.get("server-id"), "default"));
        return out;
    }

    public static Map<String, Object> moderation(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-moderation", "YaPModeration");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPModeration", "config.yml");
        out.put("serverId", DashboardNetworkSnapshots.str(yaml.get("server-id"), "default"));
        out.put("useSharedYapdb", DashboardNetworkSnapshots.bool(yaml.get("use-shared-yapdb"), true));
        out.put("kickMessage", DashboardNetworkSnapshots.str(yaml.get("kick-message"), ""));
        out.put("banMessage", DashboardNetworkSnapshots.str(yaml.get("ban-message"), ""));
        return out;
    }

    public static Map<String, Object> playerdata(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-playerdata", "YaPPlayerData");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPPlayerData", "config.yml");
        out.put("serverId", DashboardNetworkSnapshots.str(yaml.get("server-id"), "default"));
        out.put("economyEnabled", DashboardNetworkSnapshots.bool(
                DashboardNetworkSnapshots.nestedBool(yaml, "economy", "enabled"), true));
        out.put("features", DashboardNetworkSnapshots.featureBools(yaml.get("features"), List.of(
                "homes", "warps", "kits", "mail", "shops", "jobs", "auctions", "claims", "traders")));
        out.put("authEnabled", DashboardNetworkSnapshots.bool(
                DashboardNetworkSnapshots.nestedBool(yaml, "auth", "enabled"), false));
        out.put("syncInventory", DashboardNetworkSnapshots.bool(
                DashboardNetworkSnapshots.nestedBool(yaml, "sync", "inventory"), true));
        out.put("claimsEnabled", DashboardNetworkSnapshots.bool(
                DashboardNetworkSnapshots.nestedBool(yaml, "claims", "enabled"), true));
        out.put("maxHomes", DashboardNetworkSnapshots.intVal(
                DashboardNetworkSnapshots.map(yaml.get("homes")).get("max"), 3));
        return out;
    }

    public static Map<String, Object> discord(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-discord", "YaPDiscord");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPDiscord", "config.yml");
        Map<String, Object> hooks = DashboardNetworkSnapshots.map(yaml.get("webhooks"));
        Map<String, Object> events = DashboardNetworkSnapshots.map(yaml.get("events"));
        out.put("moderationConfigured", !DashboardNetworkSnapshots.str(hooks.get("moderation"), "").isBlank());
        out.put("chatConfigured", !DashboardNetworkSnapshots.str(hooks.get("chat"), "").isBlank());
        out.put("eventsConfigured", !DashboardNetworkSnapshots.str(hooks.get("events"), "").isBlank()
                || !DashboardNetworkSnapshots.str(hooks.get("chat"), "").isBlank());
        out.put("mcToDiscord", DashboardNetworkSnapshots.bool(
                DashboardNetworkSnapshots.nestedBool(yaml, "relay", "mc-to-discord"), false));
        out.put("discordToMc", DashboardNetworkSnapshots.bool(
                DashboardNetworkSnapshots.nestedBool(yaml, "relay", "discord-to-mc"), false));
        out.put("eventJoin", DashboardNetworkSnapshots.bool(events.get("join"), false));
        out.put("eventLeave", DashboardNetworkSnapshots.bool(events.get("leave"), false));
        out.put("eventDeath", DashboardNetworkSnapshots.bool(events.get("death"), false));
        out.put("eventAdvancement", DashboardNetworkSnapshots.bool(events.get("advancement"), false));
        out.put("inboundEnabled", DashboardNetworkSnapshots.bool(
                DashboardNetworkSnapshots.nestedBool(yaml, "inbound", "enabled"), false));
        out.put("inboundBind", DashboardNetworkSnapshots.str(
                DashboardNetworkSnapshots.nested(yaml, "inbound", "bind"), "127.0.0.1"));
        out.put("inboundPort", DashboardNetworkSnapshots.intVal(
                DashboardNetworkSnapshots.nested(yaml, "inbound", "port"), 8765));
        out.put("inboundSecretConfigured",
                !DashboardNetworkSnapshots.str(DashboardNetworkSnapshots.nested(yaml, "inbound", "secret"), "")
                        .isBlank()
                        && !"change-me".equals(DashboardNetworkSnapshots.str(
                        DashboardNetworkSnapshots.nested(yaml, "inbound", "secret"), "")));
        Map<String, Object> bot = DashboardNetworkSnapshots.map(yaml.get("bot"));
        out.put("botEnabled", DashboardNetworkSnapshots.bool(bot.get("enabled"), false));
        out.put("botTokenConfigured", !DashboardNetworkSnapshots.str(bot.get("token"), "").isBlank());
        out.put("botGuildId", DashboardNetworkSnapshots.str(bot.get("guild-id"), ""));
        out.put("botChatChannelId", DashboardNetworkSnapshots.str(bot.get("chat-channel-id"), ""));
        return out;
    }

    /** Third-party Tebex Folia store plugin (`plugins/tebex.jar` → `plugins/Tebex/`). */
    public static Map<String, Object> tebex(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "tebex", "Tebex");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "Tebex", "config.yml");
        Map<String, Object> buy = DashboardNetworkSnapshots.map(yaml.get("buy-command"));
        Map<String, Object> serverCfg = DashboardNetworkSnapshots.map(yaml.get("server"));
        String secret = DashboardNetworkSnapshots.str(serverCfg.get("secret-key"), "");
        boolean secretSet = !secret.isBlank();
        out.put("secretConfigured", secretSet);
        out.put("secretMasked", secretSet ? DashboardNetworkSnapshots.maskSecret(secret) : "");
        out.put("buyCommandEnabled", DashboardNetworkSnapshots.bool(buy.get("enabled"), true));
        out.put("buyCommandName", DashboardNetworkSnapshots.str(buy.get("name"), "buy"));
        out.put("proxyMode", DashboardNetworkSnapshots.bool(serverCfg.get("proxy"), false));
        out.put("verbose", DashboardNetworkSnapshots.bool(yaml.get("verbose"), false));
        out.put("checkForUpdates", DashboardNetworkSnapshots.bool(yaml.get("check-for-updates"), true));
        out.put("creatorUrl", "https://creator.tebex.io/");
        out.put("docsUrl", "https://docs.tebex.io/creators/tebex-control-panel/game-servers/minecraft-java-edition");
        out.put("yapDocs", "docs/ops/TEBEX.md");
        out.put("fetchHint", "./scripts/fetch-tebex.sh");
        out.put("packageRecipes", List.of(
                Map.of(
                        "name", "VIP rank",
                        "commands", "yapperm user {username} parent set vip\nkit grant {username} vip"),
                Map.of(
                        "name", "Adventurer kit unlock",
                        "commands", "yapperm user {username} permission set yapdata.kit.adventurer true\nkit grant {username} adventurer"),
                Map.of(
                        "name", "VIP kit unlock only",
                        "commands", "yapperm user {username} permission set yapdata.kit.vip true")));
        if (!DashboardNetworkSnapshots.bool(out.get("installed"), false)) {
            out.put("setupHint", "Run ./scripts/fetch-tebex.sh (or gradle fetchTebex), restart YaP-Folia, then paste your game-server secret key.");
        } else if (!secretSet) {
            out.put("setupHint", "Paste the game-server secret from creator.tebex.io → Game Servers, then Save secret.");
        } else {
            out.put("setupHint", "Secret set. Create packages on Tebex with the console commands below ({username} placeholder).");
        }
        return out;
    }

    public static Map<String, Object> tab(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-tab", "YaPTab");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPTab", "config.yml");
        out.put("header", DashboardNetworkSnapshots.lines(yaml.get("header")));
        out.put("footer", DashboardNetworkSnapshots.lines(yaml.get("footer")));
        Map<String, Object> sidebar = DashboardNetworkSnapshots.map(yaml.get("sidebar"));
        out.put("sidebarLines", DashboardNetworkSnapshots.lines(sidebar.get("lines")));
        out.put("sidebarEnabled", DashboardNetworkSnapshots.bool(sidebar.get("enabled"), true));
        out.put("nametagTeams", DashboardNetworkSnapshots.bool(yaml.get("nametag-teams"), true));
        out.put("refreshSeconds", DashboardNetworkSnapshots.intVal(yaml.get("refresh-seconds"), 3));
        out.put("networkSyncEnabled", DashboardNetworkSnapshots.bool(
                DashboardNetworkSnapshots.nestedBool(yaml, "network-sync", "enabled"), true));
        Map<String, Object> bossbar = DashboardNetworkSnapshots.map(yaml.get("bossbar"));
        out.put("bossBarEnabled", DashboardNetworkSnapshots.bool(bossbar.get("enabled"), false));
        out.put("bossBarWelcomeOnJoin", DashboardNetworkSnapshots.bool(bossbar.get("welcome-on-join"), true));
        out.put("bossBarTitle", DashboardNetworkSnapshots.str(bossbar.get("title"), "&6&lWelcome to YaP"));
        out.put("bossBarSubtitle", DashboardNetworkSnapshots.str(bossbar.get("subtitle"), "&7Enjoy your stay"));
        out.put("bossBarColor", DashboardNetworkSnapshots.str(bossbar.get("color"), "YELLOW"));
        out.put("bossBarDurationSeconds", DashboardNetworkSnapshots.intVal(bossbar.get("duration-seconds"), 8));
        return out;
    }

    public static Map<String, Object> guard(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-guard", "YaPGuard");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPGuard", "config.yml");
        Map<String, Object> checks = DashboardNetworkSnapshots.map(yaml.get("checks"));
        out.put("flyEnabled", DashboardNetworkSnapshots.checkEnabled(checks, "fly"));
        out.put("speedEnabled", DashboardNetworkSnapshots.checkEnabled(checks, "speed"));
        out.put("reachEnabled", DashboardNetworkSnapshots.checkEnabled(checks, "reach"));
        out.put("scaffoldEnabled", DashboardNetworkSnapshots.checkEnabled(checks, "scaffold"));
        out.put("maxViolationsBeforeKick", DashboardNetworkSnapshots.intVal(
                yaml.get("max-violations-before-kick"), 8));
        out.put("alertsEnabled", DashboardNetworkSnapshots.bool(yaml.get("alerts-enabled"), true));
        out.put("violationDecaySeconds", DashboardNetworkSnapshots.intVal(
                yaml.get("violation-decay-seconds"), 45));
        Path pluginsDir = root.resolve("plugins");
        boolean grimEnabled = DashboardNetworkSnapshots.jarPresent(pluginsDir, "grim");
        boolean grimDownloaded = grimEnabled || Files.isRegularFile(pluginsDir.resolve("grim.jar.disabled"));
        out.put("grimInstalled", grimEnabled);
        out.put("grimDownloaded", grimDownloaded);
        if (grimEnabled) {
            out.put("acHint", "Grim AC is enabled — YaPGuard movement checks should be off to avoid double punishment. See docs/ops/GRIM.md");
        } else if (grimDownloaded) {
            out.put("acHint", "Grim AC downloaded but disabled. Run ./scripts/grim-ac.sh enable and restart YaP-Folia for top-tier AC.");
        }
        return out;
    }

    public static Map<String, Object> lagguard(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-lagguard", "YaPLagGuard");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPLagGuard", "config.yml");
        out.put("enabled", DashboardNetworkSnapshots.bool(yaml.get("enabled"), true));
        out.put("maxEntitiesPerChunk", DashboardNetworkSnapshots.intVal(yaml.get("max-entities-per-chunk"), 72));
        out.put("maxPrimedTntPerChunk", DashboardNetworkSnapshots.intVal(yaml.get("max-primed-tnt-per-chunk"), 8));
        out.put("maxHopperTransfersPerWindow", DashboardNetworkSnapshots.intVal(
                yaml.get("max-hopper-transfers-per-window"), 48));
        out.put("hopperWindowTicks", DashboardNetworkSnapshots.intVal(yaml.get("hopper-window-ticks"), 20));
        out.put("maxRedstoneEventsPerWindow", DashboardNetworkSnapshots.intVal(
                yaml.get("max-redstone-events-per-window"), 96));
        out.put("redstoneWindowTicks", DashboardNetworkSnapshots.intVal(yaml.get("redstone-window-ticks"), 20));
        out.put("logTrips", DashboardNetworkSnapshots.bool(yaml.get("log-trips"), true));
        out.put("countersEphemeral", true);
        out.put("countersNote", "Trip counters live in memory and stats.json; they reset on plugin reload/restart.");
        out.put("hotChunks", List.of());
        Path stats = root.resolve("plugins").resolve("YaPLagGuard").resolve("stats.json");
        if (Files.isRegularFile(stats)) {
            try {
                String raw = Files.readString(stats);
                out.put("statsJson", raw.trim());
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = new com.google.gson.Gson().fromJson(raw, Map.class);
                    Object hot = parsed == null ? null : parsed.get("hotChunks");
                    if (hot instanceof List<?> list) {
                        out.put("hotChunks", list);
                    }
                } catch (Exception ignored) {
                }
            } catch (IOException ignored) {
            }
        }
        return out;
    }

    public static Map<String, Object> regions(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-regions", "YaPRegions");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPRegions", "config.yml");
        out.put("serverId", DashboardNetworkSnapshots.str(yaml.get("server-id"), "default"));
        out.put("flags", List.of(
                "pvp", "mob-damage", "build", "interact", "entry", "chest-access", "fire-spread", "mob-spawning",
                "item-drop", "item-pickup", "tnt", "creeper-explosion"));
        return out;
    }
}
