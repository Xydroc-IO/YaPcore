package com.yapcore.web;

import java.util.Locale;
import java.util.Map;

/** Plain-language titles and help for dashboard plugin YAML fields. */
public final class PluginConfigHints {

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("enabled", "Turn this plugin on"),
            Map.entry("use-shared-yapdb", "Use the shared YaP database"),
            Map.entry("default-group", "Rank new players get"),
            Map.entry("default-track", "Promotion track name"),
            Map.entry("default-channel", "Default chat channel"),
            Map.entry("unsigned-system-chat", "Hide “chat cannot be verified”"),
            Map.entry("economy.enabled", "Enable money"),
            Map.entry("economy.starting-balance", "Starting money"),
            Map.entry("starting-balance", "Starting money"),
            Map.entry("inventory-profile", "Shared inventory"),
            Map.entry("autosave-seconds", "Auto-save every (seconds)"),
            Map.entry("apply-starter-pack-on-first-boot", "Create starter ranks on first boot"),
            Map.entry("local-prefix", "Shortcut for nearby chat"),
            Map.entry("filter.mode", "How the filter acts"),
            Map.entry("filter.words", "Blocked words"),
            Map.entry("filter.replacement", "Replacement text"),
            Map.entry("filter.block-on-match", "Block the whole message"),
            Map.entry("homes.max", "Homes per player"),
            Map.entry("auth.enabled", "Require /login"),
            Map.entry("auth.force", "Always require /login"),
            Map.entry("auth.min-password-length", "Minimum password length"),
            Map.entry("auth.max-attempts", "Login tries before kick"),
            Map.entry("features.backpack", "Enable extra bag"),
            Map.entry("features.economy", "Enable money"),
            Map.entry("features.kits", "Enable kits"),
            Map.entry("features.homes", "Enable homes"),
            Map.entry("features.warps", "Enable warps"),
            Map.entry("features.auth", "Require /login on this server"),
            Map.entry("backpack.default-pages", "Bag pages for everyone"),
            Map.entry("backpack.max-pages", "Maximum bag pages"),
            Map.entry("jdbc.url", "Database address"),
            Map.entry("jdbc.user", "Database username"),
            Map.entry("jdbc.password", "Database password"),
            Map.entry("server-id", "This server’s id (lobby, survival…)"),
            Map.entry("network.enabled", "Share chat with other servers"),
            Map.entry("filter.enabled", "Filter bad words"),
            Map.entry("slow-mode-seconds", "Seconds between messages"),
            Map.entry("resource-pack-file", "Pack file players download"),
            Map.entry("online-mode", "Official Minecraft accounts only")
    );

    private static final Map<String, String> HINTS = Map.ofEntries(
            Map.entry("enabled", "Off = this plugin does nothing until you turn it on."),
            Map.entry("use-shared-yapdb", "Keep on so ranks, money, and homes stay in one database."),
            Map.entry("default-group", "Usually “default”. Change only if you created another starter rank."),
            Map.entry("unsigned-system-chat", "Keep on for offline / YaP Link / older clients."),
            Map.entry("economy.starting-balance", "Money given the first time someone joins."),
            Map.entry("starting-balance", "Money given the first time someone joins."),
            Map.entry("inventory-profile", "global = same items on every server. server = each world keeps its own."),
            Map.entry("apply-starter-pack-on-first-boot", "Creates default, VIP, staff, admin, and owner the first time you boot."),
            Map.entry("local-prefix", "Type ! before a message to talk only to people nearby."),
            Map.entry("filter.mode", "replace = stars out the word. block = refuse the message."),
            Map.entry("filter.words", "Comma-separated. Matching is not case-sensitive."),
            Map.entry("homes.max", "How many /sethome spots a normal player can have."),
            Map.entry("auth.enabled", "Turn on only for a public offline-mode server."),
            Map.entry("features.backpack", "Lets everyone use /bag. Pages still follow rank grants."),
            Map.entry("backpack.default-pages", "Everyone gets at least this many pages (VIP/staff can get more)."),
            Map.entry("backpack.max-pages", "Hard cap. 9 is the product max."),
            Map.entry("jdbc.password", "Same password you put in the MariaDB .env. Keep this private."),
            Map.entry("jdbc.url", "Example: jdbc:mysql://127.0.0.1:3306/yap_playerdata"),
            Map.entry("filter.enabled", "Replaces or blocks words listed under Filter."),
            Map.entry("slow-mode-seconds", "0 means no wait. Try 3 if chat is getting spammed."),
            Map.entry("network.enabled", "Needs YaP Link chat bridge on a multi-server network."),
            Map.entry("server-id", "Must match the backend name in YaP Link (often lobby).")
    );

    private static final Map<String, String> GROUPS = Map.ofEntries(
            Map.entry("features", "What this plugin does"),
            Map.entry("economy", "Money"),
            Map.entry("backpack", "Extra bag"),
            Map.entry("jdbc", "Database"),
            Map.entry("pool", "Database pool"),
            Map.entry("filter", "Word filter"),
            Map.entry("channels", "Chat channels"),
            Map.entry("network", "Other servers"),
            Map.entry("messages", "Player messages"),
            Map.entry("groups", "Ranks"),
            Map.entry("tracks", "Promotion tracks"),
            Map.entry("starter-grants", "Starter permissions"),
            Map.entry("homes", "Homes"),
            Map.entry("warps", "Warps"),
            Map.entry("kits", "Kits"),
            Map.entry("auth", "Login / register"),
            Map.entry("sync", "What follows players"),
            Map.entry("mail", "Mail"),
            Map.entry("auctions", "Auctions"),
            Map.entry("claims", "Land claims"),
            Map.entry("jobs", "Jobs"),
            Map.entry("spawn", "Spawn"),
            Map.entry("tpa", "Teleport requests"),
            Map.entry("database", "Database")
    );

    private static final Map<String, String> TITLES_PLUGIN = Map.ofEntries(
            Map.entry("yap-perms", "Ranks"),
            Map.entry("yap-playerdata", "Player data"),
            Map.entry("yap-chat", "Chat"),
            Map.entry("yap-essentials", "Daily commands"),
            Map.entry("yap-moderation", "Moderation"),
            Map.entry("yap-tab", "Tab list"),
            Map.entry("yap-discord", "Discord"),
            Map.entry("yap-guard", "Anti-cheat"),
            Map.entry("yap-db", "Database"),
            Map.entry("tebex", "Web store"),
            Map.entry("yap-protect", "Grief protection"),
            Map.entry("yap-world", "Worlds"),
            Map.entry("yap-packs", "Resource packs"),
            Map.entry("yap-commands", "Custom commands"),
            Map.entry("yap-map", "Live map"),
            Map.entry("yap-factions", "Factions"),
            Map.entry("yap-npcs", "NPCs"),
            Map.entry("yap-regions", "Regions"),
            Map.entry("yap-lagguard", "Lag guard"),
            Map.entry("yap-stacker", "Mob stacking"),
            Map.entry("yap-skills", "Skills"),
            Map.entry("yap-admin", "Admin tools"),
            Map.entry("yap-disasters", "Disasters")
    );

    private static final Map<String, String> BLURBS = Map.ofEntries(
            Map.entry("yap-perms", "Ranks, prefixes, and who can run which command."),
            Map.entry("yap-playerdata", "Money, bag, homes, warps, kits, and /login."),
            Map.entry("yap-chat", "Chat channels, filter, and the unverified-chat fix."),
            Map.entry("yap-essentials", "Spawn, TPA, /gm, /item, and other daily commands."),
            Map.entry("yap-moderation", "Ban, mute, warn, kick, and /seen."),
            Map.entry("yap-tab", "The list you see when you press Tab."),
            Map.entry("yap-discord", "Relay chat to a Discord channel."),
            Map.entry("yap-guard", "Light anti-cheat. Optional Grim is stronger."),
            Map.entry("yap-db", "Shared MariaDB connection used by other plugins."),
            Map.entry("tebex", "Web store. Do not paste secrets in chat."),
            Map.entry("yap-protect", "Stops griefing and block breaking in claimed areas."),
            Map.entry("yap-world", "Worlds, generation, and world borders."),
            Map.entry("yap-packs", "Which resource pack players download."),
            Map.entry("yap-commands", "Master switch for YAML /commands (definitions on the Custom commands tab)."),
            Map.entry("yap-map", "Web map of the world."),
            Map.entry("yap-factions", "Claim land with a faction."),
            Map.entry("yap-npcs", "Quest NPCs, dialogue, and hub actions (shop/warp/command)."),
            Map.entry("yap-regions", "Named areas with their own rules."),
            Map.entry("yap-lagguard", "Slows the world down when the server is busy."),
            Map.entry("yap-stacker", "Stacks nearby mobs to save performance."),
            Map.entry("yap-skills", "XP and gathering skills."),
            Map.entry("yap-admin", "Staff tools. Leave defaults if you are new."),
            Map.entry("yap-disasters", "Weather extremes, random events, volcano sites, and flood waves."),
            Map.entry("yap-floodgate", "Lets Bedrock players join Java."),
            Map.entry("yap-pregen", "Pre-generate chunks so new areas load smoothly.")
    );

    private PluginConfigHints() {
    }

    public static void decorate(Map<String, Object> field) {
        String key = String.valueOf(field.getOrDefault("key", ""));
        field.put("title", title(key));
        field.put("hint", hint(key));
        field.put("group", group(key));
        field.put("advanced", advanced(key));
    }

    public static String title(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String lower = key.toLowerCase(Locale.ROOT);
        String hit = TITLES.get(lower);
        if (hit != null) {
            return hit;
        }
        String leaf = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
        if (lower.startsWith("features.")) {
            return "Enable " + humanize(leaf);
        }
        if (lower.startsWith("sync.")) {
            return "Keep " + humanize(leaf) + " in sync";
        }
        if (lower.endsWith(".enabled")) {
            return "Enable " + humanize(groupHead(key));
        }
        return humanize(leaf);
    }

    public static String hint(String key) {
        if (key == null) {
            return "";
        }
        String hit = HINTS.get(key.toLowerCase(Locale.ROOT));
        if (hit != null) {
            return hit;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".password") || lower.endsWith(".secret") || lower.endsWith(".token")) {
            return "Keep this private. Only the server needs it.";
        }
        if (lower.endsWith(".enabled") || lower.equals("enabled")) {
            return "Yes turns this on. No turns it off.";
        }
        return "";
    }

    public static String group(String key) {
        if (key == null || key.isBlank() || !key.contains(".")) {
            return "Basics";
        }
        String head = key.substring(0, key.indexOf('.'));
        String named = GROUPS.get(head.toLowerCase(Locale.ROOT));
        return named != null ? named : humanize(head);
    }

    public static boolean advanced(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc.") || lower.startsWith("pool.") || lower.startsWith("database.")) {
            return true;
        }
        return lower.startsWith("groups.")
                || lower.startsWith("tracks.")
                || lower.startsWith("starter-grants")
                || lower.startsWith("editor-")
                || lower.startsWith("channels.")
                || lower.startsWith("jobs.")
                || lower.startsWith("spawn.")
                || lower.startsWith("claims.tax.")
                || lower.startsWith("claims.default-flags.")
                || lower.contains("timeout")
                || lower.contains("debug")
                || lower.contains("thread")
                || lower.contains("cache")
                || lower.contains("hmac")
                || lower.contains("ttl")
                || key.chars().filter(c -> c == '.').count() >= 3;
    }

    public static String pluginTitle(String pluginId, String fallback) {
        if (pluginId == null) {
            return fallback == null ? "" : fallback;
        }
        return TITLES_PLUGIN.getOrDefault(pluginId.toLowerCase(Locale.ROOT),
                fallback == null ? pluginId : fallback);
    }

    public static String pluginBlurb(String pluginId) {
        if (pluginId == null) {
            return "";
        }
        return BLURBS.getOrDefault(pluginId.toLowerCase(Locale.ROOT), "Settings for this plugin.");
    }

    private static String groupHead(String key) {
        if (key == null || !key.contains(".")) {
            return key == null ? "" : key;
        }
        return key.substring(0, key.indexOf('.'));
    }

    static String humanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String spaced = raw.replace('-', ' ').replace('_', ' ').trim();
        StringBuilder out = new StringBuilder();
        for (String word : spaced.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1));
            }
        }
        return out.toString();
    }
}
