package com.yapcore.perms.cmd;

import com.yapcore.perms.PermsPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PermsCommands implements CommandExecutor, TabCompleter {

    private final PermsPlugin plugin;
    private final PermsUserCommands users;
    private final PermsGroupCommands groups;
    private final PermsTrackCommands tracks;
    private final PermsIoCommands io;

    public PermsCommands(PermsPlugin plugin) {
        this.plugin = plugin;
        this.users = new PermsUserCommands(plugin);
        this.groups = new PermsGroupCommands(plugin);
        this.tracks = new PermsTrackCommands(plugin);
        this.io = new PermsIoCommands(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("promote".equals(name)) {
            return tracks.promote(sender, args);
        }
        if ("demote".equals(name)) {
            return tracks.demote(sender, args);
        }
        return yapperm(sender, args);
    }

    private boolean yapperm(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.ranksGui().openHub(player);
                return true;
            }
            sender.sendMessage("§e/yapperm gui|user|group|track|check|export|import|dump|editor-apply|reload|applypack");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return switch (sub) {
            case "gui", "menu", "ranks" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    yield true;
                }
                plugin.ranksGui().openHub(player);
                yield true;
            }
            case "reload" -> {
                if (!sender.hasPermission("yapperm.admin")) {
                    sender.sendMessage("§cNo permission.");
                    yield true;
                }
                plugin.reloadAll();
                sender.sendMessage("§aYaPPerms reloaded.");
                yield true;
            }
            case "applypack" -> {
                if (!sender.hasPermission("yapperm.admin")) {
                    sender.sendMessage("§cNo permission.");
                    yield true;
                }
                YapSched.async(plugin, () -> {
                    try {
                        plugin.repository().applyStarterPackFromConfig();
                        YapSched.global(plugin, () -> {
                            plugin.reloadAll();
                            sender.sendMessage("§aStarter rank pack applied.");
                        });
                    } catch (Exception e) {
                        YapSched.global(plugin, () ->
                                sender.sendMessage("§cApply pack failed: " + e.getMessage()));
                    }
                });
                yield true;
            }
            case "user" -> users.userCmd(sender, rest);
            case "group" -> groups.groupCmd(sender, rest);
            case "track" -> tracks.trackCmd(sender, rest);
            case "check", "verbose" -> checkCmd(sender, rest);
            case "export" -> io.exportCmd(sender, rest);
            case "import" -> io.importCmd(sender, rest);
            case "dump" -> io.dumpCmd(sender);
            case "editor-apply" -> io.editorApplyCmd(sender);
            default -> {
                sender.sendMessage("§e/yapperm gui|user|group|track|check|export|import|dump|editor-apply|reload|applypack");
                yield true;
            }
        };
    }

    private boolean checkCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapperm.admin") && !sender.hasPermission("yapperm.user")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapperm check <player> <node> [world]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String node = args[1];
        String world = args.length >= 3 ? args[2] : "";
        if (world.isBlank() && sender instanceof Player player) {
            world = player.getWorld().getName();
        }
        sender.sendMessage(plugin.explain(target.getUniqueId(),
                target.getName() != null ? target.getName() : args[0], node, world));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapperm.admin")) {
            return List.of();
        }
        if ("promote".equalsIgnoreCase(command.getName()) || "demote".equalsIgnoreCase(command.getName())) {
            if (args.length == 1) {
                return PermsCmdSupport.partial(args[0], PermsCmdSupport.onlineNames());
            }
            if (args.length == 2) {
                return PermsCmdSupport.partial(args[1], plugin.resolver().tracks().keySet());
            }
            return List.of();
        }
        if (args.length == 1) {
            return PermsCmdSupport.partial(args[0], List.of("gui", "menu", "ranks", "user", "group", "track",
                    "check", "verbose", "export", "import", "dump", "editor-apply", "reload", "applypack"));
        }
        if ("group".equalsIgnoreCase(args[0]) && args.length == 2) {
            return PermsCmdSupport.partial(args[1], List.of("list", "info", "create", "delete", "setprefix", "setsuffix",
                    "setnamecolor", "setchatcolor", "permission", "parent"));
        }
        if ("track".equalsIgnoreCase(args[0]) && args.length == 2) {
            return PermsCmdSupport.partial(args[1], List.of("list", "info", "create", "append", "remove", "delete"));
        }
        return List.of();
    }
}
