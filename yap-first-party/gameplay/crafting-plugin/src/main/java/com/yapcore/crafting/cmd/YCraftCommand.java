package com.yapcore.crafting.cmd;

import com.yapcore.crafting.CraftingPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class YCraftCommand implements CommandExecutor, TabCompleter {

    private final CraftingPlugin plugin;

    public YCraftCommand(CraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapcraft.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("Usage: /ycraft reload");
            return true;
        }
        plugin.reloadCrafting();
        sender.sendMessage("§aYaPCrafting reloaded — recipes=" + plugin.recipeCount());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
