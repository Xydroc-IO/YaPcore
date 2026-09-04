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
            sender.sendMessage("§e/region define <name> §7· §e/region define <name> at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
            sender.sendMessage("§e/region redefine <name> §7· §e/region remove <name>");
            sender.sendMessage("§e/region flag set <name> <flag> <allow|deny> §7· §e/region list [json]");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "define" -> handleDefine(sender, args);
            case "redefine" -> handleRedefine(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "flag" -> handleFlag(sender, args);
            case "list" -> handleList(sender, args);
            default -> {
                sender.sendMessage("§cUnknown subcommand. Use define, redefine, remove, flag, or list.");
                yield true;
            }
        };
    }

    private boolean handleDefine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region define <name> [at <world> <x1> <y1> <z1> <x2> <y2> <z2>]");
            return true;
        }
        String name = args[1];
        int atIdx = indexOf(args, "at", 2);
        if (atIdx >= 0) {
            if (args.length < atIdx + 8) {
                sender.sendMessage("§cUsage: /region define <name> at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
                return true;
            }
            String world = args[atIdx + 1];
            int x1 = parseInt(args[atIdx + 2], sender);
            int y1 = parseInt(args[atIdx + 3], sender);
            int z1 = parseInt(args[atIdx + 4], sender);
            int x2 = parseInt(args[atIdx + 5], sender);
            int y2 = parseInt(args[atIdx + 6], sender);
            int z2 = parseInt(args[atIdx + 7], sender);
            if (x1 == Integer.MIN_VALUE) {
                return true;
            }
            try {
                var region = regions.defineAt(name, world, x1, y1, z1, x2, y2, z2);
                sender.sendMessage("§aDefined admin region §f" + region.name() + " §7in §f" + region.world());
            } catch (Exception e) {
                sender.sendMessage("§cFailed: " + e.getMessage());
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole: use /region define <name> at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
            return true;
        }
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

    private boolean handleRedefine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region redefine <name> [at <world> <x1> <y1> <z1> <x2> <y2> <z2>]");
            return true;
        }
        String name = args[1];
        int atIdx = indexOf(args, "at", 2);
        if (atIdx >= 0) {
            if (args.length < atIdx + 8) {
                sender.sendMessage("§cUsage: /region redefine <name> at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
                return true;
            }
            String world = args[atIdx + 1];
            int x1 = parseInt(args[atIdx + 2], sender);
            int y1 = parseInt(args[atIdx + 3], sender);
            int z1 = parseInt(args[atIdx + 4], sender);
            int x2 = parseInt(args[atIdx + 5], sender);
            int y2 = parseInt(args[atIdx + 6], sender);
            int z2 = parseInt(args[atIdx + 7], sender);
            if (x1 == Integer.MIN_VALUE) {
                return true;
            }
            try {
                var region = regions.redefineAt(name, world, x1, y1, z1, x2, y2, z2);
                sender.sendMessage("§aRedefined admin region §f" + region.name() + " §7in §f" + region.world());
            } catch (Exception e) {
                sender.sendMessage("§cFailed: " + e.getMessage());
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole: use /region redefine <name> at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
            return true;
        }
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
            var region = regions.redefine(name, selection.get());
            sender.sendMessage("§aRedefined admin region §f" + region.name() + " §7(#" + region.id() + ") in §f"
                    + region.world() + " §7· " + region.minX() + "," + region.minY() + "," + region.minZ()
                    + " → " + region.maxX() + "," + region.maxY() + "," + region.maxZ());
        } catch (Exception e) {
            sender.sendMessage("§cFailed: " + e.getMessage());
            plugin.getLogger().warning("region redefine: " + e.getMessage());
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region remove <name>");
            return true;
        }
        try {
            regions.remove(args[1]);
            sender.sendMessage("§aRemoved admin region §f" + args[1]);
        } catch (Exception e) {
            sender.sendMessage("§cFailed: " + e.getMessage());
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

    private boolean handleList(CommandSender sender, String[] args) {
        boolean json = args.length >= 2 && "json".equalsIgnoreCase(args[1]);
        var list = regions.listRegions();
        if (json) {
            sender.sendMessage("YAPREGION_JSON:" + toJson(list));
            return true;
        }
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

    private static String toJson(List<com.yapcore.regions.AdminRegion> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            var r = list.get(i);
            sb.append('{')
                    .append("\"name\":").append(q(r.name())).append(',')
                    .append("\"id\":").append(r.id()).append(',')
                    .append("\"world\":").append(q(r.world())).append(',')
                    .append("\"minX\":").append(r.minX()).append(',')
                    .append("\"minY\":").append(r.minY()).append(',')
                    .append("\"minZ\":").append(r.minZ()).append(',')
                    .append("\"maxX\":").append(r.maxX()).append(',')
                    .append("\"maxY\":").append(r.maxY()).append(',')
                    .append("\"maxZ\":").append(r.maxZ()).append(',')
                    .append("\"flagCount\":").append(r.flags().size()).append(',')
                    .append("\"flags\":{");
            int fi = 0;
            for (var entry : r.flags().entrySet()) {
                if (fi++ > 0) {
                    sb.append(',');
                }
                String key = entry.getKey().name().toLowerCase(Locale.ROOT).replace('_', '-');
                sb.append(q(key)).append(':')
                        .append(q(entry.getValue().name().toLowerCase(Locale.ROOT)));
            }
            sb.append("}}");
        }
        return sb.append(']').toString();
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static int indexOf(String[] args, String needle, int from) {
        for (int i = from; i < args.length; i++) {
            if (needle.equalsIgnoreCase(args[i])) {
                return i;
            }
        }
        return -1;
    }

    private static int parseInt(String raw, CommandSender sender) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid integer: " + raw);
            return Integer.MIN_VALUE;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapregions.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return prefix(List.of("define", "redefine", "remove", "flag", "list"), args[0]);
        }
        if (args.length == 2 && ("remove".equalsIgnoreCase(args[0]) || "redefine".equalsIgnoreCase(args[0])
                || "delete".equalsIgnoreCase(args[0]))) {
            return prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[1]);
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
