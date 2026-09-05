package com.yapcore.dungeons.cmd;

import com.yapcore.dungeons.DungeonsPlugin;
import com.yapcore.dungeons.DungeonService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class YapDungeonsCommand implements CommandExecutor, TabCompleter {

    private final DungeonsPlugin plugin;
    private final DungeonService service;

    public YapDungeonsCommand(DungeonsPlugin plugin, DungeonService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapdungeons.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/yapdungeons reload|forcestop|giveportal");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadDungeons();
                sender.sendMessage("§aYaPDungeons reloaded.");
                yield true;
            }
            case "forcestop" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e/yapdungeons forcestop <runIdPrefix>");
                    yield true;
                }
                service.forceStop(args[1]).thenAccept(ok ->
                        sender.sendMessage(ok ? "§aStopped." : "§cNot found."));
                yield true;
            }
            case "giveportal" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only for giveportal (or specify online player).");
                    yield true;
                }
                Player target = player;
                if (args.length >= 2) {
                    Player t = org.bukkit.Bukkit.getPlayerExact(args[1]);
                    if (t != null) {
                        target = t;
                    }
                }
                target.getInventory().addItem(service.createPortalItem());
                sender.sendMessage("§aGave dungeon portal to " + target.getName());
                yield true;
            }
            default -> {
                sender.sendMessage("§e/yapdungeons reload|forcestop|giveportal");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : List.of("reload", "forcestop", "giveportal")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
