package com.yapcore.chat;

import com.yapcore.messages.YapMessageBundle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ChatConfig {

    public record ChannelDef(String name, String format, int radius, String permission) {
        public ChannelDef(String name, String format, int radius) {
            this(name, format, radius, "");
        }

        public boolean requiresPermission() {
            return permission != null && !permission.isBlank();
        }
    }

    private final JavaPlugin plugin;
    private boolean unsignedSystemChat = true;
    private String defaultChannel = "global";
    private String localPrefix = "!";
    private int slowModeSeconds;
    private boolean filterEnabled;
    private boolean filterBlockOnMatch;
    private String filterMode = "replace";
    private Set<String> filterWords = Set.of();
    private String filterReplacement = "***";
    private Map<String, ChannelDef> channels = Map.of();
    private YapMessageBundle messages = YapMessageBundle.fromSection(null);
    private boolean networkEnabled = true;
    private String serverId = "lobby";
    private Set<String> networkRelayChannels = Set.of("global", "staff", "admin");

    public ChatConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        unsignedSystemChat = c.getBoolean("unsigned-system-chat", true);
        defaultChannel = c.getString("default-channel", "global").toLowerCase(Locale.ROOT);
        localPrefix = c.getString("local-prefix", "!");
        slowModeSeconds = Math.max(0, c.getInt("slow-mode-seconds", 0));
        filterEnabled = c.getBoolean("filter.enabled", true);
        filterBlockOnMatch = c.getBoolean("filter.block-on-match", false);
        filterMode = c.getString("filter.mode", "replace");
        filterWords = new HashSet<>();
        for (String word : c.getStringList("filter.words")) {
            filterWords.add(word.toLowerCase(Locale.ROOT));
        }
        filterReplacement = c.getString("filter.replacement", "***");
        channels = loadChannels(c.getConfigurationSection("channels"));
        messages = YapMessageBundle.fromSection(c.getConfigurationSection("messages"));
        networkEnabled = c.getBoolean("network.enabled", true);
        serverId = c.getString("server-id", serverId);
        networkRelayChannels = new HashSet<>();
        for (String ch : c.getStringList("network.relay-channels")) {
            networkRelayChannels.add(ch.toLowerCase(Locale.ROOT));
        }
        if (networkRelayChannels.isEmpty()) {
            networkRelayChannels = Set.of("global", "staff", "admin");
        }
    }

    private static Map<String, ChannelDef> loadChannels(ConfigurationSection section) {
        if (section == null) {
            return Map.of("global", new ChannelDef("global",
                    "{prefix}{namecolor}{player}{suffix}&7: {chatcolor}{message}", -1, ""));
        }
        Map<String, ChannelDef> out = new HashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection ch = section.getConfigurationSection(key);
            if (ch == null) {
                continue;
            }
            String format = ch.getString("format", "{prefix}{namecolor}{player}{suffix}&7: {chatcolor}{message}");
            int radius = ch.getInt("radius", -1);
            String permission = ch.getString("permission", "");
            // Back-compat: staff channel defaults to yapchat.staff when omitted
            if ((permission == null || permission.isBlank()) && "staff".equalsIgnoreCase(key)) {
                permission = "yapchat.staff";
            }
            if ((permission == null || permission.isBlank()) && "admin".equalsIgnoreCase(key)) {
                permission = "yapchat.admin";
            }
            out.put(key.toLowerCase(Locale.ROOT),
                    new ChannelDef(key.toLowerCase(Locale.ROOT), format, radius,
                            permission == null ? "" : permission));
        }
        return Map.copyOf(out);
    }

    public boolean canUseChannel(org.bukkit.permissions.Permissible player, String channelName) {
        ChannelDef def = channel(channelName);
        if (!def.requiresPermission()) {
            return true;
        }
        return player.hasPermission(def.permission());
    }

    public boolean unsignedSystemChat() {
        return unsignedSystemChat;
    }

    public String defaultChannel() {
        return defaultChannel;
    }

    public String localPrefix() {
        return localPrefix;
    }

    public int slowModeSeconds() {
        return slowModeSeconds;
    }

    public boolean filterBlockOnMatch() {
        return filterBlockOnMatch || "block".equalsIgnoreCase(filterMode);
    }

    public boolean filterEnabled() {
        return filterEnabled;
    }

    /** Unit-test harness: set filter fields without Bukkit YAML. */
    void applyFilterForTest(boolean enabled, boolean blockOnMatch, Set<String> words, String replacement) {
        this.filterEnabled = enabled;
        this.filterBlockOnMatch = blockOnMatch;
        this.filterMode = blockOnMatch ? "block" : "replace";
        this.filterWords = words == null ? Set.of() : Set.copyOf(words);
        this.filterReplacement = replacement == null ? "***" : replacement;
    }

    /** Unit-test harness: channel map without Bukkit YAML. */
    void applyChannelsForTest(String defaultChannelName, Map<String, ChannelDef> channelMap) {
        this.defaultChannel = defaultChannelName == null ? "global" : defaultChannelName.toLowerCase(Locale.ROOT);
        this.channels = channelMap == null ? Map.of() : Map.copyOf(channelMap);
    }

    public Set<String> filterWords() {
        return filterWords;
    }

    public String filterReplacement() {
        return filterReplacement;
    }

    public Map<String, ChannelDef> channels() {
        return channels;
    }

    public ChannelDef channel(String name) {
        return channels.getOrDefault(name.toLowerCase(Locale.ROOT),
                channels.getOrDefault(defaultChannel,
                        new ChannelDef("global",
                                "{prefix}{namecolor}{player}&7: {chatcolor}{message}", -1, "")));
    }

    public YapMessageBundle messages() {
        return messages;
    }

    public String mutedMessage() {
        return messages.raw("muted", "&cYou are muted. &7({reason})");
    }

    public String pmSent() {
        return messages.raw("pm-sent", "&7[You → {target}] {message}");
    }

    public String pmReceived() {
        return messages.raw("pm-received", "&7[{sender} → You] {message}");
    }

    public String staffFormat() {
        return messages.raw("staff-format", "&c[Staff] {player}: {message}");
    }

    public String adminFormat() {
        return messages.raw("admin-format", "&4[Admin] {player}: {message}");
    }

    public String socialSpyFormat() {
        return messages.raw("socialspy", "&8[Spy] {sender} → {target}: {message}");
    }

    public String slowModeMessage() {
        return messages.raw("slow-mode", "&cSlow mode — wait {seconds}s.");
    }

    public String filteredMessage() {
        return messages.raw("filtered", "&cMessage blocked by filter.");
    }

    public boolean networkEnabled() {
        return networkEnabled;
    }

    public String serverId() {
        return serverId;
    }

    public Set<String> networkRelayChannels() {
        return networkRelayChannels;
    }
}
