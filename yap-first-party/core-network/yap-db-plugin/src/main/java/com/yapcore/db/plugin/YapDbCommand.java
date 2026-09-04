package com.yapcore.db.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class YapDbCommand implements CommandExecutor, TabCompleter {

    private final YapDbPlugin plugin;

    YapDbCommand(YapDbPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapdb.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /yapdb <status|reload>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage("YaPDB status:");
                sender.sendMessage("  open: " + plugin.isOpen());
                sender.sendMessage("  engine: " + plugin.engine());
                sender.sendMessage("  pool: " + plugin.poolName());
                sender.sendMessage("  jdbc: " + plugin.jdbcUrl());
            }
            case "reload" -> {
                try {
                    plugin.reloadPool();
                    sender.sendMessage("§aYaPDB pool reloaded.");
                } catch (Exception e) {
                    sender.sendMessage("§cReload failed: " + e.getMessage());
                    plugin.getLogger().severe("yapdb reload: " + e.getMessage());
                }
            }
            default -> sender.sendMessage("Usage: /yapdb <status|reload>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("status", "reload").filter(s -> s.startsWith(p)).toList();
        }
        return List.of();
    }
}
