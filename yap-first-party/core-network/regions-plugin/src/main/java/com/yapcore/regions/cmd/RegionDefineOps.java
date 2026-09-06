package com.yapcore.regions.cmd;

import com.yapcore.regions.service.RegionServiceImpl;
import com.yapcore.world.WorldServices;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Define / redefine / polygon handlers for {@link RegionCommands}. */
final class RegionDefineOps {

    private final JavaPlugin plugin;
    private final RegionServiceImpl regions;

    RegionDefineOps(JavaPlugin plugin, RegionServiceImpl regions) {
        this.plugin = plugin;
        this.regions = regions;
    }

    boolean handlePolyAdd(CommandSender sender) {
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

    boolean handlePolyClear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        regions.polyDrafts().clear(player.getUniqueId());
        sender.sendMessage("§aCleared pending polygon vertices.");
        return true;
    }

    boolean handleDefinePoly(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region definepoly <name>");
            sender.sendMessage("§cUsage: /region definepoly <name> at <world> <ymin> <ymax> <x1> <z1> <x2> <z2> ...");
            return true;
        }
        String name = args[1];
        int atIdx = RegionCommandParse.indexOf(args, "at", 2);
        if (atIdx >= 0) {
            if (args.length < atIdx + 7) {
                sender.sendMessage("§cUsage: /region definepoly <name> at <world> <ymin> <ymax> <x1> <z1> <x2> <z2> ... (≥3 vertices)");
                return true;
            }
            String world = args[atIdx + 1];
            int minY = RegionCommandParse.parseInt(args[atIdx + 2], sender);
            int maxY = RegionCommandParse.parseInt(args[atIdx + 3], sender);
            if (minY == Integer.MIN_VALUE || maxY == Integer.MIN_VALUE) {
                return true;
            }
            List<com.yapcore.regions.RegionVertex> verts = new ArrayList<>();
            for (int i = atIdx + 4; i + 1 < args.length; i += 2) {
                int x = RegionCommandParse.parseInt(args[i], sender);
                int z = RegionCommandParse.parseInt(args[i + 1], sender);
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

    boolean handleDefine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region define <name> [at <world> <x1> <y1> <z1> <x2> <y2> <z2>]");
            return true;
        }
        String name = args[1];
        int atIdx = RegionCommandParse.indexOf(args, "at", 2);
        if (atIdx >= 0) {
            if (args.length < atIdx + 8) {
                sender.sendMessage("§cUsage: /region define <name> at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
                return true;
            }
            String world = args[atIdx + 1];
            int x1 = RegionCommandParse.parseInt(args[atIdx + 2], sender);
            int y1 = RegionCommandParse.parseInt(args[atIdx + 3], sender);
            int z1 = RegionCommandParse.parseInt(args[atIdx + 4], sender);
            int x2 = RegionCommandParse.parseInt(args[atIdx + 5], sender);
            int y2 = RegionCommandParse.parseInt(args[atIdx + 6], sender);
            int z2 = RegionCommandParse.parseInt(args[atIdx + 7], sender);
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

    boolean handleRedefine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region redefine <name> [at <world> <x1> <y1> <z1> <x2> <y2> <z2>]");
            return true;
        }
        String name = args[1];
        int atIdx = RegionCommandParse.indexOf(args, "at", 2);
        if (atIdx >= 0) {
            if (args.length < atIdx + 8) {
                sender.sendMessage("§cUsage: /region redefine <name> at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
                return true;
            }
            String world = args[atIdx + 1];
            int x1 = RegionCommandParse.parseInt(args[atIdx + 2], sender);
            int y1 = RegionCommandParse.parseInt(args[atIdx + 3], sender);
            int z1 = RegionCommandParse.parseInt(args[atIdx + 4], sender);
            int x2 = RegionCommandParse.parseInt(args[atIdx + 5], sender);
            int y2 = RegionCommandParse.parseInt(args[atIdx + 6], sender);
            int z2 = RegionCommandParse.parseInt(args[atIdx + 7], sender);
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

    boolean handleRemove(CommandSender sender, String[] args) {
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
}
