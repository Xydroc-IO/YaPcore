package com.yapcore.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class CommandsAdmin implements CommandExecutor, TabCompleter {

    private final CommandsPlugin plugin;

    CommandsAdmin(CommandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapcommands.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/yapcommands reload|list|info <name>|toggle <name>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(ChatColor.GREEN + "YaPCommands reloaded ("
                        + plugin.registry().defs().size() + " defined).");
            }
            case "list" -> {
                var defs = plugin.registry().defs();
                if (defs.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "No custom commands.");
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "Custom commands (" + defs.size() + "):");
                for (CustomCommandDef d : defs.values()) {
                    sender.sendMessage(ChatColor.YELLOW + " /" + d.name()
                            + (d.enabled() ? ChatColor.GREEN + " on" : ChatColor.RED + " off")
                            + ChatColor.GRAY + " — " + d.description());
                }
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /yapcommands info <name>");
                    return true;
                }
                CustomCommandDef d = plugin.registry().defs().get(args[1].toLowerCase(Locale.ROOT));
                if (d == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown command.");
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "/" + d.name()
                        + ChatColor.GRAY + " enabled=" + d.enabled()
                        + " perm=" + d.effectivePermission()
                        + " cd=" + d.cooldownSeconds() + "s");
                sender.sendMessage(ChatColor.GRAY + "aliases=" + d.aliases());
                sender.sendMessage(ChatColor.GRAY + "messages=" + d.messages().size()
                        + " playerCmds=" + d.playerCommands().size()
                        + " consoleCmds=" + d.consoleCommands().size());
            }
            case "toggle" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /yapcommands toggle <name>");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                CustomCommandDef d = plugin.registry().defs().get(name);
                if (d == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown command.");
                    return true;
                }
                try {
                    CustomCommandDef next = new CustomCommandDef(
                            d.name(), !d.enabled(), d.aliases(), d.permission(), d.description(),
                            d.cooldownSeconds(), d.hideNoPermission(), d.messages(),
                            d.playerCommands(), d.consoleCommands(), d.broadcast());
                    plugin.registry().saveDef(next);
                    plugin.reloadAll();
                    sender.sendMessage(ChatColor.GREEN + "/" + name + " is now "
                            + (next.enabled() ? "enabled" : "disabled"));
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Toggle failed: " + e.getMessage());
                }
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "/yapcommands reload|list|info <name>|toggle <name>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapcommands.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("reload", "list", "info", "toggle"), args[0]);
        }
        if (args.length == 2 && ("info".equalsIgnoreCase(args[0]) || "toggle".equalsIgnoreCase(args[0]))) {
            return filter(new ArrayList<>(plugin.registry().defs().keySet()), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
