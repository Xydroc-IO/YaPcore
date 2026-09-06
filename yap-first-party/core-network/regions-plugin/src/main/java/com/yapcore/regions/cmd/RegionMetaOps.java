package com.yapcore.regions.cmd;

import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionMessageKind;
import com.yapcore.regions.RegionsPlugin;
import com.yapcore.regions.service.RegionServiceImpl;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Locale;

/** Flag / priority / message / template / list / info handlers for {@link RegionCommands}. */
final class RegionMetaOps {

    private final JavaPlugin plugin;
    private final RegionServiceImpl regions;

    RegionMetaOps(JavaPlugin plugin, RegionServiceImpl regions) {
        this.plugin = plugin;
        this.regions = regions;
    }

    boolean handleMessage(CommandSender sender, String[] args) {
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

    boolean handleReload(CommandSender sender) {
        if (plugin instanceof RegionsPlugin regionsPlugin) {
            regionsPlugin.reloadRegions();
            sender.sendMessage("§aReloaded YaPRegions (" + regions.listRegions().size() + " regions).");
        } else {
            regions.reload();
            sender.sendMessage("§aReloaded region cache (" + regions.listRegions().size() + " regions).");
        }
        return true;
    }

    boolean handleInfo(CommandSender sender, String[] args) {
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
                + " §8(vol=" + RegionCommandParse.volumeOf(r) + ")");
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

    boolean handlePriority(CommandSender sender, String[] args) {
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

    boolean handleTemplate(CommandSender sender, String[] args) {
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

    boolean handleApplyTemplate(CommandSender sender, String[] args) {
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

    boolean handleFlag(CommandSender sender, String[] args) {
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

    boolean handleList(CommandSender sender, String[] args) {
        boolean json = args.length >= 2 && "json".equalsIgnoreCase(args[1]);
        var list = regions.listRegions();
        if (json) {
            sender.sendMessage("YAPREGION_JSON:" + RegionCommandParse.toJson(list));
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
}
