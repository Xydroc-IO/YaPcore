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

    /** Profile sync still applying — not the same as a downed YaPDB pool. */
    public static void profileLoading(CommandSender sender) {
        send(sender, "&cYour profile is still loading…");
    }

    /** Shared / embedded SQL pool unavailable or misconfigured. */
    public static void databaseNotReady(CommandSender sender) {
        send(sender, "&cDatabase not ready — configure YaPDB or wait for the pool to open.");
    }

    /**
     * Map SQL / pool failures to a clean player message; keep other errors short without dumping stacks.
     */
    public static void commandFailed(CommandSender sender, Throwable error) {
        if (looksLikeDbDown(error)) {
            databaseNotReady(sender);
            return;
        }
        String msg = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "unknown error"
                : error.getMessage();
        if (msg.length() > 120) {
            msg = msg.substring(0, 117) + "...";
        }
        send(sender, "&cFailed: &f{reason}", "reason", msg);
    }

    public static boolean looksLikeDbDown(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) {
                continue;
            }
            String lower = m.toLowerCase();
            if (lower.contains("pool is not open")
                    || lower.contains("yapdb")
                    || lower.contains("database unavailable")
                    || lower.contains("communications link failure")
                    || lower.contains("could not connect")) {
                return true;
            }
        }
        return false;
    }

    public static Component component(String raw) {
        return YapText.component(raw);
    }
}
