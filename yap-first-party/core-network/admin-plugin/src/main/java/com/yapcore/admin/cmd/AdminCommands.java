package com.yapcore.admin.cmd;

import com.yapcore.admin.AdminPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class AdminCommands implements CommandExecutor, TabCompleter {

    private final AdminPlugin plugin;

    public AdminCommands(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapadmin.menu")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("yapadmin.server")) {
                player.sendMessage("§cNo permission.");
                return true;
            }
            plugin.reloadAdminConfig();
            player.sendMessage("§aYaPAdmin config reloaded.");
            return true;
        }
        plugin.menus().openHub(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("yapadmin.server")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(prefix)) {
                return List.of("reload");
            }
        }
        return List.of();
    }
}
