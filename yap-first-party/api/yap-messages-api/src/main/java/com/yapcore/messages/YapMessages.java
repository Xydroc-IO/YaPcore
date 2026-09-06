package com.yapcore.messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.Map;

/** Static helpers when a plugin has no {@link YapMessageBundle} yet. */
public final class YapMessages {

    private YapMessages() {
    }

    public static void send(Audience audience, String raw) {
        YapMessageBundle.sendSystem(audience, YapText.component(raw));
    }

    public static void send(Audience audience, String template, Map<String, String> placeholders) {
        YapMessageBundle.sendSystem(audience, YapText.component(template, placeholders));
    }

    public static void send(Audience audience, String template, String... keyValues) {
        send(audience, template, YapText.toMap(keyValues));
    }

    public static void noPermission(CommandSender sender) {
        noPermission(sender, null);
    }

    public static void noPermission(CommandSender sender, String node) {
        if (node == null || node.isBlank()) {
            send(sender, "&cNo permission.");
            return;
        }
        send(sender, "&cNo permission.&7 Need: &f{node}", "node", node);
    }

    public static void playersOnly(CommandSender sender) {
        send(sender, "&cPlayers only.");
    }

    public static void reloaded(CommandSender sender, String pluginName) {
        send(sender, "&a{plugin} reloaded.", "plugin", pluginName == null ? "Plugin" : pluginName);
    }

    public static Component component(String raw) {
        return YapText.component(raw);
    }
}
