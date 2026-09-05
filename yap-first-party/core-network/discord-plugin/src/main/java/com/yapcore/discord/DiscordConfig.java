package com.yapcore.discord;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DiscordConfig {

    private final JavaPlugin plugin;
    private String moderationWebhook = "";
    private String chatWebhook = "";
    private String eventsWebhook = "";
    private boolean mcToDiscord;
    private boolean discordToMc;
    private boolean eventJoin;
    private boolean eventLeave;
    private boolean eventDeath;
    private boolean eventAdvancement;
    private boolean inboundEnabled;
    private String inboundBind = "127.0.0.1";
    private int inboundPort = 8765;
    private String inboundSecret = "change-me";
    private String inboundPath = "/discord/inbound";
    private int inboundMaxBodyBytes = 8192;
    private boolean botEnabled;
    private String botToken = "";
    private String botGuildId = "";
    private String botChatChannelId = "";
    private boolean botSlashCommands = true;
    private List<String> slashRolesAdmin = List.of();
    private boolean filterBlockEveryone = true;
    private int filterMaxLength = 500;
    private boolean linkEnabled = true;
    private boolean linkUseSharedYapDb = true;
    private int linkCodeTtlSeconds = 600;
    private boolean roleSyncEnabled = true;
    private Map<String, String> roleSyncMap = Map.of();
    private boolean nicknameSyncEnabled;
    private String nicknameSyncDirection = "mc-to-discord";
    private int nicknameSyncIntervalMinutes;
    private boolean textCommandsEnabled = true;
    private boolean textCommandsPlayerlist = true;
    private String textCommandsConsolePrefix = "!c";
    private List<String> textCommandsConsoleWhitelist = List.of("tps", "list");
    private List<String> textCommandsRequireRoles = List.of();
    private boolean textCommandsListenAllGuild;
    private boolean channelUpdaterEnabled;
    private String channelUpdaterChannelId = "";
    private int channelUpdaterIntervalSeconds = 60;
    private String channelUpdaterTopicTemplate = "Online: {online} | MSPT: {mspt}";
    private boolean consoleChannelEnabled;
    private String consoleChannelId = "";
    private boolean consoleChannelInbound = true;
    private boolean consoleChannelOutbound = true;
    private List<String> consoleChannelInboundRoles = List.of();
    private List<String> consoleChannelCommandWhitelist = List.of();
    private List<String> consoleChannelOutboundFilter = List.of("INFO", "WARN", "SEVERE");
    private int consoleChannelRateLimitPer10s = 20;

    public DiscordConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        moderationWebhook = c.getString("webhooks.moderation", "");
        chatWebhook = c.getString("webhooks.chat", "");
        eventsWebhook = c.getString("webhooks.events", "");
        mcToDiscord = c.getBoolean("relay.mc-to-discord", false);
        discordToMc = c.getBoolean("relay.discord-to-mc", false);
        eventJoin = c.getBoolean("events.join", false);
        eventLeave = c.getBoolean("events.leave", false);
        eventDeath = c.getBoolean("events.death", false);
        eventAdvancement = c.getBoolean("events.advancement", false);
        inboundEnabled = c.getBoolean("inbound.enabled", false);
        inboundBind = c.getString("inbound.bind", "127.0.0.1");
        if (inboundBind == null || inboundBind.isBlank()) {
            inboundBind = "127.0.0.1";
        }
        inboundPort = Math.max(1024, c.getInt("inbound.port", 8765));
        inboundSecret = c.getString("inbound.secret", "change-me");
        inboundPath = c.getString("inbound.path", "/discord/inbound");
        inboundMaxBodyBytes = Math.max(256, Math.min(65_536, c.getInt("inbound.max-body-bytes", 8192)));
        botEnabled = c.getBoolean("bot.enabled", false);
        botToken = c.getString("bot.token", "");
        botGuildId = c.getString("bot.guild-id", "");
        botChatChannelId = c.getString("bot.chat-channel-id", "");
        botSlashCommands = c.getBoolean("bot.slash-commands", true);
        slashRolesAdmin = readStringList(c, "bot.slash-roles.admin");
        filterBlockEveryone = c.getBoolean("filters.block-everyone", true);
        filterMaxLength = Math.max(1, Math.min(2000, c.getInt("filters.max-length", 500)));
        linkEnabled = c.getBoolean("link.enabled", true);
        linkUseSharedYapDb = c.getBoolean("link.use-shared-yapdb", true);
        linkCodeTtlSeconds = Math.max(60, Math.min(3600, c.getInt("link.code-ttl-seconds", 600)));
        roleSyncEnabled = c.getBoolean("link.role-sync.enabled", true);
        Map<String, String> roles = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("link.role-sync.roles");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String group = section.getString(key);
                if (key != null && !key.isBlank() && group != null && !group.isBlank()) {
                    roles.put(key.trim(), group.trim());
                }
            }
        }
        roleSyncMap = Collections.unmodifiableMap(roles);
        nicknameSyncEnabled = c.getBoolean("link.nickname-sync.enabled", false);
        String dir = c.getString("link.nickname-sync.direction", "mc-to-discord");
        nicknameSyncDirection = dir == null || dir.isBlank() ? "mc-to-discord" : dir.trim().toLowerCase(Locale.ROOT);
        if (!"discord-to-mc".equals(nicknameSyncDirection) && !"mc-to-discord".equals(nicknameSyncDirection)) {
            nicknameSyncDirection = "mc-to-discord";
        }
        nicknameSyncIntervalMinutes = Math.max(0, c.getInt("link.nickname-sync.interval-minutes", 0));
        textCommandsEnabled = c.getBoolean("text-commands.enabled", true);
        textCommandsPlayerlist = c.getBoolean("text-commands.playerlist", true);
        String prefix = c.getString("text-commands.console-prefix", "!c");
        textCommandsConsolePrefix = prefix == null ? "" : prefix.trim();
        textCommandsConsoleWhitelist = readStringList(c, "text-commands.console-whitelist");
        textCommandsRequireRoles = readStringList(c, "text-commands.require-roles");
        textCommandsListenAllGuild = c.getBoolean("text-commands.listen-all-guild", false);
        channelUpdaterEnabled = c.getBoolean("channel-updater.enabled", false);
        String updaterChannel = c.getString("channel-updater.channel-id", "");
        channelUpdaterChannelId = updaterChannel == null ? "" : updaterChannel.trim();
        channelUpdaterIntervalSeconds = Math.max(15, c.getInt("channel-updater.interval-seconds", 60));
        String topic = c.getString("channel-updater.topic-template", "Online: {online} | MSPT: {mspt}");
        channelUpdaterTopicTemplate = topic == null || topic.isBlank()
                ? "Online: {online} | MSPT: {mspt}"
                : topic;
        consoleChannelEnabled = c.getBoolean("console-channel.enabled", false);
        String consoleCh = c.getString("console-channel.channel-id", "");
        consoleChannelId = consoleCh == null ? "" : consoleCh.trim();
        consoleChannelInbound = c.getBoolean("console-channel.inbound", true);
        consoleChannelOutbound = c.getBoolean("console-channel.outbound", true);
        consoleChannelInboundRoles = readStringList(c, "console-channel.inbound-roles");
        consoleChannelCommandWhitelist = readStringList(c, "console-channel.command-whitelist");
        List<String> filter = readStringList(c, "console-channel.outbound-filter");
        consoleChannelOutboundFilter = filter.isEmpty()
                ? List.of("INFO", "WARN", "SEVERE")
                : filter;
        consoleChannelRateLimitPer10s = Math.max(1, Math.min(100, c.getInt("console-channel.rate-limit-per-10s", 20)));
    }

    private static List<String> readStringList(FileConfiguration c, String path) {
        List<String> raw = c.getStringList(path);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return Collections.unmodifiableList(out);
    }

    public String moderationWebhook() {
        return moderationWebhook;
    }

    public String chatWebhook() {
        return chatWebhook;
    }

    public String eventsWebhook() {
        return eventsWebhook == null || eventsWebhook.isBlank() ? chatWebhook : eventsWebhook;
    }

    public boolean mcToDiscord() {
        return mcToDiscord;
    }

    public boolean discordToMc() {
        return discordToMc;
    }

    public boolean eventJoin() {
        return eventJoin;
    }

    public boolean eventLeave() {
        return eventLeave;
    }

    public boolean eventDeath() {
        return eventDeath;
    }

    public boolean eventAdvancement() {
        return eventAdvancement;
    }

    public boolean inboundEnabled() {
        return inboundEnabled;
    }

    public String inboundBind() {
        return inboundBind;
    }

    public int inboundPort() {
        return inboundPort;
    }

    public String inboundSecret() {
        return inboundSecret;
    }

    public String inboundPath() {
        return inboundPath == null || inboundPath.isBlank() ? "/discord/inbound" : inboundPath;
    }

    public int inboundMaxBodyBytes() {
        return inboundMaxBodyBytes;
    }

    /** True when inbound is enabled but secret is still the unsafe default / blank. */
    public boolean inboundSecretUnsafe() {
        return inboundSecret == null || inboundSecret.isBlank() || "change-me".equals(inboundSecret);
    }

    public boolean botEnabled() {
        return botEnabled;
    }

    public String botToken() {
        return botToken == null ? "" : botToken;
    }

    public String botGuildId() {
        return botGuildId == null ? "" : botGuildId;
    }

    public String botChatChannelId() {
        return botChatChannelId == null ? "" : botChatChannelId;
    }

    public boolean botSlashCommands() {
        return botSlashCommands;
    }

    /** Discord role snowflakes allowed for admin slash commands ({@code /broadcast}). Empty = Discord Administrator only. */
    public List<String> slashRolesAdmin() {
        return slashRolesAdmin;
    }

    public boolean filterBlockEveryone() {
        return filterBlockEveryone;
    }

    public int filterMaxLength() {
        return filterMaxLength;
    }

    public boolean linkEnabled() {
        return linkEnabled;
    }

    public boolean linkUseSharedYapDb() {
        return linkUseSharedYapDb;
    }

    public int linkCodeTtlSeconds() {
        return linkCodeTtlSeconds;
    }

    public boolean roleSyncEnabled() {
        return roleSyncEnabled;
    }

    /** Discord role snowflake → YaPPerms group name. */
    public Map<String, String> roleSyncMap() {
        return roleSyncMap;
    }

    public boolean nicknameSyncEnabled() {
        return nicknameSyncEnabled;
    }

    /** {@code mc-to-discord} or {@code discord-to-mc}. */
    public String nicknameSyncDirection() {
        return nicknameSyncDirection;
    }

    /** 0 = only on successful link (no periodic pass). */
    public int nicknameSyncIntervalMinutes() {
        return nicknameSyncIntervalMinutes;
    }

    public boolean textCommandsEnabled() {
        return textCommandsEnabled;
    }

    public boolean textCommandsPlayerlist() {
        return textCommandsPlayerlist;
    }

    /** Empty disables {@code !c} console triggers. */
    public String textCommandsConsolePrefix() {
        return textCommandsConsolePrefix == null ? "" : textCommandsConsolePrefix;
    }

    public List<String> textCommandsConsoleWhitelist() {
        return textCommandsConsoleWhitelist;
    }

    /** Discord role IDs required for {@code !c}; empty disables console triggers. */
    public List<String> textCommandsRequireRoles() {
        return textCommandsRequireRoles;
    }

    /** When true, text triggers listen in all guild channels (still guild-scoped). */
    public boolean textCommandsListenAllGuild() {
        return textCommandsListenAllGuild;
    }

    public boolean channelUpdaterEnabled() {
        return channelUpdaterEnabled;
    }

    public String channelUpdaterChannelId() {
        return channelUpdaterChannelId == null ? "" : channelUpdaterChannelId;
    }

    public int channelUpdaterIntervalSeconds() {
        return channelUpdaterIntervalSeconds;
    }

    public String channelUpdaterTopicTemplate() {
        return channelUpdaterTopicTemplate;
    }

    public boolean consoleChannelEnabled() {
        return consoleChannelEnabled;
    }

    public String consoleChannelId() {
        return consoleChannelId == null ? "" : consoleChannelId;
    }

    public boolean consoleChannelInbound() {
        return consoleChannelInbound;
    }

    public boolean consoleChannelOutbound() {
        return consoleChannelOutbound;
    }

    /** Empty → require Discord Administrator for inbound console. */
    public List<String> consoleChannelInboundRoles() {
        return consoleChannelInboundRoles;
    }

    /** Empty → allow all commands (dangerous). Prefer an explicit whitelist. */
    public List<String> consoleChannelCommandWhitelist() {
        return consoleChannelCommandWhitelist;
    }

    public List<String> consoleChannelOutboundFilter() {
        return consoleChannelOutboundFilter;
    }

    public int consoleChannelRateLimitPer10s() {
        return consoleChannelRateLimitPer10s;
    }

    /** Apply Discord→MC content filters; may return blank if fully stripped. */
    public String filterInboundContent(String content) {
        if (content == null) {
            return "";
        }
        String out = content;
        if (filterBlockEveryone) {
            out = out.replace("@everyone", "@\u200beveryone").replace("@here", "@\u200bhere");
        }
        if (out.length() > filterMaxLength) {
            out = out.substring(0, filterMaxLength);
        }
        return out.trim();
    }
}
