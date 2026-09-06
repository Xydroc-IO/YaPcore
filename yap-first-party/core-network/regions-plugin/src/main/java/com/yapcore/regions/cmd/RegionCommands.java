package com.yapcore.regions.cmd;

import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.service.RegionServiceImpl;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RegionCommands implements CommandExecutor, TabCompleter {

    private final RegionServiceImpl regions;
    private final RegionDefineOps defineOps;
    private final RegionMetaOps metaOps;

    public RegionCommands(JavaPlugin plugin, RegionServiceImpl regions) {
        this.regions = regions;
        this.defineOps = new RegionDefineOps(plugin, regions);
        this.metaOps = new RegionMetaOps(plugin, regions);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapregions.admin")) {
            YapMessages.noPermission(sender, "yapregions.admin");
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
            case "define" -> defineOps.handleDefine(sender, args);
            case "definepoly" -> defineOps.handleDefinePoly(sender, args);
            case "polyadd", "addpoly", "poly-add" -> defineOps.handlePolyAdd(sender);
            case "polyclear", "clearpoly", "poly-clear" -> defineOps.handlePolyClear(sender);
            case "redefine" -> defineOps.handleRedefine(sender, args);
            case "remove", "delete" -> defineOps.handleRemove(sender, args);
            case "flag" -> metaOps.handleFlag(sender, args);
            case "priority" -> metaOps.handlePriority(sender, args);
            case "message" -> metaOps.handleMessage(sender, args);
            case "template" -> metaOps.handleTemplate(sender, args);
            case "apply-template", "applytemplate" -> metaOps.handleApplyTemplate(sender, args);
            case "list" -> metaOps.handleList(sender, args);
            case "info" -> metaOps.handleInfo(sender, args);
            case "reload" -> metaOps.handleReload(sender);
            default -> {
                sender.sendMessage("§cUnknown subcommand. Use define, definepoly, polyadd, polyclear, redefine, remove, flag, priority, message, template, apply-template, list, info, or reload.");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapregions.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return RegionCommandParse.prefix(List.of("define", "definepoly", "polyadd", "polyclear", "redefine", "remove",
                    "flag", "priority", "message", "template", "apply-template", "list", "info", "reload"), args[0]);
        }
        if (args.length == 2 && ("remove".equalsIgnoreCase(args[0]) || "redefine".equalsIgnoreCase(args[0])
                || "delete".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0])
                || "priority".equalsIgnoreCase(args[0]) || "apply-template".equalsIgnoreCase(args[0])
                || "applytemplate".equalsIgnoreCase(args[0]))) {
            return RegionCommandParse.prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[1]);
        }
        if (args.length == 2 && "template".equalsIgnoreCase(args[0])) {
            return RegionCommandParse.prefix(List.of("save", "list"), args[1]);
        }
        if (args.length == 3 && ("apply-template".equalsIgnoreCase(args[0]) || "applytemplate".equalsIgnoreCase(args[0]))) {
            return RegionCommandParse.prefix(regions.listTemplates(), args[2]);
        }
        if (args.length == 3 && "template".equalsIgnoreCase(args[0]) && "save".equalsIgnoreCase(args[1])) {
            return List.of();
        }
        if (args.length == 4 && "template".equalsIgnoreCase(args[0]) && "save".equalsIgnoreCase(args[1])) {
            return RegionCommandParse.prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[3]);
        }
        if (args.length == 2 && "flag".equalsIgnoreCase(args[0])) {
            return RegionCommandParse.prefix(List.of("set"), args[1]);
        }
        if (args.length == 2 && "message".equalsIgnoreCase(args[0])) {
            return RegionCommandParse.prefix(List.of("set", "clear"), args[1]);
        }
        if (args.length == 3 && "flag".equalsIgnoreCase(args[0])) {
            return RegionCommandParse.prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[2]);
        }
        if (args.length == 3 && "message".equalsIgnoreCase(args[0])) {
            return RegionCommandParse.prefix(regions.listRegions().stream().map(r -> r.name()).toList(), args[2]);
        }
        if (args.length == 4 && "flag".equalsIgnoreCase(args[0])) {
            List<String> flags = new ArrayList<>();
            for (RegionFlag flag : RegionFlag.values()) {
                flags.add(flag.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            }
            return RegionCommandParse.prefix(flags, args[3]);
        }
        if (args.length == 4 && "message".equalsIgnoreCase(args[0])) {
            return RegionCommandParse.prefix(List.of("greeting", "farewell"), args[3]);
        }
        if (args.length == 5 && "flag".equalsIgnoreCase(args[0])) {
            return RegionCommandParse.prefix(List.of("allow", "deny"), args[4]);
        }
        return List.of();
    }
}
