package com.yapcore.portals.cmd;

import com.yapcore.portals.PortalColors;
import com.yapcore.portals.PortalsPlugin;
import com.yapcore.portals.listener.PortalWandListener;
import com.yapcore.portals.service.PortalServiceImpl;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PortalCommands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "wand", "pos1", "pos2", "create", "delete", "list", "info",
            "settarget", "setperm", "setcooldown", "setmessage", "setcolor",
            "enable", "disable", "go", "reload");

    private final PortalsPlugin plugin;
    private final PortalWandListener wandListener;
    private final PortalDefineOps defineOps;
    private final PortalMetaOps metaOps;

    public PortalCommands(PortalsPlugin plugin, PortalServiceImpl portals, PortalWandListener wandListener) {
        this.plugin = plugin;
        this.wandListener = wandListener;
        this.defineOps = new PortalDefineOps(portals);
        this.metaOps = new PortalMetaOps(portals);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapportals.admin")) {
            YapMessages.noPermission(sender, "yapportals.admin");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/portal wand§7 · §fpos1§7 · §fpos2§7 · §fcreate <name> <server> [color]");
            sender.sendMessage("§e/portal delete|list|info|enable|disable|go|reload");
            sender.sendMessage("§e/portal settarget|setperm|setcooldown|setmessage|setcolor <name> …");
            sender.sendMessage("§7Walk-through colored fields (no glass) → YaP Link Connect.");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "wand" -> handleWand(sender);
            case "pos1" -> defineOps.handlePos(sender, true);
            case "pos2" -> defineOps.handlePos(sender, false);
            case "create", "define" -> defineOps.handleCreate(sender, args);
            case "delete", "remove" -> defineOps.handleDelete(sender, args);
            case "list" -> metaOps.handleList(sender);
            case "info" -> metaOps.handleInfo(sender, args);
            case "settarget" -> metaOps.handleSetTarget(sender, args);
            case "setperm", "setpermission" -> metaOps.handleSetPerm(sender, args);
            case "setcooldown" -> metaOps.handleSetCooldown(sender, args);
            case "setmessage" -> metaOps.handleSetMessage(sender, args);
            case "setcolor" -> metaOps.handleSetColor(sender, args);
            case "enable" -> metaOps.handleEnable(sender, args, true);
            case "disable" -> metaOps.handleEnable(sender, args, false);
            case "go", "enter" -> metaOps.handleGo(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                YapMessages.reloaded(sender, "YaPPortals");
                yield true;
            }
            default -> {
                sender.sendMessage("§cUnknown subcommand. Try §e/portal§c for help.");
                yield true;
            }
        };
    }

    private boolean handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        player.getInventory().addItem(wandListener.createWand());
        sender.sendMessage("§aPortal wand given. §7Left-click = pos1, right-click = pos2, then §f/portal create <name> <server> [color]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapportals.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "delete", "remove", "info", "settarget", "setperm", "setpermission",
                     "setcooldown", "setmessage", "setcolor", "enable", "disable", "go", "enter" ->
                        filter(metaOps.portalNames(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if ("settarget".equals(sub) || "create".equals(sub) || "define".equals(sub)) {
                return filter(List.of("lobby", "survival"), args[2]);
            }
            if ("setcolor".equals(sub)) {
                return filter(PortalColors.names(), args[2]);
            }
        }
        if (args.length == 4 && ("create".equals(sub) || "define".equals(sub))) {
            List<String> opts = new ArrayList<>(PortalColors.names());
            opts.add(0, "at");
            return filter(opts, args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
