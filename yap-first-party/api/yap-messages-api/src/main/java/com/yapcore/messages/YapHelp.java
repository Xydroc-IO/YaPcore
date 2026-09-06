package com.yapcore.messages;

import org.bukkit.command.CommandSender;

/**
 * Lightweight structured help — same voice across plugins (Factions-style sections).
 */
public final class YapHelp {

    private YapHelp() {
    }

    public static void header(CommandSender sender, String title) {
        YapMessages.send(sender, "&6{title}", "title", title == null ? "Help" : title);
    }

    public static void usage(CommandSender sender, String usageLine) {
        YapMessages.send(sender, "&e{usage}", "usage", usageLine == null ? "" : usageLine);
    }

    public static void line(CommandSender sender, String text) {
        YapMessages.send(sender, "&7{text}", "text", text == null ? "" : text);
    }

    public static void tip(CommandSender sender, String text) {
        YapMessages.send(sender, "&8{text}", "text", text == null ? "" : text);
    }

    /** Header + one usage line (common admin entry). */
    public static void simple(CommandSender sender, String title, String usageLine) {
        header(sender, title);
        usage(sender, usageLine);
    }
}
