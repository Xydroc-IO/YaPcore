package com.yapcore.regions.cmd;

import com.yapcore.regions.RegionsPlugin;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionMessageKind;
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
            sender.sendMessage("§e/region define <name> §7· §e/region definepoly <name>");
            sender.sendMessage("§e/region polyadd §7· §e/region polyclear §7· §e/region redefine <name>");
            sender.sendMessage("§e/region remove <name> §7· §e/region info <name> §7· §e/region priority <name> <int>");
            sender.sendMessage("§e/region flag set <name> <flag> <allow|deny>");
            sender.sendMessage("§e/region template save <name> <fromRegion> §7· §e/region apply-template <region> <template>");
            sender.sendMessage("§e/region list [json] §7· §e/region reload");
            sender.sendMessage("§e/region message set <name> greeting|farewell <text> §7· §e/region message clear <name> greeting|farewell");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "define" -> handleDefine(sender, args);
            case "definepoly" -> handleDefinePoly(sender, args);
            case "polyadd", "addpoly", "poly-add" -> handlePolyAdd(sender);
            case "polyclear", "clearpoly", "poly-clear" -> handlePolyClear(sender);
            case "redefine" -> handleRedefine(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "flag" -> handleFlag(sender, args);
            case "priority" -> handlePriority(sender, args);
            case "message" -> handleMessage(sender, args);
            case "template" -> handleTemplate(sender, args);
            case "apply-template", "applytemplate" -> handleApplyTemplate(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sender.sendMessage("§cUnknown subcommand. Use define, definepoly, polyadd, polyclear, redefine, remove, flag, priority, message, template, apply-template, list, info, or reload.");
                yield true;
            }
        };
    }

    private boolean handleMessage(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /region message set <name> greeting|farewell <text>");
            sender.sendMessage("§cUsage: /region message clear <name> greeting|farewell");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("set".equals(action)) {
            if (args.length < 5) {
                sender.sendMessage("§cUsage: /region message set <name> greeting|farewell <text>");
                return true;
            }
            String name = args[2];
            RegionMessageKind kind = RegionMessageKind.parse(args[3]).orElse(null);
            if (kind == null) {
                sender.sendMessage("§cKind must be greeting or farewell.");
                return true;
            }
            String text = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
            try {
                regions.setMessage(name, kind, text);
                sender.sendMessage("§aSet §f" + kind.name().toLowerCase(Locale.ROOT)
                        + " §amessage for region §f" + name);
            } catch (Exception e) {
                sender.sendMessage("§cFailed: " + e.getMessage());
            }
            return true;
        }
        if ("clear".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("§cUsage: /region message clear <name> greeting|farewell");
                return true;
            }
            String name = args[2];
            RegionMessageKind kind = RegionMessageKind.parse(args[3]).orElse(null);
            if (kind == null) {
                sender.sendMessage("§cKind must be greeting or farewell.");
                return true;
            }
            try {
                regions.clearMessage(name, kind);
                sender.sendMessage("§aCleared §f" + kind.name().toLowerCase(Locale.ROOT)
                        + " §amessage for region §f" + name);
            } catch (Exception e) {
                sender.sendMessage("§cFailed: " + e.getMessage());
            }
            return true;
        }
        sender.sendMessage("§cUsage: /region message set|clear ...");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (plugin instanceof RegionsPlugin regionsPlugin) {
            regionsPlugin.reloadRegions();
            sender.sendMessage("§aReloaded YaPRegions (" + regions.listRegions().size() + " regions).");
        } else {
            regions.reload();
            sender.sendMessage("§aReloaded region cache (" + regions.listRegions().size() + " regions).");
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region info <name>");
            return true;
        }
        var regionOpt = regions.named(args[1]);
        if (regionOpt.isEmpty()) {
            sender.sendMessage("§cUnknown region: " + args[1]);
            return true;
        }
        var r = regionOpt.get();
        sender.sendMessage("§6Region §f" + r.name()
                + " §7id=" + r.id()
                + " §7world=§f" + r.world()
                + " §7shape=§f" + r.shape().name().toLowerCase(Locale.ROOT)
                + " §7priority=§f" + r.priority());
        sender.sendMessage("§7Bounds §f" + r.minX() + "," + r.minY() + "," + r.minZ()
                + " §7→ §f" + r.maxX() + "," + r.maxY() + "," + r.maxZ()
                + " §8(vol=" + volumeOf(r) + ")");
        if (r.isPolygon()) {
            StringBuilder verts = new StringBuilder("§7Vertices (" + r.vertices().size() + "):");
            for (var v : r.vertices()) {
                verts.append(" §f").append(v.x()).append(',').append(v.z());
            }
            sender.sendMessage(verts.toString());
        }
        if (r.flags().isEmpty()) {
            sender.sendMessage("§7Flags: §8(none — all allow)");
        } else {
            StringBuilder flags = new StringBuilder("§7Flags:");
            for (var entry : r.flags().entrySet()) {
                flags.append(" §f")
                        .append(entry.getKey().name().toLowerCase(Locale.ROOT).replace('_', '-'))
                        .append('=')
                        .append(entry.getValue().name().toLowerCase(Locale.ROOT));
            }
            sender.sendMessage(flags.toString());
        }
        return true;
    }

    private boolean handlePriority(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /region priority <name> <int>");
            return true;
        }
        String name = args[1];
        int priority;
        try {
            priority = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid integer: " + args[2]);
            return true;
        }
        try {
            regions.setPriority(name, priority);
            sender.sendMessage("§aSet priority of region §f" + name + " §ato §f" + priority);
        } catch (Exception e) {
            sender.sendMessage("§cFailed: " + e.getMessage());
        }
        return true;
    }

    private static long volumeOf(com.yapcore.regions.AdminRegion region) {
        return region.volume();
    }

    private boolean handlePolyAdd(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only. Console: /region definepoly <name> at <world> <ymin> <ymax> <x1> <z1> ...");
            return true;
        }
        var loc = player.getLocation();
        regions.polyDrafts().add(player.getUniqueId(), loc.getBlockX(), loc.getBlockZ());
        int n = regions.polyDrafts().size(player.getUniqueId());
        sender.sendMessage("§aPoly vertex #" + n + " §f" + loc.getBlockX() + ", " + loc.getBlockZ()
                + " §7(need ≥3, then /region definepoly <name>)");
        return true;
    }

    private boolean handlePolyClear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        regions.polyDrafts().clear(player.getUniqueId());
        sender.sendMessage("§aCleared pending polygon vertices.");
        return true;
    }

    private boolean handleDefinePoly(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region definepoly <name>");
            sender.sendMessage("§cUsage: /region definepoly <name> at <world> <ymin> <ymax> <x1> <z1> <x2> <z2> ...");
            return true;
        }
        String name = args[1];
        int atIdx = indexOf(args, "at", 2);
        if (atIdx >= 0) {
            if (args.length < atIdx + 7) {
                sender.sendMessage("§cUsage: /region definepoly <name> at <world> <ymin> <ymax> <x1> <z1> <x2> <z2> ... (≥3 vertices)");
                return true;
            }
            String world = args[atIdx + 1];
            int minY = parseInt(args[atIdx + 2], sender);
            int maxY = parseInt(args[atIdx + 3], sender);
            if (minY == Integer.MIN_VALUE || maxY == Integer.MIN_VALUE) {
                return true;
            }
            List<com.yapcore.regions.RegionVertex> verts = new ArrayList<>();
            for (int i = atIdx + 4; i + 1 < args.length; i += 2) {
                int x = parseInt(args[i], sender);
                int z = parseInt(args[i + 1], sender);
                if (x == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
                    return true;
                }
                verts.add(new com.yapcore.regions.RegionVertex(x, z));
            }
            if (verts.size() < 3) {
                sender.sendMessage("§cNeed at least 3 XZ vertices.");
                return true;
            }
            try {
                var region = regions.definePolygon(name, world, minY, maxY, verts);
                sender.sendMessage("§aDefined polygon region §f" + region.name()
                        + " §7(" + region.vertices().size() + " verts) in §f" + region.world());
            } catch (Exception e) {
                sender.sendMessage("§cFailed: " + e.getMessage());
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole: use /region definepoly <name> at <world> <ymin> <ymax> <x1> <z1> ...");
            return true;
        }
        List<com.yapcore.regions.RegionVertex> verts = regions.polyDrafts().points(player.getUniqueId());
        if (verts.size() < 3) {
            sender.sendMessage("§cNeed ≥3 vertices. Use §f/region polyadd §c(stand at each corner) or YaPWorld §f//sel poly §c+ wand, then polyadd.");
            return true;
        }
        int minY;
        int maxY;
        var selectionOpt = WorldServices.selection();
        if (selectionOpt.isPresent()) {
            var selection = selectionOpt.get().selection(player.getUniqueId());
            if (selection.isPresent()) {
                minY = selection.get().minY();
                maxY = selection.get().maxY();
            } else {
                minY = player.getWorld().getMinHeight();
                maxY = player.getWorld().getMaxHeight() - 1;
                sender.sendMessage("§7No cuboid selection — using full world height. Set pos1/pos2 for Y range.");
            }
        } else {
            minY = player.getWorld().getMinHeight();
            maxY = player.getWorld().getMaxHeight() - 1;
        }
        try {
            var region = regions.definePolygon(name, player.getWorld().getName(), minY, maxY, verts);
            regions.polyDrafts().clear(player.getUniqueId());
            sender.sendMessage("§aDefined polygon region §f" + region.name() + " §7(#" + region.id() + ") · "
                    + region.vertices().size() + " verts · y=" + minY + ".." + maxY);
        } catch (Exception e) {
            sender.sendMessage("§cFailed: " + e.getMessage());
            plugin.getLogger().warning("region definepoly: " + e.getMessage());
        }
        return true;
    }

    private boolean handleTemplate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region template save <template> <fromRegion>");
            sender.sendMessage("§cUsage: /region template list");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            var names = regions.listTemplates();
            if (names.isEmpty()) {
                sender.sendMessage("§7No region templates.");
            } else {
                sender.sendMessage("§6Templates: §f" + String.join("§7, §f", names));
            }
            return true;
        }
        if ("save".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("§cUsage: /region template save <template> <fromRegion>");
                return true;
            }
            try {
                regions.saveTemplate(args[2], args[3]);
                sender.sendMessage("§aSaved template §f" + args[2] + " §afrom region §f" + args[3]);
            } catch (Exception e) {
                sender.sendMessage("§cFailed: " + e.getMessage());
            }
            return true;
        }
        sender.sendMessage("§cUsage: /region template save|list ...");
        return true;
    }

    private boolean handleApplyTemplate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /region apply-template <region> <template>");
            return true;
        }
        try {
            regions.applyTemplate(args[1], args[2]);
            sender.sendMessage("§aApplied template §f" + args[2] + " §ato region §f" + args[1]);
        } catch (Exception e) {
            sender.sendMessage("§cFailed: " + e.getMessage());
        }
        return true;
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
                    + " §7· " + region.shape().name().toLowerCase(Locale.ROOT)
                    + " · " + region.minX() + "," + region.minZ() + " → " + region.maxX() + "," + region.maxZ()
                    + " · prio=" + region.priority()
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
                    .append("\"shape\":").append(q(r.shape().name().toLowerCase(Locale.ROOT))).append(',')
                    .append("\"minX\":").append(r.minX()).append(',')
                    .append("\"minY\":").append(r.minY()).append(',')
                    .append("\"minZ\":").append(r.minZ()).append(',')
                    .append("\"maxX\":").append(r.maxX()).append(',')
                    .append("\"maxY\":").append(r.maxY()).append(',')
                    .append("\"maxZ\":").append(r.maxZ()).append(',')
                    .append("\"priority\":").append(r.priority()).append(',')
                    .append("\"flagCount\":").append(r.flags().size()).append(',')
                    .append("\"vertexCount\":").append(r.vertices().size()).append(',')
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
            return prefix(List.of("define", "definepoly", "polyadd", "polyclear", "redefine", "remove",
                    "flag", "priority", "message", "template", "apply-template", "list", "info", "reload"), args[0]);
        }
        if (args.length == 2 && ("remove".equalsIgnoreCase(args[0]) || "redefine".equalsIgnoreCase(args[0])
                || "delete".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0])
                || "priority".equalsIgnoreCase(args[0]) || "apply-template".equalsIgnoreCase(args[0])
                || "applytemplate".equalsIgnoreCase(args[0]))) {
            return prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[1]);
        }
        if (args.length == 2 && "template".equalsIgnoreCase(args[0])) {
            return prefix(List.of("save", "list"), args[1]);
        }
        if (args.length == 3 && ("apply-template".equalsIgnoreCase(args[0]) || "applytemplate".equalsIgnoreCase(args[0]))) {
            return prefix(regions.listTemplates(), args[2]);
        }
        if (args.length == 3 && "template".equalsIgnoreCase(args[0]) && "save".equalsIgnoreCase(args[1])) {
            return List.of();
        }
        if (args.length == 4 && "template".equalsIgnoreCase(args[0]) && "save".equalsIgnoreCase(args[1])) {
            return prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[3]);
        }
        if (args.length == 2 && "flag".equalsIgnoreCase(args[0])) {
            return prefix(List.of("set"), args[1]);
        }
        if (args.length == 2 && "message".equalsIgnoreCase(args[0])) {
            return prefix(List.of("set", "clear"), args[1]);
        }
        if (args.length == 3 && "flag".equalsIgnoreCase(args[0])) {
            return prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[2]);
        }
        if (args.length == 3 && "message".equalsIgnoreCase(args[0])) {
            return prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[2]);
        }
        if (args.length == 4 && "flag".equalsIgnoreCase(args[0])) {
            List<String> flags = new ArrayList<>();
            for (RegionFlag flag : RegionFlag.values()) {
                flags.add(flag.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            }
            return prefix(flags, args[3]);
        }
        if (args.length == 4 && "message".equalsIgnoreCase(args[0])) {
            return prefix(List.of("greeting", "farewell"), args[3]);
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
