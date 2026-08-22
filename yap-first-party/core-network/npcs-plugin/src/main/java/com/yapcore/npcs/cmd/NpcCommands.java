package com.yapcore.npcs.cmd;

import com.yapcore.npcs.service.NpcServiceImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class NpcCommands implements CommandExecutor, TabCompleter {

    private final NpcServiceImpl npcs;

    public NpcCommands(NpcServiceImpl npcs) {
        this.npcs = npcs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapnpcs.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/npc create <id> [name] §7· §e/npc remove <id> §7· §e/npc list");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            default -> {
                sender.sendMessage("§cUnknown subcommand.");
                yield true;
            }
        };
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc create <id> [name]");
            return true;
        }
        String id = args[1];
        String name = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : id;
        if (npcs.create(player, id, name)) {
            sender.sendMessage("§aCreated NPC §f" + id + " §aat your location.");
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /npc remove <id>");
            return true;
        }
        if (npcs.remove(args[1])) {
            sender.sendMessage("§aRemoved NPC §f" + args[1]);
        } else {
            sender.sendMessage("§cNPC not found.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> ids = npcs.listIds();
        if (ids.isEmpty()) {
            sender.sendMessage("§7No NPCs on this server.");
            return true;
        }
        sender.sendMessage("§6NPCs (" + ids.size() + "): §f" + String.join(", ", ids));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapnpcs.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return prefix(List.of("create", "remove", "list"), args[0]);
        }
        if (args.length == 2 && ("remove".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0]))) {
            return prefix(npcs.listIds(), args[1]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
