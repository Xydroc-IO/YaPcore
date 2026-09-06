package com.yapcore.tab;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import com.yapcore.messages.YapMessages;

public final class TabCommands implements CommandExecutor {

    private final TabPlugin plugin;

    public TabCommands(TabPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yaptab.admin")) {
            YapMessages.noPermission(sender, "yaptab.admin");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadTab();
            YapMessages.reloaded(sender, "YaPTab");
            return true;
        }
        if (args.length >= 1 && "refresh".equalsIgnoreCase(args[0])) {
            plugin.tabService().refreshAll();
            if (plugin.networkSync() != null) {
                plugin.networkSync().publishLocalSnapshot();
            }
            sender.sendMessage("§aTab list refreshed.");
            return true;
        }
        if (args.length >= 1 && "sync".equalsIgnoreCase(args[0])) {
            if (plugin.networkSync() == null) {
                sender.sendMessage("§cNetwork sync not initialized.");
                return true;
            }
            plugin.networkSync().publishLocalSnapshot();
            sender.sendMessage("§aTab network snapshot published.");
            return true;
        }
        sender.sendMessage("§e/yaptab reload|refresh|sync");
        return true;
    }
}
