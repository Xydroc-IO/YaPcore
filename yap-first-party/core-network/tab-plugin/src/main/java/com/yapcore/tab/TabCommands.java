package com.yapcore.tab;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class TabCommands implements CommandExecutor {

    private final TabPlugin plugin;

    public TabCommands(TabPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yaptab.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadTab();
            sender.sendMessage("§aYaPTab reloaded.");
            return true;
        }
        if (args.length >= 1 && "refresh".equalsIgnoreCase(args[0])) {
            plugin.tabService().refreshAll();
            sender.sendMessage("§aTab list refreshed.");
            return true;
        }
        sender.sendMessage("§e/yaptab reload|refresh");
        return true;
    }
}
