package com.yapcore.regions.cmd;

import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.service.RegionServiceImpl;
import com.yapcore.world.WorldServices;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class RegionCommands implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final RegionServiceImpl regions;

    public RegionCommands(JavaPlugin plugin, RegionServiceImpl regions) {
        this.plugin = plugin;
        this.regions = regions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapregions.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/region define <name> §7· §e/region flag set <name> <flag> <allow|deny> §7· §e/region list");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "define" -> handleDefine(sender, args);
            case "flag" -> handleFlag(sender, args);
            case "list" -> handleList(sender);
            default -> {
                sender.sendMessage("§cUnknown subcommand. Use define, flag, or list.");
                yield true;
            }
        };
    }

    private boolean handleDefine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region define <name>");
            return true;
        }
        String name = args[1];
        var selectionOpt = WorldServices.selection();
        if (selectionOpt.isEmpty()) {
            sender.sendMessage("§cYaPWorld selection service unavailable. Use //wand pos1/pos2 first.");
            return true;
        }
        var selection = selectionOpt.get().selection(player.getUniqueId());
        if (selection.isEmpty()) {
            sender.sendMessage("§cSet pos1 and pos2 with YaPWorld wand first.");
            return true;
        }
        try {
            var region = regions.define(name, selection.get());
            sender.sendMessage("§aDefined admin region §f" + region.name() + " §7(#" + region.id() + ") in §f"
                    + region.world() + " §7· " + region.minX() + "," + region.minY() + "," + region.minZ()
                    + " → " + region.maxX() + "," + region.maxY() + "," + region.maxZ());
        } catch (Exception e) {
            sender.sendMessage("§cFailed: " + e.getMessage());
            plugin.getLogger().warning("region define: " + e.getMessage());
        }
        return true;
    }

    private boolean handleFlag(CommandSender sender, String[] args) {
        if (args.length < 5 || !"set".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§cUsage: /region flag set <name> <flag> <allow|deny>");
            return true;
        }
        String name = args[2];
        RegionFlag flag = RegionFlag.parse(args[3]).orElse(null);
        if (flag == null) {
            sender.sendMessage("§cUnknown flag. Valid: "
                    + Arrays.toString(RegionFlag.values()).replace('_', '-'));
            return true;
        }
        FlagValue value = FlagValue.parse(args[4]);
        try {
            regions.setFlag(name, flag, value);
            sender.sendMessage("§aSet §f" + flag.name().toLowerCase(Locale.ROOT).replace('_', '-')
                    + " §ato §f" + value.name().toLowerCase(Locale.ROOT) + " §afor region §f" + name);
        } catch (Exception e) {
            sender.sendMessage("§cFailed: " + e.getMessage());
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        var list = regions.listRegions();
        if (list.isEmpty()) {
            sender.sendMessage("§7No admin regions on this server.");
            return true;
        }
        sender.sendMessage("§6Admin regions (" + list.size() + "):");
        for (var region : list) {
            sender.sendMessage("§f" + region.name() + " §7(#" + region.id() + ") · §f" + region.world()
                    + " §7· " + region.minX() + "," + region.minZ() + " → " + region.maxX() + "," + region.maxZ()
                    + " · flags=" + region.flags().size());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapregions.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return prefix(List.of("define", "flag", "list"), args[0]);
        }
        if (args.length == 2 && "flag".equalsIgnoreCase(args[0])) {
            return prefix(List.of("set"), args[1]);
        }
        if (args.length == 3 && "flag".equalsIgnoreCase(args[0])) {
            return prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[2]);
        }
        if (args.length == 4 && "flag".equalsIgnoreCase(args[0])) {
            List<String> flags = new ArrayList<>();
            for (RegionFlag flag : RegionFlag.values()) {
                flags.add(flag.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            }
            return prefix(flags, args[3]);
        }
        if (args.length == 5 && "flag".equalsIgnoreCase(args[0])) {
            return prefix(List.of("allow", "deny"), args[4]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
