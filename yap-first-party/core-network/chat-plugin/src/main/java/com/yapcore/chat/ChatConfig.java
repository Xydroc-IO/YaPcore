package com.yapcore.chat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ChatConfig {

    public record ChannelDef(String name, String format, int radius) {
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
    private String mutedMessage = "&cYou are muted.";
    private String pmSent = "&7[You → {target}] {message}";
    private String pmReceived = "&7[{sender} → You] {message}";
    private String staffFormat = "&c[Staff] {player}: {message}";
    private String socialSpyFormat = "&8[Spy] {sender} → {target}: {message}";
    private String slowModeMessage = "&cSlow mode.";
    private String filteredMessage = "&cMessage blocked.";
    private boolean networkEnabled = true;
    private String serverId = "lobby";
    private Set<String> networkRelayChannels = Set.of("global", "staff");

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
        mutedMessage = c.getString("messages.muted", mutedMessage);
        pmSent = c.getString("messages.pm-sent", pmSent);
        pmReceived = c.getString("messages.pm-received", pmReceived);
        staffFormat = c.getString("messages.staff-format", staffFormat);
        socialSpyFormat = c.getString("messages.socialspy", socialSpyFormat);
        slowModeMessage = c.getString("messages.slow-mode", slowModeMessage);
        filteredMessage = c.getString("messages.filtered", filteredMessage);
        networkEnabled = c.getBoolean("network.enabled", true);
        serverId = c.getString("server-id", serverId);
        networkRelayChannels = new HashSet<>();
        for (String ch : c.getStringList("network.relay-channels")) {
            networkRelayChannels.add(ch.toLowerCase(Locale.ROOT));
        }
        if (networkRelayChannels.isEmpty()) {
            networkRelayChannels = Set.of("global", "staff");
        }
    }

    private static Map<String, ChannelDef> loadChannels(ConfigurationSection section) {
        if (section == null) {
            return Map.of("global", new ChannelDef("global", "{prefix}{player}{suffix}&7: &f{message}", -1));
        }
        Map<String, ChannelDef> out = new HashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection ch = section.getConfigurationSection(key);
            if (ch == null) {
                continue;
            }
            String format = ch.getString("format", "{prefix}{player}{suffix}&7: &f{message}");
            int radius = ch.getInt("radius", -1);
            out.put(key.toLowerCase(Locale.ROOT), new ChannelDef(key.toLowerCase(Locale.ROOT), format, radius));
        }
        return Map.copyOf(out);
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
                channels.getOrDefault(defaultChannel, new ChannelDef("global", "{prefix}{player}&7: &f{message}", -1)));
    }

    public String mutedMessage() {
        return mutedMessage;
    }

    public String pmSent() {
        return pmSent;
    }

    public String pmReceived() {
        return pmReceived;
    }

    public String staffFormat() {
        return staffFormat;
    }

    public String socialSpyFormat() {
        return socialSpyFormat;
    }

    public String slowModeMessage() {
        return slowModeMessage;
    }

    public String filteredMessage() {
        return filteredMessage;
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
