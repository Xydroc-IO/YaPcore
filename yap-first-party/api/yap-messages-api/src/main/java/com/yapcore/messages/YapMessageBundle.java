package com.yapcore.messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code messages.*} from a plugin config and sends Adventure system chat.
 * Prefer {@link #send(Audience, String, Map)} over raw {@code §} strings.
 */
public final class YapMessageBundle {

    public static final String KEY_PREFIX = "prefix";
    public static final String KEY_NO_PERMISSION = "no-permission";
    public static final String KEY_PLAYERS_ONLY = "players-only";
    public static final String KEY_RELOADED = "reloaded";
    public static final String KEY_FAILED = "failed";

    private static final Map<String, String> STANDARD_DEFAULTS = Map.of(
            KEY_PREFIX, "",
            KEY_NO_PERMISSION, "&cNo permission.&7 Need: &f{node}",
            KEY_PLAYERS_ONLY, "&cPlayers only.",
            KEY_RELOADED, "&a{plugin} reloaded.",
            KEY_FAILED, "&c{reason}");

    private final Map<String, String> messages;
    private final boolean prependPrefix;

    public YapMessageBundle(Map<String, String> messages, boolean prependPrefix) {
        this.messages = Map.copyOf(messages);
        this.prependPrefix = prependPrefix;
    }

    public static YapMessageBundle fromSection(ConfigurationSection section) {
        return fromSection(section, STANDARD_DEFAULTS, true);
    }

    public static YapMessageBundle fromSection(
            ConfigurationSection section, Map<String, String> defaults, boolean prependPrefix) {
        Map<String, String> merged = new LinkedHashMap<>(defaults == null ? STANDARD_DEFAULTS : defaults);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) {
                    merged.put(key, value);
                }
            }
        }
        return new YapMessageBundle(merged, prependPrefix);
    }

    public static Map<String, String> standardDefaults() {
        return STANDARD_DEFAULTS;
    }

    public String raw(String key) {
        return messages.getOrDefault(key, "");
    }

    public String raw(String key, String fallback) {
        String v = messages.get(key);
        return v == null || v.isBlank() ? (fallback == null ? "" : fallback) : v;
    }

    public boolean has(String key) {
        return messages.containsKey(key) && messages.get(key) != null && !messages.get(key).isBlank();
    }

    public Component component(String key, Map<String, String> placeholders) {
        String body = YapText.apply(raw(key), placeholders);
        if (prependPrefix) {
            String prefix = raw(KEY_PREFIX);
            if (prefix != null && !prefix.isBlank()) {
                body = prefix + body;
            }
        }
        return YapText.component(body);
    }

    public Component component(String key, String... keyValues) {
        return component(key, YapText.toMap(keyValues));
    }

    public void send(Audience audience, String key, Map<String, String> placeholders) {
        sendSystem(audience, component(key, placeholders));
    }

    public void send(Audience audience, String key, String... keyValues) {
        send(audience, key, YapText.toMap(keyValues));
    }

    public void sendRaw(Audience audience, String template, Map<String, String> placeholders) {
        String body = YapText.apply(template, placeholders);
        if (prependPrefix) {
            String prefix = raw(KEY_PREFIX);
            if (prefix != null && !prefix.isBlank()) {
                body = prefix + body;
            }
        }
        sendSystem(audience, YapText.component(body));
    }

    public void sendRaw(Audience audience, String template, String... keyValues) {
        sendRaw(audience, template, YapText.toMap(keyValues));
    }

    public void noPermission(CommandSender sender) {
        noPermission(sender, null);
    }

    public void noPermission(CommandSender sender, String node) {
        String template = raw(KEY_NO_PERMISSION, STANDARD_DEFAULTS.get(KEY_NO_PERMISSION));
        if (node == null || node.isBlank()) {
            // Drop trailing "Need: {node}" when no node supplied
            template = template.replaceAll("(?i)\\s*&7\\s*Need:\\s*&f\\{node\\}", "")
                    .replaceAll("(?i)\\s*Need:\\s*\\{node\\}", "")
                    .replace("{node}", "")
                    .replace("%node%", "")
                    .trim();
            if (template.isEmpty()) {
                template = "&cNo permission.";
            }
        }
        sendRaw(sender, template, Map.of("node", node == null ? "" : node));
    }

    public void playersOnly(CommandSender sender) {
        send(sender, KEY_PLAYERS_ONLY, Collections.emptyMap());
    }

    public void reloaded(CommandSender sender, String pluginName) {
        send(sender, KEY_RELOADED, Map.of("plugin", pluginName == null ? "Plugin" : pluginName));
    }

    public void failed(CommandSender sender, String reason) {
        send(sender, KEY_FAILED, Map.of("reason", reason == null ? "Failed." : reason));
    }

    /**
     * YaP-Folia: use {@link Player#sendMessage(Component)} so chat stays unsigned system chat.
     */
    public static void sendSystem(Audience audience, Component message) {
        if (audience == null || message == null) {
            return;
        }
        if (audience instanceof Player player) {
            player.sendMessage(message);
            return;
        }
        audience.sendMessage(message);
    }
}
