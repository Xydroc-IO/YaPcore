package com.yapcore.lib.cmd;

import com.yapcore.lib.YapLibPlugin;
import com.yapcore.messages.YapHelp;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class YapLibCommands implements CommandExecutor, TabCompleter {

    private final YapLibPlugin plugin;

    public YapLibCommands(YapLibPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yaplib.admin")) {
            YapMessages.noPermission(sender, "yaplib.admin");
            return true;
        }
        if (args.length == 0) {
            return status(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            default -> {
                YapHelp.simple(sender, "YaPLib", "/yaplib status|reload");
                yield true;
            }
        };
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage("§aYaPLib §7— intercept=" + on(plugin.libConfig().intercept())
                + " listeners=" + plugin.packetService().listenerCount()
                + " channels=" + plugin.tracker().size());
        sender.sendMessage("§7PacketService registered. Holograms: yap-holo.jar · aliases: /protocolib");
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadLib();
        YapMessages.reloaded(sender, "YaPLib");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yaplib.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("status", "reload");
        }
        return List.of();
    }

    private static String on(boolean value) {
        return value ? "on" : "off";
    }
}
